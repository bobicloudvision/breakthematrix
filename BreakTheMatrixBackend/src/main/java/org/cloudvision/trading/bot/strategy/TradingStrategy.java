package org.cloudvision.trading.bot.strategy;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradingData;

import java.util.List;
import java.util.Map;

public interface TradingStrategy {
    
    /**
     * Analyze market data and generate trading signals
     * @param data Current market data
     * @return List of orders to execute (empty if no action needed)
     */
    List<Order> analyze(TradingData data);
    
    /**
     * Bootstrap strategy with historical candlestick data
     * This should be called before starting real-time analysis
     * @param historicalData List of historical candlestick data
     */
    void bootstrapWithHistoricalData(List<CandlestickData> historicalData);
    
    /**
     * Check if strategy has been bootstrapped with historical data
     * @return true if bootstrapped, false otherwise
     */
    boolean isBootstrapped();
    
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
    
    /**
     * Get indicator visualization metadata
     * Defines how each indicator should be displayed in charts
     * @return Map of indicator name to its visualization metadata
     */
    Map<String, IndicatorMetadata> getIndicatorMetadata();
    
    /**
     * Generate historical visualization data from historical candlestick data
     * This is called after bootstrapping to populate visualization history
     * @param historicalData Historical candlestick data
     */
    void generateHistoricalVisualizationData(List<CandlestickData> historicalData);
    
    /**
     * Reset strategy state and clear all memory
     * This clears historical data, signals, and any cached indicators
     * Call when disabling/re-enabling strategy to start fresh
     */
    void reset();
}
