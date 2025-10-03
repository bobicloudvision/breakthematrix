package org.cloudvision.trading.bot.visualization;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a marker/point on the chart.
 * Used for signals, pivots, entry/exit points, etc.
 */
public class MarkerShape implements Shape {
    private final long time;            // Time (Unix timestamp in seconds)
    private final BigDecimal price;     // Price level
    private final String shape;         // "circle", "square", "triangle", "diamond"
    private final String color;         // Marker color
    private final String position;      // "above" or "below" the bar
    private final String text;          // Optional text label
    private final int size;             // Size in pixels
    
    private MarkerShape(Builder builder) {
        this.time = builder.time;
        this.price = builder.price;
        this.shape = builder.shape;
        this.color = builder.color;
        this.position = builder.position;
        this.text = builder.text;
        this.size = builder.size;
    }
    
    @Override
    public String getType() {
        return "marker";
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("time", time);
        map.put("price", price);
        map.put("shape", shape);
        map.put("color", color);
        map.put("position", position);
        map.put("size", size);
        if (text != null) {
            map.put("text", text);
        }
        return map;
    }
    
    // Getters
    public long getTime() { return time; }
    public BigDecimal getPrice() { return price; }
    public String getShape() { return shape; }
    public String getColor() { return color; }
    public String getPosition() { return position; }
    public String getText() { return text; }
    public int getSize() { return size; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long time;
        private BigDecimal price;
        private String shape = "circle";
        private String color = "#0000FF";
        private String position = "above";
        private String text;
        private int size = 8;
        
        public Builder time(long time) {
            this.time = time;
            return this;
        }
        
        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }
        
        public Builder shape(String shape) {
            this.shape = shape;
            return this;
        }
        
        public Builder color(String color) {
            this.color = color;
            return this;
        }
        
        public Builder position(String position) {
            this.position = position;
            return this;
        }
        
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        public Builder size(int size) {
            this.size = size;
            return this;
        }
        
        public MarkerShape build() {
            return new MarkerShape(this);
        }
    }
}

