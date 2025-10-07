package org.cloudvision.trading.bot.strategy;

import org.cloudvision.trading.model.CandlestickData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Trading Strategy Interface - Event-Driven Architecture
 * 
 * Strategies follow an event-driven approach similar to indicators:
 * 1. onInit() - Initialize state with historical data
 * 2. onNewCandle() - Process each new closed candle and generate orders
 * 3. onNewTick() - Process real-time price ticks (optional)
 * 
 * This design enables:
 * - Efficient incremental calculations
 * - Stateful strategy logic with memory
 * - Clean separation between initialization and trading logic
 * - Real-time updates for live trading
 */
public interface TradingStrategy {
    
    // ============================================================
    // EVENT-DRIVEN LIFECYCLE METHODS
    // ============================================================
    
    /**
     * Initialize strategy with historical data and parameters
     * 
     * This method is called once when the strategy is first set up.
     * Use it to:
     * - Process historical candles to build initial state
     * - Pre-calculate indicators that need historical context
     * - Initialize any internal buffers or tracking structures
     * 
     * @param historicalCandles Historical candlestick data sorted chronologically (oldest first)
     * @param params Strategy configuration parameters
     * @return Initial state object for this strategy (can be null if stateless)
     */
    Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params);
    
    /**
     * Process a new closed candle and generate trading orders
     * 
     * This is the core trading logic called for each new CLOSED candle.
     * The method receives the current candle and previous state, and returns:
     * - Trading orders to execute (buy/sell/close)
     * - Updated state for the next call
     * - Optional signal data for visualization
     * 
     * @param candle The closed candle to process
     * @param params Strategy configuration parameters
     * @param state Current state from previous call (or from onInit)
     * @return Map containing:
     *   - "orders": List<Order> - Orders to execute (required, can be empty)
     *   - "state": Updated state for next iteration (can be null)
     *   - "indicators": Map<String, BigDecimal> - Indicator values for visualization (optional)
     *   - "signals": Map<String, Object> - Additional signal data (optional)
     */
    Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state);
    
    /**
     * Process real-time price tick (optional, for intra-candle updates)
     * 
     * This method is called when a price tick occurs within the current candle.
     * Most strategies only trade on closed candles and can use the default implementation.
     * 
     * Override this for strategies that need real-time updates:
     * - Scalping strategies with tick-level entries
     * - Dynamic stop-loss adjustments
     * - Real-time risk management
     * 
     * @param symbol Trading symbol
     * @param price Current tick price
     * @param params Strategy configuration parameters
     * @param state Current state
     * @return Map containing:
     *   - "orders": List<Order> - Orders to execute (can be empty)
     *   - "state": Updated state (usually unchanged for ticks)
     *   - Additional data (optional)
     * 
     * Default implementation: Returns empty orders with unchanged state
     */
    default Map<String, Object> onNewTick(String symbol, BigDecimal price, Map<String, Object> params, Object state) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("orders", List.of());
        result.put("state", state);
        return result;
    }
    
    // ============================================================
    // STRATEGY METADATA & CONFIGURATION
    // ============================================================
    
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
     * Set strategy configuration
     * This is a simple setter - actual initialization happens in onInit()
     */
    void setConfig(StrategyConfig config);
    
    /**
     * Get strategy configuration
     */
    StrategyConfig getConfig();
    
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
     * Get minimum required number of candles for strategy initialization
     * Example: MA crossover strategy might require 200 candles for longer MA
     */
    int getMinRequiredCandles();
    
    // ============================================================
    // VISUALIZATION & CHARTING
    // ============================================================
    
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
