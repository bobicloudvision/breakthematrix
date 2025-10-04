package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple Moving Average (SMA) Indicator
 * 
 * Calculates the arithmetic mean of prices over a specified period.
 * Commonly used to identify trend direction and potential support/resistance levels.
 * 
 * This indicator uses an event-driven approach for efficient real-time processing.
 */
@Component
public class SMAIndicator extends AbstractIndicator {
    
    /**
     * Internal state class to track SMA calculation data
     * Uses a circular buffer for efficient rolling window calculations
     */
    public static class SMAState {
        private final LinkedList<BigDecimal> priceBuffer;
        private final int period;
        private BigDecimal sum;
        
        public SMAState(int period) {
            this.period = period;
            this.priceBuffer = new LinkedList<>();
            this.sum = BigDecimal.ZERO;
        }
        
        public void addPrice(BigDecimal price) {
            priceBuffer.add(price);
            sum = sum.add(price);
            
            // Remove oldest price if buffer exceeds period
            if (priceBuffer.size() > period) {
                BigDecimal removed = priceBuffer.removeFirst();
                sum = sum.subtract(removed);
            }
        }
        
        public BigDecimal calculateSMA() {
            if (priceBuffer.isEmpty()) {
                return BigDecimal.ZERO;
            }
            if (priceBuffer.size() < period) {
                // Not enough data yet, return average of available data
                return sum.divide(BigDecimal.valueOf(priceBuffer.size()), 8, java.math.RoundingMode.HALF_UP);
            }
            return sum.divide(BigDecimal.valueOf(period), 8, java.math.RoundingMode.HALF_UP);
        }
        
        public int getBufferSize() {
            return priceBuffer.size();
        }
        
        public int getPeriod() {
            return period;
        }
    }
    
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
    
    /**
     * Initialize the indicator with historical data and parameters, returns initial state
     * 
     * @param historicalCandles Historical candlestick data for initialization
     * @param params Configuration parameters
     * @return Initial state object (SMAState)
     */
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        // Validate parameters
        validateParameters(params);
        params = mergeWithDefaults(params);
        
        int period = getIntParameter(params, "period", 20);
        String source = getStringParameter(params, "source", "close");
        
        // Create initial state
        SMAState state = new SMAState(period);
        
        // Process historical candles to build initial state
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            for (CandlestickData candle : historicalCandles) {
                BigDecimal price = extractPrice(candle, source);
                state.addPrice(price);
            }
        }
        
        return state;
    }
    
    /**
     * Process a single historical or live candle, returns updated state and values
     * 
     * @param candle The candle to process
     * @param params Configuration parameters
     * @param state Current state from previous call (or from onInit)
     * @return Map containing "values" (indicator values) and "state" (updated state)
     */
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        int period = getIntParameter(params, "period", 20);
        String source = getStringParameter(params, "source", "close");
        
        // Cast or create state
        SMAState smaState = (state instanceof SMAState) ? (SMAState) state : new SMAState(period);
        
        // Extract price and add to buffer
        BigDecimal price = extractPrice(candle, source);
        smaState.addPrice(price);
        
        // Calculate SMA from current state
        BigDecimal sma = smaState.calculateSMA();
        
        // Build result
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("sma", sma);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", smaState);
        
        return result;
    }
    
    /**
     * Process a single tick for real-time updates (optional)
     * SMA typically doesn't update on individual ticks, only on completed candles
     * 
     * @param price Current tick price
     * @param params Configuration parameters
     * @param state Current state
     * @return Map containing empty values and unchanged state
     */
    @Override
    public Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        // SMA doesn't update on individual ticks, only on new candles
        return Map.of(
            "values", Map.of(),
            "state", state != null ? state : new SMAState(getIntParameter(params, "period", 20))
        );
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

