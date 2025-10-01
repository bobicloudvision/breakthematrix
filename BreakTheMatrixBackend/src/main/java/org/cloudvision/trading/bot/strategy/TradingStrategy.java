package org.cloudvision.trading.bot.strategy;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.model.TradingData;

import java.util.List;

public interface TradingStrategy {
    
    /**
     * Analyze market data and generate trading signals
     * @param data Current market data
     * @return List of orders to execute (empty if no action needed)
     */
    List<Order> analyze(TradingData data);
    
    /**
     * Get strategy identifier
     */
    String getStrategyId();
    
    /**
     * Get strategy name
     */
    String getStrategyName();
    
    /**
     * Get symbols this strategy trades
     */
    List<String> getSymbols();
    
    /**
     * Initialize strategy with parameters
     */
    void initialize(StrategyConfig config);
    
    /**
     * Check if strategy is enabled
     */
    boolean isEnabled();
    
    /**
     * Enable/disable strategy
     */
    void setEnabled(boolean enabled);
    
    /**
     * Get strategy statistics
     */
    StrategyStats getStats();
}
