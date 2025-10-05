package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.LineShape;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Support and Resistance Levels with Breaks Indicator
 * Based on LuxAlgo's indicator
 * 
 * Detects support and resistance levels using pivot points and identifies
 * breaks with volume confirmation. Shows different signals for:
 * - Regular breaks with volume
 * - Bull wicks (strong bullish rejection at resistance)
 * - Bear wicks (strong bearish rejection at support)
 * 
 * ALGORITHM:
 * 1. Detect pivot highs (resistance) and pivot lows (support)
 * 2. Draw horizontal lines for current S/R levels
 * 3. Calculate volume oscillator (EMA5 - EMA10) / EMA10
 * 4. Detect breaks when price crosses S/R with volume confirmation
 * 5. Identify wick-based rejections vs regular breaks
 */
@Component
public class SRLevelsBreaksIndicator extends AbstractIndicator {
    
    /**
     * State for event-driven calculation
     */
    public static class SRBreaksState {
        public BigDecimal currentResistance = BigDecimal.ZERO;
        public BigDecimal currentSupport = BigDecimal.ZERO;
        public Instant resistanceTime = null;
        public Instant supportTime = null;
        public List<BigDecimal> volumeHistory = new ArrayList<>();
        public int barIndex = 0;
        public BigDecimal prevClose = null; // Track previous candle close for crossover/crossunder detection
        public List<CandlestickData> recentCandles = new ArrayList<>(); // Buffer for pivot detection
    }
    
