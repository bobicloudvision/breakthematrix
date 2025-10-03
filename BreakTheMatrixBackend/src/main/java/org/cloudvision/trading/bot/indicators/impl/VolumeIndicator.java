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
 */
@Component
public class VolumeIndicator extends AbstractIndicator {
    
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
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            return Map.of("volume", BigDecimal.ZERO);
        }
        
        // Get the last candle's volume
        CandlestickData lastCandle = candles.get(candles.size() - 1);
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("volume", lastCandle.getVolume());
        
        return result;
    }
    
    /**
     * Progressive calculation that returns color information per data point
     */
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                     Map<String, Object> params, 
                                                     Object previousState) {
        if (candles == null || candles.isEmpty()) {
            return Map.of("values", Map.of("volume", BigDecimal.ZERO));
        }
        
        params = mergeWithDefaults(params);
        CandlestickData lastCandle = candles.get(candles.size() - 1);
        
        // Get color parameters
        String bullishColor = getStringParameter(params, "bullishColor", "#26a69a");
        String bearishColor = getStringParameter(params, "bearishColor", "#ef5350");
        
        // Determine color based on candle direction
        String color = getVolumeColor(lastCandle, bullishColor, bearishColor);
        
        // Return values and color
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("volume", lastCandle.getVolume());
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("color", color);
        result.put("state", null); // No state needed
        
        return result;
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
    
    /**
     * Helper method to determine volume bar color based on candle direction
     */
    public static String getVolumeColor(CandlestickData candle, String bullishColor, String bearishColor) {
        if (candle.getClose().compareTo(candle.getOpen()) >= 0) {
            return bullishColor;
        } else {
            return bearishColor;
        }
    }
}

