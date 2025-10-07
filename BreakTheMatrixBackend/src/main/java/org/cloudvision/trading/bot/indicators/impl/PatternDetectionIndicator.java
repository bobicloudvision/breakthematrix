package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.AbstractIndicator;
import org.cloudvision.trading.bot.indicators.IndicatorParameter;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.LineShape;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.bot.visualization.ShapeCollection;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pattern Detection Indicator
 * 
 * Automatically detects and visualizes classic chart patterns including:
 * 
 * REVERSAL PATTERNS:
 * - Head and Shoulders (Bearish)
 * - Inverse Head and Shoulders (Bullish)
 * - Double Top (Bearish)
 * - Double Bottom (Bullish)
 * - Triple Top (Bearish)
 * - Triple Bottom (Bullish)
 * 
 * CONTINUATION PATTERNS:
 * - Ascending Triangle (Bullish)
 * - Descending Triangle (Bearish)
 * - Symmetrical Triangle (Neutral)
 * - Rising Wedge (Bearish)
 * - Falling Wedge (Bullish)
 * - Bullish Flag
 * - Bearish Flag
 * - Pennant
 * 
 * DETECTION METHOD:
 * Uses pivot points (swing highs and lows) to identify pattern structures
 * and validates patterns based on price action and geometry rules.
 * 
 * FEATURES:
 * - Configurable sensitivity (pivot strength)
 * - Pattern validation with tolerance
 * - Real-time pattern updates
 * - Visual pattern boundaries and labels
 * - Pattern breakout detection
 * - Support/resistance projection
 */
@Component
public class PatternDetectionIndicator extends AbstractIndicator {
    
    /**
     * Pivot point representation
     */
    public static class Pivot {
        public BigDecimal price;
        public Instant time;
        public int index;
        public boolean isHigh;  // true for high, false for low
        
        public Pivot(BigDecimal price, Instant time, int index, boolean isHigh) {
            this.price = price;
            this.time = time;
            this.index = index;
            this.isHigh = isHigh;
        }
    }
    
    /**
     * Detected pattern representation
     */
    public static class Pattern {
        public String type;           // Pattern type name
        public String bias;           // "bullish", "bearish", "neutral"
        public List<Pivot> pivots;    // Key pivots forming the pattern
        public Instant startTime;
        public Instant endTime;
        public BigDecimal targetPrice; // Projected target
        public boolean confirmed;      // Pattern confirmed/broken out
        public boolean active;         // Still monitoring this pattern
        
        public Pattern(String type, String bias) {
            this.type = type;
            this.bias = bias;
            this.pivots = new ArrayList<>();
            this.confirmed = false;
            this.active = true;
        }
    }
    
    /**
     * State container for pattern detection
     */
    public static class PatternState {
        // Candle history for pivot detection
        public List<CandlestickData> candles = new ArrayList<>();
        
        // Detected pivots
        public List<Pivot> pivotHighs = new ArrayList<>();
        public List<Pivot> pivotLows = new ArrayList<>();
        
        // Detected patterns
        public List<Pattern> activePatterns = new ArrayList<>();
        public List<Pattern> confirmedPatterns = new ArrayList<>();
        
        // Bar counter
        public int barIndex = 0;
        
        // Statistics
        public int totalPatternsDetected = 0;
        public int totalPatternsConfirmed = 0;
        
        // For drawing - using ShapeCollection for cleaner shape management
        public ShapeCollection shapes = new ShapeCollection();
    }
    
