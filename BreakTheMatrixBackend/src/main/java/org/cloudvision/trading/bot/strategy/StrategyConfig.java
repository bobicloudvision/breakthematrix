package org.cloudvision.trading.bot.strategy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrategyConfig {
    private String strategyId;
    private List<String> symbols;
    private BigDecimal maxPositionSize;
    private BigDecimal stopLossPercentage;
    private BigDecimal takeProfitPercentage;
    private Map<String, Object> parameters;
    private boolean enabled;

    public StrategyConfig(String strategyId, List<String> symbols) {
        this.strategyId = strategyId;
        this.symbols = symbols;
        this.parameters = new HashMap<>();
        this.enabled = true;
        this.maxPositionSize = new BigDecimal("1000"); // Default $1000
        this.stopLossPercentage = new BigDecimal("0.02"); // 2% stop loss
        this.takeProfitPercentage = new BigDecimal("0.05"); // 5% take profit
    }

    // Getters and setters
    public String getStrategyId() { return strategyId; }
    public List<String> getSymbols() { return symbols; }
    public BigDecimal getMaxPositionSize() { return maxPositionSize; }
    public BigDecimal getStopLossPercentage() { return stopLossPercentage; }
    public BigDecimal getTakeProfitPercentage() { return takeProfitPercentage; }
    public Map<String, Object> getParameters() { return parameters; }
    public boolean isEnabled() { return enabled; }

    public void setMaxPositionSize(BigDecimal maxPositionSize) { this.maxPositionSize = maxPositionSize; }
    public void setStopLossPercentage(BigDecimal stopLossPercentage) { this.stopLossPercentage = stopLossPercentage; }
    public void setTakeProfitPercentage(BigDecimal takeProfitPercentage) { this.takeProfitPercentage = takeProfitPercentage; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    public Object getParameter(String key) {
        return parameters.get(key);
    }

    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        return type.cast(value);
    }
}
