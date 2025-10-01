package org.cloudvision.trading.bot.strategy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Metadata for how an indicator should be visualized
 */
public class IndicatorMetadata {
    private final String name;
    private final String displayName;
    private final String seriesType; // line, area, histogram, baseline
    private final Map<String, Object> config; // TradingView series configuration
    private final boolean separatePane; // Should be displayed in separate pane?
    private final Integer paneOrder; // Order in panes (0 = main chart)

    public IndicatorMetadata(String name, String displayName, String seriesType, 
                           Map<String, Object> config, boolean separatePane, Integer paneOrder) {
        this.name = name;
        this.displayName = displayName;
        this.seriesType = seriesType;
        this.config = config;
        this.separatePane = separatePane;
        this.paneOrder = paneOrder;
    }

    // Builder for easier construction
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String displayName;
        private String seriesType = "line";
        private Map<String, Object> config = new java.util.HashMap<>();
        private boolean separatePane = false;
        private Integer paneOrder = 0;

        public Builder(String name) {
            this.name = name;
            this.displayName = name;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder seriesType(String seriesType) {
            this.seriesType = seriesType;
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public Builder addConfig(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        public Builder separatePane(boolean separatePane) {
            this.separatePane = separatePane;
            return this;
        }

        public Builder paneOrder(Integer paneOrder) {
            this.paneOrder = paneOrder;
            return this;
        }

        public Builder asLine(String color, int lineWidth) {
            this.seriesType = "line";
            this.config.put("color", color);
            this.config.put("lineWidth", lineWidth);
            return this;
        }

        public Builder asArea(String topColor, String bottomColor, String lineColor) {
            this.seriesType = "area";
            this.config.put("topColor", topColor);
            this.config.put("bottomColor", bottomColor);
            this.config.put("lineColor", lineColor);
            this.config.put("lineWidth", 2);
            return this;
        }

        public Builder asHistogram(String color) {
            this.seriesType = "histogram";
            this.config.put("color", color);
            return this;
        }

        public Builder asBaseline(BigDecimal baseValue) {
            this.seriesType = "baseline";
            this.config.put("baseValue", Map.of("type", "price", "price", baseValue));
            return this;
        }

        /**
         * Bar series (for OHLC data)
         * Note: Requires data format { time, open, high, low, close }
         */
        public Builder asBar(String upColor, String downColor) {
            this.seriesType = "bar";
            this.config.put("upColor", upColor);
            this.config.put("downColor", downColor);
            return this;
        }

        /**
         * Candlestick series (for OHLC data)
         * Note: Requires data format { time, open, high, low, close }
         */
        public Builder asCandlestick(String upColor, String downColor, String wickUpColor, String wickDownColor) {
            this.seriesType = "candlestick";
            this.config.put("upColor", upColor);
            this.config.put("downColor", downColor);
            this.config.put("wickUpColor", wickUpColor);
            this.config.put("wickDownColor", wickDownColor);
            this.config.put("borderVisible", false);
            return this;
        }

        /**
         * Candlestick series with default colors
         */
        public Builder asCandlestick() {
            return asCandlestick(
                "#26a69a", // Up - green
                "#ef5350", // Down - red
                "#26a69a", // Wick up - green
                "#ef5350"  // Wick down - red
            );
        }

        public IndicatorMetadata build() {
            config.put("title", displayName);
            return new IndicatorMetadata(name, displayName, seriesType, config, separatePane, paneOrder);
        }
    }

    // Getters
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getSeriesType() { return seriesType; }
    public Map<String, Object> getConfig() { return config; }
    public boolean isSeparatePane() { return separatePane; }
    public Integer getPaneOrder() { return paneOrder; }
}

