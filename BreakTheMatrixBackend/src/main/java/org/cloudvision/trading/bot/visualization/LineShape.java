package org.cloudvision.trading.bot.visualization;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a line drawn between two points on a chart.
 * Used for trend lines, support/resistance lines, etc.
 */
public class LineShape implements Shape {
    private final long time1;           // Start time (Unix timestamp in seconds)
    private final BigDecimal price1;    // Start price
    private final long time2;           // End time (Unix timestamp in seconds)
    private final BigDecimal price2;    // End price
    private final String color;         // Line color
    private final int lineWidth;        // Line width in pixels
    private final String lineStyle;     // "solid", "dashed", "dotted"
    private final String label;         // Optional label
    
    private LineShape(Builder builder) {
        this.time1 = builder.time1;
        this.price1 = builder.price1;
        this.time2 = builder.time2;
        this.price2 = builder.price2;
        this.color = builder.color;
        this.lineWidth = builder.lineWidth;
        this.lineStyle = builder.lineStyle;
        this.label = builder.label;
    }
    
    @Override
    public String getType() {
        return "line";
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("time1", time1);
        map.put("price1", price1);
        map.put("time2", time2);
        map.put("price2", price2);
        map.put("color", color);
        map.put("lineWidth", lineWidth);
        map.put("lineStyle", lineStyle);
        if (label != null) {
            map.put("label", label);
        }
        return map;
    }
    
    // Getters
    public long getTime1() { return time1; }
    public BigDecimal getPrice1() { return price1; }
    public long getTime2() { return time2; }
    public BigDecimal getPrice2() { return price2; }
    public String getColor() { return color; }
    public int getLineWidth() { return lineWidth; }
    public String getLineStyle() { return lineStyle; }
    public String getLabel() { return label; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long time1;
        private BigDecimal price1;
        private long time2;
        private BigDecimal price2;
        private String color = "#0000FF";
        private int lineWidth = 2;
        private String lineStyle = "solid";
        private String label;
        
        public Builder time1(long time1) {
            this.time1 = time1;
            return this;
        }
        
        public Builder price1(BigDecimal price1) {
            this.price1 = price1;
            return this;
        }
        
        public Builder time2(long time2) {
            this.time2 = time2;
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
        
        public Builder lineWidth(int lineWidth) {
            this.lineWidth = lineWidth;
            return this;
        }
        
        public Builder lineStyle(String lineStyle) {
            this.lineStyle = lineStyle;
            return this;
        }
        
        public Builder label(String label) {
            this.label = label;
            return this;
        }
        
        public LineShape build() {
            return new LineShape(this);
        }
    }
}

