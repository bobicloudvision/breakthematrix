package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Engulfing Candle Indicator with RSI Filter
 * Based on Pine Script indicator by ahmedirshad419
 * 
 * Detects bullish and bearish engulfing candle patterns and filters them
 * using RSI conditions:
 * - BUY Signal: Bullish engulfing + RSI oversold (in last 3 candles)
 * - SELL Signal: Bearish engulfing + RSI overbought (in last 3 candles)
 * 
 * ALGORITHM:
 * 1. Calculate RSI for the specified period (default 14)
 * 2. Detect bullish engulfing: close >= previous open AND previous close < previous open
 * 3. Detect bearish engulfing: close <= previous open AND previous close > previous open
 * 4. Check if RSI was oversold/overbought in the last 3 candles
 * 5. Generate signals when pattern + RSI condition align
 * 
 * OUTPUT VALUES:
 * - rsi: Current RSI value
 * - bullishEngulfing: 1 if bullish engulfing detected, 0 otherwise
 * - bearishEngulfing: 1 if bearish engulfing detected, 0 otherwise
 * - buySignal: 1 if buy signal generated, 0 otherwise
 * - sellSignal: 1 if sell signal generated, 0 otherwise
 */
@Component
public class EngulfingCandleIndicator extends AbstractIndicator {
    
    /**
     * State for event-driven calculation
     */
    public static class EngulfingState {
        public List<BigDecimal> priceHistory = new ArrayList<>();
        public CandlestickData previousCandle = null;
        public List<BigDecimal> rsiHistory = new ArrayList<>(); // Keep last 3 RSI values
    }
    
    public EngulfingCandleIndicator() {
        super("engulfing", "Engulfing Candle with RSI", 
              "Detects engulfing candle patterns filtered by RSI overbought/oversold conditions",
              Indicator.IndicatorCategory.MOMENTUM);
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("rsiLength", IndicatorParameter.builder("rsiLength")
            .displayName("RSI Length")
            .description("Number of periods for RSI calculation")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(14)
            .minValue(2)
            .maxValue(100)
            .required(true)
            .build());
        
        params.put("rsiOverbought", IndicatorParameter.builder("rsiOverbought")
            .displayName("RSI Overbought Level")
            .description("RSI level considered overbought")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(70)
            .minValue(50)
            .maxValue(90)
            .required(true)
            .build());
        
        params.put("rsiOversold", IndicatorParameter.builder("rsiOversold")
            .displayName("RSI Oversold Level")
            .description("RSI level considered oversold")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(30)
            .minValue(10)
            .maxValue(50)
            .required(true)
            .build());
        
        params.put("showLabels", IndicatorParameter.builder("showLabels")
            .displayName("Show Labels")
            .description("Display buy/sell labels on chart")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("buyColor", IndicatorParameter.builder("buyColor")
            .displayName("Buy Signal Color")
            .description("Color for buy signal markers")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#26a69a")
            .required(false)
            .build());
        
        params.put("sellColor", IndicatorParameter.builder("sellColor")
            .displayName("Sell Signal Color")
            .description("Color for sell signal markers")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ef5350")
            .required(false)
            .build());
        
        return params;
    }
    
    /**
     * Initialize the indicator with historical data and parameters
     * 
     * Processes historical candles to build initial state including:
     * - Price history for RSI calculation
     * - Previous candle for engulfing detection
     * - RSI history for lookback check
     * 
     * @param historicalCandles Historical candlestick data for initialization
     * @param params Configuration parameters
     * @return Initial state object (EngulfingState)
     */
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        // Validate and merge with defaults
        validateParameters(params);
        params = mergeWithDefaults(params);
        
        int rsiLength = getIntParameter(params, "rsiLength", 14);
        
        // Create initial state
        EngulfingState state = new EngulfingState();
        
        // Process historical candles to build initial state
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            for (CandlestickData candle : historicalCandles) {
                // Build price history for RSI calculation
                state.priceHistory.add(candle.getClose());
                
                // Calculate RSI if we have enough data
                if (state.priceHistory.size() > rsiLength) {
                    BigDecimal rsi = TechnicalIndicators.calculateRSI(state.priceHistory, rsiLength);
                    if (rsi != null) {
                        state.rsiHistory.add(rsi);
                        // Keep only last 3 RSI values
                        if (state.rsiHistory.size() > 3) {
                            state.rsiHistory.remove(0);
                        }
                    }
                }
                
                // Update previous candle for next iteration
                state.previousCandle = candle;
            }
        }
        