    public PatternDetectionIndicator() {
        super(
            "pattern-detection",
            "Pattern Detection",
            "Detects classic chart patterns including Head & Shoulders, Double Tops/Bottoms, Triangles, Wedges, Flags, and more",
            IndicatorCategory.OVERLAY
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("pivotStrength", IndicatorParameter.builder("pivotStrength")
            .displayName("Pivot Strength")
            .description("Number of bars required on each side to confirm a pivot (higher = fewer, stronger pivots)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(2)
            .maxValue(20)
            .build());
            
        params.put("tolerance", IndicatorParameter.builder("tolerance")
            .displayName("Pattern Tolerance")
            .description("Price tolerance for pattern matching (percentage)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(2.0)
            .minValue(0.5)
            .maxValue(10.0)
            .build());
            
        params.put("minPatternBars", IndicatorParameter.builder("minPatternBars")
            .displayName("Min Pattern Bars")
            .description("Minimum number of bars for pattern formation")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(10)
            .minValue(5)
            .maxValue(100)
            .build());
            
        params.put("maxPatternBars", IndicatorParameter.builder("maxPatternBars")
            .displayName("Max Pattern Bars")
            .description("Maximum number of bars for pattern formation")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(100)
            .minValue(20)
            .maxValue(500)
            .build());
            
        params.put("showLabels", IndicatorParameter.builder("showLabels")
            .displayName("Show Labels")
            .description("Display pattern names and confirmation status")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .build());
            
        params.put("detectReversal", IndicatorParameter.builder("detectReversal")
            .displayName("Detect Reversal Patterns")
            .description("Enable detection of reversal patterns")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .build());
            
        params.put("detectContinuation", IndicatorParameter.builder("detectContinuation")
            .displayName("Detect Continuation Patterns")
            .description("Enable detection of continuation patterns")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        PatternState state = new PatternState();
        
        // Validate inputs
        if (historicalCandles == null || historicalCandles.isEmpty()) {
            return state;
        }
        if (params == null) {
            params = new HashMap<>();
        }
        
        int pivotStrength = getIntParameter(params, "pivotStrength", 5);
        
        // Process historical candles to build pivot points
        for (CandlestickData candle : historicalCandles) {
            state.candles.add(candle);
            state.barIndex++;
            
            // Detect pivots (need enough candles)
            if (state.candles.size() > pivotStrength * 2) {
                detectPivots(state, pivotStrength);
            }
        }
        
        // Initial pattern detection
        if (state.pivotHighs.size() >= 3 && state.pivotLows.size() >= 3) {
            detectPatterns(state, params);
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object stateObj) {
        // Validate inputs
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        if (params == null) {
            params = new HashMap<>();
        }
        
        // Initialize state if null
        PatternState state;
        if (stateObj == null) {
            state = new PatternState();
        } else {
            state = (PatternState) stateObj;
        }
        
        // Add new candle
        state.candles.add(candle);
        state.barIndex++;
        
        int pivotStrength = getIntParameter(params, "pivotStrength", 5);
        
        // Detect new pivots
        if (state.candles.size() > pivotStrength * 2) {
            detectPivots(state, pivotStrength);
        }
        
        // Update active patterns (check for confirmation/invalidation)
        updateActivePatterns(state, candle, params);
        
        // Detect new patterns
        if (state.pivotHighs.size() >= 3 && state.pivotLows.size() >= 3) {
            detectPatterns(state, params);
        }
        
        // Generate visualization
        generateVisualization(state, params);
        
        // Prepare output
        Map<String, Object> result = new HashMap<>();
        Map<String, BigDecimal> values = new HashMap<>();
        
        // Output pattern counts
        values.put("activePatterns", BigDecimal.valueOf(state.activePatterns.size()));
        values.put("confirmedPatterns", BigDecimal.valueOf(state.totalPatternsConfirmed));
        values.put("totalDetected", BigDecimal.valueOf(state.totalPatternsDetected));
        
        result.put("values", values);
        result.put("state", state);
        
        // Add shapes organized by type (lines, markers, etc.)
        // This matches the pattern used by other indicators like SRLevelsBreaksIndicator
        result.putAll(state.shapes.toMap());
        
        // Add pattern details
        List<Map<String, Object>> patternDetails = new ArrayList<>();
        for (Pattern pattern : state.activePatterns) {
            Map<String, Object> details = new HashMap<>();
            details.put("type", pattern.type);
            details.put("bias", pattern.bias);
            details.put("confirmed", pattern.confirmed);
            if (pattern.targetPrice != null) {
                details.put("target", pattern.targetPrice);
            }
            patternDetails.add(details);
        }
        result.put("patterns", patternDetails);
        
        return result;
    }
    
    /**
     * Detect pivot highs and lows
     */
    private void detectPivots(PatternState state, int strength) {
        int size = state.candles.size();
        if (size < strength * 2 + 1) {
            return;
        }
        
        // Check the candle at position (size - strength - 1)
        int checkIndex = size - strength - 1;
        CandlestickData checkCandle = state.candles.get(checkIndex);
        
        // Check for pivot high
        boolean isPivotHigh = true;
        for (int i = 1; i <= strength; i++) {
            // Check left side
            if (checkIndex - i >= 0 && state.candles.get(checkIndex - i).getHigh().compareTo(checkCandle.getHigh()) >= 0) {
                isPivotHigh = false;
                break;
            }
            // Check right side
            if (checkIndex + i < size && state.candles.get(checkIndex + i).getHigh().compareTo(checkCandle.getHigh()) >= 0) {
                isPivotHigh = false;
                break;
            }
        }
        
        if (isPivotHigh) {
            // Check if we already added this pivot
            boolean alreadyExists = state.pivotHighs.stream()
                .anyMatch(p -> p.index == checkIndex);
            if (!alreadyExists) {
                state.pivotHighs.add(new Pivot(
                    checkCandle.getHigh(),
                    checkCandle.getOpenTime(),
                    checkIndex,
                    true
                ));
            }
        }
        
        // Check for pivot low
        boolean isPivotLow = true;
        for (int i = 1; i <= strength; i++) {
            // Check left side
            if (checkIndex - i >= 0 && state.candles.get(checkIndex - i).getLow().compareTo(checkCandle.getLow()) <= 0) {
                isPivotLow = false;
                break;
            }
            // Check right side
            if (checkIndex + i < size && state.candles.get(checkIndex + i).getLow().compareTo(checkCandle.getLow()) <= 0) {
                isPivotLow = false;
                break;
            }
        }
        
        if (isPivotLow) {
            // Check if we already added this pivot
            boolean alreadyExists = state.pivotLows.stream()
                .anyMatch(p -> p.index == checkIndex);
            if (!alreadyExists) {
                state.pivotLows.add(new Pivot(
                    checkCandle.getLow(),
                    checkCandle.getOpenTime(),
                    checkIndex,
                    false
                ));
            }
        }
    }
    
    /**
     * Detect patterns from pivot points
     */
    private void detectPatterns(PatternState state, Map<String, Object> params) {
        boolean detectReversal = getBooleanParameter(params, "detectReversal", true);
        boolean detectContinuation = getBooleanParameter(params, "detectContinuation", true);
        
        if (detectReversal) {
            detectHeadAndShoulders(state, params);
            detectDoubleTopsBottoms(state, params);
            detectTripleTopsBottoms(state, params);
        }
        
        if (detectContinuation) {
            detectTriangles(state, params);
            detectWedges(state, params);
            detectFlags(state, params);
        }
    }
    
    /**
     * Detect Head and Shoulders pattern
     */
    private void detectHeadAndShoulders(PatternState state, Map<String, Object> params) {
        double tolerance = getDoubleParameter(params, "tolerance", 2.0) / 100.0;
        int minBars = getIntParameter(params, "minPatternBars", 10);
        int maxBars = getIntParameter(params, "maxPatternBars", 100);
        
        // Need at least 3 pivot highs for head and shoulders
        if (state.pivotHighs.size() < 3) return;
        
        List<Pivot> highs = state.pivotHighs;
        
        // Check last 5 pivot highs for H&S pattern
        for (int i = highs.size() - 1; i >= 4; i--) {
            Pivot rightShoulder = highs.get(i);
            Pivot head = highs.get(i - 1);
            Pivot leftShoulder = highs.get(i - 2);
            
            // Check if already part of an active pattern
            if (isPartOfActivePattern(state, Arrays.asList(leftShoulder, head, rightShoulder))) {
                continue;
            }
            
            // Check pattern timeframe
            int patternBars = rightShoulder.index - leftShoulder.index;
            if (patternBars < minBars || patternBars > maxBars) continue;
            
            // Head should be higher than shoulders
            if (head.price.compareTo(leftShoulder.price) <= 0 || 
                head.price.compareTo(rightShoulder.price) <= 0) {
                continue;
            }
            
            // Shoulders should be roughly equal (within tolerance)
            BigDecimal shoulderDiff = leftShoulder.price.subtract(rightShoulder.price).abs();
            BigDecimal shoulderAvg = leftShoulder.price.add(rightShoulder.price).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
            
            if (shoulderDiff.divide(shoulderAvg, 10, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                continue;
            }
            
            // Find neckline (support level between shoulders)
            List<Pivot> lowsBetween = state.pivotLows.stream()
                .filter(p -> p.index > leftShoulder.index && p.index < rightShoulder.index)
                .collect(Collectors.toList());
            
            if (lowsBetween.size() < 2) continue;
            
            // Valid Head and Shoulders pattern found
            Pattern pattern = new Pattern("Head and Shoulders", "bearish");
            pattern.pivots.add(leftShoulder);
            pattern.pivots.add(lowsBetween.get(0));
            pattern.pivots.add(head);
            pattern.pivots.add(lowsBetween.get(lowsBetween.size() - 1));
            pattern.pivots.add(rightShoulder);
            pattern.startTime = leftShoulder.time;
            pattern.endTime = rightShoulder.time;
            
            // Calculate target (head to neckline distance projected down)
            BigDecimal neckline = lowsBetween.stream()
                .map(p -> p.price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(lowsBetween.size()), RoundingMode.HALF_UP);
            BigDecimal distance = head.price.subtract(neckline);
            pattern.targetPrice = neckline.subtract(distance);
            
            state.activePatterns.add(pattern);
            state.totalPatternsDetected++;
            break; // Only detect one pattern of this type at a time
        }
        
        // Inverse Head and Shoulders (bullish)
        if (state.pivotLows.size() < 3) return;
        
        List<Pivot> lows = state.pivotLows;
        
        for (int i = lows.size() - 1; i >= 4; i--) {
            Pivot rightShoulder = lows.get(i);
            Pivot head = lows.get(i - 1);
            Pivot leftShoulder = lows.get(i - 2);
            
            if (isPartOfActivePattern(state, Arrays.asList(leftShoulder, head, rightShoulder))) {
                continue;
            }
            
            int patternBars = rightShoulder.index - leftShoulder.index;
            if (patternBars < minBars || patternBars > maxBars) continue;
            
            // Head should be lower than shoulders
            if (head.price.compareTo(leftShoulder.price) >= 0 || 
                head.price.compareTo(rightShoulder.price) >= 0) {
                continue;
            }
            
            // Shoulders should be roughly equal
            BigDecimal shoulderDiff = leftShoulder.price.subtract(rightShoulder.price).abs();
            BigDecimal shoulderAvg = leftShoulder.price.add(rightShoulder.price).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
            
            if (shoulderDiff.divide(shoulderAvg, 10, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                continue;
            }
            
            // Find neckline (resistance level between shoulders)
            List<Pivot> highsBetween = state.pivotHighs.stream()
                .filter(p -> p.index > leftShoulder.index && p.index < rightShoulder.index)
                .collect(Collectors.toList());
            
            if (highsBetween.size() < 2) continue;
            
            Pattern pattern = new Pattern("Inverse Head and Shoulders", "bullish");
            pattern.pivots.add(leftShoulder);
            pattern.pivots.add(highsBetween.get(0));
            pattern.pivots.add(head);
            pattern.pivots.add(highsBetween.get(highsBetween.size() - 1));
            pattern.pivots.add(rightShoulder);
            pattern.startTime = leftShoulder.time;
            pattern.endTime = rightShoulder.time;
            
            // Calculate target
            BigDecimal neckline = highsBetween.stream()
                .map(p -> p.price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(highsBetween.size()), RoundingMode.HALF_UP);
            BigDecimal distance = neckline.subtract(head.price);
            pattern.targetPrice = neckline.add(distance);
            
            state.activePatterns.add(pattern);
            state.totalPatternsDetected++;
            break;
        }
    }
    
    /**
     * Detect Double Top and Double Bottom patterns
     */
    private void detectDoubleTopsBottoms(PatternState state, Map<String, Object> params) {
        double tolerance = getDoubleParameter(params, "tolerance", 2.0) / 100.0;
        int minBars = getIntParameter(params, "minPatternBars", 10);
        int maxBars = getIntParameter(params, "maxPatternBars", 100);
        
        // Double Top
        if (state.pivotHighs.size() >= 2) {
            List<Pivot> highs = state.pivotHighs;
            
            for (int i = highs.size() - 1; i >= 1; i--) {
                Pivot second = highs.get(i);
                Pivot first = highs.get(i - 1);
                
                if (isPartOfActivePattern(state, Arrays.asList(first, second))) {
                    continue;
                }
                
                int patternBars = second.index - first.index;
                if (patternBars < minBars || patternBars > maxBars) continue;
                
                // Tops should be roughly equal
                BigDecimal diff = first.price.subtract(second.price).abs();
                BigDecimal avg = first.price.add(second.price).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                
                if (diff.divide(avg, 10, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                    continue;
                }
                
                // Find valley between peaks
                List<Pivot> valley = state.pivotLows.stream()
                    .filter(p -> p.index > first.index && p.index < second.index)
                    .collect(Collectors.toList());
                
                if (valley.isEmpty()) continue;
                
                Pattern pattern = new Pattern("Double Top", "bearish");
                pattern.pivots.add(first);
                pattern.pivots.add(valley.get(0));
                pattern.pivots.add(second);
                pattern.startTime = first.time;
                pattern.endTime = second.time;
                
                // Target = valley - (peak - valley)
                BigDecimal peak = avg;
                BigDecimal valleyPrice = valley.get(0).price;
                BigDecimal distance = peak.subtract(valleyPrice);
                pattern.targetPrice = valleyPrice.subtract(distance);
                
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
                break;
            }
        }
        
        // Double Bottom
        if (state.pivotLows.size() >= 2) {
            List<Pivot> lows = state.pivotLows;
            
            for (int i = lows.size() - 1; i >= 1; i--) {
                Pivot second = lows.get(i);
                Pivot first = lows.get(i - 1);
                
                if (isPartOfActivePattern(state, Arrays.asList(first, second))) {
                    continue;
                }
                
                int patternBars = second.index - first.index;
                if (patternBars < minBars || patternBars > maxBars) continue;
                
                // Bottoms should be roughly equal
                BigDecimal diff = first.price.subtract(second.price).abs();
                BigDecimal avg = first.price.add(second.price).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
                
                if (diff.divide(avg, 10, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(tolerance)) > 0) {
                    continue;
                }
                
                // Find peak between valleys
                List<Pivot> peak = state.pivotHighs.stream()
                    .filter(p -> p.index > first.index && p.index < second.index)
                    .collect(Collectors.toList());
                
                if (peak.isEmpty()) continue;
                
                Pattern pattern = new Pattern("Double Bottom", "bullish");
                pattern.pivots.add(first);
                pattern.pivots.add(peak.get(0));
                pattern.pivots.add(second);
                pattern.startTime = first.time;
                pattern.endTime = second.time;
                
                // Target = peak + (peak - valley)
                BigDecimal valley = avg;
                BigDecimal peakPrice = peak.get(0).price;
                BigDecimal distance = peakPrice.subtract(valley);
                pattern.targetPrice = peakPrice.add(distance);
                
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
                break;
            }
        }
    }
    
    /**
     * Detect Triple Top and Triple Bottom patterns
     */
    private void detectTripleTopsBottoms(PatternState state, Map<String, Object> params) {
        double tolerance = getDoubleParameter(params, "tolerance", 2.0) / 100.0;
        int minBars = getIntParameter(params, "minPatternBars", 10);
        int maxBars = getIntParameter(params, "maxPatternBars", 100);
        
        // Triple Top
        if (state.pivotHighs.size() >= 3) {
            List<Pivot> highs = state.pivotHighs;
            
            for (int i = highs.size() - 1; i >= 2; i--) {
                Pivot third = highs.get(i);
                Pivot second = highs.get(i - 1);
                Pivot first = highs.get(i - 2);
                
                if (isPartOfActivePattern(state, Arrays.asList(first, second, third))) {
                    continue;
                }
                
                int patternBars = third.index - first.index;
                if (patternBars < minBars || patternBars > maxBars) continue;
                
                // All three tops should be roughly equal
                BigDecimal avg = first.price.add(second.price).add(third.price)
                    .divide(BigDecimal.valueOf(3), RoundingMode.HALF_UP);
                
                boolean allEqual = Stream.of(first, second, third)
                    .allMatch(p -> {
                        BigDecimal diff = p.price.subtract(avg).abs();
                        return diff.divide(avg, 10, RoundingMode.HALF_UP)
                            .compareTo(BigDecimal.valueOf(tolerance)) <= 0;
                    });
                
                if (!allEqual) continue;
                
                // Find support level
                List<Pivot> support = state.pivotLows.stream()
                    .filter(p -> p.index > first.index && p.index < third.index)
                    .collect(Collectors.toList());
                
                if (support.size() < 2) continue;
                
                Pattern pattern = new Pattern("Triple Top", "bearish");
                pattern.pivots.addAll(Arrays.asList(first, second, third));
                pattern.startTime = first.time;
                pattern.endTime = third.time;
                
                BigDecimal supportLevel = support.stream()
                    .map(p -> p.price)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
                BigDecimal distance = avg.subtract(supportLevel);
                pattern.targetPrice = supportLevel.subtract(distance);
                
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
                break;
            }
        }
        
        // Triple Bottom
        if (state.pivotLows.size() >= 3) {
            List<Pivot> lows = state.pivotLows;
            
            for (int i = lows.size() - 1; i >= 2; i--) {
                Pivot third = lows.get(i);
                Pivot second = lows.get(i - 1);
                Pivot first = lows.get(i - 2);
                
                if (isPartOfActivePattern(state, Arrays.asList(first, second, third))) {
                    continue;
                }
                
                int patternBars = third.index - first.index;
                if (patternBars < minBars || patternBars > maxBars) continue;
                
                // All three bottoms should be roughly equal
                BigDecimal avg = first.price.add(second.price).add(third.price)
                    .divide(BigDecimal.valueOf(3), RoundingMode.HALF_UP);
                
                boolean allEqual = Stream.of(first, second, third)
                    .allMatch(p -> {
                        BigDecimal diff = p.price.subtract(avg).abs();
                        return diff.divide(avg, 10, RoundingMode.HALF_UP)
                            .compareTo(BigDecimal.valueOf(tolerance)) <= 0;
                    });
                
                if (!allEqual) continue;
                
                // Find resistance level
                List<Pivot> resistance = state.pivotHighs.stream()
                    .filter(p -> p.index > first.index && p.index < third.index)
                    .collect(Collectors.toList());
                
                if (resistance.size() < 2) continue;
                
                Pattern pattern = new Pattern("Triple Bottom", "bullish");
                pattern.pivots.addAll(Arrays.asList(first, second, third));
                pattern.startTime = first.time;
                pattern.endTime = third.time;
                
                BigDecimal resistanceLevel = resistance.stream()
                    .map(p -> p.price)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
                BigDecimal distance = resistanceLevel.subtract(avg);
                pattern.targetPrice = resistanceLevel.add(distance);
                
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
                break;
            }
        }
    }
    
    /**
     * Detect Triangle patterns (Ascending, Descending, Symmetrical)
     */
    private void detectTriangles(PatternState state, Map<String, Object> params) {
        // Need at least 2 highs and 2 lows for triangle
        if (state.pivotHighs.size() < 2 || state.pivotLows.size() < 2) return;
        
        // Get recent pivots
        List<Pivot> recentHighs = state.pivotHighs.subList(
            Math.max(0, state.pivotHighs.size() - 4),
            state.pivotHighs.size()
        );
        
        List<Pivot> recentLows = state.pivotLows.subList(
            Math.max(0, state.pivotLows.size() - 4),
            state.pivotLows.size()
        );
        
        if (recentHighs.size() < 2 || recentLows.size() < 2) return;
        
        Pivot firstHigh = recentHighs.get(0);
        Pivot lastHigh = recentHighs.get(recentHighs.size() - 1);
        Pivot firstLow = recentLows.get(0);
        Pivot lastLow = recentLows.get(recentLows.size() - 1);
        
        // Calculate slopes
        BigDecimal highSlope = lastHigh.price.subtract(firstHigh.price);
        BigDecimal lowSlope = lastLow.price.subtract(firstLow.price);
        
        // Ascending Triangle: flat top, rising bottom
        if (highSlope.abs().compareTo(firstHigh.price.multiply(BigDecimal.valueOf(0.01))) < 0 && 
            lowSlope.compareTo(BigDecimal.ZERO) > 0) {
            
            Pattern pattern = new Pattern("Ascending Triangle", "bullish");
            pattern.pivots.addAll(recentHighs);
            pattern.pivots.addAll(recentLows);
            pattern.startTime = Stream.concat(recentHighs.stream(), recentLows.stream())
                .map(p -> p.time)
                .min(Instant::compareTo)
                .orElse(Instant.now());
            pattern.endTime = Stream.concat(recentHighs.stream(), recentLows.stream())
                .map(p -> p.time)
                .max(Instant::compareTo)
                .orElse(Instant.now());
            
            // Target = resistance + height
            BigDecimal resistance = recentHighs.stream()
                .map(p -> p.price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recentHighs.size()), RoundingMode.HALF_UP);
            BigDecimal height = resistance.subtract(firstLow.price);
            pattern.targetPrice = resistance.add(height);
            
            if (!isPartOfActivePattern(state, pattern.pivots)) {
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
        }
        
        // Descending Triangle: flat bottom, falling top
        else if (lowSlope.abs().compareTo(firstLow.price.multiply(BigDecimal.valueOf(0.01))) < 0 && 
                 highSlope.compareTo(BigDecimal.ZERO) < 0) {
            
            Pattern pattern = new Pattern("Descending Triangle", "bearish");
            pattern.pivots.addAll(recentHighs);
            pattern.pivots.addAll(recentLows);
            pattern.startTime = Stream.concat(recentHighs.stream(), recentLows.stream())
                .map(p -> p.time)
                .min(Instant::compareTo)
                .orElse(Instant.now());
            pattern.endTime = Stream.concat(recentHighs.stream(), recentLows.stream())
                .map(p -> p.time)
                .max(Instant::compareTo)
                .orElse(Instant.now());
            
            // Target = support - height
            BigDecimal support = recentLows.stream()
                .map(p -> p.price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recentLows.size()), RoundingMode.HALF_UP);
            BigDecimal height = firstHigh.price.subtract(support);
            pattern.targetPrice = support.subtract(height);
            
            if (!isPartOfActivePattern(state, pattern.pivots)) {
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
        }
        
        // Symmetrical Triangle: converging highs and lows
        else if (highSlope.compareTo(BigDecimal.ZERO) < 0 && lowSlope.compareTo(BigDecimal.ZERO) > 0) {
            Pattern pattern = new Pattern("Symmetrical Triangle", "neutral");
            pattern.pivots.addAll(recentHighs);
            pattern.pivots.addAll(recentLows);
            pattern.startTime = Stream.concat(recentHighs.stream(), recentLows.stream())
                .map(p -> p.time)
                .min(Instant::compareTo)
                .orElse(Instant.now());
            pattern.endTime = Stream.concat(recentHighs.stream(), recentLows.stream())
                .map(p -> p.time)
                .max(Instant::compareTo)
                .orElse(Instant.now());
            
            // Target depends on breakout direction
            // Height can be used to calculate target once breakout direction is known
            BigDecimal apex = lastHigh.price.add(lastLow.price).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
            pattern.targetPrice = apex; // Will be updated on breakout
            
            if (!isPartOfActivePattern(state, pattern.pivots)) {
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
        }
    }
    
    /**
     * Detect Wedge patterns (Rising, Falling)
     */
    private void detectWedges(PatternState state, Map<String, Object> params) {
        // Similar to triangles but both lines slope in same direction
        if (state.pivotHighs.size() < 2 || state.pivotLows.size() < 2) return;
        
        List<Pivot> recentHighs = state.pivotHighs.subList(
            Math.max(0, state.pivotHighs.size() - 3),
            state.pivotHighs.size()
        );
        
        List<Pivot> recentLows = state.pivotLows.subList(
            Math.max(0, state.pivotLows.size() - 3),
            state.pivotLows.size()
        );
        
        if (recentHighs.size() < 2 || recentLows.size() < 2) return;
        
        Pivot firstHigh = recentHighs.get(0);
        Pivot lastHigh = recentHighs.get(recentHighs.size() - 1);
        Pivot firstLow = recentLows.get(0);
        Pivot lastLow = recentLows.get(recentLows.size() - 1);
        
        BigDecimal highSlope = lastHigh.price.subtract(firstHigh.price);
        BigDecimal lowSlope = lastLow.price.subtract(firstLow.price);
        
        // Rising Wedge: both slopes positive, converging (bearish)
        if (highSlope.compareTo(BigDecimal.ZERO) > 0 && 
            lowSlope.compareTo(BigDecimal.ZERO) > 0 &&
            lowSlope.compareTo(highSlope) > 0) {
            
            Pattern pattern = new Pattern("Rising Wedge", "bearish");
            pattern.pivots.addAll(recentHighs);
            pattern.pivots.addAll(recentLows);
            pattern.startTime = firstLow.time;
            pattern.endTime = lastHigh.time;
            
            BigDecimal height = lastHigh.price.subtract(firstLow.price);
            pattern.targetPrice = lastLow.price.subtract(height);
            
            if (!isPartOfActivePattern(state, pattern.pivots)) {
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
        }
        
        // Falling Wedge: both slopes negative, converging (bullish)
        else if (highSlope.compareTo(BigDecimal.ZERO) < 0 && 
                 lowSlope.compareTo(BigDecimal.ZERO) < 0 &&
                 lowSlope.compareTo(highSlope) < 0) {
            
            Pattern pattern = new Pattern("Falling Wedge", "bullish");
            pattern.pivots.addAll(recentHighs);
            pattern.pivots.addAll(recentLows);
            pattern.startTime = firstHigh.time;
            pattern.endTime = lastLow.time;
            
            BigDecimal height = firstHigh.price.subtract(lastLow.price);
            pattern.targetPrice = lastHigh.price.add(height);
            
            if (!isPartOfActivePattern(state, pattern.pivots)) {
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
        }
    }
    
    /**
     * Detect Flag patterns (continuation patterns after strong moves)
     */
    private void detectFlags(PatternState state, Map<String, Object> params) {
        if (state.candles.size() < 20) return;
        
        // Look for strong move followed by consolidation
        int lookback = Math.min(20, state.candles.size());
        List<CandlestickData> recent = state.candles.subList(
            state.candles.size() - lookback,
            state.candles.size()
        );
        
        // Calculate if there was a strong initial move
        BigDecimal firstPrice = recent.get(0).getClose();
        BigDecimal midPrice = recent.get(lookback / 2).getClose();
        BigDecimal lastPrice = recent.get(recent.size() - 1).getClose();
        
        BigDecimal poleMove = midPrice.subtract(firstPrice);
        BigDecimal polePct = poleMove.divide(firstPrice, 6, RoundingMode.HALF_UP).abs();
        
        // Need at least 3% move for the pole
        if (polePct.compareTo(BigDecimal.valueOf(0.03)) < 0) return;
        
        // Check if price is consolidating after the pole
        BigDecimal consolidationRange = recent.subList(lookback / 2, recent.size()).stream()
            .map(c -> c.getHigh().subtract(c.getLow()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgCandle = consolidationRange.divide(
            BigDecimal.valueOf(lookback / 2), 6, RoundingMode.HALF_UP
        );
        
        // If consolidation is tight relative to pole
        if (avgCandle.compareTo(poleMove.abs().multiply(BigDecimal.valueOf(0.3))) < 0) {
            
            // Bullish flag
            if (poleMove.compareTo(BigDecimal.ZERO) > 0) {
                Pattern pattern = new Pattern("Bullish Flag", "bullish");
                pattern.startTime = recent.get(0).getOpenTime();
                pattern.endTime = recent.get(recent.size() - 1).getOpenTime();
                pattern.targetPrice = lastPrice.add(poleMove);
                
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
            // Bearish flag
            else {
                Pattern pattern = new Pattern("Bearish Flag", "bearish");
                pattern.startTime = recent.get(0).getOpenTime();
                pattern.endTime = recent.get(recent.size() - 1).getOpenTime();
                pattern.targetPrice = lastPrice.add(poleMove);
                
                state.activePatterns.add(pattern);
                state.totalPatternsDetected++;
            }
        }
    }
    
    /**
     * Check if pivots are already part of an active pattern
     */
    private boolean isPartOfActivePattern(PatternState state, List<Pivot> pivots) {
        for (Pattern p : state.activePatterns) {
            for (Pivot pivot : pivots) {
                if (p.pivots.stream().anyMatch(pp -> pp.index == pivot.index)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Update active patterns to check for confirmation/invalidation
     */
    private void updateActivePatterns(PatternState state, CandlestickData candle, Map<String, Object> params) {
        Iterator<Pattern> iterator = state.activePatterns.iterator();
        
        while (iterator.hasNext()) {
            Pattern pattern = iterator.next();
            
            // Check if pattern is confirmed (breakout occurred)
            boolean confirmed = false;
            
            switch (pattern.type) {
                case "Double Top":
                case "Triple Top":
                case "Head and Shoulders":
                case "Descending Triangle":
                case "Rising Wedge":
                case "Bearish Flag":
                    // Bearish patterns: confirmed on break below support
                    BigDecimal support = pattern.pivots.stream()
                        .filter(p -> !p.isHigh)
                        .map(p -> p.price)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
                    if (candle.getClose().compareTo(support) < 0) {
                        confirmed = true;
                    }
                    break;
                    
                case "Double Bottom":
                case "Triple Bottom":
                case "Inverse Head and Shoulders":
                case "Ascending Triangle":
                case "Falling Wedge":
                case "Bullish Flag":
                    // Bullish patterns: confirmed on break above resistance
                    BigDecimal resistance = pattern.pivots.stream()
                        .filter(p -> p.isHigh)
                        .map(p -> p.price)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.valueOf(Double.MAX_VALUE));
                    if (candle.getClose().compareTo(resistance) > 0) {
                        confirmed = true;
                    }
                    break;
                    
                case "Symmetrical Triangle":
                    // Can break either direction
                    BigDecimal upperBound = pattern.pivots.stream()
                        .filter(p -> p.isHigh)
                        .map(p -> p.price)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.valueOf(Double.MAX_VALUE));
                    BigDecimal lowerBound = pattern.pivots.stream()
                        .filter(p -> !p.isHigh)
                        .map(p -> p.price)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
                    
                    if (candle.getClose().compareTo(upperBound) > 0) {
                        pattern.bias = "bullish";
                        confirmed = true;
                    } else if (candle.getClose().compareTo(lowerBound) < 0) {
                        pattern.bias = "bearish";
                        confirmed = true;
                    }
                    break;
            }
            
            // Check if pattern should be removed
            boolean shouldRemove = false;
            
            if (confirmed) {
                pattern.confirmed = true;
                pattern.active = false;
                state.confirmedPatterns.add(pattern);
                state.totalPatternsConfirmed++;
                shouldRemove = true;
            }
            // Remove old patterns (after maxPatternBars * 2) - only if not already removed
            else {
                int maxBars = getIntParameter(params, "maxPatternBars", 100);
                if (!pattern.pivots.isEmpty() && state.barIndex - pattern.pivots.get(0).index > maxBars * 2) {
                    pattern.active = false;
                    shouldRemove = true;
                }
            }
            
            // Remove from active list if needed (only once per iteration)
            if (shouldRemove) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Generate visualization shapes for patterns
     */
    private void generateVisualization(PatternState state, Map<String, Object> params) {
        // Clear previous shapes and create new collection
        state.shapes = new ShapeCollection();
        
        boolean showLabels = getBooleanParameter(params, "showLabels", true);
        
        // Draw active patterns
        for (Pattern pattern : state.activePatterns) {
            drawPattern(state, pattern, showLabels);
        }
        
        // Draw recently confirmed patterns (last 5)
        int confirmCount = Math.min(5, state.confirmedPatterns.size());
        for (int i = state.confirmedPatterns.size() - confirmCount; i < state.confirmedPatterns.size(); i++) {
            Pattern pattern = state.confirmedPatterns.get(i);
            drawPattern(state, pattern, showLabels);
        }
    }
    
    /**
     * Draw a single pattern with proper trendlines
     */
    private void drawPattern(PatternState state, Pattern pattern, boolean showLabels) {
        if (pattern.pivots.isEmpty()) return;
        
        String color = getPatternColor(pattern);
        String lineStyle = pattern.confirmed ? "solid" : "dashed";
        int lineWidth = 2;
        
        // Draw pattern-specific trendlines
        switch (pattern.type) {
            case "Head and Shoulders":
            case "Inverse Head and Shoulders":
                drawHeadAndShouldersLines(state, pattern, color, lineStyle, lineWidth);
                break;
                
            case "Double Top":
            case "Double Bottom":
                drawDoubleTopBottomLines(state, pattern, color, lineStyle, lineWidth);
                break;
                
            case "Triple Top":
            case "Triple Bottom":
                drawTripleTopBottomLines(state, pattern, color, lineStyle, lineWidth);
                break;
                
            case "Ascending Triangle":
            case "Descending Triangle":
            case "Symmetrical Triangle":
                drawTriangleLines(state, pattern, color, lineStyle, lineWidth);
                break;
                
            case "Rising Wedge":
            case "Falling Wedge":
                drawWedgeLines(state, pattern, color, lineStyle, lineWidth);
                break;
                
            case "Bullish Flag":
            case "Bearish Flag":
                drawFlagLines(state, pattern, color, lineStyle, lineWidth);
                break;
                
            default:
                // For unknown patterns, just connect pivots
                drawConnectedPivots(state, pattern, color, lineStyle, lineWidth);
                break;
        }
        
        // Draw pivot markers (smaller, subtle)
        for (Pivot pivot : pattern.pivots) {
            state.shapes.addMarker(MarkerShape.builder()
                .time(pivot.time.getEpochSecond())
                .price(pivot.price)
                .shape("circle")
                .color(color)
                .position(pivot.isHigh ? "above" : "below")
                .size(4)
                .opacity(0.7)
                .build());
        }
        
        // Draw label at the start of pattern
        if (showLabels) {
            Pivot firstPivot = pattern.pivots.get(0);
            BigDecimal labelPrice = pattern.pivots.stream()
                .map(p -> p.price)
                .max(BigDecimal::compareTo)
                .orElse(firstPivot.price);
            
            // Add some spacing above the pattern
            BigDecimal priceRange = pattern.pivots.stream()
                .map(p -> p.price)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .subtract(pattern.pivots.stream()
                    .map(p -> p.price)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO));
            labelPrice = labelPrice.add(priceRange.multiply(BigDecimal.valueOf(0.02)));
            
            state.shapes.addMarker(MarkerShape.builder()
                .time(firstPivot.time.getEpochSecond())
                .price(labelPrice)
                .shape("square")
                .color(color)
                .position("above")
                .text(pattern.type + (pattern.confirmed ? " ✓" : ""))
                .size(8)
                .build());
        }
    }
    
    /**
     * Draw Head and Shoulders pattern lines
     */
    private void drawHeadAndShouldersLines(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        if (pattern.pivots.size() < 5) return;
        
        // Head and Shoulders: 5 pivots - LS, L1, H, L2, RS
        Pivot leftShoulder = pattern.pivots.get(0);
        Pivot leftLow = pattern.pivots.get(1);
        Pivot head = pattern.pivots.get(2);
        Pivot rightLow = pattern.pivots.get(3);
        Pivot rightShoulder = pattern.pivots.get(4);
        
        // Draw neckline (support line through the lows)
        state.shapes.addLine(LineShape.builder()
            .time1(leftLow.time.getEpochSecond())
            .price1(leftLow.price)
            .time2(rightLow.time.getEpochSecond())
            .price2(rightLow.price)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .label("Neckline")
            .build());
        
        // Draw shoulder line (resistance through shoulders)
        state.shapes.addLine(LineShape.builder()
            .time1(leftShoulder.time.getEpochSecond())
            .price1(leftShoulder.price)
            .time2(rightShoulder.time.getEpochSecond())
            .price2(rightShoulder.price)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .build());
        
        // Draw lines to head
        state.shapes.addLine(LineShape.builder()
            .time1(leftShoulder.time.getEpochSecond())
            .price1(leftShoulder.price)
            .time2(head.time.getEpochSecond())
            .price2(head.price)
            .color(color)
            .lineWidth(1)
            .lineStyle("dotted")
            .build());
        
        state.shapes.addLine(LineShape.builder()
            .time1(head.time.getEpochSecond())
            .price1(head.price)
            .time2(rightShoulder.time.getEpochSecond())
            .price2(rightShoulder.price)
            .color(color)
            .lineWidth(1)
            .lineStyle("dotted")
            .build());
    }
    
    /**
     * Draw Double Top/Bottom pattern lines
     */
    private void drawDoubleTopBottomLines(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        if (pattern.pivots.size() < 3) return;
        
        Pivot first = pattern.pivots.get(0);
        Pivot middle = pattern.pivots.get(1);
        Pivot second = pattern.pivots.get(2);
        
        // Draw resistance/support line through the peaks/valleys
        state.shapes.addLine(LineShape.builder()
            .time1(first.time.getEpochSecond())
            .price1(first.price)
            .time2(second.time.getEpochSecond())
            .price2(second.price)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .label(pattern.type.contains("Top") ? "Resistance" : "Support")
            .build());
        
        // Draw neckline (valley/peak between)
        long extendedTime = second.time.getEpochSecond() + (second.time.getEpochSecond() - first.time.getEpochSecond()) / 2;
        state.shapes.addLine(LineShape.builder()
            .time1(first.time.getEpochSecond())
            .price1(middle.price)
            .time2(extendedTime)
            .price2(middle.price)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .label("Neckline")
            .build());
    }
    
    /**
     * Draw Triple Top/Bottom pattern lines
     */
    private void drawTripleTopBottomLines(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        if (pattern.pivots.size() < 3) return;
        
        List<Pivot> peaks = pattern.pivots;
        Pivot first = peaks.get(0);
        Pivot last = peaks.get(peaks.size() - 1);
        
        // Draw resistance/support line through all three peaks/valleys
        state.shapes.addLine(LineShape.builder()
            .time1(first.time.getEpochSecond())
            .price1(first.price)
            .time2(last.time.getEpochSecond())
            .price2(last.price)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .label(pattern.type.contains("Top") ? "Triple Resistance" : "Triple Support")
            .build());
    }
    
    /**
     * Draw Triangle pattern lines (converging trendlines)
     */
    private void drawTriangleLines(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        if (pattern.pivots.size() < 4) return;
        
        // Separate highs and lows
        List<Pivot> highs = pattern.pivots.stream()
            .filter(p -> p.isHigh)
            .collect(Collectors.toList());
        List<Pivot> lows = pattern.pivots.stream()
            .filter(p -> !p.isHigh)
            .collect(Collectors.toList());
        
        if (highs.size() >= 2 && lows.size() >= 2) {
            Pivot firstHigh = highs.get(0);
            Pivot lastHigh = highs.get(highs.size() - 1);
            Pivot firstLow = lows.get(0);
            Pivot lastLow = lows.get(lows.size() - 1);
            
            // Extend lines to show convergence
            long duration = lastHigh.time.getEpochSecond() - firstHigh.time.getEpochSecond();
            long extendTime = lastHigh.time.getEpochSecond() + duration / 2;
            
            // Calculate extended prices
            BigDecimal highSlope = lastHigh.price.subtract(firstHigh.price)
                .divide(BigDecimal.valueOf(duration), 10, RoundingMode.HALF_UP);
            BigDecimal lowSlope = lastLow.price.subtract(firstLow.price)
                .divide(BigDecimal.valueOf(duration), 10, RoundingMode.HALF_UP);
            
            BigDecimal extendedHighPrice = lastHigh.price.add(
                highSlope.multiply(BigDecimal.valueOf(duration / 2))
            );
            BigDecimal extendedLowPrice = lastLow.price.add(
                lowSlope.multiply(BigDecimal.valueOf(duration / 2))
            );
            
            // Draw upper trendline
            state.shapes.addLine(LineShape.builder()
                .time1(firstHigh.time.getEpochSecond())
                .price1(firstHigh.price)
                .time2(extendTime)
                .price2(extendedHighPrice)
                .color(color)
                .lineWidth(lineWidth)
                .lineStyle(lineStyle)
                .label("Resistance")
                .build());
            
            // Draw lower trendline
            state.shapes.addLine(LineShape.builder()
                .time1(firstLow.time.getEpochSecond())
                .price1(firstLow.price)
                .time2(extendTime)
                .price2(extendedLowPrice)
                .color(color)
                .lineWidth(lineWidth)
                .lineStyle(lineStyle)
                .label("Support")
                .build());
        }
    }
    
    /**
     * Draw Wedge pattern lines (converging but both sloping same direction)
     */
    private void drawWedgeLines(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        // Wedges are drawn similarly to triangles
        drawTriangleLines(state, pattern, color, lineStyle, lineWidth);
    }
    
    /**
     * Draw Flag pattern lines (parallel channel after strong move)
     */
    private void drawFlagLines(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        if (pattern.pivots.isEmpty()) return;
        
        // For flags, just draw a box showing the consolidation range
        long startTime = pattern.startTime.getEpochSecond();
        long endTime = pattern.endTime.getEpochSecond();
        
        // Calculate high and low of flag
        BigDecimal flagHigh = state.candles.stream()
            .filter(c -> c.getOpenTime().getEpochSecond() >= startTime && 
                        c.getOpenTime().getEpochSecond() <= endTime)
            .map(CandlestickData::getHigh)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal flagLow = state.candles.stream()
            .filter(c -> c.getOpenTime().getEpochSecond() >= startTime && 
                        c.getOpenTime().getEpochSecond() <= endTime)
            .map(CandlestickData::getLow)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        // Draw upper boundary
        state.shapes.addLine(LineShape.builder()
            .time1(startTime)
            .price1(flagHigh)
            .time2(endTime)
            .price2(flagHigh)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .build());
        
        // Draw lower boundary
        state.shapes.addLine(LineShape.builder()
            .time1(startTime)
            .price1(flagLow)
            .time2(endTime)
            .price2(flagLow)
            .color(color)
            .lineWidth(lineWidth)
            .lineStyle(lineStyle)
            .build());
    }
    
    /**
     * Draw simple connected pivots for generic patterns
     */
    private void drawConnectedPivots(PatternState state, Pattern pattern, String color, String lineStyle, int lineWidth) {
        for (int i = 0; i < pattern.pivots.size() - 1; i++) {
            Pivot p1 = pattern.pivots.get(i);
            Pivot p2 = pattern.pivots.get(i + 1);
            
            state.shapes.addLine(LineShape.builder()
                .time1(p1.time.getEpochSecond())
                .price1(p1.price)
                .time2(p2.time.getEpochSecond())
                .price2(p2.price)
                .color(color)
                .lineWidth(lineWidth)
                .lineStyle(lineStyle)
                .build());
        }
    }
    
    /**
     * Get color based on pattern bias
     */
    private String getPatternColor(Pattern pattern) {
        if (pattern.confirmed) {
            return pattern.bias.equals("bullish") ? "#00FF00" : "#FF0000";
        }
        
        switch (pattern.bias) {
            case "bullish": return "#4CAF50";
            case "bearish": return "#F44336";
            case "neutral": return "#2196F3";
            default: return "#9E9E9E";
        }
    }
    
    /**
     * Helper to get double parameter
     */
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
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // This indicator uses shapes for visualization
        metadata.put("activePatterns", IndicatorMetadata.builder("activePatterns")
            .displayName("Active Patterns")
            .seriesType("line")
            .addConfig("color", "#2196F3")
            .addConfig("lineWidth", 0) // Don't draw line, just use shapes
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        int pivotStrength = getIntParameter(params, "pivotStrength", 5);
        // Need at least enough candles to detect pivots
        return pivotStrength * 2 + 10;
    }
}

