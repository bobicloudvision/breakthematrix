package org.cloudvision.trading.bot.visualization;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a rectangular box/zone on a chart, typically used for support/resistance zones,
 * order blocks, supply/demand zones, etc.
 */
public class BoxShape implements Shape {
    private final long time1;           // Start time (Unix timestamp in seconds)
    private final long time2;           // End time (Unix timestamp in seconds)
    private final BigDecimal price1;    // Top price
    private final BigDecimal price2;    // Bottom price
    private final String color;         // Fill color (e.g., "rgba(0, 255, 0, 0.2)")
    private final String borderColor;   // Border color (e.g., "#00FF00")
    private final String label;         // Optional label
    
    private BoxShape(Builder builder) {
        this.time1 = builder.time1;
        this.time2 = builder.time2;
        this.price1 = builder.price1;
        this.price2 = builder.price2;
        this.color = builder.color;
        this.borderColor = builder.borderColor;
        this.label = builder.label;
    }
    
    @Override
    public String getType() {
        return "box";
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("time1", time1);
        map.put("time2", time2);
        map.put("price1", price1);
        map.put("price2", price2);
        map.put("color", color);
        map.put("borderColor", borderColor);
        if (label != null) {
            map.put("label", label);
        }
        return map;
    }
    
    // Getters
    public long getTime1() { return time1; }
    public long getTime2() { return time2; }
    public BigDecimal getPrice1() { return price1; }
    public BigDecimal getPrice2() { return price2; }
    public String getColor() { return color; }
    public String getBorderColor() { return borderColor; }
    public String getLabel() { return label; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long time1;
        private long time2;
        private BigDecimal price1;
        private BigDecimal price2;
        private String color = "rgba(0, 0, 255, 0.2)";
        private String borderColor = "#0000FF";
        private String label;
        
        public Builder time1(long time1) {
            this.time1 = time1;
            return this;
        }
        
        public Builder time2(long time2) {
            this.time2 = time2;
            return this;
        }
        
        public Builder price1(BigDecimal price1) {
            this.price1 = price1;
            return this;
        }
        
        public Builder price2(BigDecimal price2) {
            this.price2 = price2;
            return this;
        }
        
        public Builder color(String color) {
            this.color = color;
            return this;
        }
        
        public Builder borderColor(String borderColor) {
            this.borderColor = borderColor;
            return this;
        }
        
        public Builder label(String label) {
            this.label = label;
            return this;
        }
        
        public BoxShape build() {
            return new BoxShape(this);
        }
    }
}