        return state;
    }
    
    /**
     * Process a single historical or live candle
     * 
     * Updates state with new candle data and detects:
     * - Bullish and bearish engulfing patterns
     * - RSI overbought/oversold conditions
     * - Buy/sell signals based on pattern + RSI filter
     * 
     * @param candle The candle to process
     * @param params Configuration parameters
     * @param state Current state from previous call (or from onInit)
     * @return Map containing values, state, and markers
     */
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        int rsiLength = getIntParameter(params, "rsiLength", 14);
        int rsiOverbought = getIntParameter(params, "rsiOverbought", 70);
        int rsiOversold = getIntParameter(params, "rsiOversold", 30);
        boolean showLabels = getBooleanParameter(params, "showLabels", true);
        String buyColor = getStringParameter(params, "buyColor", "#26a69a");
        String sellColor = getStringParameter(params, "sellColor", "#ef5350");
        
        // Cast or create state
        EngulfingState engulfingState = (state instanceof EngulfingState) ? 
            (EngulfingState) state : new EngulfingState();
        
        // Add current candle close to price history
        engulfingState.priceHistory.add(candle.getClose());
        
        // Calculate RSI
        BigDecimal rsi = null;
        if (engulfingState.priceHistory.size() > rsiLength) {
            rsi = TechnicalIndicators.calculateRSI(engulfingState.priceHistory, rsiLength);
            if (rsi != null) {
                engulfingState.rsiHistory.add(rsi);
                // Keep only last 3 RSI values
                if (engulfingState.rsiHistory.size() > 3) {
                    engulfingState.rsiHistory.remove(0);
                }
            }
        }
        
        // Initialize signal flags
        boolean bullishEngulfing = false;
        boolean bearishEngulfing = false;
        boolean buySignal = false;
        boolean sellSignal = false;
        
        List<Map<String, Object>> markers = new ArrayList<>();
        
        // Check for engulfing patterns (need previous candle)
        if (engulfingState.previousCandle != null && rsi != null) {
            BigDecimal currentClose = candle.getClose();
            BigDecimal currentOpen = candle.getOpen();
            BigDecimal prevClose = engulfingState.previousCandle.getClose();
            BigDecimal prevOpen = engulfingState.previousCandle.getOpen();
            
            // Bullish Engulfing: close >= open[1] and close[1] < open[1]
            if (currentClose.compareTo(prevOpen) >= 0 && prevClose.compareTo(prevOpen) < 0) {
                bullishEngulfing = true;
            }
            
            // Bearish Engulfing: close <= open[1] and close[1] > open[1]
            if (currentClose.compareTo(prevOpen) <= 0 && prevClose.compareTo(prevOpen) > 0) {
                bearishEngulfing = true;
            }
            
            // Check RSI conditions in last 3 candles
            boolean rsiWasOversold = false;
            boolean rsiWasOverbought = false;
            
            for (BigDecimal historicalRsi : engulfingState.rsiHistory) {
                if (historicalRsi.compareTo(new BigDecimal(rsiOversold)) <= 0) {
                    rsiWasOversold = true;
                }
                if (historicalRsi.compareTo(new BigDecimal(rsiOverbought)) >= 0) {
                    rsiWasOverbought = true;
                }
            }
            
            // Generate signals
            if (bullishEngulfing && rsiWasOversold) {
                buySignal = true;
                if (showLabels) {
                    markers.add(createSignalMarker(candle, "BUY MIT", buyColor, "below", "triangle_up"));
                }
            }
            
            if (bearishEngulfing && rsiWasOverbought) {
                sellSignal = true;
                if (showLabels) {
                    markers.add(createSignalMarker(candle, "SELL MIT", sellColor, "above", "triangle_down"));
                }
            }
        }
        
        // Update previous candle for next iteration
        engulfingState.previousCandle = candle;
        
        // Build values
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("rsi", rsi != null ? rsi : BigDecimal.ZERO);
        values.put("bullishEngulfing", bullishEngulfing ? BigDecimal.ONE : BigDecimal.ZERO);
        values.put("bearishEngulfing", bearishEngulfing ? BigDecimal.ONE : BigDecimal.ZERO);
        values.put("buySignal", buySignal ? BigDecimal.ONE : BigDecimal.ZERO);
        values.put("sellSignal", sellSignal ? BigDecimal.ONE : BigDecimal.ZERO);
        
        Map<String, Object> result = new HashMap<>();
//        result.put("values", values);
        result.put("state", engulfingState);
        result.put("markers", markers);
        
        return result;
    }
    
    /**
     * Process a single tick for real-time updates (optional)
     * Engulfing patterns only form on candle close, so we use the default implementation
     */
    @Override
    public Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        // Engulfing patterns only update on new candles, not on ticks
        return Map.of(
            "values", Map.of(),
            "state", state != null ? state : new EngulfingState()
        );
    }
    
    /**
     * Create signal marker
     */
    private Map<String, Object> createSignalMarker(CandlestickData candle, String text,
                                                   String color, String position, String shape) {
        MarkerShape marker = MarkerShape.builder()
            .time(candle.getCloseTime().getEpochSecond())
            .price(position.equals("above") ? candle.getHigh() : candle.getLow())
            .shape(shape)
            .color(color)
            .position(position)
            .text(text)
            .size(10)
            .build();
        
        return marker.toMap();
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // RSI in separate pane
        int rsiOverbought = getIntParameter(params, "rsiOverbought", 70);
        int rsiOversold = getIntParameter(params, "rsiOversold", 30);
        
        metadata.put("rsi", IndicatorMetadata.builder("rsi")
            .displayName("RSI")
            .asLine("#7E57C2", 2)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        // Overbought and oversold levels
        metadata.put("rsiOverbought", IndicatorMetadata.builder("rsiOverbought")
            .displayName("Overbought")
            .asLine("#ef5350", 1)
            .addConfig("lineStyle", "dashed")
            .addConfig("constant", rsiOverbought)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        metadata.put("rsiOversold", IndicatorMetadata.builder("rsiOversold")
            .displayName("Oversold")
            .asLine("#26a69a", 1)
            .addConfig("lineStyle", "dashed")
            .addConfig("constant", rsiOversold)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int rsiLength = getIntParameter(params, "rsiLength", 14);
        // Need RSI period + 1 for price changes + 3 for RSI history lookback + 1 for previous candle
        return rsiLength + 5;
    }
}

