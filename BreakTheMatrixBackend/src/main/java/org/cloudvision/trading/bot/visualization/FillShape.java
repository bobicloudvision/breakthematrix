package org.cloudvision.trading.bot.visualization;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a fill configuration for areas on a chart.
 * Supports multiple modes: series-based fills, horizontal line fills, and various color modes.
 * 
 * <p>Example usage for series mode with dynamic colors:</p>
 * <pre>{@code
 * FillShape fill = FillShape.builder()
 *     .enabled(true)
 *     .mode("series")
 *     .source1("close")
 *     .source2(null)  // Will use indicator series
 *     .colorMode("dynamic")
 *     .upFillColor("rgba(76, 175, 80, 0.15)")
 *     .downFillColor("rgba(239, 83, 80, 0.15)")
 *     .neutralFillColor("rgba(158, 158, 158, 0.1)")
 *     .build();
 * }</pre>
 * 
 * <p>Example usage for hline mode with static color:</p>
 * <pre>{@code
 * FillShape fill = FillShape.builder()
 *     .enabled(true)
 *     .mode("hline")
 *     .hline1(70)
 *     .hline2(30)
 *     .colorMode("static")
 *     .color("rgba(41, 98, 255, 0.1)")
 *     .build();
 * }</pre>
 * 
 * <p>Example usage with gradient colors:</p>
 * <pre>{@code
 * FillShape fill = FillShape.builder()
 *     .enabled(true)
 *     .mode("series")
 *     .source1("high")
 *     .source2("low")
 *     .colorMode("gradient")
 *     .topColor("rgba(76, 175, 80, 0.2)")
 *     .bottomColor("rgba(244, 67, 54, 0.2)")
 *     .build();
 * }</pre>
 */
public class FillShape implements Shape {
    // Core properties
    private final boolean enabled;
    private final String mode;              // "series" or "hline"
    
    // Series mode properties
    private final String source1;           // "close", "open", "high", "low", "hl2", "hlc3", "ohlc4", or array
    private final Object source2;           // Array data, series name, or null
    
    // Hline mode properties
    private final BigDecimal hline1;
    private final BigDecimal hline2;
    
    // Color configuration
    private final String colorMode;         // "static", "dynamic", or "gradient"
    
    // Static color mode
    private final String color;
    
    // Dynamic color mode
    private final String upFillColor;
    private final String downFillColor;
    private final String neutralFillColor;
    
    // Gradient color mode
    private final String topColor;
    private final String bottomColor;
    
    // Additional options
    private final boolean fillGaps;
    private final boolean display;
    
    private FillShape(Builder builder) {
        this.enabled = builder.enabled;
        this.mode = builder.mode;
        this.source1 = builder.source1;
        this.source2 = builder.source2;
        this.hline1 = builder.hline1;
        this.hline2 = builder.hline2;
        this.colorMode = builder.colorMode;
        this.color = builder.color;
        this.upFillColor = builder.upFillColor;
        this.downFillColor = builder.downFillColor;
        this.neutralFillColor = builder.neutralFillColor;
        this.topColor = builder.topColor;
        this.bottomColor = builder.bottomColor;
        this.fillGaps = builder.fillGaps;
        this.display = builder.display;
    }
    
