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

