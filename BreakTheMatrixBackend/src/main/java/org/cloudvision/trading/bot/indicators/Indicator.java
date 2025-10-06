package org.cloudvision.trading.bot.indicators;

import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Base interface for all technical indicators
 * 
 * Indicators are standalone, reusable components that can be:
 * - Used within trading strategies
 * - Added independently to charts via REST API
 * - Configured with custom parameters
 * 
 * Event-Driven Architecture:
 * Indicators use an event-driven approach with three lifecycle methods:
 * 1. onInit(historicalCandles, params) - Initialize state with historical data
 * 2. onNewCandle(candle, params, state) - Process each new candle (historical or live)
 * 3. onNewTick(price, params, state) - Process real-time price ticks (optional)
 * 
 * This design enables:
 * - Efficient incremental calculations (no need to reprocess entire history)
 * - Stateful indicators (order blocks, market structure, etc.)
 * - Real-time updates for live trading
 * - Clean separation between initialization and calculation
 * 
 * Thread Safety:
 * Indicator instances are thread-safe. State is managed per calculation context,
 * allowing multiple concurrent calculations with independent state objects.
 */
public interface Indicator {
    
    /**
     * Get unique identifier for this indicator
     * Examples: "sma", "rsi", "supertrend", "volume"
     */
    String getId();
    
    /**
     * Get display name for this indicator
     * Examples: "Simple Moving Average", "Relative Strength Index"
     */
    String getName();
    
    /**
     * Get description of what this indicator measures
     */
    String getDescription();
    
    /**
     * Get indicator category
     * Examples: "trend", "momentum", "volatility", "volume"
     */
    IndicatorCategory getCategory();
    
    /**
     * Get available configuration parameters for this indicator
     * Returns map of parameter name -> parameter metadata
     * Example: {"period": {"type": "int", "default": 14, "min": 1, "max": 200}}
     */
    Map<String, IndicatorParameter> getParameters();
    
