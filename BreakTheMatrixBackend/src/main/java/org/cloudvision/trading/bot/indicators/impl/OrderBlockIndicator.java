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
 * Order Block Indicator
 * 
 * Detects institutional order blocks based on volume pivots and market structure.
 * Order blocks are zones where institutional traders have placed significant orders.
 * 
 * - Bullish Order Blocks: Form during downtrends at volume pivots (support zones)
 * - Bearish Order Blocks: Form during uptrends at volume pivots (resistance zones)
 * 
 * ALGORITHM:
 * 1. Detect market structure (uptrend vs downtrend) by tracking higher highs/lower lows
 * 2. Identify volume pivots (local volume highs indicating institutional activity)
 * 3. Create order blocks at volume pivots:
 *    - Bullish OB in downtrend: support zone from low to HL2
 *    - Bearish OB in uptrend: resistance zone from HL2 to high
 * 4. Track volume strength (pivot volume / average volume)
 * 5. Mark OBs as "mitigated" when price breaks through them
 * 
 * OUTPUT VALUES (calculate method):
 * - marketStructure: 0 (uptrend) or 1 (downtrend)
 * - bullishOBTop: Top of most recent active bullish order block
 * - bullishOBBottom: Bottom of most recent active bullish order block
 * - bearishOBTop: Top of most recent active bearish order block
 * - bearishOBBottom: Bottom of most recent active bearish order block
 * - volumeStrength: Volume strength at most recent pivot
 * - activeBullishOBs: Count of active bullish order blocks
 * - activeBearishOBs: Count of active bearish order blocks
 * 
 * OUTPUT (calculateProgressive method):
 * Returns formatted BOXES for visualization with time/price coordinates, colors, and labels.
 * Use this method for charting and visual display of order blocks.
 */
@Component
public class OrderBlockIndicator extends AbstractIndicator {
    
    // Order Block data class
    public static class OrderBlock {
        public BigDecimal top;
        public BigDecimal bottom;
        public BigDecimal average;
        public Instant timestamp;
        public int barIndex;
        public BigDecimal volumeStrength;
        public boolean touched;
        public boolean mitigated;
        public Instant mitigationTime;
        
        public OrderBlock(BigDecimal top, BigDecimal bottom, Instant timestamp, int barIndex, BigDecimal volumeStrength) {
            this.top = top;
            this.bottom = bottom;
            this.average = top.add(bottom).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
            this.timestamp = timestamp;
            this.barIndex = barIndex;
            this.volumeStrength = volumeStrength;
            this.touched = false;
            this.mitigated = false;
            this.mitigationTime = null;
        }
    }
    
    // State for progressive calculation (used in calculateProgressive)
    public static class OrderBlockState {
        public List<OrderBlock> bullishOrderBlocks = new ArrayList<>();
        public List<OrderBlock> bearishOrderBlocks = new ArrayList<>();
        public int marketStructure = 0; // 0 = uptrend, 1 = downtrend
        public int barIndex = 0;
    }
    
