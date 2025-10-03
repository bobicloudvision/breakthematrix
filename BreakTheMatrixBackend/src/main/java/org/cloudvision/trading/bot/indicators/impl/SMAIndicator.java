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
 * Simple Moving Average (SMA) Indicator
 * 
 * Calculates the arithmetic mean of prices over a specified period.
 * Commonly used to identify trend direction and potential support/resistance levels.
 */
@Component
public class SMAIndicator extends AbstractIndicator {
    
    public SMAIndicator() {
        super(
            "sma",
            "Simple Moving Average",
            "Arithmetic mean of closing prices over a specified period. Smooths price data to identify trends.",
            IndicatorCategory.TREND
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("period", IndicatorParameter.builder("period")
            .displayName("Period")
            .description("Number of periods for the moving average")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(20)
            .minValue(1)
            .maxValue(500)
            .required(true)
            .build());
        
        params.put("source", IndicatorParameter.builder("source")
            .displayName("Price Source")
            .description("Price type to use for calculation")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("close")
            .required(false)
            .build());
        
        params.put("color", IndicatorParameter.builder("color")
            .displayName("Line Color")
            .description("Color for the SMA line on the chart")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#2962FF")
            .required(false)
            .build());
        
        params.put("lineWidth", IndicatorParameter.builder("lineWidth")
            .displayName("Line Width")
            .description("Width of the SMA line")
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
        
        int period = getIntParameter(params, "period", 20);
        String source = getStringParameter(params, "source", "close");
        
        if (candles == null || candles.size() < period) {
            return Map.of("sma", BigDecimal.ZERO);
        }
        
        // Extract prices based on source
        List<BigDecimal> prices = candles.stream()
            .map(c -> extractPrice(c, source))
            .collect(Collectors.toList());
        
        // Calculate SMA
        BigDecimal sma = TechnicalIndicators.calculateSMA(prices, period);
        
        return Map.of("sma", sma);
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        int period = getIntParameter(params, "period", 20);
        String color = getStringParameter(params, "color", "#2962FF");
        int lineWidth = getIntParameter(params, "lineWidth", 2);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("sma", IndicatorMetadata.builder("sma")
            .displayName("SMA(" + period + ")")
            .asLine(color, lineWidth)
            .separatePane(false)  // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        return getIntParameter(params, "period", 20);
    }
    
    /**
     * Extract price from candle based on source type
     */
    private BigDecimal extractPrice(CandlestickData candle, String source) {
        return switch (source.toLowerCase()) {
            case "open" -> candle.getOpen();
            case "high" -> candle.getHigh();
            case "low" -> candle.getLow();
            case "close" -> candle.getClose();
            case "hl2" -> candle.getHigh().add(candle.getLow())
                .divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP);
            case "hlc3" -> candle.getHigh().add(candle.getLow()).add(candle.getClose())
                .divide(BigDecimal.valueOf(3), 8, java.math.RoundingMode.HALF_UP);
            case "ohlc4" -> candle.getOpen().add(candle.getHigh()).add(candle.getLow()).add(candle.getClose())
                .divide(BigDecimal.valueOf(4), 8, java.math.RoundingMode.HALF_UP);
            default -> candle.getClose();
        };
    }
}

