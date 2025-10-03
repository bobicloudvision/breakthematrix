package org.cloudvision.trading.bot.visualization;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an arrow on the chart.
 * Used for directional signals, trade directions, etc.
 */
public class ArrowShape implements Shape {
    private final long time;            // Time (Unix timestamp in seconds)
    private final BigDecimal price;     // Price level
    private final String direction;     // "up" or "down"
    private final String color;         // Arrow color
    private final String text;          // Optional text label
    private final int size;             // Size in pixels
    
    private ArrowShape(Builder builder) {
        this.time = builder.time;
        this.price = builder.price;
        this.direction = builder.direction;
        this.color = builder.color;
        this.text = builder.text;
        this.size = builder.size;
    }
    
    @Override
    public String getType() {
        return "arrow";
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("time", time);
        map.put("price", price);
        map.put("direction", direction);
        map.put("color", color);
        map.put("size", size);
        if (text != null) {
            map.put("text", text);
        }
        return map;
    }
    
    // Getters
    public long getTime() { return time; }
    public BigDecimal getPrice() { return price; }
    public String getDirection() { return direction; }
    public String getColor() { return color; }
    public String getText() { return text; }
    public int getSize() { return size; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long time;
        private BigDecimal price;
        private String direction = "up";
        private String color = "#00FF00";
        private String text;
        private int size = 12;
        
        public Builder time(long time) {
            this.time = time;
            return this;
        }
        
        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }
        
        public Builder direction(String direction) {
            this.direction = direction;
            return this;
        }
        
        public Builder color(String color) {
            this.color = color;
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
        
        public ArrowShape build() {
            return new ArrowShape(this);
        }
    }
}

