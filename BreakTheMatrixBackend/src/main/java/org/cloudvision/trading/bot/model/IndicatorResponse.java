package org.cloudvision.trading.bot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.ShapeRegistry;
import org.cloudvision.trading.model.CandlestickData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified response structure for indicator data
 * Used by both REST API and WebSocket to ensure consistent data formatting
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndicatorResponse {
    
    // Basic indicator information
    private String indicatorId;
    private String instanceKey;
    private String provider;
    private String symbol;
    private String interval;
    private Map<String, Object> params;
    
    // Current values
    private Instant timestamp;
    private Map<String, BigDecimal> values;
    private Map<String, Object> additionalData;
    
    // Visualization metadata
    private Map<String, IndicatorMetadata> metadata;
    
    // Historical series data (for REST API historical endpoint)
    private Map<String, List<SeriesPoint>> series;
    
    // Shapes data (boxes, lines, markers, arrows)
    private Map<String, List<Map<String, Object>>> shapes;
    private Map<String, Integer> shapesSummary;
    private Boolean supportsShapes;
    
    // Statistics
    private Integer updateCount;
    private Integer dataPointCount;
    
    // Associated candle data (for WebSocket updates)
    private CandleData candle;
    
    // ============================================================
    // Constructors
    // ============================================================
    
    public IndicatorResponse() {
    }
    
    // ============================================================
    // Builder Pattern
    // ============================================================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final IndicatorResponse response;
        
        public Builder() {
            this.response = new IndicatorResponse();
        }
        
        public Builder indicatorId(String indicatorId) {
            response.indicatorId = indicatorId;
            return this;
        }
        
        public Builder instanceKey(String instanceKey) {
            response.instanceKey = instanceKey;
            return this;
        }
        
        public Builder provider(String provider) {
            response.provider = provider;
            return this;
        }
        
        public Builder symbol(String symbol) {
            response.symbol = symbol;
            return this;
        }
        
        public Builder interval(String interval) {
            response.interval = interval;
            return this;
        }
        
        public Builder params(Map<String, Object> params) {
            response.params = params;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            response.timestamp = timestamp;
            return this;
        }
        
        public Builder values(Map<String, BigDecimal> values) {
            response.values = values;
            return this;
        }
        
        public Builder additionalData(Map<String, Object> additionalData) {
            response.additionalData = additionalData;
            return this;
        }
        
        public Builder metadata(Map<String, IndicatorMetadata> metadata) {
            response.metadata = metadata;
            return this;
        }
        
        public Builder series(Map<String, List<SeriesPoint>> series) {
            response.series = series;
            return this;
        }
        
        public Builder shapes(Map<String, List<Map<String, Object>>> shapes) {
            response.shapes = shapes;
            return this;
        }
        
        public Builder shapesSummary(Map<String, Integer> shapesSummary) {
            response.shapesSummary = shapesSummary;
            return this;
        }
        
        public Builder supportsShapes(Boolean supportsShapes) {
            response.supportsShapes = supportsShapes;
            return this;
        }
        
        public Builder updateCount(Integer updateCount) {
            response.updateCount = updateCount;
            return this;
        }
        
        public Builder dataPointCount(Integer dataPointCount) {
            response.dataPointCount = dataPointCount;
            return this;
        }
        
        public Builder candle(CandleData candle) {
            response.candle = candle;
            return this;
        }
        
        public Builder fromInstance(IndicatorInstanceManager.IndicatorInstance instance) {
            response.indicatorId = instance.getIndicatorId();
            response.instanceKey = instance.getInstanceKey();
            response.provider = instance.getProvider();
            response.symbol = instance.getSymbol();
            response.interval = instance.getInterval();
            response.params = instance.getParams();
            response.updateCount = (int) instance.getUpdateCount
                    ();
            return this;
        }
        
        public Builder fromResult(IndicatorInstanceManager.IndicatorResult result) {
            response.timestamp = result.getTimestamp();
            response.values = result.getValues();
            
            // Extract shapes from additional data and set remaining as additionalData
            if (result.getAdditionalData() != null && !result.getAdditionalData().isEmpty()) {
                Map<String, Object> additionalDataCopy = new HashMap<>(result.getAdditionalData());
                
                // Extract shapes
                Map<String, List<Map<String, Object>>> extractedShapes = 
                    ShapeRegistry.extractShapes(additionalDataCopy);
                
                if (!extractedShapes.isEmpty()) {
                    response.shapes = extractedShapes;
                    response.supportsShapes = true;
                    
                    // Calculate shapes summary
                    Map<String, Integer> summary = new HashMap<>();
                    for (Map.Entry<String, List<Map<String, Object>>> entry : extractedShapes.entrySet()) {
                        summary.put(entry.getKey(), entry.getValue().size());
                    }
                    response.shapesSummary = summary;
                }
                
                // Set remaining additional data (without shapes)
                if (!additionalDataCopy.isEmpty()) {
                    response.additionalData = additionalDataCopy;
                }
            }
            
            return this;
        }
        
        public Builder fromCandlestick(CandlestickData candlestick) {
            if (candlestick != null) {
                response.candle = CandleData.fromCandlestick(candlestick);
            }
            return this;
        }
        
        public IndicatorResponse build() {
            return response;
        }
    }
    
    // ============================================================
    // Factory Methods
    // ============================================================
    
    /**
     * Create response for current/real-time indicator value
     */
    public static IndicatorResponse forCurrentValue(
            IndicatorInstanceManager.IndicatorInstance instance,
            IndicatorInstanceManager.IndicatorResult result,
            Map<String, IndicatorMetadata> metadata) {
        
        return builder()
            .fromInstance(instance)
            .fromResult(result)
            .metadata(metadata)
            .build();
    }
    
    /**
     * Create response for WebSocket indicator update (includes candle)
     */
    public static IndicatorResponse forWebSocketUpdate(
            IndicatorInstanceManager.IndicatorInstance instance,
            IndicatorInstanceManager.IndicatorResult result,
            CandlestickData candle) {
        
        return builder()
            .fromInstance(instance)
            .fromResult(result)
            .fromCandlestick(candle)
            .build();
    }
    
    /**
     * Create response for historical data
     */
    public static IndicatorResponse forHistoricalData(
            IndicatorInstanceManager.IndicatorInstance instance,
            List<IndicatorInstanceManager.IndicatorResult> dataPoints,
            Map<String, IndicatorMetadata> metadata) {
        
        // Build series data
        Map<String, List<SeriesPoint>> seriesData = buildSeriesData(dataPoints);
        
        // Collect and deduplicate shapes
        Map<String, List<Map<String, Object>>> allShapes = collectShapes(dataPoints);
        
        Builder builder = builder()
            .fromInstance(instance)
            .series(seriesData)
            .metadata(metadata)
            .dataPointCount(dataPoints.size());
        
        // Add latest values if available
        if (!dataPoints.isEmpty()) {
            IndicatorInstanceManager.IndicatorResult latest = dataPoints.get(dataPoints.size() - 1);
            builder.timestamp(latest.getTimestamp())
                   .values(latest.getValues());
        }
        
        // Add shapes if any
        if (!allShapes.isEmpty()) {
            builder.shapes(allShapes)
                   .supportsShapes(true);
            
            Map<String, Integer> summary = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : allShapes.entrySet()) {
                summary.put(entry.getKey(), entry.getValue().size());
            }
            builder.shapesSummary(summary);
        }
        
        return builder.build();
    }
    
    // ============================================================
    // Helper Methods
    // ============================================================
    
    private static Map<String, List<SeriesPoint>> buildSeriesData(
            List<IndicatorInstanceManager.IndicatorResult> dataPoints) {
        
        Map<String, List<SeriesPoint>> seriesData = new HashMap<>();
        
        if (dataPoints.isEmpty()) {
            return seriesData;
        }
        
        // Get all keys from first data point
        Map<String, BigDecimal> firstValues = dataPoints.get(0).getValues();
        
        for (String key : firstValues.keySet()) {
            List<SeriesPoint> seriesPoints = dataPoints.stream()
                .map(dp -> SeriesPoint.from(dp, key))
                .collect(Collectors.toList());
            seriesData.put(key, seriesPoints);
        }
        
        return seriesData;
    }
    
    private static Map<String, List<Map<String, Object>>> collectShapes(
            List<IndicatorInstanceManager.IndicatorResult> dataPoints) {
        
        Map<String, List<Map<String, Object>>> shapesByType = new HashMap<>();
        
        for (IndicatorInstanceManager.IndicatorResult dp : dataPoints) {
            if (dp.getAdditionalData() != null) {
                Map<String, List<Map<String, Object>>> shapesFromPoint = 
                    ShapeRegistry.extractShapes(dp.getAdditionalData());
                
                if (!shapesFromPoint.isEmpty()) {
                    for (Map.Entry<String, List<Map<String, Object>>> entry : shapesFromPoint.entrySet()) {
                        shapesByType.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                   .addAll(entry.getValue());
                    }
                }
            }
        }
        
        // Deduplicate shapes
        Map<String, List<Map<String, Object>>> uniqueShapesByType = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : shapesByType.entrySet()) {
            List<Map<String, Object>> uniqueShapes = ShapeRegistry.deduplicate(
                entry.getKey(), 
                entry.getValue()
            );
            uniqueShapesByType.put(entry.getKey(), uniqueShapes);
        }
        
        return uniqueShapesByType;
    }
    
    // ============================================================
    // Nested Classes
    // ============================================================
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SeriesPoint {
        private Long time;  // Unix timestamp in seconds
        private BigDecimal value;
        private String color;
        private Map<String, Object> extra;
        
        public SeriesPoint() {}
        
        public SeriesPoint(Long time, BigDecimal value) {
            this.time = time;
            this.value = value;
        }
        
        public static SeriesPoint from(IndicatorInstanceManager.IndicatorResult result, String key) {
            SeriesPoint point = new SeriesPoint();
            point.time = result.getTimestamp().getEpochSecond();
            point.value = result.getValues().get(key);
            
            // Check for color in additional data
            if (result.getAdditionalData() != null && !result.getAdditionalData().isEmpty()) {
                Map<String, Object> additionalData = new HashMap<>(result.getAdditionalData());
                
                // Extract shapes (removes them from additionalData)
                ShapeRegistry.extractShapes(additionalData);
                
                // Check for color
                if (additionalData.containsKey("color")) {
                    point.color = (String) additionalData.get("color");
                    additionalData.remove("color");
                }
                
                // Store any remaining extra data
                if (!additionalData.isEmpty()) {
                    point.extra = additionalData;
                }
            }
            
            return point;
        }
        
        // Getters and setters
        public Long getTime() { return time; }
        public void setTime(Long time) { this.time = time; }
        
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
        
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public Map<String, Object> getExtra() { return extra; }
        public void setExtra(Map<String, Object> extra) { this.extra = extra; }
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CandleData {
        private String openTime;
        private String closeTime;
        private Long time;        // Unix seconds (TradingView format)
        private Long timestamp;   // Unix seconds (alias)
        private Long timeMs;      // Unix milliseconds (Chart.js format)
        private Double open;
        private Double high;
        private Double low;
        private Double close;
        private Double volume;
        private Boolean closed;
        
        public CandleData() {}
        
        public static CandleData fromCandlestick(CandlestickData candle) {
            CandleData data = new CandleData();
            data.openTime = candle.getOpenTime().toString();
            data.closeTime = candle.getCloseTime().toString();
            data.time = candle.getOpenTime().getEpochSecond();
            data.timestamp = candle.getOpenTime().getEpochSecond();
            data.timeMs = candle.getOpenTime().toEpochMilli();
            data.open = candle.getOpen().doubleValue();
            data.high = candle.getHigh().doubleValue();
            data.low = candle.getLow().doubleValue();
            data.close = candle.getClose().doubleValue();
            data.volume = candle.getVolume().doubleValue();
            data.closed = candle.isClosed();
            return data;
        }
        
        // Getters and setters
        public String getOpenTime() { return openTime; }
        public void setOpenTime(String openTime) { this.openTime = openTime; }
        
        public String getCloseTime() { return closeTime; }
        public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
        
        public Long getTime() { return time; }
        public void setTime(Long time) { this.time = time; }
        
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        
        public Long getTimeMs() { return timeMs; }
        public void setTimeMs(Long timeMs) { this.timeMs = timeMs; }
        
        public Double getOpen() { return open; }
        public void setOpen(Double open) { this.open = open; }
        
        public Double getHigh() { return high; }
        public void setHigh(Double high) { this.high = high; }
        
        public Double getLow() { return low; }
        public void setLow(Double low) { this.low = low; }
        
        public Double getClose() { return close; }
        public void setClose(Double close) { this.close = close; }
        
        public Double getVolume() { return volume; }
        public void setVolume(Double volume) { this.volume = volume; }
        
        public Boolean getClosed() { return closed; }
        public void setClosed(Boolean closed) { this.closed = closed; }
    }
    
    // ============================================================
    // Getters and Setters
    // ============================================================
    
    public String getIndicatorId() { return indicatorId; }
    public void setIndicatorId(String indicatorId) { this.indicatorId = indicatorId; }
    
    public String getInstanceKey() { return instanceKey; }
    public void setInstanceKey(String instanceKey) { this.instanceKey = instanceKey; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
    
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public Map<String, BigDecimal> getValues() { return values; }
    public void setValues(Map<String, BigDecimal> values) { this.values = values; }
    
    public Map<String, Object> getAdditionalData() { return additionalData; }
    public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }
    
    public Map<String, IndicatorMetadata> getMetadata() { return metadata; }
    public void setMetadata(Map<String, IndicatorMetadata> metadata) { this.metadata = metadata; }
    
    public Map<String, List<SeriesPoint>> getSeries() { return series; }
    public void setSeries(Map<String, List<SeriesPoint>> series) { this.series = series; }
    
    public Map<String, List<Map<String, Object>>> getShapes() { return shapes; }
    public void setShapes(Map<String, List<Map<String, Object>>> shapes) { this.shapes = shapes; }
    
    public Map<String, Integer> getShapesSummary() { return shapesSummary; }
    public void setShapesSummary(Map<String, Integer> shapesSummary) { this.shapesSummary = shapesSummary; }
    
    public Boolean getSupportsShapes() { return supportsShapes; }
    public void setSupportsShapes(Boolean supportsShapes) { this.supportsShapes = supportsShapes; }
    
    public Integer getUpdateCount() { return updateCount; }
    public void setUpdateCount(Integer updateCount) { this.updateCount = updateCount; }
    
    public Integer getDataPointCount() { return dataPointCount; }
    public void setDataPointCount(Integer dataPointCount) { this.dataPointCount = dataPointCount; }
    
    public CandleData getCandle() { return candle; }
    public void setCandle(CandleData candle) { this.candle = candle; }
}

