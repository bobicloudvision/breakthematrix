package org.cloudvision.trading.bot.visualization;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class StrategyVisualizationData {
    private final String strategyId;
    private final String symbol;
    private final Instant timestamp;
    private final BigDecimal price;
    private final Map<String, BigDecimal> indicators;
    private final Map<String, Object> signals;
    private final Map<String, BigDecimal> performance;
    private final String action; // "BUY", "SELL", "HOLD"

    public StrategyVisualizationData(String strategyId, String symbol, Instant timestamp, 
                                   BigDecimal price, Map<String, BigDecimal> indicators,
                                   Map<String, Object> signals, Map<String, BigDecimal> performance,
                                   String action) {
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.price = price;
        this.indicators = indicators;
        this.signals = signals;
        this.performance = performance;
        this.action = action;
    }

    // Getters
    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public Instant getTimestamp() { return timestamp; }
    public BigDecimal getPrice() { return price; }
    public Map<String, BigDecimal> getIndicators() { return indicators; }
    public Map<String, Object> getSignals() { return signals; }
    public Map<String, BigDecimal> getPerformance() { return performance; }
    public String getAction() { return action; }

    @Override
    public String toString() {
        return String.format("StrategyViz{strategy='%s', symbol='%s', price=%s, action='%s', indicators=%s}",
                strategyId, symbol, price, action, indicators.keySet());
    }
}