    public OrderBlockIndicator() {
        super(
            "orderblock",
            "Order Block",
            "Detects institutional order blocks based on volume pivots and market structure. Shows support/resistance zones where large orders were placed.",
            IndicatorCategory.VOLUME
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("volumePivotLength", IndicatorParameter.builder("volumePivotLength")
            .displayName("Volume Pivot Length")
            .description("Number of bars on each side to detect volume pivot highs")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(2)
            .maxValue(20)
            .required(true)
            .build());
        
        params.put("maxBullishOrderBlocks", IndicatorParameter.builder("maxBullishOrderBlocks")
            .displayName("Max Bullish Order Blocks")
            .description("Maximum number of active bullish order blocks to display")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(1)
            .maxValue(10)
            .required(false)
            .build());
        
        params.put("maxBearishOrderBlocks", IndicatorParameter.builder("maxBearishOrderBlocks")
            .displayName("Max Bearish Order Blocks")
            .description("Maximum number of active bearish order blocks to display")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(1)
            .maxValue(10)
            .required(false)
            .build());
        
        params.put("mitigationMethod", IndicatorParameter.builder("mitigationMethod")
            .displayName("Mitigation Method")
            .description("Method to detect order block invalidation: Wick (more sensitive) or Close (more conservative)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Wick")
            .required(false)
            .build());
        
        params.put("showMitigatedBoxes", IndicatorParameter.builder("showMitigatedBoxes")
            .displayName("Show Mitigated Boxes")
            .description("Display mitigated (historical) order blocks in gray")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("bullishColor", IndicatorParameter.builder("bullishColor")
            .displayName("Bullish Order Block Color")
            .description("Color for bullish order blocks (support zones)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(22, 148, 0, 0.15)")
            .required(false)
            .build());
        
        params.put("bearishColor", IndicatorParameter.builder("bearishColor")
            .displayName("Bearish Order Block Color")
            .description("Color for bearish order blocks (resistance zones)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(255, 17, 0, 0.15)")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int volumePivotLength = getIntParameter(params, "volumePivotLength", 5);
        String mitigationMethod = getStringParameter(params, "mitigationMethod", "Wick");
        
        if (candles == null || candles.size() < volumePivotLength * 2 + 1) {
            return createEmptyResult();
        }
        
        // Extract price data
        List<BigDecimal> highs = candles.stream().map(CandlestickData::getHigh).collect(Collectors.toList());
        List<BigDecimal> lows = candles.stream().map(CandlestickData::getLow).collect(Collectors.toList());
        List<BigDecimal> closes = candles.stream().map(CandlestickData::getClose).collect(Collectors.toList());
        List<BigDecimal> volumes = candles.stream().map(CandlestickData::getVolume).collect(Collectors.toList());
        List<Instant> timestamps = candles.stream().map(CandlestickData::getCloseTime).collect(Collectors.toList());
        
        // Calculate market structure
        int marketStructure = updateMarketStructure(highs, lows, volumePivotLength);
        
        // Detect volume pivot
        BigDecimal pivotVolume = detectVolumePivot(volumes, volumePivotLength);
        BigDecimal volumeStrength = BigDecimal.ZERO;
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("marketStructure", new BigDecimal(marketStructure));
        
        if (pivotVolume != null) {
            int pivotIndex = volumePivotLength;
            volumeStrength = calculateVolumeStrength(volumes, pivotIndex);
            result.put("volumeStrength", volumeStrength);
            
            // Check if we detected a new order block
            if (marketStructure == 1) { // Downtrend - Bullish OB
                BigDecimal top = getHL2(highs, lows, pivotIndex);
                BigDecimal bottom = lows.get(lows.size() - 1 - pivotIndex);
                result.put("bullishOBTop", top);
                result.put("bullishOBBottom", bottom);
            } else if (marketStructure == 0) { // Uptrend - Bearish OB
                BigDecimal top = highs.get(highs.size() - 1 - pivotIndex);
                BigDecimal bottom = getHL2(highs, lows, pivotIndex);
                result.put("bearishOBTop", top);
                result.put("bearishOBBottom", bottom);
            }
        }
        
        // Add default values for missing keys
        result.putIfAbsent("volumeStrength", BigDecimal.ZERO);
        result.putIfAbsent("bullishOBTop", BigDecimal.ZERO);
        result.putIfAbsent("bullishOBBottom", BigDecimal.ZERO);
        result.putIfAbsent("bearishOBTop", BigDecimal.ZERO);
        result.putIfAbsent("bearishOBBottom", BigDecimal.ZERO);
        result.put("activeBullishOBs", BigDecimal.ZERO);
        result.put("activeBearishOBs", BigDecimal.ZERO);
        
        return result;
    }
    
    /**
     * Progressive calculation that maintains state across candles
     * This is the recommended method for real-time or historical sequential processing
     * 
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with indicator values (marketStructure, volumeStrength, etc.)
     *   - "state": OrderBlockState for next iteration (internal use only)
     *   - "boxes": List<Map<String, Object>> formatted boxes for visualization (time1/2, price1/2, colors, text)
     */
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                    Map<String, Object> params,
                                                    Object previousState) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int volumePivotLength = getIntParameter(params, "volumePivotLength", 5);
        int maxBullishOBs = getIntParameter(params, "maxBullishOrderBlocks", 3);
        int maxBearishOBs = getIntParameter(params, "maxBearishOrderBlocks", 3);
        String mitigationMethod = getStringParameter(params, "mitigationMethod", "Wick");
        
        // Cast previousState to OrderBlockState (or create new if null)
        OrderBlockState state = (previousState instanceof OrderBlockState) 
            ? (OrderBlockState) previousState 
            : new OrderBlockState();
        
        if (candles == null || candles.size() < volumePivotLength * 2 + 1) {
            return Map.of(
                "values", createEmptyResult(),
                "state", state,
                "boxes", new ArrayList<>()
            );
        }
        
        // Extract price data
        List<BigDecimal> highs = candles.stream().map(CandlestickData::getHigh).collect(Collectors.toList());
        List<BigDecimal> lows = candles.stream().map(CandlestickData::getLow).collect(Collectors.toList());
        List<BigDecimal> closes = candles.stream().map(CandlestickData::getClose).collect(Collectors.toList());
        List<BigDecimal> volumes = candles.stream().map(CandlestickData::getVolume).collect(Collectors.toList());
        List<Instant> timestamps = candles.stream().map(CandlestickData::getCloseTime).collect(Collectors.toList());
        
        CandlestickData currentCandle = candles.get(candles.size() - 1);
        state.barIndex++;
        
        // Update market structure
        state.marketStructure = updateMarketStructure(highs, lows, volumePivotLength);
        
        // Detect volume pivot
        BigDecimal pivotVolume = detectVolumePivot(volumes, volumePivotLength);
        BigDecimal volumeStrength = BigDecimal.ZERO;
        
        if (pivotVolume != null) {
            int pivotIndex = volumePivotLength;
            volumeStrength = calculateVolumeStrength(volumes, pivotIndex);
            Instant obTimestamp = timestamps.get(timestamps.size() - 1 - pivotIndex);
            
            // Create new order block
            if (state.marketStructure == 1) { // Downtrend - Bullish OB
                BigDecimal top = getHL2(highs, lows, pivotIndex);
                BigDecimal bottom = lows.get(lows.size() - 1 - pivotIndex);
                OrderBlock ob = new OrderBlock(top, bottom, obTimestamp, state.barIndex - pivotIndex, volumeStrength);
                addOrderBlock(state.bullishOrderBlocks, ob, maxBullishOBs);
            } else if (state.marketStructure == 0) { // Uptrend - Bearish OB
                BigDecimal top = highs.get(highs.size() - 1 - pivotIndex);
                BigDecimal bottom = getHL2(highs, lows, pivotIndex);
                OrderBlock ob = new OrderBlock(top, bottom, obTimestamp, state.barIndex - pivotIndex, volumeStrength);
                addOrderBlock(state.bearishOrderBlocks, ob, maxBearishOBs);
            }
        }
        
        // Check for mitigation
        BigDecimal targetPrice = "Wick".equals(mitigationMethod) ? currentCandle.getLow() : currentCandle.getClose();
        checkMitigation(state.bullishOrderBlocks, targetPrice, true);
        
        targetPrice = "Wick".equals(mitigationMethod) ? currentCandle.getHigh() : currentCandle.getClose();
        checkMitigation(state.bearishOrderBlocks, targetPrice, false);
        
        // Check for touched order blocks
        markTouchedOrderBlocks(state.bullishOrderBlocks, currentCandle, true);
        markTouchedOrderBlocks(state.bearishOrderBlocks, currentCandle, false);
        
        // Build result
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("marketStructure", new BigDecimal(state.marketStructure));
        values.put("volumeStrength", volumeStrength);
        
        // Get most recent active order blocks
        OrderBlock activeBullOB = getActiveOrderBlock(state.bullishOrderBlocks);
        OrderBlock activeBearOB = getActiveOrderBlock(state.bearishOrderBlocks);
        
        values.put("bullishOBTop", activeBullOB != null ? activeBullOB.top : BigDecimal.ZERO);
        values.put("bullishOBBottom", activeBullOB != null ? activeBullOB.bottom : BigDecimal.ZERO);
        values.put("bearishOBTop", activeBearOB != null ? activeBearOB.top : BigDecimal.ZERO);
        values.put("bearishOBBottom", activeBearOB != null ? activeBearOB.bottom : BigDecimal.ZERO);
        
        long activeBullCount = state.bullishOrderBlocks.stream().filter(ob -> !ob.mitigated).count();
        long activeBearCount = state.bearishOrderBlocks.stream().filter(ob -> !ob.mitigated).count();
        
        values.put("activeBullishOBs", new BigDecimal(activeBullCount));
        values.put("activeBearishOBs", new BigDecimal(activeBearCount));
        
        // Convert order blocks to BoxShape objects for visualization
        boolean showMitigated = getBooleanParameter(params, "showMitigatedBoxes", false);
        String bullishColor = getStringParameter(params, "bullishColor", "rgba(22, 148, 0, 0.15)");
        String bearishColor = getStringParameter(params, "bearishColor", "rgba(255, 17, 0, 0.15)");
        Instant currentTime = currentCandle.getCloseTime();
        
        List<BoxShape> boxShapes = new ArrayList<>();
        
        // Convert bullish order blocks to boxes
        for (OrderBlock ob : state.bullishOrderBlocks) {
            if (ob.mitigated && !showMitigated) continue;
            boxShapes.add(convertToBox(ob, currentTime, bullishColor, true));
        }
        
        // Convert bearish order blocks to boxes
        for (OrderBlock ob : state.bearishOrderBlocks) {
            if (ob.mitigated && !showMitigated) continue;
            boxShapes.add(convertToBox(ob, currentTime, bearishColor, false));
        }
        
        // Convert BoxShape objects to Map for API serialization
        List<Map<String, Object>> boxes = boxShapes.stream()
            .map(BoxShape::toMap)
            .collect(Collectors.toList());
        
        // Return formatted boxes for visualization
        Map<String, Object> output = new HashMap<>();
        output.put("values", values);
        output.put("state", state);
        output.put("boxes", boxes); // Formatted boxes ready for charting
        
        return output;
    }
    
    /**
     * Convert OrderBlock to BoxShape for visualization
     */
    private BoxShape convertToBox(OrderBlock ob, Instant currentTime, 
                                  String color, boolean isBullish) {
        // Calculate end time
        long endTime;
        if (ob.mitigated && ob.mitigationTime != null) {
            endTime = ob.mitigationTime.getEpochSecond();
        } else {
            // Active blocks: extend to current time
            endTime = currentTime.getEpochSecond();
        }
        
        // Style based on mitigation status
        String backgroundColor;
        String label;
        if (ob.mitigated) {
            // Mitigated: Gray with very low opacity
            backgroundColor = "rgba(128, 128, 128, 0.08)";
            label = (isBullish ? "Bullish" : "Bearish") + " OB (Mitigated)";
        } else {
            // Active: Use provided color
            backgroundColor = color;
            String touchedMark = ob.touched ? " ✓" : "";
            label = (isBullish ? "Bullish" : "Bearish") + " OB" + touchedMark;
        }
        
        // Extract border color from background color or use a default
        String borderColor = color.replace("rgba", "rgb").replace(", 0.15)", ", 1.0)");
        if (ob.mitigated) {
            borderColor = "rgba(128, 128, 128, 0.3)";
        }
        
        return BoxShape.builder()
            .time1(ob.timestamp.getEpochSecond())
            .time2(endTime)
            .price1(ob.top)
            .price2(ob.bottom)
            .color(backgroundColor)
            .borderColor(borderColor)
            .label(label)
            .build();
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        int volumePivotLength = getIntParameter(params, "volumePivotLength", 5);
        String bullishColor = getStringParameter(params, "bullishColor", "rgba(22, 148, 0, 0.15)");
        String bearishColor = getStringParameter(params, "bearishColor", "rgba(255, 17, 0, 0.15)");
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Market structure indicator (as histogram in separate pane)
        metadata.put("marketStructure", IndicatorMetadata.builder("marketStructure")
            .displayName("Market Structure")
            .asHistogram("#2962FF")
            .addConfig("downColor", "#FF6D00")
            .addConfig("baseValue", 0.5)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        // Note: Order blocks themselves are rendered as boxes/rectangles
        // which requires special handling in the frontend
        // The actual box data is provided through the calculateProgressive method
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int volumePivotLength = getIntParameter(params, "volumePivotLength", 5);
        return volumePivotLength * 2 + 1;
    }
    
    // ============ Helper Methods ============
    
    private Map<String, BigDecimal> createEmptyResult() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("marketStructure", BigDecimal.ZERO);
        result.put("volumeStrength", BigDecimal.ZERO);
        result.put("bullishOBTop", BigDecimal.ZERO);
        result.put("bullishOBBottom", BigDecimal.ZERO);
        result.put("bearishOBTop", BigDecimal.ZERO);
        result.put("bearishOBBottom", BigDecimal.ZERO);
        result.put("activeBullishOBs", BigDecimal.ZERO);
        result.put("activeBearishOBs", BigDecimal.ZERO);
        return result;
    }
    
    private int updateMarketStructure(List<BigDecimal> highs, List<BigDecimal> lows, int volumePivotLength) {
        int size = highs.size();
        if (size < volumePivotLength + 1) return 0;
        
        BigDecimal currentHigh = highs.get(size - 1 - volumePivotLength);
        BigDecimal currentLow = lows.get(size - 1 - volumePivotLength);
        
        BigDecimal upper = calculateHighest(highs, volumePivotLength, volumePivotLength);
        BigDecimal lower = calculateLowest(lows, volumePivotLength, volumePivotLength);
        
        // Default to uptrend
        int os = 0;
        if (currentHigh.compareTo(upper) > 0) {
            os = 0; // Uptrend
        } else if (currentLow.compareTo(lower) < 0) {
            os = 1; // Downtrend
        }
        
        return os;
    }
    
    private BigDecimal detectVolumePivot(List<BigDecimal> volumes, int length) {
        int size = volumes.size();
        if (size < length * 2 + 1) return null;
        
        int centerIndex = size - 1 - length;
        BigDecimal centerVolume = volumes.get(centerIndex);
        
        // Check left side
        for (int i = 1; i <= length; i++) {
            if (volumes.get(centerIndex - i).compareTo(centerVolume) >= 0) {
                return null;
            }
        }
        
        // Check right side
        for (int i = 1; i <= length; i++) {
            if (volumes.get(centerIndex + i).compareTo(centerVolume) >= 0) {
                return null;
            }
        }
        
        return centerVolume;
    }
    
    private BigDecimal getHL2(List<BigDecimal> highs, List<BigDecimal> lows, int offset) {
        int index = highs.size() - 1 - offset;
        BigDecimal high = highs.get(index);
        BigDecimal low = lows.get(index);
        return high.add(low).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateHighest(List<BigDecimal> values, int period, int offset) {
        int size = values.size();
        int startIdx = size - 1 - offset - period;
        if (startIdx < 0) startIdx = 0;
        
        BigDecimal max = values.get(startIdx);
        for (int i = 0; i < period && (startIdx + i) < size; i++) {
            BigDecimal val = values.get(startIdx + i);
            if (val.compareTo(max) > 0) {
                max = val;
            }
        }
        return max;
    }
    
    private BigDecimal calculateLowest(List<BigDecimal> values, int period, int offset) {
        int size = values.size();
        int startIdx = size - 1 - offset - period;
        if (startIdx < 0) startIdx = 0;
        
        BigDecimal min = values.get(startIdx);
        for (int i = 0; i < period && (startIdx + i) < size; i++) {
            BigDecimal val = values.get(startIdx + i);
            if (val.compareTo(min) < 0) {
                min = val;
            }
        }
        return min;
    }
    
    private BigDecimal calculateVolumeStrength(List<BigDecimal> volumes, int pivotIndex) {
        int size = volumes.size();
        int pivotIdx = size - 1 - pivotIndex;
        BigDecimal pivotVolume = volumes.get(pivotIdx);
        
        BigDecimal sumVolume = BigDecimal.ZERO;
        int period = Math.min(20, size - 1);
        
        for (int i = 0; i < period; i++) {
            sumVolume = sumVolume.add(volumes.get(size - 1 - i));
        }
        
        BigDecimal avgVolume = sumVolume.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        
        if (avgVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return pivotVolume.divide(avgVolume, 8, RoundingMode.HALF_UP);
    }
    
    private void addOrderBlock(List<OrderBlock> blocks, OrderBlock ob, int maxBlocks) {
        blocks.add(0, ob);
        
        // Count active blocks
        long activeCount = blocks.stream().filter(block -> !block.mitigated).count();
        
        // Remove oldest active blocks if we exceed the limit
        if (activeCount > maxBlocks) {
            for (int i = blocks.size() - 1; i >= 0 && activeCount > maxBlocks; i--) {
                if (!blocks.get(i).mitigated) {
                    blocks.remove(i);
                    activeCount--;
                }
            }
        }
    }
    
    private void checkMitigation(List<OrderBlock> blocks, BigDecimal targetPrice, boolean isBullish) {
        for (OrderBlock ob : blocks) {
            if (!ob.mitigated) {
                boolean mitigated = isBullish ? 
                    targetPrice.compareTo(ob.bottom) < 0 : 
                    targetPrice.compareTo(ob.top) > 0;
                
                if (mitigated) {
                    ob.mitigated = true;
                    ob.mitigationTime = Instant.now();
                }
            }
        }
    }
    
    private void markTouchedOrderBlocks(List<OrderBlock> blocks, CandlestickData candle, boolean isBullish) {
        BigDecimal currentPrice = candle.getClose();
        
        for (OrderBlock ob : blocks) {
            if (ob.mitigated) continue;
            
            boolean touching = currentPrice.compareTo(ob.bottom) >= 0 && currentPrice.compareTo(ob.top) <= 0;
            
            // Check if wick touched the zone
            if (!touching) {
                if (isBullish) {
                    touching = candle.getLow().compareTo(ob.bottom) <= 0 && candle.getHigh().compareTo(ob.bottom) >= 0;
                } else {
                    touching = candle.getHigh().compareTo(ob.top) >= 0 && candle.getLow().compareTo(ob.top) <= 0;
                }
            }
            
            if (touching && !ob.touched) {
                ob.touched = true;
            }
        }
    }
    
    private OrderBlock getActiveOrderBlock(List<OrderBlock> blocks) {
        return blocks.stream()
            .filter(ob -> !ob.mitigated)
            .findFirst()
            .orElse(null);
    }
}

