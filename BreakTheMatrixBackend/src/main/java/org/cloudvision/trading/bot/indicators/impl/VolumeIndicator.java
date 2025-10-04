package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Volume Indicator
 * 
 * Displays trading volume with color-coded bars:
 * - Green for bullish candles (close >= open)
 * - Red for bearish candles (close < open)
 * 
 * This indicator uses an event-driven approach for efficient real-time processing.
 */
@Component
public class VolumeIndicator extends AbstractIndicator {
    
    /**
     * Internal state class to track volume data
     * For this simple indicator, state is minimal but included for consistency
     */
    public static class VolumeState {
        private BigDecimal lastVolume;
        private String lastColor;
        
        public VolumeState() {
            this.lastVolume = BigDecimal.ZERO;
            this.lastColor = null;
        }
        
        public BigDecimal getLastVolume() {
            return lastVolume;
        }
        
        public void setLastVolume(BigDecimal lastVolume) {
            this.lastVolume = lastVolume;
        }
        
        public String getLastColor() {
            return lastColor;
        }
        
        public void setLastColor(String lastColor) {
            this.lastColor = lastColor;
        }
    }
    
    public VolumeIndicator() {
        super(
            "volume",
            "Volume",
            "Trading volume with color-coded bars based on candle direction",
            IndicatorCategory.VOLUME
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("bullishColor", IndicatorParameter.builder("bullishColor")
            .displayName("Bullish Color")
            .description("Color for volume bars on bullish candles")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#26a69a")
            .required(false)
            .build());
        
        params.put("bearishColor", IndicatorParameter.builder("bearishColor")
            .displayName("Bearish Color")
            .description("Color for volume bars on bearish candles")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ef5350")
            .required(false)
            .build());
        
        return params;
    }
    
    /**
     * Initialize the indicator with historical data and parameters, returns initial state
     * 
     * For Volume indicator, we don't need to process historical candles since each volume bar
     * is independent and doesn't depend on previous values. However, if historical candles
     * are provided, we can initialize state with the last candle's volume.
     * 
     * @param historicalCandles Historical candlestick data for initialization (can be empty)
     * @param params Configuration parameters
     * @return Initial state object (VolumeState)
     */
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        // Validate parameters
        validateParameters(params);
        
        // Create initial state
        VolumeState state = new VolumeState();
        
        // If historical candles are provided, initialize with the last candle
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            params = mergeWithDefaults(params);
            
            CandlestickData lastCandle = historicalCandles.get(historicalCandles.size() - 1);
            String bullishColor = getStringParameter(params, "bullishColor", "#26a69a");
            String bearishColor = getStringParameter(params, "bearishColor", "#ef5350");
            
            state.setLastVolume(lastCandle.getVolume());
            state.setLastColor(determineVolumeColor(lastCandle, bullishColor, bearishColor));
        }
        
        return state;
    }
    
    /**
     * Process a single historical or live candle, returns updated state and values
     * 
     * @param candle The candle to process
     * @param params Configuration parameters
     * @param state Current state from previous call (or from onInit)
     * @return Map containing "values" (indicator values), "state" (updated state), and "color" (bar color)
     */
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        // Cast state
        VolumeState volumeState = (state instanceof VolumeState) ? (VolumeState) state : new VolumeState();
        
        // Get color parameters
        String bullishColor = getStringParameter(params, "bullishColor", "#26a69a");
        String bearishColor = getStringParameter(params, "bearishColor", "#ef5350");
        
        // Get volume from candle
        BigDecimal volume = candle.getVolume();
        
        // Determine color based on candle direction
        String color = determineVolumeColor(candle, bullishColor, bearishColor);
        
        // Update state
        volumeState.setLastVolume(volume);
        volumeState.setLastColor(color);
        
        // Build result
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("volume", volume);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", volumeState);
        result.put("color", color);
        
        return result;
    }
    
    /**
     * Process a single tick (optional)
     * Volume doesn't change on individual ticks, so we return the current state
     * 
     * @param price Current price
     * @param params Configuration parameters
     * @param state Current state
     * @return Map containing empty values and unchanged state
     */
    public Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        // Volume doesn't update on individual ticks, only on new candles
        // Return empty values with current state
        return Map.of(
            "values", Map.of(),
            "state", state != null ? state : new VolumeState()
        );
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        String bullishColor = getStringParameter(params, "bullishColor", "#26a69a");
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("volume", IndicatorMetadata.builder("volume")
            .displayName("Volume")
            .asHistogram(bullishColor)  // Default color, will be overridden per bar
            .addConfig("priceFormat", Map.of("type", "volume"))
            .addConfig("priceScaleId", "volume")
            .separatePane(true)  // Show in separate pane below price
            .paneOrder(1)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1;  // Only needs 1 candle to show volume
    }
    
    // ============================================================
    // Helper methods
    // ============================================================
    
    /**
     * Helper method to determine volume bar color based on candle direction
     * 
     * @param candle The candle to analyze
     * @param bullishColor Color for bullish candles (close >= open)
     * @param bearishColor Color for bearish candles (close < open)
     * @return The appropriate color based on candle direction
     */
    private static String determineVolumeColor(CandlestickData candle, String bullishColor, String bearishColor) {
        if (candle.getClose().compareTo(candle.getOpen()) >= 0) {
            return bullishColor;
        } else {
            return bearishColor;
        }
    }
    
    /**
     * Legacy helper method for backward compatibility
     * @deprecated Use {@link #determineVolumeColor(CandlestickData, String, String)} instead
     */
    @Deprecated
    public static String getVolumeColor(CandlestickData candle, String bullishColor, String bearishColor) {
        return determineVolumeColor(candle, bullishColor, bearishColor);
    }
}

