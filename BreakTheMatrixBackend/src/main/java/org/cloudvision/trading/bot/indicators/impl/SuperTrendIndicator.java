package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SuperTrend Indicator
 * 
 * A trend-following indicator that uses Average True Range (ATR) to identify trend direction.
 * Plots a line that changes color based on trend:
 * - Green (uptrend): Price above SuperTrend line, direction < 0
 * - Red (downtrend): Price below SuperTrend line, direction > 0
 * 
 * The indicator is particularly useful for:
 * - Identifying trend direction
 * - Dynamic support/resistance levels
 * - Entry/exit signals when trend changes
 */
@Component
public class SuperTrendIndicator extends AbstractIndicator {
    
    public SuperTrendIndicator() {
        super(
            "supertrend",
            "SuperTrend",
            "Trend-following indicator using ATR. Green line indicates uptrend, red indicates downtrend. Used for identifying trend direction and dynamic support/resistance.",
            IndicatorCategory.TREND
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("atrPeriod", IndicatorParameter.builder("atrPeriod")
            .displayName("ATR Period")
            .description("Number of periods for Average True Range calculation")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(10)
            .minValue(1)
            .maxValue(100)
            .required(true)
            .build());
        
        params.put("multiplier", IndicatorParameter.builder("multiplier")
            .displayName("ATR Multiplier")
            .description("Multiplier for ATR to create the bands")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(3.0)
            .minValue(0.5)
            .maxValue(10.0)
            .required(true)
            .build());
        
        params.put("upColor", IndicatorParameter.builder("upColor")
            .displayName("Uptrend Color")
            .description("Color for the SuperTrend line during uptrend")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#26a69a")
            .required(false)
            .build());
        
        params.put("downColor", IndicatorParameter.builder("downColor")
            .displayName("Downtrend Color")
            .description("Color for the SuperTrend line during downtrend")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ef5350")
            .required(false)
            .build());
        
        params.put("lineWidth", IndicatorParameter.builder("lineWidth")
            .displayName("Line Width")
            .description("Width of the SuperTrend line")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(2)
            .minValue(1)
            .maxValue(10)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int atrPeriod = getIntParameter(params, "atrPeriod", 10);
        double multiplierDouble = getDoubleParameter(params, "multiplier", 3.0);
        BigDecimal multiplier = BigDecimal.valueOf(multiplierDouble);
        
        if (candles == null || candles.size() < atrPeriod + 1) {
            return Map.of(
                "supertrend", BigDecimal.ZERO,
                "direction", BigDecimal.ZERO
            );
        }
        
        // Extract price data
        List<BigDecimal> highs = candles.stream()
            .map(CandlestickData::getHigh)
            .collect(Collectors.toList());
        
        List<BigDecimal> lows = candles.stream()
            .map(CandlestickData::getLow)
            .collect(Collectors.toList());
        
        List<BigDecimal> closes = candles.stream()
            .map(CandlestickData::getClose)
            .collect(Collectors.toList());
        
        // Calculate SuperTrend
        BigDecimal[] result = TechnicalIndicators.calculateSuperTrend(
            highs, lows, closes, atrPeriod, multiplier, null, null
        );
        
        if (result == null) {
            return Map.of(
                "supertrend", BigDecimal.ZERO,
                "direction", BigDecimal.ZERO
            );
        }
        
        Map<String, BigDecimal> output = new HashMap<>();
        output.put("supertrend", result[0]); // SuperTrend value
        output.put("direction", result[1]);  // Direction: -1 = uptrend, +1 = downtrend
        
        // Calculate current price for reference
        BigDecimal currentPrice = closes.get(closes.size() - 1);
        output.put("price", currentPrice);
        
        // Calculate distance from SuperTrend
        BigDecimal distance = currentPrice.subtract(result[0]).abs();
        output.put("distance", distance);
        
        return output;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        int atrPeriod = getIntParameter(params, "atrPeriod", 10);
        double multiplier = getDoubleParameter(params, "multiplier", 3.0);
        String upColor = getStringParameter(params, "upColor", "#26a69a");
        String downColor = getStringParameter(params, "downColor", "#ef5350");
        int lineWidth = getIntParameter(params, "lineWidth", 2);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // SuperTrend line with dynamic coloring
        metadata.put("supertrend", IndicatorMetadata.builder("supertrend")
            .displayName("SuperTrend(" + atrPeriod + ", " + multiplier + ")")
            .asLine(upColor, lineWidth)  // Default to uptrend color
            .addConfig("upColor", upColor)
            .addConfig("downColor", downColor)
            .addConfig("lineStyle", 0)  // Solid line
            .addConfig("dynamicColor", true)  // Flag for frontend to use direction-based coloring
            .separatePane(false)  // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int atrPeriod = getIntParameter(params, "atrPeriod", 10);
        return atrPeriod + 1;  // Need at least ATR period + 1 for calculation
    }
    
    /**
     * Helper method to get double parameter
     */
    private double getDoubleParameter(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
    
    /**
     * Calculate SuperTrend for multiple candles progressively (for historical data)
     * This maintains state across calculations for proper SuperTrend tracking
     */
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                    Map<String, Object> params,
                                                    BigDecimal previousSuperTrend,
                                                    BigDecimal previousDirection) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int atrPeriod = getIntParameter(params, "atrPeriod", 10);
        double multiplierDouble = getDoubleParameter(params, "multiplier", 3.0);
        BigDecimal multiplier = BigDecimal.valueOf(multiplierDouble);
        
        if (candles == null || candles.size() < atrPeriod + 1) {
            return Map.of(
                "values", Map.of(
                    "supertrend", BigDecimal.ZERO,
                    "direction", BigDecimal.ZERO
                ),
                "previousSuperTrend", BigDecimal.ZERO,
                "previousDirection", BigDecimal.ZERO
            );
        }
        
        // Extract price data
        List<BigDecimal> highs = candles.stream()
            .map(CandlestickData::getHigh)
            .collect(Collectors.toList());
        
        List<BigDecimal> lows = candles.stream()
            .map(CandlestickData::getLow)
            .collect(Collectors.toList());
        
        List<BigDecimal> closes = candles.stream()
            .map(CandlestickData::getClose)
            .collect(Collectors.toList());
        
        // Calculate SuperTrend with previous state
        BigDecimal[] result = TechnicalIndicators.calculateSuperTrend(
            highs, lows, closes, atrPeriod, multiplier, 
            previousSuperTrend, previousDirection
        );
        
        if (result == null) {
            return Map.of(
                "values", Map.of(
                    "supertrend", BigDecimal.ZERO,
                    "direction", BigDecimal.ZERO
                ),
                "previousSuperTrend", previousSuperTrend != null ? previousSuperTrend : BigDecimal.ZERO,
                "previousDirection", previousDirection != null ? previousDirection : BigDecimal.ZERO
            );
        }
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("supertrend", result[0]);
        values.put("direction", result[1]);
        
        // Calculate additional metrics
        BigDecimal currentPrice = closes.get(closes.size() - 1);
        values.put("price", currentPrice);
        values.put("distance", currentPrice.subtract(result[0]).abs());
        
        Map<String, Object> output = new HashMap<>();
        output.put("values", values);
        output.put("previousSuperTrend", result[0]);
        output.put("previousDirection", result[1]);
        
        return output;
    }
}

