package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.BoxShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Support and Resistance Indicator
 * 
 * Identifies key support and resistance levels based on pivot highs and lows.
 * These levels represent areas where price has historically reversed or consolidated.
 * 
 * ALGORITHM:
 * 1. Detect pivot highs (resistance) and pivot lows (support) using lookback periods
 * 2. Create zones around these pivots with a configurable width (percentage or fixed)
 * 3. Track strength based on number of touches (more touches = stronger level)
 * 4. Mark levels as "broken" when price decisively moves through them
 * 5. Merge nearby levels to avoid clutter
 * 
 * OUTPUT VALUES:
 * - nearestSupport: Price of nearest support level below current price
 * - nearestResistance: Price of nearest resistance level above current price
 * - supportStrength: Strength of nearest support (number of touches)
 * - resistanceStrength: Strength of nearest resistance (number of touches)
 * - activeSupportLevels: Count of active support levels
 * - activeResistanceLevels: Count of active resistance levels
 * 
 * OUTPUT (calculateProgressive method):
 * Returns formatted BOXES for visualization with time/price coordinates, colors, and labels.
 */
@Component
public class SupportResistanceIndicator extends AbstractIndicator {
    
    /**
     * Support/Resistance Level data class
     */
    public static class SRLevel {
        public BigDecimal price;           // Center price of the level
        public BigDecimal top;             // Top of the zone
        public BigDecimal bottom;          // Bottom of the zone
        public Instant timestamp;          // When the level was created
        public int touches;                // Number of times price touched this level
        public boolean isSupport;          // true = support, false = resistance
        public boolean broken;             // true if price broke through decisively
        public Instant brokenTime;         // When the level was broken
        public Instant lastTouchTime;      // Last time price touched this level
        
        public SRLevel(BigDecimal price, BigDecimal top, BigDecimal bottom, 
                       Instant timestamp, boolean isSupport) {
            this.price = price;
            this.top = top;
            this.bottom = bottom;
            this.timestamp = timestamp;
            this.isSupport = isSupport;
            this.touches = 1;
            this.broken = false;
            this.brokenTime = null;
            this.lastTouchTime = timestamp;
        }
    }
    
    /**
     * State for progressive calculation
     */
    public static class SRState {
        public List<SRLevel> supportLevels = new ArrayList<>();
        public List<SRLevel> resistanceLevels = new ArrayList<>();
        public int barIndex = 0;
    }
    