    public SRLevelsBreaksIndicator() {
        super("srbreaks", "S/R Levels with Breaks", 
              "Support and resistance levels with volume-confirmed break detection",
              Indicator.IndicatorCategory.CUSTOM);
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("leftBars", IndicatorParameter.builder("leftBars")
            .displayName("Left Bars")
            .description("Number of bars to the left for pivot detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(15)
            .minValue(1)
            .maxValue(50)
            .required(true)
            .build());
        
        params.put("rightBars", IndicatorParameter.builder("rightBars")
            .displayName("Right Bars")
            .description("Number of bars to the right for pivot detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(15)
            .minValue(1)
            .maxValue(50)
            .required(true)
            .build());
        
        params.put("volumeThreshold", IndicatorParameter.builder("volumeThreshold")
            .displayName("Volume Threshold %")
            .description("Minimum volume oscillator percentage to confirm break")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(20.0)
            .minValue(0.0)
            .maxValue(100.0)
            .required(true)
            .build());
        
        params.put("showBreaks", IndicatorParameter.builder("showBreaks")
            .displayName("Show Breaks")
            .description("Display break markers on chart")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("resistanceColor", IndicatorParameter.builder("resistanceColor")
            .displayName("Resistance Color")
            .description("Color for resistance line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#FF0000")
            .required(false)
            .build());
        
        params.put("supportColor", IndicatorParameter.builder("supportColor")
            .displayName("Support Color")
            .description("Color for support line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#233DEE")
            .required(false)
            .build());
        
        return params;
    }
    
    /**
     * Initialize the indicator with historical data and parameters
     * 
     * Processes historical candles to build initial state including:
     * - Volume history for oscillator calculation
     * - Recent candles buffer for pivot detection
     * - Initial S/R levels from pivots
     * 
     * @param historicalCandles Historical candlestick data for initialization
     * @param params Configuration parameters
     * @return Initial state object (SRBreaksState)
     */
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        // Validate and merge with defaults
        validateParameters(params);
        params = mergeWithDefaults(params);
        
        int leftBars = getIntParameter(params, "leftBars", 15);
        int rightBars = getIntParameter(params, "rightBars", 15);
        
        // Create initial state
        SRBreaksState state = new SRBreaksState();
        
        // Process historical candles to build initial state
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            for (CandlestickData candle : historicalCandles) {
                // Add to recent candles buffer (keep enough for pivot detection)
                state.recentCandles.add(candle);
                int maxBufferSize = leftBars + rightBars + 1;
                if (state.recentCandles.size() > maxBufferSize) {
                    state.recentCandles.remove(0);
                }
                
                // Build volume history (keep last 10 for EMA calculation)
                state.volumeHistory.add(candle.getVolume());
                if (state.volumeHistory.size() > 10) {
                    state.volumeHistory.remove(0);
                }
                
                // Update bar index
                state.barIndex++;
                
                // Check for pivots if we have enough data
                if (state.recentCandles.size() >= leftBars + rightBars + 1) {
                    int pivotIdx = state.recentCandles.size() - rightBars - 1;
                    
                    if (isPivotHigh(state.recentCandles, pivotIdx, leftBars, rightBars)) {
                        state.currentResistance = state.recentCandles.get(pivotIdx).getHigh();
                        state.resistanceTime = state.recentCandles.get(pivotIdx).getCloseTime();
                    }
                    
                    if (isPivotLow(state.recentCandles, pivotIdx, leftBars, rightBars)) {
                        state.currentSupport = state.recentCandles.get(pivotIdx).getLow();
                        state.supportTime = state.recentCandles.get(pivotIdx).getCloseTime();
                    }
                }
                
                // Track previous close
                state.prevClose = candle.getClose();
            }
        }
        
        return state;
    }
    
    /**
     * Process a single historical or live candle
     * 
     * Updates state with new candle data and detects:
     * - New pivot points (S/R levels)
     * - Break signals with volume confirmation
     * - Wick-based rejections
     * 
     * @param candle The candle to process
     * @param params Configuration parameters
     * @param state Current state from previous call (or from onInit)
     * @return Map containing values, state, lines, and markers
     */
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        int leftBars = getIntParameter(params, "leftBars", 15);
        int rightBars = getIntParameter(params, "rightBars", 15);
        double volumeThreshold = getDoubleParameter(params, "volumeThreshold", 20.0);
        boolean showBreaks = getBooleanParameter(params, "showBreaks", true);
        String resistanceColor = getStringParameter(params, "resistanceColor", "#FF0000");
        String supportColor = getStringParameter(params, "supportColor", "#233DEE");
        
        // Cast or create state
        SRBreaksState srState = (state instanceof SRBreaksState) ? (SRBreaksState) state : new SRBreaksState();
        
        // Add candle to recent buffer
        srState.recentCandles.add(candle);
        int maxBufferSize = leftBars + rightBars + 1;
        if (srState.recentCandles.size() > maxBufferSize) {
            srState.recentCandles.remove(0);
        }
        
        // Update volume history
        srState.volumeHistory.add(candle.getVolume());
        if (srState.volumeHistory.size() > 10) {
            srState.volumeHistory.remove(0);
        }
        
        srState.barIndex++;
        
        // Calculate volume oscillator
        BigDecimal volumeOsc = calculateVolumeOscillator(srState.volumeHistory);
        
        // Check for new pivots if we have enough data
        if (srState.recentCandles.size() >= leftBars + rightBars + 1) {
            int pivotIdx = srState.recentCandles.size() - rightBars - 1;
            
            if (isPivotHigh(srState.recentCandles, pivotIdx, leftBars, rightBars)) {
                srState.currentResistance = srState.recentCandles.get(pivotIdx).getHigh();
                srState.resistanceTime = srState.recentCandles.get(pivotIdx).getCloseTime();
            }
            
            if (isPivotLow(srState.recentCandles, pivotIdx, leftBars, rightBars)) {
                srState.currentSupport = srState.recentCandles.get(pivotIdx).getLow();
                srState.supportTime = srState.recentCandles.get(pivotIdx).getCloseTime();
            }
        }
        
        // Detect breaks and create markers
        List<Map<String, Object>> markers = new ArrayList<>();
        BigDecimal close = candle.getClose();
        BigDecimal open = candle.getOpen();
        BigDecimal high = candle.getHigh();
        BigDecimal low = candle.getLow();
        
        String breakType = null;
        
        // Check for support break
        if (showBreaks && srState.currentSupport.compareTo(BigDecimal.ZERO) > 0 && srState.prevClose != null) {
            // Check for crossunder (previous close >= support, current close < support)
            if (srState.prevClose.compareTo(srState.currentSupport) >= 0 && 
                close.compareTo(srState.currentSupport) < 0) {
                
                // Check for bear wick
                BigDecimal bodySize = open.subtract(close).abs();
                BigDecimal upperWick = high.subtract(open.max(close));
                
                if (upperWick.compareTo(bodySize) > 0) {
                    // Bear wick (strong rejection)
                    breakType = "bearWick";
                    markers.add(createBreakMarker(candle, "Bear Wick", 
                                                  "#FF0000", "above", "triangle_down"));
                } else if (volumeOsc.doubleValue() > volumeThreshold) {
                    // Regular break with volume confirmation
                    breakType = "supportBreak";
                    markers.add(createBreakMarker(candle, "B", 
                                                  "#FF0000", "above", "circle"));
                }
            }
        }
        
        // Check for resistance break
        if (showBreaks && srState.currentResistance.compareTo(BigDecimal.ZERO) > 0 && srState.prevClose != null) {
            // Check for crossover (previous close <= resistance, current close > resistance)
            if (srState.prevClose.compareTo(srState.currentResistance) <= 0 && 
                close.compareTo(srState.currentResistance) > 0) {
                
                // Check for bull wick
                BigDecimal bodySize = close.subtract(open).abs();
                BigDecimal lowerWick = open.min(close).subtract(low);
                
                if (lowerWick.compareTo(bodySize) > 0) {
                    // Bull wick (strong rejection)
                    breakType = "bullWick";
                    markers.add(createBreakMarker(candle, "Bull Wick", 
                                                  "#00FF00", "below", "triangle_up"));
                } else if (volumeOsc.doubleValue() > volumeThreshold) {
                    // Regular break with volume confirmation
                    breakType = "resistanceBreak";
                    markers.add(createBreakMarker(candle, "B", 
                                                  "#00FF00", "below", "circle"));
                }
            }
        }
        
        // Create S/R lines
        List<Map<String, Object>> lines = new ArrayList<>();
        
        if (srState.currentResistance.compareTo(BigDecimal.ZERO) > 0 && srState.resistanceTime != null) {
            // Use fixed end time for stable deduplication
            long fixedEndTime = srState.resistanceTime.getEpochSecond() + (20 * 60);
            lines.add(createSRLine(srState.resistanceTime, 
                                  Instant.ofEpochSecond(fixedEndTime),
                                  srState.currentResistance, resistanceColor, "Resistance"));
        }
        
        if (srState.currentSupport.compareTo(BigDecimal.ZERO) > 0 && srState.supportTime != null) {
            // Use fixed end time for stable deduplication
            long fixedEndTime = srState.supportTime.getEpochSecond() + (20 * 60);
            lines.add(createSRLine(srState.supportTime, 
                                  Instant.ofEpochSecond(fixedEndTime),
                                  srState.currentSupport, supportColor, "Support"));
        }
        
        // Update previous close for next iteration
        srState.prevClose = close;
        
        // Build values
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("resistance", srState.currentResistance);
        values.put("support", srState.currentSupport);
        values.put("volumeOsc", volumeOsc);
        values.put("breakType", breakType != null ? new BigDecimal(1) : BigDecimal.ZERO);
        
        Map<String, Object> result = new HashMap<>();
//        result.put("values", values);
        result.put("state", srState);
        result.put("lines", lines);
        result.put("markers", markers);
        
        return result;
    }
    
