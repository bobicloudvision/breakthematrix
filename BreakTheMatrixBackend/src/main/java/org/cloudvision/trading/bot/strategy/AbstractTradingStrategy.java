package org.cloudvision.trading.bot.strategy;

import org.cloudvision.trading.bot.account.AccountManager;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderType;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Abstract base class for trading strategies
 * Provides common functionality like price extraction, history management, and order creation
 * 
 * Uses event-driven architecture matching the Indicator pattern:
 * - onInit() initializes strategy state with historical data
 * - onNewCandle() processes each closed candle
 * - onNewTick() handles real-time price updates (optional)
 */
public abstract class AbstractTradingStrategy implements TradingStrategy {
    
    protected StrategyConfig config;
    protected StrategyStats stats;
    protected boolean enabled = true;
    protected boolean bootstrapped = false;
    
    @Autowired(required = false)
    protected AccountManager accountManager;
    
    @Autowired(required = false)
    protected org.cloudvision.trading.service.CandlestickHistoryService candlestickHistoryService;
    
    // Track which provider each symbol uses (for reading from history service)
    protected final Map<String, String> symbolProviders = new HashMap<>();
    
    // Track which interval each symbol uses
    protected final Map<String, String> symbolIntervals = new HashMap<>();
    
    // Signal tracking (1 = bullish, -1 = bearish, 0 = neutral)
    protected final Map<String, BigDecimal> lastSignal = new HashMap<>();
    
    // Timestamp of last data point for each symbol
    protected final Map<String, Instant> lastUpdateTime = new HashMap<>();
    
    // Event-driven state management - tracks state per symbol
    protected final Map<String, Object> strategyStateBySymbol = new HashMap<>();
    
    // Strategy parameters (from config)
    protected Map<String, Object> strategyParams = new HashMap<>();
    
    // ============================================================
    // EVENT-DRIVEN LIFECYCLE METHODS
    // ============================================================
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        if (historicalCandles == null || historicalCandles.isEmpty()) {
            System.out.println("⚠️ No historical data provided for onInit() in " + getStrategyName());
            return null;
        }
        
        System.out.println("🔄 onInit() called for " + getStrategyName() + " with " + 
                         historicalCandles.size() + " historical candles");
        
        // Merge params with config parameters (params take precedence)
        this.strategyParams = new HashMap<>();
        if (config != null && config.getParameters() != null) {
            this.strategyParams.putAll(config.getParameters());
        }
        if (params != null) {
            this.strategyParams.putAll(params);
        }
        
        // Group data by symbol
        Map<String, List<CandlestickData>> dataBySymbol = new HashMap<>();
        for (CandlestickData candle : historicalCandles) {
            dataBySymbol.computeIfAbsent(candle.getSymbol(), k -> new ArrayList<>()).add(candle);
        }
        
        // Initialize state for each symbol
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<CandlestickData> candles = entry.getValue();
            
            // Sort by openTime
            candles.sort(Comparator.comparing(CandlestickData::getOpenTime));
            
            // Track provider and interval
            if (!candles.isEmpty()) {
                CandlestickData firstCandle = candles.get(0);
                symbolProviders.put(symbol, firstCandle.getProvider());
                symbolIntervals.put(symbol, firstCandle.getInterval());
            }
            
            // Call strategy-specific initialization for this symbol
            Object initialState = onSymbolInit(symbol, candles, params);
            strategyStateBySymbol.put(symbol, initialState);
            