    @Override
    public String getType() {
        return "fill";
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        
        // Core properties
        map.put("enabled", enabled);
        map.put("mode", mode);
        
        // Mode-specific properties
        if ("series".equals(mode)) {
            map.put("source1", source1);
            if (source2 != null) {
                map.put("source2", source2);
            } else {
                map.put("source2", null);
            }
        } else if ("hline".equals(mode)) {
            if (hline1 != null) {
                map.put("hline1", hline1);
            }
            if (hline2 != null) {
                map.put("hline2", hline2);
            }
        }
        
        // Color configuration
        map.put("colorMode", colorMode);
        
        if ("static".equals(colorMode)) {
            map.put("color", color);
        } else if ("dynamic".equals(colorMode)) {
            map.put("upFillColor", upFillColor);
            map.put("downFillColor", downFillColor);
            map.put("neutralFillColor", neutralFillColor);
        } else if ("gradient".equals(colorMode)) {
            map.put("topColor", topColor);
            map.put("bottomColor", bottomColor);
        }
        
        // Additional options
        map.put("fillGaps", fillGaps);
        map.put("display", display);
        
        return map;
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public String getMode() { return mode; }
    public String getSource1() { return source1; }
    public Object getSource2() { return source2; }
    public BigDecimal getHline1() { return hline1; }
    public BigDecimal getHline2() { return hline2; }
    public String getColorMode() { return colorMode; }
    public String getColor() { return color; }
    public String getUpFillColor() { return upFillColor; }
    public String getDownFillColor() { return downFillColor; }
    public String getNeutralFillColor() { return neutralFillColor; }
    public String getTopColor() { return topColor; }
    public String getBottomColor() { return bottomColor; }
    public boolean isFillGaps() { return fillGaps; }
    public boolean isDisplay() { return display; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        // Core properties
        private boolean enabled = true;
        private String mode = "series";
        
        // Series mode properties
        private String source1 = "close";
        private Object source2 = null;
        
        // Hline mode properties
        private BigDecimal hline1 = null;
        private BigDecimal hline2 = null;
        
        // Color configuration
        private String colorMode = "static";
        
        // Static color mode
        private String color = "rgba(41, 98, 255, 0.1)";
        
        // Dynamic color mode
        private String upFillColor = "rgba(76, 175, 80, 0.15)";
        private String downFillColor = "rgba(239, 83, 80, 0.15)";
        private String neutralFillColor = "rgba(158, 158, 158, 0.1)";
        
        // Gradient color mode
        private String topColor = "rgba(76, 175, 80, 0.2)";
        private String bottomColor = "rgba(244, 67, 54, 0.2)";
        
        // Additional options
        private boolean fillGaps = true;
        private boolean display = true;
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder source1(String source1) {
            this.source1 = source1;
            return this;
        }
        
        public Builder source2(Object source2) {
            this.source2 = source2;
            return this;
        }
        
        public Builder source2List(List<BigDecimal> source2) {
            this.source2 = source2;
            return this;
        }
        
        public Builder source2String(String source2) {
            this.source2 = source2;
            return this;
        }
        
        public Builder hline1(BigDecimal hline1) {
            this.hline1 = hline1;
            return this;
        }
        
        public Builder hline1(double hline1) {
            this.hline1 = BigDecimal.valueOf(hline1);
            return this;
        }
        
        public Builder hline2(BigDecimal hline2) {
            this.hline2 = hline2;
            return this;
        }
        
        public Builder hline2(double hline2) {
            this.hline2 = BigDecimal.valueOf(hline2);
            return this;
        }
        
        public Builder colorMode(String colorMode) {
            this.colorMode = colorMode;
            return this;
        }
        
        public Builder color(String color) {
            this.color = color;
            return this;
        }
        
        public Builder upFillColor(String upFillColor) {
            this.upFillColor = upFillColor;
            return this;
        }
        
        public Builder downFillColor(String downFillColor) {
            this.downFillColor = downFillColor;
            return this;
        }
        
        public Builder neutralFillColor(String neutralFillColor) {
            this.neutralFillColor = neutralFillColor;
            return this;
        }
        
        public Builder topColor(String topColor) {
            this.topColor = topColor;
            return this;
        }
        
        public Builder bottomColor(String bottomColor) {
            this.bottomColor = bottomColor;
            return this;
        }
        
        public Builder fillGaps(boolean fillGaps) {
            this.fillGaps = fillGaps;
            return this;
        }
        
        public Builder display(boolean display) {
            this.display = display;
            return this;
        }
        
        public FillShape build() {
            return new FillShape(this);
        }
    }
}

