package org.cloudvision.trading.provider.impl;

import org.cloudvision.trading.model.*;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mock Trading Provider for testing
 * Generates random but realistic market data
 */
@Component
public class MockTradingProvider implements TradingDataProvider {
    
    private Consumer<TradingData> dataHandler;
    private boolean connected = false;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);
    private final Random random = new Random();
    
    // Configuration
    private long tickerUpdateMillis = 100; // Update every 100ms (10x per second) - FAST!
    private BigDecimal defaultVolatility = new BigDecimal("0.001"); // 0.1% per tick
    private MarketScenario currentScenario = MarketScenario.NORMAL;
    
    // Market simulation parameters
    private final Map<String, MarketState> marketStates = new HashMap<>();
    private final Map<String, List<TimeInterval>> subscribedKlines = new HashMap<>();
    
    // Cache for historical data - key is "symbol_interval"
    private final Map<String, List<CandlestickData>> historicalCache = new ConcurrentHashMap<>();
    
    public enum MarketScenario {
        BULL_RUN,      // Strong uptrend with high momentum
        BEAR_MARKET,   // Strong downtrend 
        VOLATILE,      // High volatility, choppy
        SIDEWAYS,      // Range-bound, low volatility
        PUMP_AND_DUMP, // Sudden spike followed by crash
        NORMAL         // Normal random walk
    }
    
    private static class MarketState {
        BigDecimal currentPrice;
        BigDecimal basePrice; // Anchor price for mean reversion
        BigDecimal trend; // Positive = uptrend, Negative = downtrend
        BigDecimal volatility; // Price change percentage
        BigDecimal momentum; // Acceleration of price movement
        Instant lastUpdate;
        int ticksSinceTrendChange;
        MarketScenario scenario;
        
        // For pump and dump
        boolean isPumping;
        boolean isDumping;
        int pumpTicks;
        
        MarketState(BigDecimal startPrice, BigDecimal volatility) {
            this.currentPrice = startPrice;
            this.basePrice = startPrice; // Set base price for mean reversion
            this.trend = BigDecimal.ZERO;
            this.volatility = volatility;
            this.momentum = BigDecimal.ZERO;
            this.lastUpdate = Instant.now();
            this.ticksSinceTrendChange = 0;
            this.scenario = MarketScenario.NORMAL;
            this.isPumping = false;
            this.isDumping = false;
            this.pumpTicks = 0;
        }
    }
    
    // Configuration methods
    public void setTickerUpdateSpeed(long millis) {
        this.tickerUpdateMillis = millis;
        System.out.println("⚙️ Mock ticker update speed set to " + millis + "ms");
    }
    
    public void setDefaultVolatility(BigDecimal volatility) {
        this.defaultVolatility = volatility;
        System.out.println("⚙️ Mock default volatility set to " + volatility);
    }
    
    public void setMarketScenario(MarketScenario scenario) {
        this.currentScenario = scenario;
        System.out.println("📊 Market scenario changed to: " + scenario);
        
        // Update all market states
        for (MarketState state : marketStates.values()) {
            state.scenario = scenario;
            applyScenarioToState(state);
        }
    }
    
    public void setSymbolVolatility(String symbol, BigDecimal volatility) {
        MarketState state = marketStates.get(symbol);
        if (state != null) {
            state.volatility = volatility;
            System.out.println("⚙️ " + symbol + " volatility set to " + volatility);
        }
    }
    
    // Cache management methods
    public void clearHistoricalCache() {
        historicalCache.clear();
        System.out.println("🗑️ Historical data cache cleared");
    }
    
    public void clearHistoricalCache(String symbol) {
        int removed = 0;
        Iterator<String> iterator = historicalCache.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(symbol + "_")) {
                iterator.remove();
                removed++;
            }
        }
        System.out.println("🗑️ Historical data cache cleared for " + symbol + " (" + removed + " entries removed)");
    }
    
    public int getCacheSize() {
        return historicalCache.size();
    }
    
    public Map<String, Integer> getCacheInfo() {
        Map<String, Integer> info = new HashMap<>();
        historicalCache.forEach((key, value) -> info.put(key, value.size()));
        return info;
    }
    
    /**
     * Get the latest price for a symbol from either market state or cache
     */
    public BigDecimal getLatestPrice(String symbol) {
        MarketState state = marketStates.get(symbol);
        if (state != null) {
            return state.currentPrice;
        }
        
        // Fallback to cache
        for (String cacheKey : historicalCache.keySet()) {
            if (cacheKey.startsWith(symbol + "_")) {
                List<CandlestickData> cachedData = historicalCache.get(cacheKey);
                if (!cachedData.isEmpty()) {
                    return cachedData.get(cachedData.size() - 1).getClose();
                }
            }
        }
        
        return null;
    }

    @Override
    public void connect() {
        if (connected) {
            System.out.println("🔗 Mock provider already connected");
            return;
        }
        
        connected = true;
        System.out.println("✅ Mock Trading Provider connected (generating random data)");
    }

    @Override
    public void disconnect() {
        connected = false;
        executorService.shutdownNow();
        System.out.println("🔌 Mock Trading Provider disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void setDataHandler(Consumer<TradingData> handler) {
        this.dataHandler = handler;
    }

    @Override
    public void subscribe(String symbol) {
        if (!connected) {
            connect();
        }
        
        // Initialize market state for symbol
        if (!marketStates.containsKey(symbol)) {
            BigDecimal startPrice = getStartPriceForSymbol(symbol);
            MarketState state = new MarketState(startPrice, defaultVolatility);
            state.scenario = currentScenario;
            applyScenarioToState(state);
            marketStates.put(symbol, state);
            System.out.println("📈 Initialized " + symbol + " market state at price: " + startPrice);
        }
        
        // Start generating ticker data at configured speed (default 100ms = 10 updates/second)
        executorService.scheduleAtFixedRate(() -> generateTickerData(symbol), 
            0, tickerUpdateMillis, TimeUnit.MILLISECONDS);
        
        System.out.println("📊 Mock provider subscribed to " + symbol + " ticker (updates every " + 
            tickerUpdateMillis + "ms)");
    }

    @Override
    public void subscribeToKlines(String symbol, TimeInterval interval) {
        if (!connected) {
            connect();
        }
        
        // Initialize market state
        if (!marketStates.containsKey(symbol)) {
            BigDecimal startPrice = getStartPriceForSymbol(symbol);
            MarketState state = new MarketState(startPrice, defaultVolatility);
            state.scenario = currentScenario;
            applyScenarioToState(state);
            marketStates.put(symbol, state);
            System.out.println("📈 Initialized " + symbol + " market state at price: " + startPrice);
        }
        
        // Initialize historical cache if not exists - so live candles can be appended
        String cacheKey = symbol + "_" + interval.getValue();
        if (!historicalCache.containsKey(cacheKey)) {
            // Generate initial historical data (500 candles by default for subscriptions)
            System.out.println("📊 Initializing historical cache for live kline subscription: " + symbol + " " + interval.getValue());
            getHistoricalKlines(symbol, interval, 500);
        }
        
        subscribedKlines.computeIfAbsent(symbol, k -> new ArrayList<>()).add(interval);
        
        // Generate kline data at interval rate
        // Start after one interval to avoid overlap with historical data
        long delaySeconds = getIntervalSeconds(interval);
        executorService.scheduleAtFixedRate(() -> generateKlineData(symbol, interval), 
            delaySeconds, delaySeconds, TimeUnit.SECONDS);
        
        System.out.println("📊 Mock provider subscribed to " + symbol + " " + interval.getValue() + 
            " klines (first candle in " + delaySeconds + " seconds)");
    }

    @Override
    public void unsubscribeFromKlines(String symbol, TimeInterval interval) {
        List<TimeInterval> intervals = subscribedKlines.get(symbol);
        if (intervals != null) {
            intervals.remove(interval);
            if (intervals.isEmpty()) {
                subscribedKlines.remove(symbol);
            }
            System.out.println("🔕 Mock provider unsubscribed from " + symbol + " " + interval.getValue() + " klines");
        }
    }

    @Override
    public void unsubscribe(String symbol) {
        subscribedKlines.remove(symbol);
        System.out.println("🔕 Mock provider unsubscribed from " + symbol);
    }

    @Override
    public String getProviderName() {
        return "Mock";
    }

    @Override
    public List<org.cloudvision.trading.model.TradeData> getHistoricalTrades(String symbol, int limit) {
        // Mock provider doesn't support historical data
        return new ArrayList<>();
    }
    
    @Override
    public List<org.cloudvision.trading.model.TradeData> getHistoricalAggregateTrades(String symbol, int limit) {
        // Mock provider doesn't support historical data
        return new ArrayList<>();
    }
    
    @Override
    public List<org.cloudvision.trading.model.TradeData> getHistoricalAggregateTrades(String symbol, java.time.Instant startTime, java.time.Instant endTime, int limit) {
        // Mock provider doesn't support historical data
        return new ArrayList<>();
    }
    
    @Override
    public org.cloudvision.trading.model.OrderBookData getOrderBookSnapshot(String symbol, int limit) {
        // Mock provider doesn't support order book snapshots
        return null;
    }
    
    @Override
    public List<String> getSupportedSymbols() {
        return List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "SOLUSDT");
    }

    @Override
    public List<TimeInterval> getSupportedIntervals() {
        return Arrays.asList(TimeInterval.values());
    }

    @Override
    public List<CandlestickData> getHistoricalKlines(String symbol, TimeInterval interval, int limit) {
        String cacheKey = symbol + "_" + interval.getValue(); // Removed limit from cache key
        
        // Check cache first
        if (historicalCache.containsKey(cacheKey)) {
            List<CandlestickData> cachedData = historicalCache.get(cacheKey);
            
            // Return the last 'limit' candles from cache (most recent)
            int startIndex = Math.max(0, cachedData.size() - limit);
            List<CandlestickData> result = new ArrayList<>(cachedData.subList(startIndex, cachedData.size()));
            
            System.out.println("📦 Returning cached historical data for " + symbol + " " + interval.getValue() + 
                " (requested: " + limit + ", returning: " + result.size() + " from cache of " + cachedData.size() + ")");
            return result;
        }
        
        System.out.println("📊 Mock provider generating " + limit + " historical candles for " + symbol);
        
        List<CandlestickData> candles = new ArrayList<>();
        
        long intervalSeconds = getIntervalSeconds(interval);
        Instant now = Instant.now();
        
        // Calculate proper start time (align to interval boundaries)
        Instant startTime = now.minusSeconds(intervalSeconds * limit);
        
        // Use existing market state price if available, otherwise generate a start price
        MarketState state = marketStates.get(symbol);
        BigDecimal startPrice = state != null ? state.currentPrice : getRandomStartPrice(symbol);
        
        // Work backwards from the current price to make history consistent
        BigDecimal currentPrice = startPrice;
        
        for (int i = 0; i < limit; i++) {
            Instant openTime = startTime.plusSeconds(intervalSeconds * i);
            Instant closeTime = openTime.plusSeconds(intervalSeconds);
            
            // Generate realistic OHLC using consistent price change logic
            BigDecimal open = currentPrice;
            
            // Use a percentage change that scales with the interval (longer intervals = bigger moves)
            BigDecimal volatilityFactor = new BigDecimal("0.005").multiply(
                new BigDecimal(Math.sqrt(intervalSeconds / 60.0))
            );
            BigDecimal change = generatePriceChange(currentPrice, volatilityFactor);
            BigDecimal close = open.add(change);
            
            // High and low should respect the open-close range
            BigDecimal wickVolatility = volatilityFactor.multiply(new BigDecimal("0.5"));
            BigDecimal highWick = generatePriceChange(open, wickVolatility).abs();
            BigDecimal lowWick = generatePriceChange(open, wickVolatility).abs();
            
            BigDecimal high = open.max(close).add(highWick);
            BigDecimal low = open.min(close).subtract(lowWick);
            
            // Volume scales with interval duration (longer intervals = more volume)
            BigDecimal baseVolume = generateVolume();
            BigDecimal volume = baseVolume.multiply(new BigDecimal(intervalSeconds / 60.0));
            BigDecimal quoteVolume = volume.multiply(open.add(close).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP));
            
            candles.add(new CandlestickData(
                symbol,
                openTime,
                closeTime,
                open,
                high,
                low,
                close,
                volume,
                quoteVolume,
                random.nextInt(1000) + 100,
                interval.getValue(),
                getProviderName(),
                true
            ));
            
            currentPrice = close;
        }
        
        // Store in cache (or update if exists)
        List<CandlestickData> existingCache = historicalCache.get(cacheKey);
        if (existingCache != null) {
            // Cache exists - only add new candles that don't overlap
            Instant lastCachedTime = existingCache.get(existingCache.size() - 1).getCloseTime();
            for (CandlestickData candle : candles) {
                if (candle.getOpenTime().isAfter(lastCachedTime)) {
                    existingCache.add(candle);
                }
            }
            // Maintain max cache size
            while (existingCache.size() > 2000) {
                existingCache.remove(0);
            }
            System.out.println("📦 Updated cache for " + symbol + " " + interval.getValue() + 
                ", now has " + existingCache.size() + " candles");
        } else {
            // New cache entry
            historicalCache.put(cacheKey, new ArrayList<>(candles));
            System.out.println("✅ Generated and cached " + candles.size() + " historical candles for " + symbol);
        }
        
        // Update market state with the final price to ensure continuity with live data
        if (!candles.isEmpty()) {
            MarketState marketState = marketStates.get(symbol);
            if (marketState != null) {
                BigDecimal lastClose = candles.get(candles.size() - 1).getClose();
                marketState.currentPrice = lastClose;
                marketState.basePrice = lastClose; // Update base price for mean reversion
                marketState.trend = BigDecimal.ZERO; // Reset trend
                marketState.momentum = BigDecimal.ZERO; // Reset momentum
                System.out.println("🔄 Updated " + symbol + " market state to last historical price: " + lastClose);
            }
        }
        
        return candles;
    }

    @Override
    public List<CandlestickData> getHistoricalKlines(String symbol, TimeInterval interval, 
                                                     Instant startTime, Instant endTime) {
        long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        long intervalSeconds = getIntervalSeconds(interval);
        int limit = (int) (durationSeconds / intervalSeconds);
        
        return getHistoricalKlines(symbol, interval, Math.min(limit, 1000));
    }

    // Helper methods
    
    private void generateTickerData(String symbol) {
        if (dataHandler == null) return;
        
        try {
            MarketState state = marketStates.get(symbol);
            if (state == null) return;
            
            // Update based on market scenario
            updateMarketState(state);
            
            // Generate price change with trend, momentum, and scenario
            BigDecimal priceChange = calculatePriceChange(state);
            state.currentPrice = state.currentPrice.add(priceChange);
            
            // Ensure price doesn't go negative or too low
            if (state.currentPrice.compareTo(new BigDecimal("0.01")) < 0) {
                state.currentPrice = new BigDecimal("0.01");
            }
            
            // Log price deviation warnings (every 1000 ticks = ~100 seconds)
            if (state.ticksSinceTrendChange % 1000 == 0) {
                BigDecimal deviation = state.currentPrice.subtract(state.basePrice);
                BigDecimal deviationPercent = deviation.divide(state.basePrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                if (deviationPercent.abs().compareTo(new BigDecimal("5")) > 0) {
                    System.out.println(String.format("⚠️ %s price deviation: %.2f%% (Current: %s, Base: %s)", 
                        symbol, deviationPercent, state.currentPrice, state.basePrice));
                }
            }
            
            state.ticksSinceTrendChange++;
            
            BigDecimal volume = generateVolume();
            
            // Update the last candle in all subscribed interval caches with current price
            updateLatestCandlesWithCurrentPrice(symbol, state.currentPrice, volume);
            
            TradingData data = new TradingData(
                symbol,
                state.currentPrice,
                volume,
                Instant.now(),
                getProviderName(),
                TradingDataType.TICKER
            );
            
            dataHandler.accept(data);
            
        } catch (Exception e) {
            System.err.println("❌ Mock provider error generating ticker: " + e.getMessage());
        }
    }
    
    /**
     * Update the last (current) candle in historical cache with latest ticker price
     * This makes the historical data reflect real-time price changes
     */
    private void updateLatestCandlesWithCurrentPrice(String symbol, BigDecimal currentPrice, BigDecimal volume) {
        List<TimeInterval> intervals = subscribedKlines.get(symbol);
        if (intervals == null || intervals.isEmpty()) return;
        
        Instant now = Instant.now();
        int updatedCount = 0;
        
        for (TimeInterval interval : intervals) {
            String cacheKey = symbol + "_" + interval.getValue();
            List<CandlestickData> cachedCandles = historicalCache.get(cacheKey);
            
            if (cachedCandles != null && !cachedCandles.isEmpty()) {
                CandlestickData lastCandle = cachedCandles.get(cachedCandles.size() - 1);
                
                // Calculate which interval period we're currently in
                long intervalSeconds = getIntervalSeconds(interval);
                long currentPeriodStart = (now.getEpochSecond() / intervalSeconds) * intervalSeconds;
                Instant currentPeriodStartTime = Instant.ofEpochSecond(currentPeriodStart);
                
                // Check if the last candle is from the current period
                if (lastCandle.getOpenTime().equals(currentPeriodStartTime) || 
                    lastCandle.getOpenTime().isAfter(currentPeriodStartTime.minusSeconds(intervalSeconds))) {
                    
                    // Update the last candle with current price
                    BigDecimal open = lastCandle.getOpen();
                    BigDecimal high = lastCandle.getHigh().max(currentPrice);
                    BigDecimal low = lastCandle.getLow().min(currentPrice);
                    BigDecimal close = currentPrice; // Current price becomes close
                    
                    // Accumulate volume
                    BigDecimal newVolume = lastCandle.getVolume().add(volume);
                    BigDecimal avgPrice = open.add(close).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
                    BigDecimal quoteVolume = newVolume.multiply(avgPrice);
                    
                    // Create updated candle
                    CandlestickData updatedCandle = new CandlestickData(
                        symbol,
                        lastCandle.getOpenTime(),
                        now,
                        open,
                        high,
                        low,
                        close,
                        newVolume,
                        quoteVolume,
                        lastCandle.getNumberOfTrades() + 1,
                        interval.getValue(),
                        getProviderName(),
                        false // Not closed yet - still updating
                    );
                    
                    // Replace the last candle with updated version
                    cachedCandles.set(cachedCandles.size() - 1, updatedCandle);
                    updatedCount++;
                }
            }
        }
        
        // Log every 100 updates (every ~10 seconds at 100ms tick rate) to avoid spam
        if (updatedCount > 0 && random.nextInt(100) == 0) {
            System.out.println(String.format("🔄 Real-time update: %s price %.2f (updated %d intervals)", 
                symbol, currentPrice, updatedCount));
        }
    }
    
    private void updateMarketState(MarketState state) {
        state.ticksSinceTrendChange++;
        
        switch (state.scenario) {
            case BULL_RUN:
                // Strong uptrend with occasional pullbacks (REDUCED for stability)
                if (random.nextInt(100) < 5) { // 5% chance of pullback
                    state.trend = new BigDecimal("-0.0005");
                    state.momentum = new BigDecimal("-0.0002");
                } else {
                    state.trend = new BigDecimal("0.0008"); // Moderate up
                    state.momentum = state.momentum.add(new BigDecimal("0.00002"));
                }
                break;
                
            case BEAR_MARKET:
                // Strong downtrend with occasional relief rallies (REDUCED for stability)
                if (random.nextInt(100) < 5) { // 5% chance of rally
                    state.trend = new BigDecimal("0.0005");
                    state.momentum = new BigDecimal("0.0002");
                } else {
                    state.trend = new BigDecimal("-0.0008"); // Moderate down
                    state.momentum = state.momentum.subtract(new BigDecimal("0.00002"));
                }
                break;
                
            case VOLATILE:
                // Rapid changes, high volatility (REDUCED for stability)
                if (state.ticksSinceTrendChange > 20) { // Change every ~2 seconds
                    state.trend = new BigDecimal(random.nextGaussian() * 0.001); // Reduced from 0.005
                    state.volatility = new BigDecimal("0.003"); // Reduced from 0.005
                    state.ticksSinceTrendChange = 0;
                }
                break;
                
            case SIDEWAYS:
                // Range-bound, mean-reverting
                state.volatility = new BigDecimal("0.0005"); // Low volatility
                if (state.ticksSinceTrendChange > 50) {
                    state.trend = state.trend.negate(); // Reverse direction
                    state.ticksSinceTrendChange = 0;
                }
                break;
                
            case PUMP_AND_DUMP:
                // Sudden spike followed by crash
                if (!state.isPumping && !state.isDumping && random.nextInt(1000) < 5) {
                    // Start pump
                    state.isPumping = true;
                    state.pumpTicks = 0;
                    System.out.println("🚀 PUMP starting!");
                }
                
                if (state.isPumping) {
                    state.trend = new BigDecimal("0.01"); // Massive up
                    state.volatility = new BigDecimal("0.003");
                    state.pumpTicks++;
                    
                    if (state.pumpTicks > 100) { // Pump for ~10 seconds
                        state.isPumping = false;
                        state.isDumping = true;
                        state.pumpTicks = 0;
                        System.out.println("💥 DUMP starting!");
                    }
                }
                
                if (state.isDumping) {
                    state.trend = new BigDecimal("-0.015"); // Massive down
                    state.volatility = new BigDecimal("0.005");
                    state.pumpTicks++;
                    
                    if (state.pumpTicks > 80) { // Dump for ~8 seconds
                        state.isDumping = false;
                        state.trend = BigDecimal.ZERO;
                        state.volatility = defaultVolatility;
                        System.out.println("📊 Back to normal");
                    }
                }
                break;
                
            case NORMAL:
            default:
                // Random walk with occasional trend changes (REDUCED for stability)
                if (state.ticksSinceTrendChange > 100) { // Change every ~10 seconds
                    state.trend = new BigDecimal(random.nextGaussian() * 0.0003); // Reduced from 0.002
                    state.ticksSinceTrendChange = 0;
                }
                break;
        }
        
        // Cap momentum
        if (state.momentum.abs().compareTo(new BigDecimal("0.01")) > 0) {
            state.momentum = state.momentum.divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        }
    }
    
    private BigDecimal calculatePriceChange(MarketState state) {
        // Combine trend, momentum, random noise, and mean reversion
        double randomNoise = random.nextGaussian(); // Normal distribution
        
        BigDecimal trendComponent = state.currentPrice.multiply(state.trend);
        BigDecimal momentumComponent = state.currentPrice.multiply(state.momentum);
        BigDecimal noiseComponent = state.currentPrice.multiply(state.volatility)
            .multiply(new BigDecimal(randomNoise));
        
        // Mean reversion: if price drifts too far from base, pull it back
        // This prevents excessive drift over time
        BigDecimal deviation = state.currentPrice.subtract(state.basePrice);
        BigDecimal deviationPercent = deviation.divide(state.basePrice, 8, RoundingMode.HALF_UP);
        
        // If price has moved more than 10% from base, apply mean reversion
        BigDecimal meanReversionComponent = BigDecimal.ZERO;
        if (deviationPercent.abs().compareTo(new BigDecimal("0.10")) > 0) {
            // Pull back towards base price (stronger pull the further away)
            meanReversionComponent = deviation.multiply(new BigDecimal("-0.01"));
        }
        
        return trendComponent.add(momentumComponent).add(noiseComponent).add(meanReversionComponent)
            .setScale(8, RoundingMode.HALF_UP);
    }
    
    private void applyScenarioToState(MarketState state) {
        switch (state.scenario) {
            case BULL_RUN:
                state.trend = new BigDecimal("0.0008"); // Reduced for stability
                state.volatility = new BigDecimal("0.0015");
                break;
            case BEAR_MARKET:
                state.trend = new BigDecimal("-0.0008"); // Reduced for stability
                state.volatility = new BigDecimal("0.0015");
                break;
            case VOLATILE:
                state.volatility = new BigDecimal("0.003"); // Reduced for stability
                break;
            case SIDEWAYS:
                state.trend = BigDecimal.ZERO;
                state.volatility = new BigDecimal("0.0005");
                break;
            case PUMP_AND_DUMP:
            case NORMAL:
            default:
                state.volatility = defaultVolatility;
                break;
        }
    }
    
    private void generateKlineData(String symbol, TimeInterval interval) {
        if (dataHandler == null) return;
        
        try {
            MarketState state = marketStates.get(symbol);
            if (state == null) return;
            
            long intervalSeconds = getIntervalSeconds(interval);
            Instant now = Instant.now();
            
            // Calculate the proper interval boundaries
            long currentPeriodStart = (now.getEpochSecond() / intervalSeconds) * intervalSeconds;
            Instant openTime = Instant.ofEpochSecond(currentPeriodStart);
            Instant closeTime = openTime.plusSeconds(intervalSeconds);
            
            // Check if we should finalize the previous candle and start a new one
            String cacheKey = symbol + "_" + interval.getValue();
            List<CandlestickData> cachedCandles = historicalCache.get(cacheKey);
            
            CandlestickData lastCandle = null;
            if (cachedCandles != null && !cachedCandles.isEmpty()) {
                lastCandle = cachedCandles.get(cachedCandles.size() - 1);
                
                // If last candle is from a previous period, finalize it and create new one
                if (lastCandle.getOpenTime().isBefore(openTime)) {
                    // Mark the previous candle as closed
                    CandlestickData closedCandle = new CandlestickData(
                        lastCandle.getSymbol(),
                        lastCandle.getOpenTime(),
                        lastCandle.getCloseTime(),
                        lastCandle.getOpen(),
                        lastCandle.getHigh(),
                        lastCandle.getLow(),
                        lastCandle.getClose(),
                        lastCandle.getVolume(),
                        lastCandle.getQuoteAssetVolume(),
                        lastCandle.getNumberOfTrades(),
                        lastCandle.getInterval(),
                        lastCandle.getProvider(),
                        true // Now closed
                    );
                    cachedCandles.set(cachedCandles.size() - 1, closedCandle);
                    lastCandle = null; // Force creation of new candle
                }
            }
            
            // Create a new candle for the current period if one doesn't exist
            if (lastCandle == null || !lastCandle.getOpenTime().equals(openTime)) {
                BigDecimal open = state.currentPrice;
                BigDecimal high = state.currentPrice;
                BigDecimal low = state.currentPrice;
                BigDecimal close = state.currentPrice;
                
                // Small initial volume
                BigDecimal volume = generateVolume().multiply(new BigDecimal("0.1"));
                BigDecimal quoteVolume = volume.multiply(open);
                
                CandlestickData newCandle = new CandlestickData(
                    symbol,
                    openTime,
                    closeTime,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    quoteVolume,
                    1,
                    interval.getValue(),
                    getProviderName(),
                    false // Not closed - will be updated by ticker
                );
                
                // Append new candle to cache
                if (cachedCandles != null) {
                    cachedCandles.add(newCandle);
                    System.out.println(String.format("🆕 New candle period started for %s %s at price %.2f", 
                        symbol, interval.getValue(), open));
                }
                
                // Send the new candle via websocket
                TradingData data = new TradingData(symbol, now, getProviderName(), TradingDataType.KLINE, newCandle);
                dataHandler.accept(data);
            } else {
                // Current candle already exists and is being updated by ticker
                // Just send it via websocket
                TradingData data = new TradingData(symbol, now, getProviderName(), TradingDataType.KLINE, lastCandle);
                dataHandler.accept(data);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Mock provider error generating kline: " + e.getMessage());
        }
    }
    
    /**
     * Append a candle to the historical cache, maintaining continuity
     * between historical and live data
     */
    private void appendToHistoricalCache(String symbol, TimeInterval interval, CandlestickData candle) {
        String cacheKey = symbol + "_" + interval.getValue();
        
        List<CandlestickData> cachedCandles = historicalCache.get(cacheKey);
        if (cachedCandles != null) {
            // Only append if this candle is newer than the last cached candle
            if (!cachedCandles.isEmpty()) {
                Instant lastCachedTime = cachedCandles.get(cachedCandles.size() - 1).getCloseTime();
                if (candle.getOpenTime().isBefore(lastCachedTime) || candle.getOpenTime().equals(lastCachedTime)) {
                    // Skip duplicate or older candles
                    System.out.println("⏩ Skipped duplicate/old candle for " + symbol + " " + interval.getValue());
                    return;
                }
            }
            
            // Append the new candle
            cachedCandles.add(candle);
            System.out.println(String.format("➕ Appended live candle to cache: %s %s (cache size: %d, price: %.2f)", 
                symbol, interval.getValue(), cachedCandles.size(), candle.getClose()));
            
            // Limit cache size to prevent memory issues (keep last 2000 candles)
            if (cachedCandles.size() > 2000) {
                cachedCandles.remove(0); // Remove oldest
            }
        } else {
            System.out.println("⚠️ Cannot append candle - cache not initialized for " + symbol + " " + interval.getValue());
        }
    }
    
    private BigDecimal getRandomStartPrice(String symbol) {
        // Generate realistic starting prices based on symbol
        if (symbol.startsWith("BTC")) {
            return new BigDecimal(40000 + random.nextInt(20000)); // 40k-60k
        } else if (symbol.startsWith("ETH")) {
            return new BigDecimal(2000 + random.nextInt(1000)); // 2k-3k
        } else if (symbol.startsWith("BNB")) {
            return new BigDecimal(300 + random.nextInt(200)); // 300-500
        } else if (symbol.startsWith("SOL")) {
            return new BigDecimal(50 + random.nextInt(100)); // 50-150
        } else {
            return new BigDecimal(1 + random.nextInt(10)); // 1-10 for others
        }
    }
    
    /**
     * Get start price for a symbol, checking cached historical data first
     * to ensure continuity between historical and live data
     */
    private BigDecimal getStartPriceForSymbol(String symbol) {
        // Check if we have any cached historical data for this symbol
        for (String cacheKey : historicalCache.keySet()) {
            if (cacheKey.startsWith(symbol + "_")) {
                List<CandlestickData> cachedData = historicalCache.get(cacheKey);
                if (!cachedData.isEmpty()) {
                    // Use the close price of the last historical candle
                    BigDecimal lastPrice = cachedData.get(cachedData.size() - 1).getClose();
                    System.out.println("🔗 Using last cached price for " + symbol + ": " + lastPrice);
                    return lastPrice;
                }
            }
        }
        
        // No cached data, generate a random start price
        return getRandomStartPrice(symbol);
    }
    
    private BigDecimal generatePriceChange(BigDecimal currentPrice, BigDecimal volatility) {
        // Generate random walk - used for historical data generation
        double randomChange = random.nextGaussian(); // Normal distribution
        BigDecimal change = currentPrice.multiply(volatility)
            .multiply(new BigDecimal(randomChange));
        
        return change.setScale(8, RoundingMode.HALF_UP);
    }
    
    // API Methods for external control
    public void triggerPump(String symbol) {
        MarketState state = marketStates.get(symbol);
        if (state != null) {
            state.scenario = MarketScenario.PUMP_AND_DUMP;
            state.isPumping = true;
            state.isDumping = false;
            state.pumpTicks = 0;
            System.out.println("🚀 Manually triggered PUMP for " + symbol);
        }
    }
    
    public void triggerDump(String symbol) {
        MarketState state = marketStates.get(symbol);
        if (state != null) {
            state.scenario = MarketScenario.PUMP_AND_DUMP;
            state.isPumping = false;
            state.isDumping = true;
            state.pumpTicks = 0;
            System.out.println("💥 Manually triggered DUMP for " + symbol);
        }
    }
    
    public void setSymbolTrend(String symbol, BigDecimal trend) {
        MarketState state = marketStates.get(symbol);
        if (state != null) {
            state.trend = trend;
            System.out.println("📈 " + symbol + " trend set to " + trend);
        }
    }
    
    public void resetSymbolPrice(String symbol, BigDecimal price) {
        MarketState state = marketStates.get(symbol);
        if (state != null) {
            state.currentPrice = price;
            state.basePrice = price;
            state.trend = BigDecimal.ZERO;
            state.momentum = BigDecimal.ZERO;
            System.out.println("🔄 " + symbol + " price reset to " + price);
        }
    }
    
    public Map<String, Map<String, BigDecimal>> getMarketStates() {
        Map<String, Map<String, BigDecimal>> states = new HashMap<>();
        for (Map.Entry<String, MarketState> entry : marketStates.entrySet()) {
            MarketState state = entry.getValue();
            Map<String, BigDecimal> stateInfo = new HashMap<>();
            stateInfo.put("currentPrice", state.currentPrice);
            stateInfo.put("basePrice", state.basePrice);
            stateInfo.put("trend", state.trend);
            stateInfo.put("volatility", state.volatility);
            stateInfo.put("momentum", state.momentum);
            states.put(entry.getKey(), stateInfo);
        }
        return states;
    }
    
    private BigDecimal generateVolume() {
        // Generate random volume with realistic distribution
        // Using exponential-like distribution to simulate real market volume patterns
        double baseVolume = 100 + random.nextDouble() * 900; // 100-1000 base
        double volatilityMultiplier = 1.0 + random.nextGaussian() * 0.5; // Add variance
        double volume = baseVolume * Math.max(0.1, volatilityMultiplier);
        return new BigDecimal(volume).setScale(2, RoundingMode.HALF_UP);
    }
    
    private long getIntervalSeconds(TimeInterval interval) {
        switch (interval) {
            case ONE_MINUTE: return 60;
            case THREE_MINUTES: return 180;
            case FIVE_MINUTES: return 300;
            case FIFTEEN_MINUTES: return 900;
            case THIRTY_MINUTES: return 1800;
            case ONE_HOUR: return 3600;
            case TWO_HOURS: return 7200;
            case FOUR_HOURS: return 14400;
            case SIX_HOURS: return 21600;
            case EIGHT_HOURS: return 28800;
            case TWELVE_HOURS: return 43200;
            case ONE_DAY: return 86400;
            case THREE_DAYS: return 259200;
            case ONE_WEEK: return 604800;
            case ONE_MONTH: return 2592000;
            default: return 60;
        }
    }
    
    // ========== ORDER FLOW STUB IMPLEMENTATIONS ==========
    // Mock provider doesn't generate order flow data yet
    
    @Override
    public void subscribeToTrades(String symbol) {
        System.out.println("⚠️ Mock provider: Trade stream not implemented for " + symbol);
    }
    
    @Override
    public void unsubscribeFromTrades(String symbol) {
        System.out.println("⚠️ Mock provider: Trade stream not implemented");
    }
    
    @Override
    public void subscribeToAggregateTrades(String symbol) {
        System.out.println("⚠️ Mock provider: Aggregate trade stream not implemented for " + symbol);
    }
    
    @Override
    public void unsubscribeFromAggregateTrades(String symbol) {
        System.out.println("⚠️ Mock provider: Aggregate trade stream not implemented");
    }
    
    @Override
    public void subscribeToOrderBook(String symbol, int depth) {
        System.out.println("⚠️ Mock provider: Order book stream not implemented for " + symbol);
    }
    
    @Override
    public void unsubscribeFromOrderBook(String symbol) {
        System.out.println("⚠️ Mock provider: Order book stream not implemented");
    }
    
    @Override
    public void subscribeToBookTicker(String symbol) {
        System.out.println("⚠️ Mock provider: Book ticker stream not implemented for " + symbol);
    }
    
    @Override
    public void unsubscribeFromBookTicker(String symbol) {
        System.out.println("⚠️ Mock provider: Book ticker stream not implemented");
    }
}

