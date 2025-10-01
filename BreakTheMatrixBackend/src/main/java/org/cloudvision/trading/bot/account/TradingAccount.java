package org.cloudvision.trading.bot.account;

import org.cloudvision.trading.bot.model.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Trading Account Interface
 * Represents a trading account (paper or live)
 */
public interface TradingAccount {
    
    /**
     * Get account ID
     */
    String getAccountId();
    
    /**
     * Get account name
     */
    String getAccountName();
    
    /**
     * Get account type
     */
    AccountType getAccountType();
    
    /**
     * Execute an order
     * @return Executed order with updated status
     */
    Order executeOrder(Order order);
    
    /**
     * Cancel an order
     */
    boolean cancelOrder(String orderId);
    
    /**
     * Get account balance (total value including all assets)
     */
    BigDecimal getBalance();
    
    /**
     * Get available balance (free cash for trading)
     */
    BigDecimal getAvailableBalance();
    
    /**
     * Get balance for specific asset
     */
    BigDecimal getAssetBalance(String asset);
    
    /**
     * Get all asset balances
     */
    Map<String, BigDecimal> getAllBalances();
    
    /**
     * Get total exposure (value of all positions)
     */
    BigDecimal getTotalExposure();
    
    /**
     * Get daily profit/loss
     */
    BigDecimal getDailyPnL();
    
    /**
     * Get order by ID
     */
    Order getOrder(String orderId);
    
    /**
     * Get all orders
     */
    List<Order> getAllOrders();
    
    /**
     * Get open orders
     */
    List<Order> getOpenOrders();
    
    /**
     * Get filled orders (completed trades)
     */
    List<Order> getFilledOrders();
    
    /**
     * Get total profit/loss
     */
    BigDecimal getTotalPnL();
    
    /**
     * Get account statistics
     */
    AccountStats getAccountStats();
    
    /**
     * Reset account (paper trading only)
     */
    void reset();
    
    /**
     * Check if account is enabled
     */
    boolean isEnabled();
    
    /**
     * Enable/disable account
     */
    void setEnabled(boolean enabled);
    
    // Position management
    
    /**
     * Get all open positions
     */
    List<Position> getOpenPositions();
    
    /**
     * Get open positions for a specific symbol
     */
    List<Position> getOpenPositionsBySymbol(String symbol);
    
    /**
     * Get position by ID
     */
    Position getPosition(String positionId);
    
    /**
     * Get position history (closed positions)
     */
    List<Position> getPositionHistory();
    
    /**
     * Get position manager
     */
    PositionManager getPositionManager();
    
    /**
     * Update current prices for all open positions
     * This updates unrealized P&L and checks stop loss/take profit
     * @param currentPrices Map of symbol -> current price
     */
    void updateCurrentPrices(Map<String, BigDecimal> currentPrices);
}

