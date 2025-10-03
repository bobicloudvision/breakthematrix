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
     * State for progressive calculation
     */
    public static class SRBreaksState {
        public BigDecimal currentResistance = BigDecimal.ZERO;
        public BigDecimal currentSupport = BigDecimal.ZERO;
        public Instant resistanceTime = null;
        public Instant supportTime = null;
        public List<BigDecimal> volumeHistory = new ArrayList<>();
        public int barIndex = 0;
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
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        Map<String, Object> progressive = calculateProgressive(candles, params, null);
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) progressive.get("values");
        return values;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                    Map<String, Object> params,
                                                    Object previousState) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int leftBars = getIntParameter(params, "leftBars", 15);
        int rightBars = getIntParameter(params, "rightBars", 15);
        double volumeThreshold = getDoubleParameter(params, "volumeThreshold", 20.0);
        boolean showBreaks = getBooleanParameter(params, "showBreaks", true);
        String resistanceColor = getStringParameter(params, "resistanceColor", "#FF0000");
        String supportColor = getStringParameter(params, "supportColor", "#233DEE");
        
        SRBreaksState state = (previousState instanceof SRBreaksState) 
            ? (SRBreaksState) previousState 
            : new SRBreaksState();
        
        int minRequired = leftBars + rightBars + 1;
        if (candles == null || candles.size() < minRequired) {
            return Map.of(
                "values", createEmptyResult(),
                "state", state,
                "lines", new ArrayList<>(),
                "markers", new ArrayList<>()
            );
        }
        
        int n = candles.size();
        CandlestickData currentCandle = candles.get(n - 1);
        state.barIndex++;
        
        // Update volume history (keep last 10 for EMA calculation)
        state.volumeHistory.add(currentCandle.getVolume());
        if (state.volumeHistory.size() > 10) {
            state.volumeHistory.remove(0);
        }
        
        // Calculate volume oscillator
        BigDecimal volumeOsc = calculateVolumeOscillator(state.volumeHistory);
        
        // Check for new pivot high (resistance) at position n - rightBars - 1
        if (n >= leftBars + rightBars + 1) {
            int pivotIdx = n - rightBars - 1;
            
            if (isPivotHigh(candles, pivotIdx, leftBars, rightBars)) {
                state.currentResistance = candles.get(pivotIdx).getHigh();
                state.resistanceTime = candles.get(pivotIdx).getCloseTime();
            }
            
            if (isPivotLow(candles, pivotIdx, leftBars, rightBars)) {
                state.currentSupport = candles.get(pivotIdx).getLow();
                state.supportTime = candles.get(pivotIdx).getCloseTime();
            }
        }
        
        // Detect breaks and create markers
        List<Map<String, Object>> markers = new ArrayList<>();
        BigDecimal close = currentCandle.getClose();
        BigDecimal open = currentCandle.getOpen();
        BigDecimal high = currentCandle.getHigh();
        BigDecimal low = currentCandle.getLow();
        
        String breakType = null;
        
        if (showBreaks && state.currentSupport.compareTo(BigDecimal.ZERO) > 0) {
            // Get previous close for crossunder detection
            BigDecimal prevClose = n > 1 ? candles.get(n - 2).getClose() : close;
            
            // Check for support break (crossunder)
            if (prevClose.compareTo(state.currentSupport) >= 0 && 
                close.compareTo(state.currentSupport) < 0) {
                
                // Check for bear wick
                BigDecimal bodySize = open.subtract(close).abs();
                BigDecimal upperWick = high.subtract(open.max(close));
                
                if (upperWick.compareTo(bodySize) > 0) {
                    // Bear wick
                    breakType = "bearWick";
                    markers.add(createBreakMarker(currentCandle, "Bear Wick", 
                                                  "#FF0000", "above", "triangle_down"));
                } else if (volumeOsc.doubleValue() > volumeThreshold) {
                    // Regular break with volume
                    breakType = "supportBreak";
                    markers.add(createBreakMarker(currentCandle, "B", 
                                                  "#FF0000", "above", "circle"));
                }
            }
        }
        
        if (showBreaks && state.currentResistance.compareTo(BigDecimal.ZERO) > 0) {
            // Get previous close for crossover detection
            BigDecimal prevClose = n > 1 ? candles.get(n - 2).getClose() : close;
            
            // Check for resistance break (crossover)
            if (prevClose.compareTo(state.currentResistance) <= 0 && 
                close.compareTo(state.currentResistance) > 0) {
                
                // Check for bull wick
                BigDecimal bodySize = close.subtract(open).abs();
                BigDecimal lowerWick = open.min(close).subtract(low);
                
                if (lowerWick.compareTo(bodySize) > 0) {
                    // Bull wick
                    breakType = "bullWick";
                    markers.add(createBreakMarker(currentCandle, "Bull Wick", 
                                                  "#00FF00", "below", "triangle_up"));
                } else if (volumeOsc.doubleValue() > volumeThreshold) {
                    // Regular break with volume
                    breakType = "resistanceBreak";
                    markers.add(createBreakMarker(currentCandle, "B", 
                                                  "#00FF00", "below", "circle"));
                }
            }
        }
        
        // Create S/R lines
        List<Map<String, Object>> lines = new ArrayList<>();
        
        if (state.currentResistance.compareTo(BigDecimal.ZERO) > 0 && state.resistanceTime != null) {
            lines.add(createSRLine(state.resistanceTime, currentCandle.getCloseTime(),
                                  state.currentResistance, resistanceColor, "Resistance"));
        }
        
        if (state.currentSupport.compareTo(BigDecimal.ZERO) > 0 && state.supportTime != null) {
            lines.add(createSRLine(state.supportTime, currentCandle.getCloseTime(),
                                  state.currentSupport, supportColor, "Support"));
        }
        
        // Build values
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("resistance", state.currentResistance);
        values.put("support", state.currentSupport);
        values.put("volumeOsc", volumeOsc);
        values.put("breakType", breakType != null ? new BigDecimal(1) : BigDecimal.ZERO);
        
        Map<String, Object> output = new HashMap<>();
        output.put("values", values);
        output.put("state", state);
        output.put("lines", lines);
        output.put("markers", markers);
        
        return output;
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
    
    private Map<String, BigDecimal> createEmptyResult() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("resistance", BigDecimal.ZERO);
        result.put("support", BigDecimal.ZERO);
        result.put("volumeOsc", BigDecimal.ZERO);
        result.put("breakType", BigDecimal.ZERO);
        return result;
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
    
    protected double getDoubleParameter(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}