            System.out.println("✅ Initialized state for " + symbol + " with " + candles.size() + " candles");
        }
        
        // Mark strategy as bootstrapped
        bootstrapped = true;
        
        // For strategies that need a single global state, they can override this
        // Most strategies will use per-symbol state
        return null;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {

        System.out.println("🔔 onNewCandle() received for " + candle.getSymbol() + " at " + candle.getCloseTime() +
                         " (closed=" + candle.isClosed() + ")");

        if (!enabled) {
            Map<String, Object> result = new HashMap<>();
            result.put("orders", Collections.emptyList());
            result.put("state", state);
            return result;
        }
        
        String symbol = candle.getSymbol();
        
        // Only process closed candles
        if (!candle.isClosed()) {
            Map<String, Object> result = new HashMap<>();
            result.put("orders", Collections.emptyList());
            result.put("state", strategyStateBySymbol.getOrDefault(symbol, state));
            return result;
        }
        
        // Track provider and interval
        symbolProviders.put(symbol, candle.getProvider());
        symbolIntervals.put(symbol, candle.getInterval());
        lastUpdateTime.put(symbol, candle.getCloseTime());
        
        // Get current state for this symbol
        Object currentState = strategyStateBySymbol.getOrDefault(symbol, state);
        
        // Call strategy-specific candle processing
        Map<String, Object> result = onCandleClosed(symbol, candle, params, currentState);
        
        // Update state for this symbol
        Object newState = result.getOrDefault("state", currentState);
        strategyStateBySymbol.put(symbol, newState);
        
        // Ensure orders key exists
        if (!result.containsKey("orders")) {
            result.put("orders", Collections.emptyList());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> onNewTick(String symbol, BigDecimal price, Map<String, Object> params, Object state) {

        System.out.println("🔔 onNewTick() received for " + symbol + " at price " + price);

        if (!enabled) {
            Map<String, Object> result = new HashMap<>();
            result.put("orders", Collections.emptyList());
            result.put("state", state);
            return result;
        }
        
        // Get current state for this symbol
        Object currentState = strategyStateBySymbol.getOrDefault(symbol, state);
        
        // Call strategy-specific tick processing (most strategies won't override this)
        Map<String, Object> result = onTickUpdate(symbol, price, params, currentState);
        
        // Update state if changed
        Object newState = result.getOrDefault("state", currentState);
        strategyStateBySymbol.put(symbol, newState);
        
        // Ensure orders key exists
        if (!result.containsKey("orders")) {
            result.put("orders", Collections.emptyList());
        }
        
        return result;
    }
    
    // ============================================================
    // STRATEGY-SPECIFIC ABSTRACT METHODS (Subclasses implement these)
    // ============================================================
    
    /**
     * Initialize state for a specific symbol with historical data
     * Subclasses override this to set up indicator buffers, calculate initial values, etc.
     * 
     * @param symbol The trading symbol
     * @param historicalCandles Historical candles for this symbol (sorted chronologically)
     * @param params Strategy configuration parameters
     * @return Initial state object for this symbol (can be null)
     */
    protected abstract Object onSymbolInit(String symbol, List<CandlestickData> historicalCandles, Map<String, Object> params);
    
    /**
     * Process a closed candle and generate trading orders
     * This is the core strategy logic that subclasses must implement
     * 
     * @param symbol Trading symbol
     * @param candle Closed candlestick data
     * @param params Strategy configuration parameters
     * @param state Current state for this symbol
     * @return Map containing:
     *   - "orders": List<Order> - Orders to execute (required)
     *   - "state": Updated state for next call (optional)
     *   - "indicators": Map<String, BigDecimal> - Indicator values for charts (optional)
     *   - "signals": Map<String, Object> - Additional signal data (optional)
     */
    protected abstract Map<String, Object> onCandleClosed(String symbol, CandlestickData candle, Map<String, Object> params, Object state);
    
    /**
     * Process a price tick (optional - override if needed for intra-candle updates)
     * Default implementation returns empty orders
     * 
     * @param symbol Trading symbol
     * @param price Current tick price
     * @param params Strategy configuration parameters
     * @param state Current state for this symbol
     * @return Map containing orders and updated state
     */
    protected Map<String, Object> onTickUpdate(String symbol, BigDecimal price, Map<String, Object> params, Object state) {
        // Default: do nothing on ticks
        Map<String, Object> result = new HashMap<>();
        result.put("orders", Collections.emptyList());
        result.put("state", state);
        return result;
    }
    
    // ============================================================
    // UTILITY METHODS FOR STRATEGY IMPLEMENTATIONS
    // ============================================================

    /**
     * Get price history for a symbol from centralized storage
     * Reads close prices from the last N candlesticks
     */
    protected List<BigDecimal> getPriceHistory(String symbol, int count) {
        if (candlestickHistoryService == null) {
            System.err.println("⚠️ CandlestickHistoryService not injected, returning empty history");
            return Collections.emptyList();
        }
        
        String provider = symbolProviders.get(symbol);
        String interval = symbolIntervals.get(symbol);
        
        if (provider == null || interval == null) {
            System.err.println("⚠️ Provider or interval not tracked for " + symbol);
            return Collections.emptyList();
        }
        
        List<CandlestickData> candles = candlestickHistoryService.getLastNCandlesticks(
            provider, symbol, interval, count
        );
        
        return candles.stream()
            .map(CandlestickData::getClose)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get price history for a symbol (uses minimum required candles)
     */
    protected List<BigDecimal> getPriceHistory(String symbol) {
        return getPriceHistory(symbol, getMinRequiredCandles());
    }
    
    /**
     * Get full candlestick history for a symbol
     */
    protected List<CandlestickData> getCandlestickHistory(String symbol, int count) {
        if (candlestickHistoryService == null) {
            return Collections.emptyList();
        }
        
        String provider = symbolProviders.get(symbol);
        String interval = symbolIntervals.get(symbol);
        
        if (provider == null || interval == null) {
            return Collections.emptyList();
        }
        
        return candlestickHistoryService.getLastNCandlesticks(provider, symbol, interval, count);
    }

    /**
     * Check if enough data is available for analysis
     */
    protected boolean hasEnoughData(String symbol, int requiredPeriod) {
        if (candlestickHistoryService == null) {
            return false;
        }
        
        String provider = symbolProviders.get(symbol);
        String interval = symbolIntervals.get(symbol);
        
        if (provider == null || interval == null) {
            return false;
        }
        
        return candlestickHistoryService.hasEnoughData(provider, symbol, interval, requiredPeriod);
    }

    /**
     * Create a buy order to open LONG position
     */
    protected Order createBuyOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = calculateOrderQuantity(symbol, price);
        Order order = new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            OrderSide.BUY,
            quantity,
            price,
            getStrategyId()
        );
        order.setPositionSide(org.cloudvision.trading.bot.account.PositionSide.LONG);
        return order;
    }
    
    /**
     * Create a sell order to close LONG position
     */
    protected Order createCloseLongOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("⚠️ No LONG position to close for " + symbol);
        }
        
        Order order = new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            OrderSide.SELL,
            quantity,
            price,
            getStrategyId()
        );
        order.setPositionSide(org.cloudvision.trading.bot.account.PositionSide.LONG);
        return order;
    }
    
    /**
     * FUTURES: Create a sell order to open SHORT position
     */
    protected Order createShortOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = calculateOrderQuantity(symbol, price);
        Order order = new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            OrderSide.SELL,
            quantity,
            price,
            getStrategyId()
        );
        order.setPositionSide(org.cloudvision.trading.bot.account.PositionSide.SHORT);
        return order;
    }
    
    /**
     * FUTURES: Create a buy order to close SHORT position
     */
    protected Order createCloseShortOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("⚠️ No SHORT position to close for " + symbol);
        }
        
        Order order = new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            OrderSide.BUY,
            quantity,
            price,
            getStrategyId()
        );
        order.setPositionSide(org.cloudvision.trading.bot.account.PositionSide.SHORT);
        return order;
    }

    /**
     * Calculate order quantity for BUY orders based on position size and price
     */
    protected BigDecimal calculateOrderQuantity(String symbol, BigDecimal price) {
        if (config == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return config.getMaxPositionSize().divide(price, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * FUTURES: Calculate quantity to close for a specific position side (LONG or SHORT)
     */
    protected BigDecimal calculateCloseQuantity(String symbol, org.cloudvision.trading.bot.account.PositionSide positionSide) {
        if (accountManager == null) {
            System.out.println("⚠️ AccountManager not available, using default quantity calculation");
            return BigDecimal.ZERO;
        }
        
        try {
            TradingAccount activeAccount = accountManager.getActiveAccount();
            if (activeAccount == null) {
                System.out.println("⚠️ No active account");
                return BigDecimal.ZERO;
            }
            
            // FUTURES: Get total quantity from open positions of the specific side
            List<org.cloudvision.trading.bot.account.Position> openPositions = 
                activeAccount.getOpenPositionsBySymbol(symbol).stream()
                    .filter(p -> p.getSide() == positionSide)
                    .toList();
            
            if (openPositions.isEmpty()) {
                String sideStr = positionSide == org.cloudvision.trading.bot.account.PositionSide.LONG ? "LONG" : "SHORT";
                System.out.println("⚠️ No open " + sideStr + " positions for " + symbol);
                return BigDecimal.ZERO;
            }
            
            // Sum up all position quantities for this symbol and side
            BigDecimal totalQuantity = openPositions.stream()
                .map(org.cloudvision.trading.bot.account.Position::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            String sideStr = positionSide == org.cloudvision.trading.bot.account.PositionSide.LONG ? "LONG" : "SHORT";
            System.out.println("📊 Total open " + sideStr + " position for " + symbol + ": " + totalQuantity);
            return totalQuantity;
            
        } catch (Exception e) {
            System.err.println("❌ Error calculating close quantity: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Extract base asset from trading pair symbol
     * Examples: BTCUSDT -> BTC, ETHBTC -> ETH
     */
    protected String extractBaseAsset(String symbol) {
        // Common quote assets
        String[] quoteAssets = {"USDT", "BUSD", "USDC", "BTC", "ETH", "BNB"};
        
        for (String quote : quoteAssets) {
            if (symbol.endsWith(quote)) {
                return symbol.substring(0, symbol.length() - quote.length());
            }
        }
        
        // Default: assume last 4 characters are quote asset
        if (symbol.length() > 4) {
            return symbol.substring(0, symbol.length() - 4);
        }
        
        return symbol;
    }

    
    @Override
    public int getMinRequiredCandles() {
        // Default: require 200 candles for initialization
        // Subclasses should override to specify their actual requirements
        return 200;
    }

    @Override
    public boolean isBootstrapped() {
        return bootstrapped;
    }

    @Override
    public List<String> getSymbols() {
        return config != null ? config.getSymbols() : List.of();
    }

    @Override
    public void setConfig(StrategyConfig config) {
        this.config = config;
        if (this.stats == null) {
            this.stats = new StrategyStats();
        }
        System.out.println("📝 Config set for " + getStrategyName());
    }
    
    @Override
    public StrategyConfig getConfig() {
        return config;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public StrategyStats getStats() {
        return stats;
    }

    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        // Default implementation - subclasses should override to define their indicators
        return new HashMap<>();
    }

    @Override
    public void generateHistoricalVisualizationData(List<CandlestickData> historicalData) {
        // Default implementation does nothing
        // Subclasses should override to generate visualization data from historical candles
        System.out.println("⚠️ " + getStrategyName() + " does not implement historical visualization generation");
    }
    
    @Override
    public void reset() {
        // Clear all tracking maps
        symbolProviders.clear();
        symbolIntervals.clear();
        lastSignal.clear();
        lastUpdateTime.clear();
        
        // Clear event-driven state management
        strategyStateBySymbol.clear();
        strategyParams.clear();
        
        // Reset bootstrapped flag
        bootstrapped = false;
        
        // Reset stats if they exist
        if (stats != null) {
            stats = new StrategyStats();
        }
        
        System.out.println("🔄 " + getStrategyName() + " state reset - all memory cleared");
    }
}