    public SupportResistanceIndicator() {
        super("sr", "Support & Resistance", 
              "Identifies key support and resistance levels based on pivot points",
              Indicator.IndicatorCategory.CUSTOM);
        
        // Pivot detection parameters
        addParameter("pivotLookback", "Pivot Lookback", 
                    "Number of candles to look left and right for pivot detection", 
                    IndicatorParameter.ParameterType.INTEGER, 5, 1, 50);
        
        // Zone width
        addParameter("zoneWidthPercent", "Zone Width %", 
                    "Width of support/resistance zone as percentage of price", 
                    IndicatorParameter.ParameterType.DECIMAL, 0.3, 0.1, 5.0);
        
        // Level management
        addParameter("maxLevels", "Max Levels", 
                    "Maximum number of support/resistance levels to track", 
                    IndicatorParameter.ParameterType.INTEGER, 5, 1, 20);
        
        addParameter("mergeThresholdPercent", "Merge Threshold %", 
                    "Merge levels within this percentage distance", 
                    IndicatorParameter.ParameterType.DECIMAL, 0.5, 0.1, 3.0);
        
        addParameter("minTouchesForStrong", "Min Touches (Strong)", 
                    "Minimum touches to consider a level as strong", 
                    IndicatorParameter.ParameterType.INTEGER, 3, 2, 10);
        
        addParameter("breakConfirmationPercent", "Break Confirmation %", 
                    "Percentage price must move beyond level to confirm break", 
                    IndicatorParameter.ParameterType.DECIMAL, 0.2, 0.1, 2.0);
        
        // Display options
        addParameter("showBrokenLevels", "Show Broken Levels", 
                    "Display levels that have been broken", 
                    IndicatorParameter.ParameterType.BOOLEAN, false, null, null);
        
        addParameter("supportColor", "Support Color", 
                    "Color for support zones", 
                    IndicatorParameter.ParameterType.STRING, "rgba(34, 139, 34, 0.2)", null, null);
        
        addParameter("resistanceColor", "Resistance Color", 
                    "Color for resistance zones", 
                    IndicatorParameter.ParameterType.STRING, "rgba(220, 20, 60, 0.2)", null, null);
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        // Use progressive calculation and return only the values
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
        
        int pivotLookback = getIntParameter(params, "pivotLookback", 5);
        double zoneWidthPct = getDoubleParameter(params, "zoneWidthPercent", 0.3);
        int maxLevels = getIntParameter(params, "maxLevels", 5);
        double mergeThresholdPct = getDoubleParameter(params, "mergeThresholdPercent", 0.5);
        int minTouchesForStrong = getIntParameter(params, "minTouchesForStrong", 3);
        double breakConfirmationPct = getDoubleParameter(params, "breakConfirmationPercent", 0.2);
        
        // Cast or create state
        SRState state = (previousState instanceof SRState) 
            ? (SRState) previousState 
            : new SRState();
        
        if (candles == null || candles.size() < pivotLookback * 2 + 1) {
            return Map.of(
                "values", createEmptyResult(),
                "state", state,
                "boxes", new ArrayList<>()
            );
        }
        
        int n = candles.size();
        CandlestickData currentCandle = candles.get(n - 1);
        state.barIndex++;
        
        // Check for pivot high (resistance) at lookback position
        if (n >= pivotLookback * 2 + 1) {
            int pivotIdx = n - pivotLookback - 1;
            if (isPivotHigh(candles, pivotIdx, pivotLookback)) {
                CandlestickData pivotCandle = candles.get(pivotIdx);
                addResistanceLevel(state, pivotCandle.getHigh(), pivotCandle.getCloseTime(), 
                                  zoneWidthPct, mergeThresholdPct, maxLevels);
            }
            
            if (isPivotLow(candles, pivotIdx, pivotLookback)) {
                CandlestickData pivotCandle = candles.get(pivotIdx);
                addSupportLevel(state, pivotCandle.getLow(), pivotCandle.getCloseTime(), 
                               zoneWidthPct, mergeThresholdPct, maxLevels);
            }
        }
        
        // Update level status (touches and breaks)
        updateLevelStatus(state, currentCandle, breakConfirmationPct);
        
        // Clean up old/weak levels
        cleanupLevels(state, maxLevels);
        
        // Build result values
        Map<String, BigDecimal> values = calculateOutputValues(state, currentCandle.getClose());
        
        // Convert levels to boxes for visualization
        boolean showBroken = getBooleanParameter(params, "showBrokenLevels", false);
        String supportColor = getStringParameter(params, "supportColor", "rgba(34, 139, 34, 0.2)");
        String resistanceColor = getStringParameter(params, "resistanceColor", "rgba(220, 20, 60, 0.2)");
        
        List<BoxShape> boxShapes = new ArrayList<>();
        
        // Convert support levels to boxes
        for (SRLevel level : state.supportLevels) {
            if (level.broken && !showBroken) continue;
            boxShapes.add(convertLevelToBox(level, currentCandle.getCloseTime(), 
                                           supportColor, minTouchesForStrong));
        }
        
        // Convert resistance levels to boxes
        for (SRLevel level : state.resistanceLevels) {
            if (level.broken && !showBroken) continue;
            boxShapes.add(convertLevelToBox(level, currentCandle.getCloseTime(), 
                                           resistanceColor, minTouchesForStrong));
        }
        
        // Convert BoxShape objects to Map for API serialization
        List<Map<String, Object>> boxes = boxShapes.stream()
            .map(BoxShape::toMap)
            .collect(Collectors.toList());
        
        Map<String, Object> output = new HashMap<>();
        output.put("values", values);
        output.put("state", state);
        output.put("boxes", boxes);
        
        return output;
    }
    
