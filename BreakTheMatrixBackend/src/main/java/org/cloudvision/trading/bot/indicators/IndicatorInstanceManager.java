package org.cloudvision.trading.bot.indicators;

import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.service.CandlestickHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Unified Indicator Management Service
 * 
 * Provides:
 * - Registry of all available indicators
 * - Active instance management across multiple symbols/providers/timeframes
 * - Initialization with historical data
 * - Automatic updates when new candles arrive
 * - Query capabilities by various criteria
 * 
 * This service combines indicator registration and instance lifecycle management
 * into a single, cohesive API.
 * 
 * Thread-Safe: All operations use ConcurrentHashMap and atomic operations
 */
@Service
public class IndicatorInstanceManager {
    
    // Indicator registry: indicatorId -> Indicator implementation
    private final Map<String, Indicator> indicators = new HashMap<>();
    
    private final CandlestickHistoryService historyService;
    
    // Active indicator instances: composite key -> IndicatorInstance
    private final Map<String, IndicatorInstance> activeInstances = new ConcurrentHashMap<>();
    
    // Index for fast lookups by symbol/provider/interval
    private final Map<String, Set<String>> instancesByContext = new ConcurrentHashMap<>();
    
    private final org.cloudvision.trading.service.TradeHistoryService tradeHistoryService;
    private final org.cloudvision.trading.service.OrderBookHistoryService orderBookHistoryService;
    
    // Trading providers (for fetching historical data)
    private final Map<String, org.cloudvision.trading.provider.TradingDataProvider> tradingProviders = new HashMap<>();
    
    @Autowired
    public IndicatorInstanceManager(List<Indicator> indicatorList,
                                   CandlestickHistoryService historyService,
                                   org.cloudvision.trading.service.TradeHistoryService tradeHistoryService,
                                   org.cloudvision.trading.service.OrderBookHistoryService orderBookHistoryService,
                                   List<org.cloudvision.trading.provider.TradingDataProvider> providers) {
        this.historyService = historyService;
        this.tradeHistoryService = tradeHistoryService;
        this.orderBookHistoryService = orderBookHistoryService;
        
        // Register trading providers by name
        for (org.cloudvision.trading.provider.TradingDataProvider provider : providers) {
            tradingProviders.put(provider.getProviderName(), provider);
        }
        
        // Register all indicators
        for (Indicator indicator : indicatorList) {
            indicators.put(indicator.getId(), indicator);
        }
        
        System.out.println("✅ IndicatorInstanceManager initialized with " + indicators.size() + " indicators");
    }
    
    // ============================================================
    // Indicator Registry Methods
    // ============================================================
    
    /**
     * Get all available indicators
     */
    public List<Indicator> getAllIndicators() {
        return new ArrayList<>(indicators.values());
    }
    
    /**
     * Get indicators by category
     */
    public List<Indicator> getIndicatorsByCategory(Indicator.IndicatorCategory category) {
        return indicators.values().stream()
            .filter(ind -> ind.getCategory() == category)
            .collect(Collectors.toList());
    }
    
    /**
     * Get specific indicator by ID
     */
    public Indicator getIndicator(String indicatorId) {
        Indicator indicator = indicators.get(indicatorId);
        if (indicator == null) {
            throw new IllegalArgumentException("Indicator not found: " + indicatorId);
        }
        return indicator;
    }
    
    /**
     * Check if indicator exists
     */
    public boolean hasIndicator(String indicatorId) {
        return indicators.containsKey(indicatorId);
    }
    
    /**
     * Get visualization metadata for an indicator
     */
    public Map<String, IndicatorMetadata> getVisualizationMetadata(String indicatorId,
                                                                   Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        return indicator.getVisualizationMetadata(params);
    }
    
    // ============================================================
    // Instance Management Methods
    // ============================================================
    