    /**
     * Initialize the indicator with historical data and parameters, returns initial state
     * 
     * This method is called once when the indicator is first set up.
     * Use it to:
     * - Process historical candles to build initial state
     * - Validate parameters
     * - Pre-calculate values that depend on historical context (e.g., initial MA values)
     * - Initialize any internal buffers or data structures
     * 
     * For indicators that need a warm-up period (e.g., SMA(20) needs 20 candles),
     * the historical candles list should contain at least getMinRequiredCandles() candles.
     * 
     * @param historicalCandles Historical candlestick data for initialization (can be empty for stateless indicators)
     *                         Sorted chronologically (oldest first)
     * @param params Configuration parameters for this indicator
     * @return Initial state object (can be null if no state is needed)
     */
    Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params);
    
    /**
     * Process a single historical or live candle, returns updated state and values
     * 
     * This is the core calculation method called for each new candle.
     * The method receives the current candle and previous state, and returns:
     * - Updated indicator values
     * - Updated state for the next call
     * - Optional additional data (colors, shapes, boxes, etc.)
     * 
     * @param candle The candle to process
     * @param params Configuration parameters for this calculation
     * @param state Current state from previous call (or from onInit)
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with indicator values (required)
     *   - "state": Updated state for next iteration (can be null)
     *   - Additional data like "color", "boxes", "orderBlocks", "shapes" (optional)
     * 
     * Example return for Volume:
     *   {"values": {"volume": 1234.56}, "state": volumeState, "color": "#26a69a"}
     * 
     * Example return for SuperTrend:
     *   {"values": {"supertrend": 50000.00, "direction": 1}, "state": supertrendState}
     */
    Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state);
    
    /**
     * Process a single tick for real-time updates (optional)
     * 
     * This method is called when a price tick occurs within the current candle.
     * Most indicators don't need tick-level updates and can use the default implementation.
     * 
     * Override this for indicators that need real-time updates, such as:
     * - Live price lines that follow the current price
     * - Dynamic trailing stops
     * - Real-time momentum calculations
     * 
     * @param price Current tick price
     * @param params Configuration parameters
     * @param state Current state
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with updated indicator values (can be empty)
     *   - "state": Updated state (usually unchanged for ticks)
     *   - Additional data (optional)
     * 
     * Default implementation: Returns empty values with unchanged state
     */
    default Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        return Map.of(
            "values", Map.of(),
            "state", state
        );
    }
    
    // ============================================================
    // EXTENDED DATA TYPE SUPPORT FOR BOOKMAP-STYLE INDICATORS
    // ============================================================
    
    /**
     * Process order book data for indicators that need market depth information
     * 
     * This is OPTIONAL and only needed for indicators that visualize or analyze:
     * - Order book heatmaps (Bookmap-style)
     * - Bid/Ask imbalances
     * - Liquidity levels
     * - Market depth changes
     * 
     * WHEN TO IMPLEMENT:
     * Override this method if your indicator needs to:
     * 1. Show order book heatmap visualization
     * 2. Calculate bid/ask ratios or imbalances
     * 3. Detect liquidity walls or absorption
     * 4. Track order book changes in real-time
     * 
     * WHAT YOU GET:
     * OrderBookData contains:
     * - List of bid levels (price + quantity)
     * - List of ask levels (price + quantity)
     * - Best bid/ask prices
     * - Spread calculation
     * - Volume at each price level
     * 
     * HOW IT WORKS:
     * 1. System receives order book snapshot/update from exchange
     * 2. This method is called with current order book state
     * 3. Indicator processes and returns visualization data
     * 4. Results are broadcast to WebSocket clients
     * 
     * EXAMPLE USE CASES:
     * - Bookmap heatmap: Show volume at each price level
     * - Imbalance indicator: Calculate bid/ask ratio
     * - Liquidity indicator: Detect large walls in order book
     * - Absorption: Track when large orders are being filled
     * 
     * @param orderBook Current order book snapshot with bid/ask levels
     * @param params Configuration parameters for the indicator
     * @param state Current indicator state (for stateful processing)
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with calculated values (e.g., {"bidAskRatio": 1.5})
     *   - "state": Updated state object (or unchanged if stateless)
     *   - "heatmap": Optional heatmap data structure for visualization
     *   - "levels": Optional price level highlights (support/resistance from order book)
     *   - Additional visualization data
     * 
     * PERFORMANCE NOTE:
     * Order book updates are HIGH FREQUENCY (can be 100+ per second)
     * Keep processing lightweight or use throttling/aggregation
     * 
     * Default implementation: Returns empty result (indicator doesn't process order book data)
     */
    default Map<String, Object> onOrderBookUpdate(
            org.cloudvision.trading.model.OrderBookData orderBook, 
            Map<String, Object> params, 
            Object state) {
        return Map.of(
            "values", Map.of(),
            "state", state
        );
    }
    
    /**
     * Process trade data for indicators that need individual trade information
     * 
     * This is OPTIONAL and only needed for indicators that analyze:
     * - Order flow (buy vs sell pressure)
     * - Trade size distribution
     * - Aggressive buying/selling
     * - Volume profiling
     * 
     * WHEN TO IMPLEMENT:
     * Override this method if your indicator needs to:
     * 1. Build footprint candles or volume profiles
     * 2. Track buy vs sell volume (delta)
     * 3. Detect large trades (whales)
     * 4. Analyze order flow imbalances
     * 5. Calculate cumulative volume delta (CVD)
     * 
     * WHAT YOU GET:
     * TradeData contains:
     * - Trade ID
     * - Price and quantity
     * - Timestamp
     * - Side (buy/sell) - isBuyerMaker flag
     * - Whether it's aggressive buy/sell
     * - For aggregate trades: first/last trade ID
     * 
     * HOW IT WORKS:
     * 1. System receives individual/aggregate trade from exchange
     * 2. This method is called for each trade
     * 3. Indicator processes and accumulates data
     * 4. Results are broadcast when candle closes or on demand
     * 
     * EXAMPLE USE CASES:
     * - CVD: Accumulate buy volume - sell volume
     * - Order flow imbalance: Calculate buy/sell ratio per price level
     * - Volume profile: Build volume-at-price histogram
     * - Footprint chart: Track buy/sell volume per price level per time period
     * 
     * @param trade Individual or aggregate trade data
     * @param params Configuration parameters
     * @param state Current indicator state
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with calculated values
     *   - "state": Updated state (trades are usually accumulated in state)
     *   - "delta": Optional current delta value
     *   - "volumeProfile": Optional volume-at-price data
     *   - Additional data for visualization
     * 
     * PERFORMANCE NOTE:
     * Trade updates are VERY HIGH FREQUENCY (1000+ per second on active pairs)
     * Accumulate data in state and only output on candle close or periodic intervals
     * Consider using aggregate trades instead of individual trades
     * 
     * Default implementation: Returns empty result (indicator doesn't process trade data)
     */
    default Map<String, Object> onTradeUpdate(
            org.cloudvision.trading.model.TradeData trade, 
            Map<String, Object> params, 
            Object state) {
        return Map.of(
            "values", Map.of(),
            "state", state
        );
    }
    
    /**
     * Declare what data types this indicator needs to function
     * 
     * This allows the system to:
     * 1. Automatically subscribe to required data streams
     * 2. Route only relevant data to the indicator
     * 3. Optimize performance by not sending unnecessary data
     * 4. Validate that required data sources are available
     * 
     * RETURN A SET OF DATA TYPES YOUR INDICATOR NEEDS:
     * - KLINE: Candlestick/OHLC data (most common)
     * - TRADE: Individual trades (high frequency)
     * - AGGREGATE_TRADE: Compressed trade data (recommended for order flow)
     * - ORDER_BOOK: Full order book depth (very high frequency)
     * - BOOK_TICKER: Best bid/ask only (lightweight)
     * 
     * EXAMPLES:
     * 
     * Simple Moving Average:
     *   return Set.of(TradingDataType.KLINE);  // Only needs candles
     * 
     * Bookmap Heatmap:
     *   return Set.of(TradingDataType.ORDER_BOOK, TradingDataType.KLINE);
     *   // Needs order book for heatmap + candles for time axis
     * 
     * Order Flow Indicator:
     *   return Set.of(TradingDataType.AGGREGATE_TRADE, TradingDataType.KLINE);
     *   // Needs trades for order flow + candles for structure
     * 
     * Volume Profile:
     *   return Set.of(TradingDataType.AGGREGATE_TRADE);
     *   // Only needs trades to build volume profile
     * 
     * @return Set of TradingDataType values this indicator requires
     * 
     * Default implementation: Only requires KLINE (candlestick) data
     */
    default java.util.Set<org.cloudvision.trading.model.TradingDataType> getRequiredDataTypes() {
        return java.util.Set.of(org.cloudvision.trading.model.TradingDataType.KLINE);
    }
    
    /**
     * Get visualization metadata for frontend rendering
     * Defines how this indicator should be displayed on charts
     */
    Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params);
    
    /**
     * Get minimum required number of candles for calculation
     * Example: SMA(20) requires at least 20 candles
     */
    int getMinRequiredCandles(Map<String, Object> params);
    
    /**
     * Validate parameters before calculation
     * Throws IllegalArgumentException if parameters are invalid
     */
    default void validateParameters(Map<String, Object> params) {
        for (Map.Entry<String, IndicatorParameter> entry : getParameters().entrySet()) {
            String paramName = entry.getKey();
            IndicatorParameter paramMeta = entry.getValue();
            
            if (paramMeta.isRequired() && !params.containsKey(paramName)) {
                throw new IllegalArgumentException("Required parameter missing: " + paramName);
            }
        }
    }
    
    /**
     * Indicator categories for organization
     */
    enum IndicatorCategory {
        TREND("Trend Indicators"),
        MOMENTUM("Momentum Indicators"),
        VOLATILITY("Volatility Indicators"),
        VOLUME("Volume Indicators"),
        ORDER_FLOW("Order Flow Indicators"),
        OVERLAY("Overlay Indicators"),
        CUSTOM("Custom Indicators");
        
        private final String displayName;
        
        IndicatorCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}