    /**
     * Check if index is a pivot high
     */
    private boolean isPivotHigh(List<CandlestickData> candles, int index, int lookback) {
        if (index < lookback || index >= candles.size() - lookback) {
            return false;
        }
        
        BigDecimal pivotHigh = candles.get(index).getHigh();
        
        // Check left side
        for (int i = index - lookback; i < index; i++) {
            if (candles.get(i).getHigh().compareTo(pivotHigh) > 0) {
                return false;
            }
        }
        
        // Check right side
        for (int i = index + 1; i <= index + lookback; i++) {
            if (candles.get(i).getHigh().compareTo(pivotHigh) > 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if index is a pivot low
     */
    private boolean isPivotLow(List<CandlestickData> candles, int index, int lookback) {
        if (index < lookback || index >= candles.size() - lookback) {
            return false;
        }
        
        BigDecimal pivotLow = candles.get(index).getLow();
        
        // Check left side
        for (int i = index - lookback; i < index; i++) {
            if (candles.get(i).getLow().compareTo(pivotLow) < 0) {
                return false;
            }
        }
        
        // Check right side
        for (int i = index + 1; i <= index + lookback; i++) {
            if (candles.get(i).getLow().compareTo(pivotLow) < 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Add or merge a support level
     */
    private void addSupportLevel(SRState state, BigDecimal price, Instant timestamp,
                                 double zoneWidthPct, double mergeThresholdPct, int maxLevels) {
        BigDecimal zoneWidth = price.multiply(BigDecimal.valueOf(zoneWidthPct / 100.0));
        BigDecimal top = price.add(zoneWidth.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        BigDecimal bottom = price.subtract(zoneWidth.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        
        // Check if we should merge with existing level
        for (SRLevel existing : state.supportLevels) {
            if (existing.broken) continue;
            
            BigDecimal distance = existing.price.subtract(price).abs();
            BigDecimal threshold = price.multiply(BigDecimal.valueOf(mergeThresholdPct / 100.0));
            
            if (distance.compareTo(threshold) <= 0) {
                // Merge: increase touch count and update zone
                existing.touches++;
                existing.lastTouchTime = timestamp;
                // Expand zone if needed
                if (top.compareTo(existing.top) > 0) existing.top = top;
                if (bottom.compareTo(existing.bottom) < 0) existing.bottom = bottom;
                return;
            }
        }
        
        // Add new level
        SRLevel newLevel = new SRLevel(price, top, bottom, timestamp, true);
        state.supportLevels.add(newLevel);
    }
    
    /**
     * Add or merge a resistance level
     */
    private void addResistanceLevel(SRState state, BigDecimal price, Instant timestamp,
                                   double zoneWidthPct, double mergeThresholdPct, int maxLevels) {
        BigDecimal zoneWidth = price.multiply(BigDecimal.valueOf(zoneWidthPct / 100.0));
        BigDecimal top = price.add(zoneWidth.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        BigDecimal bottom = price.subtract(zoneWidth.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        
        // Check if we should merge with existing level
        for (SRLevel existing : state.resistanceLevels) {
            if (existing.broken) continue;
            
            BigDecimal distance = existing.price.subtract(price).abs();
            BigDecimal threshold = price.multiply(BigDecimal.valueOf(mergeThresholdPct / 100.0));
            
            if (distance.compareTo(threshold) <= 0) {
                // Merge: increase touch count and update zone
                existing.touches++;
                existing.lastTouchTime = timestamp;
                // Expand zone if needed
                if (top.compareTo(existing.top) > 0) existing.top = top;
                if (bottom.compareTo(existing.bottom) < 0) existing.bottom = bottom;
                return;
            }
        }
        
        // Add new level
        SRLevel newLevel = new SRLevel(price, top, bottom, timestamp, false);
        state.resistanceLevels.add(newLevel);
    }
    
    /**
     * Update level status based on current candle
     */
    private void updateLevelStatus(SRState state, CandlestickData candle, double breakConfirmationPct) {
        BigDecimal high = candle.getHigh();
        BigDecimal low = candle.getLow();
        BigDecimal close = candle.getClose();
        
        // Check support levels
        for (SRLevel level : state.supportLevels) {
            if (level.broken) continue;
            
            // Check if price touched the zone
            if (low.compareTo(level.bottom) <= 0 && high.compareTo(level.bottom) >= 0) {
                level.touches++;
                level.lastTouchTime = candle.getCloseTime();
            }
            
            // Check if price broke through (close below zone with confirmation)
            BigDecimal breakThreshold = level.bottom.multiply(
                BigDecimal.ONE.subtract(BigDecimal.valueOf(breakConfirmationPct / 100.0))
            );
            if (close.compareTo(breakThreshold) < 0) {
                level.broken = true;
                level.brokenTime = candle.getCloseTime();
            }
        }
        
        // Check resistance levels
        for (SRLevel level : state.resistanceLevels) {
            if (level.broken) continue;
            
            // Check if price touched the zone
            if (high.compareTo(level.top) >= 0 && low.compareTo(level.top) <= 0) {
                level.touches++;
                level.lastTouchTime = candle.getCloseTime();
            }
            
            // Check if price broke through (close above zone with confirmation)
            BigDecimal breakThreshold = level.top.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(breakConfirmationPct / 100.0))
            );
            if (close.compareTo(breakThreshold) > 0) {
                level.broken = true;
                level.brokenTime = candle.getCloseTime();
            }
        }
    }
    
    /**
     * Clean up old and weak levels
     */
    private void cleanupLevels(SRState state, int maxLevels) {
        // Remove broken levels
        state.supportLevels.removeIf(level -> level.broken);
        state.resistanceLevels.removeIf(level -> level.broken);
        
        // Keep only the strongest levels if we have too many
        if (state.supportLevels.size() > maxLevels) {
            state.supportLevels.sort((a, b) -> Integer.compare(b.touches, a.touches));
            state.supportLevels = new ArrayList<>(state.supportLevels.subList(0, maxLevels));
        }
        
        if (state.resistanceLevels.size() > maxLevels) {
            state.resistanceLevels.sort((a, b) -> Integer.compare(b.touches, a.touches));
            state.resistanceLevels = new ArrayList<>(state.resistanceLevels.subList(0, maxLevels));
        }
    }
    
    /**
     * Calculate output values for current candle
     */
    private Map<String, BigDecimal> calculateOutputValues(SRState state, BigDecimal currentPrice) {
        Map<String, BigDecimal> values = new HashMap<>();
        
        // Find nearest support (below current price)
        SRLevel nearestSupport = null;
        for (SRLevel level : state.supportLevels) {
            if (level.broken) continue;
            if (level.price.compareTo(currentPrice) < 0) {
                if (nearestSupport == null || level.price.compareTo(nearestSupport.price) > 0) {
                    nearestSupport = level;
                }
            }
        }
        
        // Find nearest resistance (above current price)
        SRLevel nearestResistance = null;
        for (SRLevel level : state.resistanceLevels) {
            if (level.broken) continue;
            if (level.price.compareTo(currentPrice) > 0) {
                if (nearestResistance == null || level.price.compareTo(nearestResistance.price) < 0) {
                    nearestResistance = level;
                }
            }
        }
        
        values.put("nearestSupport", nearestSupport != null ? nearestSupport.price : BigDecimal.ZERO);
        values.put("nearestResistance", nearestResistance != null ? nearestResistance.price : BigDecimal.ZERO);
        values.put("supportStrength", nearestSupport != null ? new BigDecimal(nearestSupport.touches) : BigDecimal.ZERO);
        values.put("resistanceStrength", nearestResistance != null ? new BigDecimal(nearestResistance.touches) : BigDecimal.ZERO);
        
        long activeSupportCount = state.supportLevels.stream().filter(l -> !l.broken).count();
        long activeResistanceCount = state.resistanceLevels.stream().filter(l -> !l.broken).count();
        
        values.put("activeSupportLevels", new BigDecimal(activeSupportCount));
        values.put("activeResistanceLevels", new BigDecimal(activeResistanceCount));
        
        return values;
    }
    
    /**
     * Convert SR level to BoxShape for visualization
     */
    private BoxShape convertLevelToBox(SRLevel level, Instant currentTime, 
                                      String color, int minTouchesForStrong) {
        // Calculate end time
        long endTime;
        if (level.broken && level.brokenTime != null) {
            endTime = level.brokenTime.getEpochSecond();
        } else {
            // Active levels: extend to current time
            endTime = currentTime.getEpochSecond();
        }
        
        // Determine strength
        boolean isStrong = level.touches >= minTouchesForStrong;
        String strengthLabel = isStrong ? " (Strong)" : "";
        
        // Style based on status
        String backgroundColor;
        String label;
        if (level.broken) {
            backgroundColor = "rgba(128, 128, 128, 0.08)";
            label = (level.isSupport ? "Support" : "Resistance") + " (Broken)";
        } else {
            backgroundColor = color;
            label = (level.isSupport ? "Support" : "Resistance") + 
                   strengthLabel + " [" + level.touches + " touches]";
        }
        
        // Border color with higher opacity
        String borderColor = color.replace("0.2)", "0.6)");
        if (level.broken) {
            borderColor = "rgba(128, 128, 128, 0.3)";
        } else if (isStrong) {
            borderColor = color.replace("0.2)", "1.0)");
        }
        
        return BoxShape.builder()
            .time1(level.timestamp.getEpochSecond())
            .time2(endTime)
            .price1(level.top)
            .price2(level.bottom)
            .color(backgroundColor)
            .borderColor(borderColor)
            .label(label)
            .build();
    }
    
    private Map<String, BigDecimal> createEmptyResult() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("nearestSupport", BigDecimal.ZERO);
        result.put("nearestResistance", BigDecimal.ZERO);
        result.put("supportStrength", BigDecimal.ZERO);
        result.put("resistanceStrength", BigDecimal.ZERO);
        result.put("activeSupportLevels", BigDecimal.ZERO);
        result.put("activeResistanceLevels", BigDecimal.ZERO);
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Nearest support price line
        metadata.put("nearestSupport", IndicatorMetadata.builder("nearestSupport")
            .displayName("Nearest Support")
            .asLine("#228B22")
            .addConfig("lineWidth", 1)
            .addConfig("lineStyle", 2) // Dashed
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        // Nearest resistance price line
        metadata.put("nearestResistance", IndicatorMetadata.builder("nearestResistance")
            .displayName("Nearest Resistance")
            .asLine("#DC143C")
            .addConfig("lineWidth", 1)
            .addConfig("lineStyle", 2) // Dashed
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int pivotLookback = getIntParameter(params, "pivotLookback", 5);
        return pivotLookback * 2 + 1;
    }
}

