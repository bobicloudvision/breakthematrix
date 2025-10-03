package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * ZigZag Indicator
 * 
 * Filters out small price movements to identify significant trend reversals.
 * Connects swing highs and swing lows that exceed a minimum percentage change threshold.
 * 
 * The ZigZag indicator:
 * - Eliminates random price fluctuations
 * - Helps identify chart patterns
 * - Shows significant trend changes
 * - Useful for Elliott Wave analysis and support/resistance identification
 * 
 * Note: The ZigZag is a lagging indicator - the last segment may repaint until confirmed.
 */
@Component
public class ZigZagIndicator extends AbstractIndicator {
    
    public ZigZagIndicator() {
        super(
            "zigzag",
            "ZigZag",
            "Identifies significant price swings by filtering out movements smaller than a specified percentage. Connects pivot highs and lows to show major trend changes.",
            IndicatorCategory.OVERLAY
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("deviation", IndicatorParameter.builder("deviation")
            .displayName("Deviation (%)")
            .description("Minimum percentage change required to form a new ZigZag line. Higher values = fewer, larger swings.")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(5.0)
            .minValue(0.1)
            .maxValue(50.0)
            .required(true)
            .build());
        
        params.put("depth", IndicatorParameter.builder("depth")
            .displayName("Depth")
            .description("Minimum number of candles between pivot points. Prevents too-frequent reversals.")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(12)
            .minValue(1)
            .maxValue(100)
            .required(false)
            .build());
        
        params.put("source", IndicatorParameter.builder("source")
            .displayName("Price Source")
            .description("Price type to use: 'high-low' (default) uses highs for peaks and lows for troughs, 'close' uses only closing prices")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("high-low")
            .required(false)
            .build());
        
        params.put("color", IndicatorParameter.builder("color")
            .displayName("Line Color")
            .description("Color for the ZigZag lines")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#2962FF")
            .required(false)
            .build());
        
        params.put("lineWidth", IndicatorParameter.builder("lineWidth")
            .displayName("Line Width")
            .description("Width of the ZigZag lines")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(2)
            .minValue(1)
            .maxValue(10)
            .required(false)
            .build());
        
        params.put("showLabels", IndicatorParameter.builder("showLabels")
            .displayName("Show Labels")
            .description("Display percentage change labels at pivot points")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        if (candles == null || candles.isEmpty()) {
            return Map.of("zigzag", BigDecimal.ZERO);
        }
        
        List<ZigZagPoint> pivots = calculateZigZagPoints(candles, params);
        
        if (pivots.isEmpty()) {
            return Map.of("zigzag", BigDecimal.ZERO);
        }
        
        // Return the most recent pivot value
        ZigZagPoint lastPivot = pivots.get(pivots.size() - 1);
        return Map.of("zigzag", lastPivot.price);
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                    Map<String, Object> params,
                                                    Object previousState) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        if (candles == null || candles.isEmpty()) {
            return Map.of(
                "values", Map.of("zigzag", BigDecimal.ZERO),
                "state", previousState != null ? previousState : new Object(),
                "points", new ArrayList<>()
            );
        }
        
        // Calculate zigzag points
        List<ZigZagPoint> pivots = calculateZigZagPoints(candles, params);
        
        BigDecimal currentValue = BigDecimal.ZERO;
        if (!pivots.isEmpty()) {
            currentValue = pivots.get(pivots.size() - 1).price;
        }
        
        // Convert to serializable format for frontend
        List<Map<String, Object>> pointsData = new ArrayList<>();
        for (ZigZagPoint point : pivots) {
            Map<String, Object> pointData = new HashMap<>();
            pointData.put("timestamp", point.timestamp.toEpochMilli());
            pointData.put("price", point.price.doubleValue());
            pointData.put("type", point.type.toString().toLowerCase());
            pointData.put("changePercent", point.changePercent.doubleValue());
            pointsData.add(pointData);
        }
        
        // Create line segments for visualization
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < pivots.size() - 1; i++) {
            ZigZagPoint from = pivots.get(i);
            ZigZagPoint to = pivots.get(i + 1);
            
            Map<String, Object> line = new HashMap<>();
            line.put("fromTimestamp", from.timestamp.toEpochMilli());
            line.put("fromPrice", from.price.doubleValue());
            line.put("toTimestamp", to.timestamp.toEpochMilli());
            line.put("toPrice", to.price.doubleValue());
            line.put("changePercent", to.changePercent.doubleValue());
            line.put("direction", from.price.compareTo(to.price) < 0 ? "up" : "down");
            lines.add(line);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", Map.of("zigzag", currentValue));
        result.put("state", pivots);
        result.put("points", pointsData);
        result.put("lines", lines);
        
        return result;
    }
    
    /**
     * Calculate ZigZag pivot points
     */
    private List<ZigZagPoint> calculateZigZagPoints(List<CandlestickData> candles, Map<String, Object> params) {
        double deviation = getDoubleParameter(params, "deviation", 5.0);
        int depth = getIntParameter(params, "depth", 12);
        String source = getStringParameter(params, "source", "high-low");
        
        List<ZigZagPoint> pivots = new ArrayList<>();
        
        if (candles.size() < depth) {
            return pivots;
        }
        
        // Find the first significant pivot
        ZigZagPoint currentPivot = findFirstPivot(candles, depth, source);
        if (currentPivot == null) {
            return pivots;
        }
        
        pivots.add(currentPivot);
        
        // Find subsequent pivots
        int startIndex = currentPivot.index + depth;
        
        while (startIndex < candles.size()) {
            ZigZagPoint nextPivot = findNextPivot(
                candles, 
                startIndex, 
                currentPivot, 
                deviation, 
                depth, 
                source
            );
            
            if (nextPivot != null) {
                // Calculate percentage change
                BigDecimal priceChange = nextPivot.price.subtract(currentPivot.price);
                BigDecimal percentChange = priceChange
                    .divide(currentPivot.price, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                nextPivot.changePercent = percentChange.abs();
                
                pivots.add(nextPivot);
                currentPivot = nextPivot;
                startIndex = nextPivot.index + depth;
            } else {
                break;
            }
        }
        
        return pivots;
    }
    
    /**
     * Find the first pivot point to start the ZigZag
     */
    private ZigZagPoint findFirstPivot(List<CandlestickData> candles, int depth, String source) {
        if (candles.size() < depth) {
            return null;
        }
        
        // Use high-low source by default
        boolean useHighLow = !source.equals("close");
        
        // Start by finding the first significant high or low
        BigDecimal firstHigh = useHighLow ? candles.get(0).getHigh() : candles.get(0).getClose();
        BigDecimal firstLow = useHighLow ? candles.get(0).getLow() : candles.get(0).getClose();
        int highIndex = 0;
        int lowIndex = 0;
        
        for (int i = 1; i < Math.min(depth * 2, candles.size()); i++) {
            BigDecimal high = useHighLow ? candles.get(i).getHigh() : candles.get(i).getClose();
            BigDecimal low = useHighLow ? candles.get(i).getLow() : candles.get(i).getClose();
            
            if (high.compareTo(firstHigh) > 0) {
                firstHigh = high;
                highIndex = i;
            }
            if (low.compareTo(firstLow) < 0) {
                firstLow = low;
                lowIndex = i;
            }
        }
        
        // Start with whichever came first
        if (highIndex < lowIndex) {
            return new ZigZagPoint(
                highIndex,
                candles.get(highIndex).getOpenTime(),
                firstHigh,
                PivotType.HIGH,
                BigDecimal.ZERO
            );
        } else {
            return new ZigZagPoint(
                lowIndex,
                candles.get(lowIndex).getOpenTime(),
                firstLow,
                PivotType.LOW,
                BigDecimal.ZERO
            );
        }
    }
    
    /**
     * Find the next pivot point that exceeds the deviation threshold
     */
    private ZigZagPoint findNextPivot(List<CandlestickData> candles, 
                                     int startIndex, 
                                     ZigZagPoint currentPivot,
                                     double deviation,
                                     int depth,
                                     String source) {
        if (startIndex >= candles.size()) {
            return null;
        }
        
        boolean useHighLow = !source.equals("close");
        BigDecimal threshold = currentPivot.price.multiply(
            BigDecimal.valueOf(deviation / 100.0)
        );
        
        // Looking for opposite of current pivot
        PivotType searchingFor = (currentPivot.type == PivotType.HIGH) ? PivotType.LOW : PivotType.HIGH;
        
        BigDecimal extremePrice = null;
        int extremeIndex = -1;
        
        for (int i = startIndex; i < candles.size(); i++) {
            BigDecimal price;
            
            if (searchingFor == PivotType.HIGH) {
                price = useHighLow ? candles.get(i).getHigh() : candles.get(i).getClose();
                
                if (extremePrice == null || price.compareTo(extremePrice) > 0) {
                    extremePrice = price;
                    extremeIndex = i;
                }
                
                // Check if we found a significant high
                BigDecimal change = extremePrice.subtract(currentPivot.price);
                if (change.compareTo(threshold) >= 0) {
                    return new ZigZagPoint(
                        extremeIndex,
                        candles.get(extremeIndex).getOpenTime(),
                        extremePrice,
                        PivotType.HIGH,
                        BigDecimal.ZERO
                    );
                }
            } else {
                price = useHighLow ? candles.get(i).getLow() : candles.get(i).getClose();
                
                if (extremePrice == null || price.compareTo(extremePrice) < 0) {
                    extremePrice = price;
                    extremeIndex = i;
                }
                
                // Check if we found a significant low
                BigDecimal change = currentPivot.price.subtract(extremePrice);
                if (change.compareTo(threshold) >= 0) {
                    return new ZigZagPoint(
                        extremeIndex,
                        candles.get(extremeIndex).getOpenTime(),
                        extremePrice,
                        PivotType.LOW,
                        BigDecimal.ZERO
                    );
                }
            }
        }
        
        return null;
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
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        String color = getStringParameter(params, "color", "#2962FF");
        int lineWidth = getIntParameter(params, "lineWidth", 2);
        double deviation = getDoubleParameter(params, "deviation", 5.0);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("zigzag", IndicatorMetadata.builder("zigzag")
            .displayName(String.format("ZigZag(%.1f%%)", deviation))
            .asLine(color, lineWidth)
            .addConfig("renderType", "zigzag") // Special rendering hint for frontend
            .separatePane(false)  // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int depth = getIntParameter(params, "depth", 12);
        return depth * 3; // Need at least 3 depth periods to form meaningful zigzags
    }
    
    /**
     * Represents a ZigZag pivot point
     */
    private static class ZigZagPoint {
        final int index;
        final Instant timestamp;
        final BigDecimal price;
        final PivotType type;
        BigDecimal changePercent;
        
        ZigZagPoint(int index, Instant timestamp, BigDecimal price, PivotType type, BigDecimal changePercent) {
            this.index = index;
            this.timestamp = timestamp;
            this.price = price;
            this.type = type;
            this.changePercent = changePercent;
        }
    }
    
    /**
     * Type of pivot point
     */
    private enum PivotType {
        HIGH,
        LOW
    }
}

