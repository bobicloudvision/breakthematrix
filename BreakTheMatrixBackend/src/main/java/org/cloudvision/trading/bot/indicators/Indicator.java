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
 * Each indicator is stateless and thread-safe.
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
     * Calculate indicator value(s) based on candlestick data
     * 
     * @param candles Historical candlestick data (sorted chronologically)
     * @param params Configuration parameters for this calculation
     * @return Map of indicator output names to values
     *         Example for SMA: {"sma": 50000.00}
     *         Example for Bollinger Bands: {"upper": 51000, "middle": 50000, "lower": 49000}
     */
    Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params);
    
    /**
     * Progressive calculation that maintains state across candles
     * This is used for efficient historical data processing and indicators that need to track state
     * or return additional data like shapes/boxes.
     * 
     * Default implementation: wraps standard calculate() method
     * Override this for:
     * - Indicators that need to maintain state (e.g., order blocks, market structure)
     * - Indicators that return shapes/boxes/additional data
     * - Performance optimization when calculating many sequential data points
     * 
     * @param candles Historical candlestick data (sorted chronologically)
     * @param params Configuration parameters for this calculation
     * @param previousState State from previous calculation (null for first call, type depends on indicator)
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with indicator values (required)
     *   - "state": Object for next iteration (can be null)
     *   - Additional data (e.g., "boxes", "orderBlocks", "shapes") - optional
     */
    default Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                     Map<String, Object> params,
                                                     Object previousState) {
        // Default: just call calculate and wrap result
        return Map.of(
            "values", calculate(candles, params),
            "state", previousState != null ? previousState : new Object()
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

