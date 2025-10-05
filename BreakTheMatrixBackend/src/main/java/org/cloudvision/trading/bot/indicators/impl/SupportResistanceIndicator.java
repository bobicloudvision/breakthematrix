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
        public List<CandlestickData> candleBuffer = new ArrayList<>(); // Keep recent candles for pivot detection
        public int maxBufferSize = 100; // Keep last 100 candles
    }
    
    public SupportResistanceIndicator() {
        super("sr", "Support & Resistance", 
              "Identifies key support and resistance levels based on pivot points",
              Indicator.IndicatorCategory.OVERLAY);
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        // Pivot detection parameters
        params.put("pivotLookback", IndicatorParameter.builder("pivotLookback")
            .displayName("Pivot Lookback")
            .description("Number of candles to look left and right for pivot detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(1)
            .maxValue(50)
            .required(true)
            .build());
        
        // Zone width (thin horizontal zones like manually drawn by traders)
        params.put("zoneWidthPercent", IndicatorParameter.builder("zoneWidthPercent")
            .displayName("Zone Width %")
            .description("Width of support/resistance zone as percentage of price (thin zones like manually drawn)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.08)
            .minValue(0.02)
            .maxValue(0.5)
            .required(true)
            .build());
        
        // Extend boxes backward in time for better visualization
        params.put("extendBackBars", IndicatorParameter.builder("extendBackBars")
            .displayName("Extend Back (Bars)")
            .description("Number of bars to extend the zone backward in time")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(20)
            .minValue(5)
            .maxValue(100)
            .required(false)
            .build());
        
        // Level management (fewer levels for cleaner chart)
        params.put("maxLevels", IndicatorParameter.builder("maxLevels")
            .displayName("Max Levels")
            .description("Maximum number of support/resistance levels to display (fewer = cleaner)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(2)
            .minValue(1)
            .maxValue(10)
            .required(true)
            .build());
        
        params.put("mergeThresholdPercent", IndicatorParameter.builder("mergeThresholdPercent")
            .displayName("Merge Threshold %")
            .description("Merge levels within this percentage distance (higher = fewer levels)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(1.0)
            .minValue(0.1)
            .maxValue(5.0)
            .required(true)
            .build());
        
        params.put("onlyShowNearby", IndicatorParameter.builder("onlyShowNearby")
            .displayName("Only Show Nearby Levels")
            .description("Only display levels near current price (reduces clutter)")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("maxDistancePercent", IndicatorParameter.builder("maxDistancePercent")
            .displayName("Max Distance %")
            .description("Maximum distance from current price to show levels (when nearby filter is on)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(3.0)
            .minValue(0.5)
            .maxValue(10.0)
            .required(false)
            .build());
        
        params.put("minTouchesForStrong", IndicatorParameter.builder("minTouchesForStrong")
            .displayName("Min Touches (Strong)")
            .description("Minimum touches to consider a level as strong")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(2)
            .maxValue(10)
            .required(true)
            .build());
        
        params.put("breakConfirmationPercent", IndicatorParameter.builder("breakConfirmationPercent")
            .displayName("Break Confirmation %")
            .description("Percentage price must move beyond level to confirm break")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.2)
            .minValue(0.1)
            .maxValue(2.0)
            .required(true)
            .build());
        
        // Display options
        params.put("showBrokenLevels", IndicatorParameter.builder("showBrokenLevels")
            .displayName("Show Broken Levels")
            .description("Display levels that have been broken")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("supportColor", IndicatorParameter.builder("supportColor")
            .displayName("Support Color")
            .description("Color for support zones")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(34, 139, 34, 0.2)")
            .required(false)
            .build());
        
        params.put("resistanceColor", IndicatorParameter.builder("resistanceColor")
            .displayName("Resistance Color")
            .description("Color for resistance zones")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(220, 20, 60, 0.2)")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        SRState state = new SRState();
        int pivotLookback = getIntParameter(params, "pivotLookback", 5);
        
        // If we have historical candles, process them to build initial state
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            // Add all historical candles to buffer
            state.candleBuffer.addAll(historicalCandles);
            
            // Process each candle to detect pivots and build levels
            for (int i = 0; i < historicalCandles.size(); i++) {
                state.barIndex++;
                CandlestickData candle = historicalCandles.get(i);
                
                // Only check for pivots if we have enough candles
                if (i >= pivotLookback * 2) {
                    int pivotIdx = i - pivotLookback;
                    processCandle(candle, pivotIdx, state, params);
                }
            }
            
            // Trim buffer to max size
            trimCandleBuffer(state);
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object stateObj) {
        params = mergeWithDefaults(params);
        
        // Cast or create state
        SRState state = (stateObj instanceof SRState) ? (SRState) stateObj : new SRState();
        
        int pivotLookback = getIntParameter(params, "pivotLookback", 5);
        
        // Add new candle to buffer
        state.candleBuffer.add(candle);
        state.barIndex++;
        
        // Check for pivot at the appropriate lookback position
        int bufferSize = state.candleBuffer.size();
        if (bufferSize >= pivotLookback * 2 + 1) {
            int pivotIdx = bufferSize - pivotLookback - 1;
            processCandle(candle, pivotIdx, state, params);
        }
        
        // Update level status (touches and breaks)
        double breakConfirmationPct = getDoubleParameter(params, "breakConfirmationPercent", 0.2);
        updateLevelStatus(state, candle, breakConfirmationPct);
        
        // Clean up old/weak levels
        int maxLevels = getIntParameter(params, "maxLevels", 5);
        cleanupLevels(state, maxLevels);
        
        // Trim candle buffer to prevent unlimited growth
        trimCandleBuffer(state);
        
        // Build result values
        Map<String, BigDecimal> values = calculateOutputValues(state, candle.getClose());
        
        // Convert levels to boxes for visualization
        List<Map<String, Object>> boxes = convertLevelsToBoxes(state, candle, params);
        
        Map<String, Object> output = new HashMap<>();
        output.put("values", values);
        output.put("state", state);
        output.put("boxes", boxes);
        
        return output;
    }
    
    /**
     * Process a candle and detect pivots
     */
    private void processCandle(CandlestickData candle, int pivotIdx, SRState state, Map<String, Object> params) {
        int pivotLookback = getIntParameter(params, "pivotLookback", 5);
        double zoneWidthPct = getDoubleParameter(params, "zoneWidthPercent", 0.3);
        double mergeThresholdPct = getDoubleParameter(params, "mergeThresholdPercent", 0.5);
        int maxLevels = getIntParameter(params, "maxLevels", 5);
        
        // Check for pivot high (resistance)
        if (isPivotHigh(state.candleBuffer, pivotIdx, pivotLookback)) {
            CandlestickData pivotCandle = state.candleBuffer.get(pivotIdx);
            addResistanceLevel(state, pivotCandle.getHigh(), pivotCandle.getCloseTime(), 
                              zoneWidthPct, mergeThresholdPct, maxLevels);
        }
        
        // Check for pivot low (support)
        if (isPivotLow(state.candleBuffer, pivotIdx, pivotLookback)) {
            CandlestickData pivotCandle = state.candleBuffer.get(pivotIdx);
            addSupportLevel(state, pivotCandle.getLow(), pivotCandle.getCloseTime(), 
                           zoneWidthPct, mergeThresholdPct, maxLevels);
        }
    }
    
    /**
     * Convert levels to boxes for visualization
     */
    private List<Map<String, Object>> convertLevelsToBoxes(SRState state, CandlestickData currentCandle, Map<String, Object> params) {
        boolean showBroken = getBooleanParameter(params, "showBrokenLevels", false);
        boolean onlyShowNearby = getBooleanParameter(params, "onlyShowNearby", true);
        double maxDistancePct = getDoubleParameter(params, "maxDistancePercent", 3.0);
        String supportColor = getStringParameter(params, "supportColor", "rgba(34, 139, 34, 0.2)");
        String resistanceColor = getStringParameter(params, "resistanceColor", "rgba(220, 20, 60, 0.2)");
        int extendBackBars = getIntParameter(params, "extendBackBars", 20);
        int minTouchesForStrong = getIntParameter(params, "minTouchesForStrong", 3);
        
        BigDecimal currentPrice = currentCandle.getClose();
        BigDecimal maxDistance = currentPrice.multiply(BigDecimal.valueOf(maxDistancePct / 100.0));
        
        List<BoxShape> boxShapes = new ArrayList<>();
        
        // Convert support levels to boxes
        for (SRLevel level : state.supportLevels) {
            if (level.broken && !showBroken) continue;
            
            if (onlyShowNearby) {
                BigDecimal distance = currentPrice.subtract(level.price).abs();
                if (distance.compareTo(maxDistance) > 0) continue;
            }
            
            boxShapes.add(convertLevelToBox(level, currentCandle.getCloseTime(), 
                                           supportColor, minTouchesForStrong, 
                                           extendBackBars, state.candleBuffer));
        }
        
        // Convert resistance levels to boxes
        for (SRLevel level : state.resistanceLevels) {
            if (level.broken && !showBroken) continue;
            
            if (onlyShowNearby) {
                BigDecimal distance = currentPrice.subtract(level.price).abs();
                if (distance.compareTo(maxDistance) > 0) continue;
            }
            
            boxShapes.add(convertLevelToBox(level, currentCandle.getCloseTime(), 
                                           resistanceColor, minTouchesForStrong,
                                           extendBackBars, state.candleBuffer));
        }
        
        // Convert BoxShape objects to Map for API serialization
        return boxShapes.stream()
            .map(BoxShape::toMap)
            .collect(Collectors.toList());
    }
    
    /**
     * Trim candle buffer to prevent unlimited growth
     */
    private void trimCandleBuffer(SRState state) {
        if (state.candleBuffer.size() > state.maxBufferSize) {
            int excess = state.candleBuffer.size() - state.maxBufferSize;
            state.candleBuffer = new ArrayList<>(state.candleBuffer.subList(excess, state.candleBuffer.size()));
        }
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
     * Add or merge a support level (keep zones stable for deduplication)
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
                // Merge: increase touch count but DON'T expand zone (keeps prices stable for deduplication)
                existing.touches++;
                existing.lastTouchTime = timestamp;
                return;
            }
        }
        
        // Add new level
        SRLevel newLevel = new SRLevel(price, top, bottom, timestamp, true);
        state.supportLevels.add(newLevel);
    }
    
    /**
     * Add or merge a resistance level (keep zones stable for deduplication)
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
                // Merge: increase touch count but DON'T expand zone (keeps prices stable for deduplication)
                existing.touches++;
                existing.lastTouchTime = timestamp;
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
     * Clean up old and weak levels (prioritize stronger and more recent levels)
     */
    private void cleanupLevels(SRState state, int maxLevels) {
        // Remove broken levels
        state.supportLevels.removeIf(level -> level.broken);
        state.resistanceLevels.removeIf(level -> level.broken);
        
        // Keep only the strongest and most recent levels if we have too many
        // Sort by: 1) Number of touches (stronger levels), 2) Most recent touch time
        if (state.supportLevels.size() > maxLevels) {
            state.supportLevels.sort((a, b) -> {
                // First, compare by touches (more touches = stronger)
                int touchCompare = Integer.compare(b.touches, a.touches);
                if (touchCompare != 0) return touchCompare;
                
                // If same touches, prefer more recent
                return b.lastTouchTime.compareTo(a.lastTouchTime);
            });
            state.supportLevels = new ArrayList<>(state.supportLevels.subList(0, maxLevels));
        }
        
        if (state.resistanceLevels.size() > maxLevels) {
            state.resistanceLevels.sort((a, b) -> {
                // First, compare by touches (more touches = stronger)
                int touchCompare = Integer.compare(b.touches, a.touches);
                if (touchCompare != 0) return touchCompare;
                
                // If same touches, prefer more recent
                return b.lastTouchTime.compareTo(a.lastTouchTime);
            });
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
        
//        values.put("nearestSupport", nearestSupport != null ? nearestSupport.price : BigDecimal.ZERO);
//        values.put("nearestResistance", nearestResistance != null ? nearestResistance.price : BigDecimal.ZERO);
//        values.put("supportStrength", nearestSupport != null ? new BigDecimal(nearestSupport.touches) : BigDecimal.ZERO);
//        values.put("resistanceStrength", nearestResistance != null ? new BigDecimal(nearestResistance.touches) : BigDecimal.ZERO);
//
        long activeSupportCount = state.supportLevels.stream().filter(l -> !l.broken).count();
        long activeResistanceCount = state.resistanceLevels.stream().filter(l -> !l.broken).count();
        
//        values.put("activeSupportLevels", new BigDecimal(activeSupportCount));
//        values.put("activeResistanceLevels", new BigDecimal(activeResistanceCount));
        
        return values;
    }
    
    /**
     * Convert SR level to BoxShape for visualization (thin horizontal zones like manually drawn)
     * Uses level creation time and last touch time for stable deduplication
     */
    private BoxShape convertLevelToBox(SRLevel level, Instant currentTime, 
                                      String color, int minTouchesForStrong, 
                                      int extendBackBars, List<CandlestickData> candles) {
        // Use level's creation time as start (stable for deduplication)
        long startTime = level.timestamp.getEpochSecond();
        
        // Calculate end time based on level status
        long endTime;
        if (level.broken && level.brokenTime != null) {
            // Broken levels: use break time
            endTime = level.brokenTime.getEpochSecond();
        } else {
            // Active levels: extend a fixed amount from last touch (stable)
            // This makes the boxes deduplicate properly
            endTime = level.lastTouchTime.getEpochSecond() + (extendBackBars * 60); // Assume 1min candles
        }
        
        // Determine strength
        boolean isStrong = level.touches >= minTouchesForStrong;
        String strengthLabel = isStrong ? " 💪" : "";
        
        // Style based on status
        String backgroundColor;
        String label;
        if (level.broken) {
            backgroundColor = "rgba(128, 128, 128, 0.05)";
            label = (level.isSupport ? "S" : "R") + " (Broken)";
        } else {
            backgroundColor = color;
            label = (level.isSupport ? "Support" : "Resistance") + 
                   strengthLabel + " (" + level.touches + "x)";
        }
        
        // Border color with higher opacity for clear visualization
        String borderColor = color.replace("0.2)", "0.8)");
        if (level.broken) {
            borderColor = "rgba(128, 128, 128, 0.3)";
        } else if (isStrong) {
            // Strong levels get solid border
            borderColor = color.replace("0.2)", "1.0)");
        }
        
        // Round prices to 2 decimal places for better deduplication
        // (ShapeRegistry deduplicates based on time1 + price1 + price2)
        BigDecimal roundedTop = level.top.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal roundedBottom = level.bottom.setScale(2, java.math.RoundingMode.HALF_UP);
        
        return BoxShape.builder()
            .time1(startTime)
            .time2(endTime)
            .price1(roundedTop)
            .price2(roundedBottom)
            .color(backgroundColor)
            .borderColor(borderColor)
            .label(label)
            .build();
    }
    
    private Map<String, BigDecimal> createEmptyResult() {
        Map<String, BigDecimal> result = new HashMap<>();
//        result.put("nearestSupport", BigDecimal.ZERO);
//        result.put("nearestResistance", BigDecimal.ZERO);
//        result.put("supportStrength", BigDecimal.ZERO);
//        result.put("resistanceStrength", BigDecimal.ZERO);
//        result.put("activeSupportLevels", BigDecimal.ZERO);
//        result.put("activeResistanceLevels", BigDecimal.ZERO);
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Nearest support price line
//        metadata.put("nearestSupport", IndicatorMetadata.builder("nearestSupport")
//            .displayName("Nearest Support")
//            .asLine("#228B22", 1)
//            .addConfig("lineStyle", 2) // Dashed
//            .separatePane(false)
//            .paneOrder(0)
//            .build());
//
//        // Nearest resistance price line
//        metadata.put("nearestResistance", IndicatorMetadata.builder("nearestResistance")
//            .displayName("Nearest Resistance")
//            .asLine("#DC143C", 1)
//            .addConfig("lineStyle", 2) // Dashed
//            .separatePane(false)
//            .paneOrder(0)
//            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int pivotLookback = getIntParameter(params, "pivotLookback", 5);
        return pivotLookback * 2 + 1;
    }
}