    /**
     * Create and register an active indicator instance
     * Automatically loads maximum available historical candles (up to 5000)
     * 
     * @param indicatorId Indicator ID (e.g., "sma", "volume")
     * @param provider Provider name (e.g., "Binance")
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @param interval Time interval (e.g., "1m", "5m")
     * @param params Indicator parameters
     * @return Unique instance key for the registered indicator
     */
    public String activateIndicator(String indicatorId, String provider, 
                                   String symbol, String interval,
                                   Map<String, Object> params) {
        
        // Generate unique key for this indicator instance
        String instanceKey = generateInstanceKey(indicatorId, provider, symbol, interval, params);
        
        // Check if already active
        if (activeInstances.containsKey(instanceKey)) {
            System.out.println("ℹ️ Indicator already active: " + instanceKey);
            return instanceKey;
        }
        
        // Get the indicator implementation
        Indicator indicator = getIndicator(indicatorId);
        
        // Load maximum available historical candles (up to buffer limit)
        int candlesToLoad = IndicatorInstance.MAX_HISTORY_SIZE; // 5000
        
        List<CandlestickData> candles = historyService.getLastNCandlesticks(
            provider, symbol, interval, candlesToLoad
        );
        
        // Initialize indicator with minimum required candles for warm-up
        int minRequired = indicator.getMinRequiredCandles(params);
        Object state = indicator.onInit(
            candles.subList(0, Math.min(minRequired, candles.size())), 
            params
        );
        
        // Create state wrapper
        IndicatorState indicatorState = new IndicatorState(
            indicatorId, provider, symbol, interval, params, state, minRequired
        );
        
        // Create and register the instance
        IndicatorInstance instance = new IndicatorInstance(
            instanceKey, indicatorId, provider, symbol, interval, params, indicatorState
        );
        
        activeInstances.put(instanceKey, instance);
        
        // ============================================================
        // LOAD HISTORICAL ORDER FLOW DATA (TRADES & ORDER BOOKS)
        // ============================================================
        // Check if this indicator needs trade or order book data
        java.util.Set<org.cloudvision.trading.model.TradingDataType> requiredTypes = 
            indicator.getRequiredDataTypes();
        
        boolean needsTrades = requiredTypes.contains(org.cloudvision.trading.model.TradingDataType.TRADE) ||
                             requiredTypes.contains(org.cloudvision.trading.model.TradingDataType.AGGREGATE_TRADE);
        boolean needsOrderBooks = requiredTypes.contains(org.cloudvision.trading.model.TradingDataType.ORDER_BOOK);
        
        // Check if indicator wants historical order flow data
        boolean shouldLoadHistorical = indicator.shouldLoadHistoricalOrderFlow();
        
        if ((needsTrades || needsOrderBooks) && !shouldLoadHistorical) {
            System.out.println("⏭️  Skipping historical order flow data for " + indicatorId + 
                             " (indicator requires real-time data only)");
        }
        
        // ============================================================
        // LOAD AND PREPARE HISTORICAL ORDER FLOW DATA
        // ============================================================
        // Load trade and order book data that we'll match to candles
        List<org.cloudvision.trading.model.TradeData> allHistoricalTrades = new ArrayList<>();
        List<org.cloudvision.trading.model.OrderBookData> allHistoricalOrderBooks = new ArrayList<>();
        
        if ((needsTrades || needsOrderBooks) && shouldLoadHistorical) {
            System.out.println("📦 Loading historical order flow data for " + indicatorId + "...");
            
            // Load historical trades if needed
            if (needsTrades) {
                allHistoricalTrades = tradeHistoryService.getTrades(provider, symbol);
                
                // Fetch from provider if no history exists OR if we need more trades
                // Calculate required time range from candles
                java.time.Instant startTime = candles.isEmpty() ? java.time.Instant.now() : candles.get(0).getOpenTime();
                java.time.Instant endTime = candles.isEmpty() ? java.time.Instant.now() : candles.get(candles.size() - 1).getCloseTime();
                
                // Check if we have trades covering the full time range
                boolean needsMoreTrades = allHistoricalTrades.isEmpty();
                if (!allHistoricalTrades.isEmpty()) {
                    // Check if existing trades cover our time range
                    java.time.Instant firstTradeTime = allHistoricalTrades.get(0).getTimestamp();
                    java.time.Instant lastTradeTime = allHistoricalTrades.get(allHistoricalTrades.size() - 1).getTimestamp();
                    
                    // If trades don't cover the candle range, fetch more
                    if (firstTradeTime.isAfter(startTime.plusSeconds(300))) { // 5 min tolerance
                        needsMoreTrades = true;
                        System.out.println("   ⚠️ Existing trades don't cover full time range, will fetch more...");
                    }
                }
                
                if (needsMoreTrades) {
                    System.out.println("   📥 Fetching trades for time range: " + startTime + " to " + endTime + " (" + candles.size() + " candles)");
                    
                    try {
                        org.cloudvision.trading.provider.TradingDataProvider tradingProvider = 
                            getTradingProvider(provider);
                        
                        if (tradingProvider != null) {
                            // Fetch maximum trades (Binance allows up to 1000 per call)
                            // For better coverage, we'll make multiple calls if needed
                            List<org.cloudvision.trading.model.TradeData> fetchedTrades = new ArrayList<>();
                            
                            // Calculate number of calls needed (rough estimate: 1000 trades per 10-30 minutes)
                            long timeRangeMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
                            int estimatedCalls = Math.max(1, (int)(timeRangeMinutes / 15)); // ~1 call per 15 minutes
                            estimatedCalls = Math.min(estimatedCalls, 5); // Cap at 5 calls to avoid rate limits
                            
                            System.out.println("   📊 Estimated " + estimatedCalls + " API calls needed for " + timeRangeMinutes + " minutes of data");
                            
                            // Fetch in batches, working backwards from endTime
                            for (int i = 0; i < estimatedCalls; i++) {
                                List<org.cloudvision.trading.model.TradeData> batch = 
                                    tradingProvider.getHistoricalAggregateTrades(symbol, 1000);
                                
                                if (!batch.isEmpty()) {
                                    fetchedTrades.addAll(batch);
                                    System.out.println("   ✅ Batch " + (i+1) + ": Fetched " + batch.size() + " trades");
                                } else {
                                    break; // No more trades available
                                }
                            }
                            
                            if (!fetchedTrades.isEmpty()) {
                                tradeHistoryService.addTrades(provider, symbol, fetchedTrades);
                                allHistoricalTrades = tradeHistoryService.getTrades(provider, symbol); // Get merged list
                                System.out.println("   ✅ Total fetched and stored: " + fetchedTrades.size() + " historical trades");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("   ❌ Failed to fetch historical trades: " + e.getMessage());
                    }
                }
                
                if (!allHistoricalTrades.isEmpty()) {
                    System.out.println("   ✅ Loaded " + allHistoricalTrades.size() + " historical trades (will match to candles)");
                }
            }
            
            // Load historical order books if needed
            if (needsOrderBooks) {
                allHistoricalOrderBooks = orderBookHistoryService.getOrderBooks(provider, symbol);
                
                // Fetch from provider if no history exists
                if (allHistoricalOrderBooks.isEmpty()) {
                    System.out.println("   ⚠️ No historical order books found in cache, fetching snapshot from " + provider + "...");
                    
                    try {
                        org.cloudvision.trading.provider.TradingDataProvider tradingProvider = 
                            getTradingProvider(provider);
                        
                        if (tradingProvider != null) {
                            org.cloudvision.trading.model.OrderBookData snapshot = 
                                tradingProvider.getOrderBookSnapshot(symbol, 100);
                            
                            if (snapshot != null) {
                                orderBookHistoryService.addOrderBook(snapshot);
                                allHistoricalOrderBooks = List.of(snapshot);
                                System.out.println("   ✅ Fetched and stored order book snapshot");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("   ❌ Failed to fetch order book snapshot: " + e.getMessage());
                    }
                }
                
                if (!allHistoricalOrderBooks.isEmpty()) {
                    System.out.println("   ✅ Loaded " + allHistoricalOrderBooks.size() + " historical order book snapshots (will match to candles)");
                }
            }
        }
        
        // ============================================================
        // PROCESS HISTORICAL CANDLES WITH MATCHED ORDER FLOW DATA
        // ============================================================
        // Process each candle along with its matching trades and order books
        // This ensures trades are properly associated with their candles
        System.out.println("📊 Populating historical buffer with " + candles.size() + " candles...");
        
        for (int i = minRequired; i < candles.size(); i++) {
            CandlestickData candle = candles.get(i);
            
            // ============================================================
            // MATCH TRADES TO THIS CANDLE'S TIME WINDOW
            // ============================================================
            if (!allHistoricalTrades.isEmpty()) {
                // Get candle time boundaries
                java.time.Instant candleStart = candle.getOpenTime();
                java.time.Instant candleEnd = candle.getCloseTime();
                
                // Filter trades that fall within this candle's time window
                List<org.cloudvision.trading.model.TradeData> candleTrades = allHistoricalTrades.stream()
                    .filter(trade -> {
                        java.time.Instant tradeTime = trade.getTimestamp();
                        return !tradeTime.isBefore(candleStart) && tradeTime.isBefore(candleEnd);
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                // Process trades for this candle
                for (org.cloudvision.trading.model.TradeData trade : candleTrades) {
                    Map<String, Object> tradeResult = indicator.onTradeUpdate(
                        trade, params, indicatorState.getState()
                    );
                    
                    Object newState = tradeResult.get("state");
                    if (newState != null) {
                        indicatorState.setState(newState);
                    }
                }
            }
            
            // ============================================================
            // MATCH ORDER BOOK TO THIS CANDLE'S TIME
            // ============================================================
            if (!allHistoricalOrderBooks.isEmpty()) {
                // Find order book closest to this candle's time
                java.time.Instant candleTime = candle.getOpenTime();
                org.cloudvision.trading.model.OrderBookData closestOrderBook = null;
                long minTimeDiff = Long.MAX_VALUE;
                
                for (org.cloudvision.trading.model.OrderBookData ob : allHistoricalOrderBooks) {
                    long timeDiff = Math.abs(java.time.Duration.between(candleTime, ob.getTimestamp()).toMillis());
                    if (timeDiff < minTimeDiff) {
                        minTimeDiff = timeDiff;
                        closestOrderBook = ob;
                    }
                }
                
                // Process order book for this candle
                if (closestOrderBook != null) {
                    Map<String, Object> obResult = indicator.onOrderBookUpdate(
                        closestOrderBook, params, indicatorState.getState()
                    );
                    
                    Object newState = obResult.get("state");
                    if (newState != null) {
                        indicatorState.setState(newState);
                    }
                }
            }
            
            // ============================================================
            // PROCESS THE CANDLE (after trades/orderbooks are loaded)
            // ============================================================
            Map<String, Object> result = indicator.onNewCandle(candle, params, indicatorState.getState());
            
            // Extract values and new state
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
            Object newState = result.get("state");
            
            // Extract additional data
            Map<String, Object> additionalData = extractAdditionalData(result);
            
            // Update state
            indicatorState.setState(newState);
            indicatorState.incrementCandleCount();
            
            // Create and store result
            IndicatorResult indicatorResult = new IndicatorResult(
                candle.getOpenTime(),
                values != null ? values : Map.of(),
                candle,
                additionalData
            );
            
            instance.addHistoricalResult(indicatorResult);
        }
        
        // Add to context index for fast lookups
        String contextKey = generateContextKey(provider, symbol, interval);
        instancesByContext.computeIfAbsent(contextKey, k -> ConcurrentHashMap.newKeySet())
                         .add(instanceKey);
        
        System.out.println("✅ Activated indicator: " + indicatorId + 
                         " for " + symbol + " " + interval + 
                         " with " + candles.size() + " historical candles" +
                         " (" + instance.getHistoricalResultCount() + " results stored)" +
                         " (total active: " + activeInstances.size() + ")");
        
        return instanceKey;
    }
    
    /**
     * Activate an indicator using provided historical candles
     * 
     * @param indicatorId Indicator ID
     * @param candles Historical candlestick data for initialization
     * @param params Indicator parameters
     * @return Unique instance key for the registered indicator
     */
    public String activateIndicatorWithCandles(String indicatorId,
                                              List<CandlestickData> candles,
                                              Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("Candles cannot be null or empty");
        }
        
        String provider = candles.get(0).getProvider();
        String symbol = candles.get(0).getSymbol();
        String interval = candles.get(0).getInterval();
        
        // Generate unique key
        String instanceKey = generateInstanceKey(indicatorId, provider, symbol, interval, params);
        
        // Check if already active
        if (activeInstances.containsKey(instanceKey)) {
            System.out.println("ℹ️ Indicator already active: " + instanceKey);
            return instanceKey;
        }
        
        // Get the indicator implementation
        Indicator indicator = getIndicator(indicatorId);
        
        // Initialize indicator with minimum required candles for warm-up
        int minRequired = indicator.getMinRequiredCandles(params);
        Object state = indicator.onInit(
            candles.subList(0, Math.min(minRequired, candles.size())), 
            params
        );
        
        // Create state wrapper
        IndicatorState indicatorState = new IndicatorState(
            indicatorId, provider, symbol, interval, params, state, minRequired
        );
        
        // Create and register the instance
        IndicatorInstance instance = new IndicatorInstance(
            instanceKey, indicatorId, provider, symbol, interval, params, indicatorState
        );
        
        activeInstances.put(instanceKey, instance);
        
        // Process all historical candles to populate the historical results buffer
        System.out.println("📊 Populating historical buffer with " + candles.size() + " candles...");
        for (int i = minRequired; i < candles.size(); i++) {
            CandlestickData candle = candles.get(i);
            
            // Process the candle with current state
            Map<String, Object> result = indicator.onNewCandle(candle, params, indicatorState.getState());
            
            // Extract values and new state
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
            Object newState = result.get("state");
            
            // Extract additional data
            Map<String, Object> additionalData = extractAdditionalData(result);
            
            // Update state
            indicatorState.setState(newState);
            indicatorState.incrementCandleCount();
            
            // Create and store result
            IndicatorResult indicatorResult = new IndicatorResult(
                candle.getOpenTime(),
                values != null ? values : Map.of(),
                candle,
                additionalData
            );
            
            instance.addHistoricalResult(indicatorResult);
        }
        
        // Add to context index
        String contextKey = generateContextKey(provider, symbol, interval);
        instancesByContext.computeIfAbsent(contextKey, k -> ConcurrentHashMap.newKeySet())
                         .add(instanceKey);
        
        System.out.println("✅ Activated indicator: " + indicatorId + 
                         " for " + symbol + " " + interval + 
                         " with " + candles.size() + " historical candles" +
                         " (" + instance.getHistoricalResultCount() + " results stored)");
        
        return instanceKey;
    }
    
    /**
     * Deactivate an indicator instance
     * 
     * @param instanceKey Instance key returned from activateIndicator
     * @return true if deactivated, false if not found
     */
    public boolean deactivateIndicator(String instanceKey) {
        IndicatorInstance instance = activeInstances.remove(instanceKey);
        
        if (instance != null) {
            // Remove from context index
            String contextKey = generateContextKey(
                instance.getProvider(), instance.getSymbol(), instance.getInterval()
            );
            Set<String> contextInstances = instancesByContext.get(contextKey);
            if (contextInstances != null) {
                contextInstances.remove(instanceKey);
                if (contextInstances.isEmpty()) {
                    instancesByContext.remove(contextKey);
                }
            }
            
            System.out.println("✅ Deactivated indicator: " + instance.getIndicatorId() + 
                             " for " + instance.getSymbol() + " " + instance.getInterval() +
                             " (remaining active: " + activeInstances.size() + ")");
            return true;
        }
        
        return false;
    }
    
    /**
     * Deactivate all indicators for a specific symbol/provider/interval combination
     * 
     * @return Number of indicators deactivated
     */
    public int deactivateAllForContext(String provider, String symbol, String interval) {
        String contextKey = generateContextKey(provider, symbol, interval);
        Set<String> contextInstances = instancesByContext.get(contextKey);
        
        if (contextInstances == null || contextInstances.isEmpty()) {
            return 0;
        }
        
        int deactivated = 0;
        for (String instanceKey : new ArrayList<>(contextInstances)) {
            if (deactivateIndicator(instanceKey)) {
                deactivated++;
            }
        }
        
        return deactivated;
    }
    
    /**
     * Update parameters for an existing indicator instance and recalculate historical data
     * This will:
     * 1. Keep the same instance key (no change to instance key)
     * 2. Update the parameters on the existing instance
     * 3. Reinitialize the indicator with new parameters
     * 4. Reload and recalculate all historical data with new parameters
     * 5. Return the same instance key
     * 
     * @param instanceKey The instance key
     * @param newParams The new parameters to apply
     * @return The same instance key (unchanged)
     * @throws IllegalArgumentException if instance not found
     */
    public String updateIndicatorParams(String instanceKey, Map<String, Object> newParams) {
        // Get the instance
        IndicatorInstance instance = activeInstances.get(instanceKey);
        
        if (instance == null) {
            throw new IllegalArgumentException("Instance not found: " + instanceKey);
        }
        
        // Extract context from instance
        String indicatorId = instance.getIndicatorId();
        String provider = instance.getProvider();
        String symbol = instance.getSymbol();
        String interval = instance.getInterval();
        
        System.out.println("🔄 Updating indicator params: " + instanceKey);
        System.out.println("   Old params: " + instance.getParams());
        System.out.println("   New params: " + newParams);
        
        // Get the indicator implementation
        Indicator indicator = getIndicator(indicatorId);
        
        // Load maximum available historical candles (up to buffer limit)
        int candlesToLoad = IndicatorInstance.MAX_HISTORY_SIZE; // 5000
        
        List<CandlestickData> candles = historyService.getLastNCandlesticks(
            provider, symbol, interval, candlesToLoad
        );
        
        // Initialize indicator with minimum required candles for warm-up
        int minRequired = indicator.getMinRequiredCandles(newParams);
        Object state = indicator.onInit(
            candles.subList(0, Math.min(minRequired, candles.size())), 
            newParams
        );
        
        // Update the instance state and params
        instance.updateParams(newParams, state, minRequired);
        
        // Clear old historical results
        instance.clearHistoricalResults();
        
        // ============================================================
        // RELOAD HISTORICAL ORDER FLOW DATA (TRADES & ORDER BOOKS)
        // ============================================================
        // Check if this indicator needs trade or order book data and wants historical loading
        java.util.Set<org.cloudvision.trading.model.TradingDataType> requiredTypes = 
            indicator.getRequiredDataTypes();
        
        boolean needsTrades = requiredTypes.contains(org.cloudvision.trading.model.TradingDataType.TRADE) ||
                             requiredTypes.contains(org.cloudvision.trading.model.TradingDataType.AGGREGATE_TRADE);
        boolean needsOrderBooks = requiredTypes.contains(org.cloudvision.trading.model.TradingDataType.ORDER_BOOK);
        
        // Check if indicator wants historical order flow data
        boolean shouldLoadHistorical = indicator.shouldLoadHistoricalOrderFlow();
        
        if ((needsTrades || needsOrderBooks) && !shouldLoadHistorical) {
            System.out.println("⏭️  Skipping historical order flow data for " + indicatorId + 
                             " (indicator requires real-time data only)");
        }
        
        // ============================================================
        // LOAD AND PREPARE HISTORICAL ORDER FLOW DATA FOR RECALCULATION
        // ============================================================
        List<org.cloudvision.trading.model.TradeData> allHistoricalTrades = new ArrayList<>();
        List<org.cloudvision.trading.model.OrderBookData> allHistoricalOrderBooks = new ArrayList<>();
        
        if ((needsTrades || needsOrderBooks) && shouldLoadHistorical) {
            System.out.println("📦 Reloading historical order flow data for " + indicatorId + "...");
            
            if (needsTrades) {
                allHistoricalTrades = tradeHistoryService.getTrades(provider, symbol);
                if (!allHistoricalTrades.isEmpty()) {
                    System.out.println("   ✅ Loaded " + allHistoricalTrades.size() + " historical trades (will match to candles)");
                }
            }
            
            if (needsOrderBooks) {
                allHistoricalOrderBooks = orderBookHistoryService.getOrderBooks(provider, symbol);
                if (!allHistoricalOrderBooks.isEmpty()) {
                    System.out.println("   ✅ Loaded " + allHistoricalOrderBooks.size() + " historical order book snapshots (will match to candles)");
                }
            }
        }
        
        // ============================================================
        // RECALCULATE HISTORICAL DATA WITH MATCHED ORDER FLOW
        // ============================================================
        System.out.println("📊 Recalculating historical buffer with " + candles.size() + " candles...");
        
        for (int i = minRequired; i < candles.size(); i++) {
            CandlestickData candle = candles.get(i);
            
            // Match trades to this candle's time window
            if (!allHistoricalTrades.isEmpty()) {
                java.time.Instant candleStart = candle.getOpenTime();
                java.time.Instant candleEnd = candle.getCloseTime();
                
                List<org.cloudvision.trading.model.TradeData> candleTrades = allHistoricalTrades.stream()
                    .filter(trade -> {
                        java.time.Instant tradeTime = trade.getTimestamp();
                        return !tradeTime.isBefore(candleStart) && tradeTime.isBefore(candleEnd);
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                for (org.cloudvision.trading.model.TradeData trade : candleTrades) {
                    Map<String, Object> tradeResult = indicator.onTradeUpdate(
                        trade, newParams, instance.getState().getState()
                    );
                    Object newState = tradeResult.get("state");
                    if (newState != null) {
                        instance.getState().setState(newState);
                    }
                }
            }
            
            // Match order book to this candle's time
            if (!allHistoricalOrderBooks.isEmpty()) {
                java.time.Instant candleTime = candle.getOpenTime();
                org.cloudvision.trading.model.OrderBookData closestOrderBook = null;
                long minTimeDiff = Long.MAX_VALUE;
                
                for (org.cloudvision.trading.model.OrderBookData ob : allHistoricalOrderBooks) {
                    long timeDiff = Math.abs(java.time.Duration.between(candleTime, ob.getTimestamp()).toMillis());
                    if (timeDiff < minTimeDiff) {
                        minTimeDiff = timeDiff;
                        closestOrderBook = ob;
                    }
                }
                
                if (closestOrderBook != null) {
                    Map<String, Object> obResult = indicator.onOrderBookUpdate(
                        closestOrderBook, newParams, instance.getState().getState()
                    );
                    Object newState = obResult.get("state");
                    if (newState != null) {
                        instance.getState().setState(newState);
                    }
                }
            }
            
            // Process the candle with new state
            Map<String, Object> result = indicator.onNewCandle(candle, newParams, instance.getState().getState());
            
            // Extract values and new state
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
            Object newState = result.get("state");
            
            // Extract additional data
            Map<String, Object> additionalData = extractAdditionalData(result);
            
            // Update state
            instance.getState().setState(newState);
            instance.getState().incrementCandleCount();
            
            // Create and store result
            IndicatorResult indicatorResult = new IndicatorResult(
                candle.getOpenTime(),
                values != null ? values : Map.of(),
                candle,
                additionalData
            );
            
            instance.addHistoricalResult(indicatorResult);
        }
        
        System.out.println("✅ Updated indicator instance (same key)");
        System.out.println("   Instance key: " + instanceKey + " (unchanged)");
        System.out.println("   Historical results: " + instance.getHistoricalResultCount());
        
        return instanceKey; // Return the same key
    }
    
    /**
     * Update a specific indicator instance with a new candle
     * 
     * @param instanceKey Instance key
     * @param candle New candle to process
     * @return IndicatorResult containing updated values, or null if instance not found
     */
    public IndicatorResult updateWithCandle(String instanceKey, CandlestickData candle) {
        IndicatorInstance instance = activeInstances.get(instanceKey);
        
        if (instance == null) {
            return null;
        }
        
        IndicatorState state = instance.getState();
        Indicator indicator = getIndicator(state.getIndicatorId());
        
        // Process the new candle with current state
        Map<String, Object> result = indicator.onNewCandle(candle, state.getParams(), state.getState());
        
        // Extract values and new state
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        Object newState = result.get("state");
        
        // Extract additional data (boxes, colors, shapes, etc.)
        Map<String, Object> additionalData = extractAdditionalData(result);
        
        // Update state for next iteration
        state.setState(newState);
        state.incrementCandleCount();
        
        // Update instance metadata
        instance.setLastUpdate(Instant.now());
        instance.incrementUpdateCount();
        
        // Create result
        IndicatorResult indicatorResult = new IndicatorResult(
            candle.getOpenTime(),
            values != null ? values : Map.of(),
            candle,
            additionalData
        );
        
        // Store in historical results for consistency
        instance.addHistoricalResult(indicatorResult);
        
        return indicatorResult;
    }
    
    /**
     * Get historical indicator data from stored results
     * Returns the ACTUAL results that the active instance calculated in real-time
     * This ensures consistency - historical data matches what the instance really computed
     * 
     * @param instanceKey Instance key
     * @param count Number of historical results to retrieve (or all if greater than stored)
     * @return List of IndicatorResults that were actually calculated by the instance
     */
    public List<IndicatorResult> getHistoricalData(String instanceKey, int count) {
        IndicatorInstance instance = activeInstances.get(instanceKey);
        
        if (instance == null) {
            return List.of();
        }
        
        // Return stored historical results (not recalculated!)
        return instance.getHistoricalResults(count);
    }
    
    /**
     * Update all indicators for a specific symbol/provider/interval when a new candle arrives
     * 
     * @param candle New candle
     * @return Map of instanceKey -> IndicatorResult for all updated indicators
     */
    public Map<String, IndicatorResult> updateAllForContext(CandlestickData candle) {
        String contextKey = generateContextKey(
            candle.getProvider(), candle.getSymbol(), candle.getInterval()
        );
        
        Set<String> contextInstances = instancesByContext.get(contextKey);
        
        if (contextInstances == null || contextInstances.isEmpty()) {
            return Map.of();
        }
        
        Map<String, IndicatorResult> results = new HashMap<>();
        
        for (String instanceKey : contextInstances) {
            IndicatorResult result = updateWithCandle(instanceKey, candle);
            if (result != null) {
                results.put(instanceKey, result);
            }
        }
        
        return results;
    }
    
    /**
     * Update a specific indicator instance with a price tick
     * 
     * @param instanceKey Instance key
     * @param price Current tick price
     * @return IndicatorResult containing updated values, or null if instance not found
     */
    public IndicatorResult updateWithTick(String instanceKey, BigDecimal price) {
        IndicatorInstance instance = activeInstances.get(instanceKey);
        
        if (instance == null) {
            return null;
        }
        
        IndicatorState state = instance.getState();
        Indicator indicator = getIndicator(state.getIndicatorId());
        
        // Process the tick with current state
        Map<String, Object> result = indicator.onNewTick(price, state.getParams(), state.getState());
        
        // Extract values and new state
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        Object newState = result.get("state");
        
        // Extract additional data
        Map<String, Object> additionalData = extractAdditionalData(result);
        
        // Update state (though usually unchanged for ticks)
        if (newState != null) {
            state.setState(newState);
        }
        
        // NOTE: We don't store tick results in historical buffer - only closed candles
        
        return new IndicatorResult(
            Instant.now(),
            values != null ? values : Map.of(),
            null,  // No candle for tick updates
            additionalData
        );
    }
    
    /**
     * Update all indicators for a specific symbol/provider/interval when a price tick arrives
     * This provides real-time updates between candle closes
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param interval Time interval
     * @param price Current tick price
     * @return Map of instanceKey -> IndicatorResult for all updated indicators
     */
    public Map<String, IndicatorResult> updateAllForTick(String provider, String symbol, 
                                                         String interval, BigDecimal price) {
        String contextKey = generateContextKey(provider, symbol, interval);
        
        Set<String> contextInstances = instancesByContext.get(contextKey);
        
        if (contextInstances == null || contextInstances.isEmpty()) {
            return Map.of();
        }
        
        Map<String, IndicatorResult> results = new HashMap<>();
        
        for (String instanceKey : contextInstances) {
            IndicatorResult result = updateWithTick(instanceKey, price);
            if (result != null) {
                results.put(instanceKey, result);
            }
        }
        
        return results;
    }
    
    // ============================================================
    // EXTENDED DATA TYPE SUPPORT - ORDER BOOK AND TRADE DATA
    // ============================================================
    
    /**
     * Update a specific indicator instance with order book data
     * 
     * This method is called when:
     * - Exchange sends order book snapshot or update
     * - Indicator has declared it needs ORDER_BOOK data type
     * 
     * WHAT HAPPENS:
     * 1. Check if indicator is active
     * 2. Call indicator's onOrderBookUpdate() method
     * 3. Extract values and state from result
     * 4. Update internal state
     * 5. Return result for broadcasting to WebSocket clients
     * 
     * TYPICAL FLOW:
     * Exchange -> IndicatorWebSocketHandler -> updateWithOrderBook() -> Indicator.onOrderBookUpdate()
     * 
     * USE CASES:
     * - Bookmap heatmap visualization
     * - Bid/ask imbalance calculations
     * - Liquidity wall detection
     * - Order book depth analysis
     * 
     * @param instanceKey Instance key for the indicator
     * @param orderBook Order book data with bid/ask levels
     * @return IndicatorResult containing updated values, or null if instance not found
     */
    public IndicatorResult updateWithOrderBook(String instanceKey, 
                                              org.cloudvision.trading.model.OrderBookData orderBook) {
        // Get the active indicator instance
        IndicatorInstance instance = activeInstances.get(instanceKey);
        
        if (instance == null) {
            return null; // Instance not found
        }
        
        // Get indicator state and implementation
        IndicatorState state = instance.getState();
        Indicator indicator = getIndicator(state.getIndicatorId());
        
        // Process the order book update
        // This calls the indicator's onOrderBookUpdate() method
        Map<String, Object> result = indicator.onOrderBookUpdate(
            orderBook, 
            state.getParams(), 
            state.getState()
        );
        
        // Extract values and new state
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        Object newState = result.get("state");
        
        // Extract additional data (heatmap, levels, etc.)
        Map<String, Object> additionalData = extractAdditionalData(result);
        
        // Update state if changed
        if (newState != null) {
            state.setState(newState);
        }
        
        // NOTE: We don't store order book results in historical buffer
        // Order book data is too high frequency and transient
        // Only closed candle results are stored for historical queries
        
        // Create and return result
        return new IndicatorResult(
            orderBook.getTimestamp(),
            values != null ? values : Map.of(),
            null,  // No candle for order book updates
            additionalData
        );
    }
    
    /**
     * Update all indicators for a specific symbol that need order book data
     * 
     * This method:
     * 1. Finds all active indicators for the symbol
     * 2. Filters to only indicators that require ORDER_BOOK data type
     * 3. Calls updateWithOrderBook() for each matching indicator
     * 4. Returns all results for broadcasting
     * 
     * PERFORMANCE OPTIMIZATION:
     * Order book updates are VERY frequent (100+ per second)
     * We only update indicators that explicitly need this data
     * Check indicator.getRequiredDataTypes() to filter
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param orderBook Order book data
     * @return Map of instanceKey -> IndicatorResult for all updated indicators
     */
    public Map<String, IndicatorResult> updateAllWithOrderBook(
            String provider, 
            String symbol, 
            org.cloudvision.trading.model.OrderBookData orderBook) {
        
        Map<String, IndicatorResult> results = new HashMap<>();
        
        // Find all instances for this symbol (across all intervals)
        // Order book data doesn't have interval, so we check all intervals
        for (IndicatorInstance instance : getInstancesBySymbol(symbol)) {
            // Only update if provider matches
            if (!instance.getProvider().equals(provider)) {
                continue;
            }
            
            // Check if this indicator needs order book data
            Indicator indicator = getIndicator(instance.getIndicatorId());
            if (!indicator.getRequiredDataTypes().contains(
                    org.cloudvision.trading.model.TradingDataType.ORDER_BOOK)) {
                continue; // Skip indicators that don't need order book data
            }
            
            // Update the indicator with order book data
            IndicatorResult result = updateWithOrderBook(instance.getInstanceKey(), orderBook);
            if (result != null) {
                results.put(instance.getInstanceKey(), result);
            }
        }
        
        return results;
    }
    
    /**
     * Update a specific indicator instance with trade data
     * 
     * This method is called when:
     * - Exchange sends individual or aggregate trade
     * - Indicator has declared it needs TRADE or AGGREGATE_TRADE data type
     * 
     * WHAT HAPPENS:
     * 1. Check if indicator is active
     * 2. Call indicator's onTradeUpdate() method
     * 3. Extract values and state from result
     * 4. Update internal state (trades are accumulated)
     * 5. Return result (may be empty until candle closes)
     * 
     * TYPICAL FLOW:
     * Exchange -> IndicatorWebSocketHandler -> updateWithTrade() -> Indicator.onTradeUpdate()
     * 
     * USE CASES:
     * - Cumulative Volume Delta (CVD)
     * - Order flow imbalance detection
     * - Volume profile building
     * - Footprint candle construction
     * 
     * PERFORMANCE NOTE:
     * This is called VERY frequently (1000+ times per second)
     * Most indicators accumulate in state and only output on candle close
     * 
     * @param instanceKey Instance key for the indicator
     * @param trade Trade data (individual or aggregate)
     * @return IndicatorResult containing updated values, or null if instance not found
     */
    public IndicatorResult updateWithTrade(String instanceKey, 
                                          org.cloudvision.trading.model.TradeData trade) {
        // Get the active indicator instance
        IndicatorInstance instance = activeInstances.get(instanceKey);
        
        if (instance == null) {
            return null; // Instance not found
        }
        
        // Get indicator state and implementation
        IndicatorState state = instance.getState();
        Indicator indicator = getIndicator(state.getIndicatorId());
        
        // Process the trade update
        // This calls the indicator's onTradeUpdate() method
        Map<String, Object> result = indicator.onTradeUpdate(
            trade, 
            state.getParams(), 
            state.getState()
        );
        
        // Extract values and new state
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        Object newState = result.get("state");
        
        // Extract additional data (delta, volume profile, etc.)
        Map<String, Object> additionalData = extractAdditionalData(result);
        
        // Update state (trades are accumulated in state)
        if (newState != null) {
            state.setState(newState);
        }
        
        // NOTE: We don't store every trade result in historical buffer
        // Trade data is extremely high frequency
        // Indicators accumulate trades and output on candle close
        
        // Create and return result
        return new IndicatorResult(
            trade.getTimestamp(),
            values != null ? values : Map.of(),
            null,  // No candle for trade updates
            additionalData
        );
    }
    
    /**
     * Update all indicators for a specific symbol that need trade data
     * 
     * This method:
     * 1. Finds all active indicators for the symbol
     * 2. Filters to only indicators that require TRADE or AGGREGATE_TRADE data
     * 3. Calls updateWithTrade() for each matching indicator
     * 4. Returns all results for broadcasting
     * 
     * PERFORMANCE OPTIMIZATION:
     * Trade updates are EXTREMELY frequent (1000+ per second)
     * We only update indicators that explicitly need this data
     * Most indicators accumulate and only output on candle close
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param trade Trade data
     * @return Map of instanceKey -> IndicatorResult for all updated indicators
     */
    public Map<String, IndicatorResult> updateAllWithTrade(
            String provider, 
            String symbol, 
            org.cloudvision.trading.model.TradeData trade) {
        
        Map<String, IndicatorResult> results = new HashMap<>();
        
        // Find all instances for this symbol (across all intervals)
        // Trade data doesn't have interval, so we check all intervals
        for (IndicatorInstance instance : getInstancesBySymbol(symbol)) {
            // Only update if provider matches
            if (!instance.getProvider().equals(provider)) {
                continue;
            }
            
            // Check if this indicator needs trade data
            Indicator indicator = getIndicator(instance.getIndicatorId());
            java.util.Set<org.cloudvision.trading.model.TradingDataType> requiredTypes = 
                indicator.getRequiredDataTypes();
            
            boolean needsTrades = requiredTypes.contains(
                org.cloudvision.trading.model.TradingDataType.TRADE) ||
                requiredTypes.contains(
                org.cloudvision.trading.model.TradingDataType.AGGREGATE_TRADE);
            
            if (!needsTrades) {
                continue; // Skip indicators that don't need trade data
            }
            
            // Update the indicator with trade data
            IndicatorResult result = updateWithTrade(instance.getInstanceKey(), trade);
            if (result != null) {
                results.put(instance.getInstanceKey(), result);
            }
        }
        
        return results;
    }
    
    /**
     * Get an active indicator instance
     * 
     * @param instanceKey Instance key
     * @return IndicatorInstance or null if not found
     */
    public IndicatorInstance getInstance(String instanceKey) {
        return activeInstances.get(instanceKey);
    }
    
    /**
     * Get all active indicator instances
     * 
     * @return List of all active instances
     */
    public List<IndicatorInstance> getAllInstances() {
        return new ArrayList<>(activeInstances.values());
    }
    
    /**
     * Get all active instances for a specific symbol/provider/interval
     * 
     * @return List of instances matching the context
     */
    public List<IndicatorInstance> getInstancesForContext(String provider, 
                                                          String symbol, 
                                                          String interval) {
        String contextKey = generateContextKey(provider, symbol, interval);
        Set<String> contextInstances = instancesByContext.get(contextKey);
        
        if (contextInstances == null || contextInstances.isEmpty()) {
            return List.of();
        }
        
        return contextInstances.stream()
            .map(activeInstances::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all active instances for a specific indicator ID
     * 
     * @return List of instances for the indicator
     */
    public List<IndicatorInstance> getInstancesByIndicator(String indicatorId) {
        return activeInstances.values().stream()
            .filter(instance -> instance.getIndicatorId().equals(indicatorId))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all active instances for a specific symbol (across all providers/intervals)
     * 
     * @return List of instances for the symbol
     */
    public List<IndicatorInstance> getInstancesBySymbol(String symbol) {
        return activeInstances.values().stream()
            .filter(instance -> instance.getSymbol().equals(symbol))
            .collect(Collectors.toList());
    }
    
    /**
     * Check if an indicator is active
     * 
     * @param instanceKey Instance key
     * @return true if active, false otherwise
     */
    public boolean isActive(String instanceKey) {
        return activeInstances.containsKey(instanceKey);
    }
    
    /**
     * Get count of active indicator instances
     */
    public int getActiveCount() {
        return activeInstances.size();
    }
    
    /**
     * Get statistics about active indicators
     * 
     * @return Map containing various statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalActive", activeInstances.size());
        stats.put("uniqueContexts", instancesByContext.size());
        
        // Count by indicator ID
        Map<String, Long> byIndicator = activeInstances.values().stream()
            .collect(Collectors.groupingBy(
                IndicatorInstance::getIndicatorId,
                Collectors.counting()
            ));
        stats.put("byIndicator", byIndicator);
        
        // Count by symbol
        Map<String, Long> bySymbol = activeInstances.values().stream()
            .collect(Collectors.groupingBy(
                IndicatorInstance::getSymbol,
                Collectors.counting()
            ));
        stats.put("bySymbol", bySymbol);
        
        // Count by interval
        Map<String, Long> byInterval = activeInstances.values().stream()
            .collect(Collectors.groupingBy(
                IndicatorInstance::getInterval,
                Collectors.counting()
            ));
        stats.put("byInterval", byInterval);
        
        return stats;
    }
    
    /**
     * Clear all active indicators
     */
    public void clearAll() {
        int count = activeInstances.size();
        activeInstances.clear();
        instancesByContext.clear();
        System.out.println("🗑️ Cleared all " + count + " active indicator instances");
    }
    
    // ============================================================
    // Key Generation Utilities
    // ============================================================
    
    /**
     * Generate unique instance key
     * Format: provider:symbol:interval:indicatorId:paramsHash
     */
    private String generateInstanceKey(String indicatorId, String provider, 
                                      String symbol, String interval,
                                      Map<String, Object> params) {
        String paramsHash = generateParamsHash(params);
        return String.format("%s:%s:%s:%s:%s", 
            provider, symbol, interval, indicatorId, paramsHash);
    }
    
    /**
     * Generate context key for indexing
     * Format: provider:symbol:interval
     */
    private String generateContextKey(String provider, String symbol, String interval) {
        return String.format("%s:%s:%s", provider, symbol, interval);
    }
    
    /**
     * Generate a hash/signature for parameters
     * This allows different parameter combinations to have separate instances
     */
    private String generateParamsHash(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "default";
        }
        
        // Create a sorted, deterministic string representation of params
        List<String> sortedParams = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sortedParams.add(entry.getKey() + "=" + entry.getValue());
        }
        Collections.sort(sortedParams);
        
        String paramsStr = String.join(",", sortedParams);
        
        // Use simple hash (could use MD5/SHA if needed for shorter keys)
        return Integer.toHexString(paramsStr.hashCode());
    }
    
    /**
     * Extract additional data from indicator result (excluding values and state)
     * This includes: boxes, orderBlocks, shapes, color, etc.
     */
    private Map<String, Object> extractAdditionalData(Map<String, Object> result) {
        Map<String, Object> additionalData = new HashMap<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String key = entry.getKey();
            // Skip internal fields
            if (!key.equals("values") && !key.equals("state")) {
                additionalData.put(key, entry.getValue());
            }
        }
        return additionalData;
    }
    
    /**
     * Get trading provider by name (e.g., "Binance", "Mock")
     * Used for fetching historical order flow data from REST API
     */
    private org.cloudvision.trading.provider.TradingDataProvider getTradingProvider(String providerName) {
        return tradingProviders.get(providerName);
    }
    
    // ============================================================
    // Data Classes
    // ============================================================
    
    /**
     * Represents the state of an indicator instance
     * 
     * This object tracks:
     * - Indicator configuration (id, params)
     * - Trading context (symbol, provider, interval)
     * - Internal state (for stateful indicators)
     * - Metadata (candle count processed)
     */
    public static class IndicatorState {
        private final String indicatorId;
        private final String provider;
        private final String symbol;
        private final String interval;
        private final Map<String, Object> params;
        private Object state;
        private int candleCount;
        
        public IndicatorState(String indicatorId, String provider, String symbol, 
                            String interval, Map<String, Object> params, 
                            Object state, int candleCount) {
            this.indicatorId = indicatorId;
            this.provider = provider;
            this.symbol = symbol;
            this.interval = interval;
            this.params = params != null ? params : Map.of();
            this.state = state;
            this.candleCount = candleCount;
        }
        
        public String getIndicatorId() { return indicatorId; }
        public String getProvider() { return provider; }
        public String getSymbol() { return symbol; }
        public String getInterval() { return interval; }
        public Map<String, Object> getParams() { return params; }
        public Object getState() { return state; }
        public int getCandleCount() { return candleCount; }
        
        public void setState(Object state) { this.state = state; }
        public void incrementCandleCount() { this.candleCount++; }
    }
    
    /**
     * Result returned by updateWithCandle and updateWithTick
     * 
     * Contains:
     * - Timestamp of the update
     * - Indicator values (e.g., {"sma": 50000.00})
     * - Original candle data (if available)
     * - Additional data (colors, boxes, shapes, etc.)
     */
    public static class IndicatorResult {
        private final Instant timestamp;
        private final Map<String, BigDecimal> values;
        private final CandlestickData candle;
        private final Map<String, Object> additionalData;
        
        public IndicatorResult(Instant timestamp, Map<String, BigDecimal> values, 
                             CandlestickData candle, Map<String, Object> additionalData) {
            this.timestamp = timestamp;
            this.values = values != null ? values : Map.of();
            this.candle = candle;
            this.additionalData = additionalData != null ? additionalData : Map.of();
        }
        
        public Instant getTimestamp() { return timestamp; }
        public Map<String, BigDecimal> getValues() { return values; }
        public CandlestickData getCandle() { return candle; }
        public Map<String, Object> getAdditionalData() { return additionalData; }
        
        /**
         * Get a specific value by name
         * @param name Value name (e.g., "sma", "rsi", "supertrend")
         * @return The value, or null if not found
         */
        public BigDecimal getValue(String name) {
            return values.get(name);
        }
        
        /**
         * Check if result has a specific additional data field
         */
        public boolean hasAdditionalData(String key) {
            return additionalData.containsKey(key);
        }
        
        /**
         * Get additional data by key (e.g., "color", "boxes", "orderBlocks")
         */
        public Object getAdditionalData(String key) {
            return additionalData.get(key);
        }
    }
    
    /**
     * Represents an active indicator instance
     * Tracks indicator state, configuration, and metadata
     */
    public static class IndicatorInstance {
        private final String instanceKey;
        private final String indicatorId;
        private final String provider;
        private final String symbol;
        private final String interval;
        private Map<String, Object> params; // Made mutable for in-place updates
        private IndicatorState state; // Made mutable for in-place updates
        
        // Metadata
        private final Instant createdAt;
        private Instant lastUpdate;
        private long updateCount;
        
        // Historical results storage (circular buffer with max size)
        private final java.util.Deque<IndicatorResult> historicalResults;
        public static final int MAX_HISTORY_SIZE = 5000;
        
        public IndicatorInstance(String instanceKey, String indicatorId, 
                               String provider, String symbol, String interval,
                               Map<String, Object> params,
                               IndicatorState state) {
            this.instanceKey = instanceKey;
            this.indicatorId = indicatorId;
            this.provider = provider;
            this.symbol = symbol;
            this.interval = interval;
            this.params = params != null ? new HashMap<>(params) : new HashMap<>();
            this.state = state;
            this.createdAt = Instant.now();
            this.lastUpdate = Instant.now();
            this.updateCount = 0;
            this.historicalResults = new java.util.LinkedList<>();
        }
        
        // Getters
        public String getInstanceKey() { return instanceKey; }
        public String getIndicatorId() { return indicatorId; }
        public String getProvider() { return provider; }
        public String getSymbol() { return symbol; }
        public String getInterval() { return interval; }
        public Map<String, Object> getParams() { return params; }
        public IndicatorState getState() { return state; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastUpdate() { return lastUpdate; }
        public long getUpdateCount() { return updateCount; }
        
        // Setters for metadata
        public void setLastUpdate(Instant lastUpdate) { this.lastUpdate = lastUpdate; }
        public void incrementUpdateCount() { this.updateCount++; }
        
        /**
         * Update parameters and reinitialize state for in-place parameter updates
         * This allows updating indicator parameters without changing the instance key
         */
        public void updateParams(Map<String, Object> newParams, Object newState, int minRequired) {
            this.params = newParams != null ? new HashMap<>(newParams) : new HashMap<>();
            this.state = new IndicatorState(
                this.indicatorId,
                this.provider,
                this.symbol,
                this.interval,
                this.params,
                newState,
                minRequired
            );
            this.lastUpdate = Instant.now();
        }
        
        /**
         * Clear all historical results
         * Used when recalculating with new parameters
         */
        public void clearHistoricalResults() {
            this.historicalResults.clear();
        }
        
        // Historical results management
        public void addHistoricalResult(IndicatorResult result) {
            historicalResults.addLast(result);
            // Keep only last MAX_HISTORY_SIZE results
            while (historicalResults.size() > MAX_HISTORY_SIZE) {
                historicalResults.removeFirst();
            }
        }
        
        public List<IndicatorResult> getHistoricalResults(int count) {
            int size = historicalResults.size();
            int startIndex = Math.max(0, size - count);
            return new ArrayList<>(historicalResults).subList(startIndex, size);
        }
        
        public List<IndicatorResult> getAllHistoricalResults() {
            return new ArrayList<>(historicalResults);
        }
        
        public int getHistoricalResultCount() {
            return historicalResults.size();
        }
        
        @Override
        public String toString() {
            return String.format("IndicatorInstance[%s:%s:%s:%s, updates=%d]",
                indicatorId, provider, symbol, interval, updateCount);
        }
    }
}