    /**
     * Process a single tick for real-time updates (optional)
     * S/R levels don't update on individual ticks, so we use the default implementation
     */
    @Override
    public Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        // S/R levels only update on new candles, not on ticks
        return Map.of(
            "values", Map.of(),
            "state", state != null ? state : new SRBreaksState()
        );
    }
    
    /**
     * Check if index is a pivot high
     */
    private boolean isPivotHigh(List<CandlestickData> candles, int index, int leftBars, int rightBars) {
        if (index < leftBars || index >= candles.size() - rightBars) {
            return false;
        }
        
        BigDecimal pivotHigh = candles.get(index).getHigh();
        
        for (int i = index - leftBars; i < index; i++) {
            if (candles.get(i).getHigh().compareTo(pivotHigh) > 0) {
                return false;
            }
        }
        
        for (int i = index + 1; i <= index + rightBars; i++) {
            if (candles.get(i).getHigh().compareTo(pivotHigh) > 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if index is a pivot low
     */
    private boolean isPivotLow(List<CandlestickData> candles, int index, int leftBars, int rightBars) {
        if (index < leftBars || index >= candles.size() - rightBars) {
            return false;
        }
        
        BigDecimal pivotLow = candles.get(index).getLow();
        
        for (int i = index - leftBars; i < index; i++) {
            if (candles.get(i).getLow().compareTo(pivotLow) < 0) {
                return false;
            }
        }
        
        for (int i = index + 1; i <= index + rightBars; i++) {
            if (candles.get(i).getLow().compareTo(pivotLow) < 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Calculate volume oscillator: 100 * (EMA5 - EMA10) / EMA10
     */
    private BigDecimal calculateVolumeOscillator(List<BigDecimal> volumes) {
        if (volumes.size() < 10) {
            return BigDecimal.ZERO;
        }
        
        // Calculate EMA5 (short)
        BigDecimal ema5 = calculateEMA(volumes, 5);
        
        // Calculate EMA10 (long)
        BigDecimal ema10 = calculateEMA(volumes, 10);
        
        if (ema10.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // osc = 100 * (short - long) / long
        return ema5.subtract(ema10)
                   .divide(ema10, 8, RoundingMode.HALF_UP)
                   .multiply(new BigDecimal("100"));
    }
    
    /**
     * Calculate EMA for given period
     */
    private BigDecimal calculateEMA(List<BigDecimal> values, int period) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal multiplier = new BigDecimal("2").divide(
            new BigDecimal(period + 1), 8, RoundingMode.HALF_UP
        );
        
        BigDecimal ema = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ema = values.get(i).multiply(multiplier)
                       .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }
        
        return ema;
    }
    
    /**
     * Create S/R line shape
     */
    private Map<String, Object> createSRLine(Instant startTime, Instant endTime,
                                            BigDecimal price, String color, String label) {
        LineShape line = LineShape.builder()
            .time1(startTime.getEpochSecond())
            .time2(endTime.getEpochSecond())
            .price1(price)
            .price2(price)
            .color(color)
            .lineWidth(3)
            .lineStyle("solid")
            .label(label)
            .build();
        
        return line.toMap();
    }
    
    /**
     * Create break marker
     */
    private Map<String, Object> createBreakMarker(CandlestickData candle, String text,
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
        
        // Volume oscillator in separate pane
        metadata.put("volumeOsc", IndicatorMetadata.builder("volumeOsc")
            .displayName("Volume Oscillator")
            .asHistogram("#2962FF")
            .addConfig("baseValue", 0)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int leftBars = getIntParameter(params, "leftBars", 15);
        int rightBars = getIntParameter(params, "rightBars", 15);
        return leftBars + rightBars + 10; // +10 for volume EMA
    }
}

