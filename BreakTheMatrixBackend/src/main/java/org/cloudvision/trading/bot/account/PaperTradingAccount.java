package org.cloudvision.trading.bot.account;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper Trading Account
 * Simulates order execution with virtual money
 */
@Component
public class PaperTradingAccount implements TradingAccount {
    
    private final String accountId;
    private final String accountName;
    private final BigDecimal initialBalance;
    private BigDecimal balance; // FUTURES: Single USDT balance
    private BigDecimal lockedMargin; // Margin locked in open positions
    private final Map<String, Order> orders; // OrderId -> Order
    private final List<Trade> tradeHistory;
    private final PositionManager positionManager; // Position tracking
    private final Instant createdAt;
    private boolean enabled;
    
    // Statistics tracking
    private BigDecimal totalPnL = BigDecimal.ZERO;
    private BigDecimal realizedPnL = BigDecimal.ZERO;
    private int totalTrades = 0;
    private int winningTrades = 0;
    private int losingTrades = 0;
    private BigDecimal largestWin = BigDecimal.ZERO;
    private BigDecimal largestLoss = BigDecimal.ZERO;
    private Instant lastTradeAt;
    
    // Daily tracking
    private BigDecimal dailyStartBalance;
    private java.time.LocalDate currentDay;
    
    public PaperTradingAccount() {
        this("paper-main", "PaperTradePRO", new BigDecimal("600000")); // $600k default
    }
    
    public PaperTradingAccount(String accountId, String accountName, BigDecimal initialBalance) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.initialBalance = initialBalance;
        this.balance = initialBalance; // FUTURES: All money in USDT
        this.lockedMargin = BigDecimal.ZERO;
        this.orders = new ConcurrentHashMap<>();
        this.tradeHistory = Collections.synchronizedList(new ArrayList<>());
        this.positionManager = new PositionManager(); // Initialize position manager
        this.createdAt = Instant.now();
        this.enabled = true;
        this.dailyStartBalance = initialBalance;
        this.currentDay = java.time.LocalDate.now();
        
        // Register listener for automatic position closes (stop loss/take profit)
        this.positionManager.setPositionCloseListener((position, pnl, closePrice, closedQuantity) -> {
            updateAccountStatsOnClose(pnl);
            unlockMarginAndApplyPnL(position, pnl);
        });
        
        System.out.println("💰 Futures Trading Account created: " + accountName + 
                         " with $" + initialBalance + " USDT");
    }
    
    @Override
    public String getAccountId() {
        return accountId;
    }
    
    @Override
    public String getAccountName() {
        return accountName;
    }
    
    @Override
    public AccountType getAccountType() {
        return AccountType.PAPER_TRADING;
    }
    
    @Override
    public Order executeOrder(Order order) {
        if (!enabled) {
            order.setStatus(OrderStatus.REJECTED);
            System.err.println("❌ Account " + accountName + " is disabled");
            return order;
        }
        
        try {
            // Simulate order execution
            System.out.println("📝 [PAPER] Executing order: " + order.getSymbol() + 
                             " " + order.getSide() + " " + order.getQuantity() + 
                             " @ " + order.getPrice());
            
            // FUTURES TRADING: Calculate position value (margin required)
            BigDecimal positionValue = order.getPrice().multiply(order.getQuantity());
            PositionSide positionSide = order.getPositionSide() != null ? order.getPositionSide() : PositionSide.LONG;
            
            // Determine order intent
            boolean isOpening = (order.getSide() == OrderSide.BUY && positionSide == PositionSide.LONG) ||
                               (order.getSide() == OrderSide.SELL && positionSide == PositionSide.SHORT);
            
            if (isOpening) {
                // OPENING POSITION (LONG or SHORT) - Lock margin
                BigDecimal availableBalance = balance.subtract(lockedMargin);
                if (availableBalance.compareTo(positionValue) < 0) {
                    order.setStatus(OrderStatus.REJECTED);
                    System.err.println("❌ Insufficient available balance (margin)");
                    System.err.println("   Available: $" + availableBalance + " | Required: $" + positionValue);
                    return order;
                }
                
                // Lock margin for this position
                lockedMargin = lockedMargin.add(positionValue);
                
                // Open position (LONG or SHORT)
                Position newPosition = positionManager.openPosition(order.getSymbol(), positionSide, 
                    order.getPrice(), order.getQuantity());
                
                // Set stop loss and take profit if suggested by strategy
                if (order.getSuggestedStopLoss() != null) {
                    newPosition.setStopLoss(order.getSuggestedStopLoss());
                    System.out.println("🛡️ Stop loss set at: " + order.getSuggestedStopLoss());
                }
                if (order.getSuggestedTakeProfit() != null) {
                    newPosition.setTakeProfit(order.getSuggestedTakeProfit());
                    System.out.println("🎯 Take profit set at: " + order.getSuggestedTakeProfit());
                }
                
                newPosition.setStrategyId(order.getStrategyId());
                
                String positionType = positionSide == PositionSide.LONG ? "LONG" : "SHORT";
                System.out.println("💰 Opened " + positionType + " position | Margin locked: $" + positionValue + 
                    " | Total locked: $" + lockedMargin);
                
            } else {
                // CLOSING POSITION (LONG or SHORT) - Unlock margin and apply P&L
                List<Position> openPositions = positionManager.getOpenPositionsBySymbol(order.getSymbol()).stream()
                    .filter(p -> p.getSide() == positionSide)  // Filter by position side
                    .toList();
                    
                if (openPositions.isEmpty()) {
                    order.setStatus(OrderStatus.REJECTED);
                    String positionType = positionSide == PositionSide.LONG ? "LONG" : "SHORT";
                    System.err.println("❌ No open " + positionType + " positions to close for " + order.getSymbol());
                    return order;
                }
                
                // Close positions (FIFO - close oldest first)
                BigDecimal remainingQty = order.getQuantity();
                for (Position position : openPositions) {
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                    
                    BigDecimal closeQty = remainingQty.min(position.getQuantity());
                    BigDecimal pnlBeforeClose = position.getRealizedPnL();
                    
                    // Close the position
                    positionManager.closePosition(position.getPositionId(), order.getPrice(), closeQty);
                    
                    // Calculate P&L from this close
                    BigDecimal positionPnL = position.getRealizedPnL().subtract(pnlBeforeClose);
                    
                    // Unlock margin proportionally
                    BigDecimal marginToUnlock = position.getEntryPrice().multiply(closeQty);
                    lockedMargin = lockedMargin.subtract(marginToUnlock);
                    
                    // Apply P&L to balance
                    balance = balance.add(positionPnL);
                    
                    // Update account statistics
                    updateAccountStatsOnClose(positionPnL);
                    
                    String positionType = position.getSide() == PositionSide.LONG ? "LONG" : "SHORT";
                    System.out.println("💰 Closed " + positionType + " | Margin unlocked: $" + marginToUnlock + 
                        " | P&L: $" + positionPnL + " | New balance: $" + balance);
                    
                    remainingQty = remainingQty.subtract(closeQty);
                }
            }
            
            // Update order status
            order.setStatus(OrderStatus.FILLED);
            order.setExecutedQuantity(order.getQuantity());
            order.setExecutedPrice(order.getPrice());
            order.setExecutedAt(Instant.now());
            
            // Store order
            orders.put(order.getId(), order);
            
            // Record trade
            Trade trade = new Trade(order, Instant.now());
            tradeHistory.add(trade);
            totalTrades++;
            lastTradeAt = Instant.now();
            
            System.out.println("✅ [PAPER] Order executed: " + order.getId());
            
            return order;
            
        } catch (Exception e) {
            System.err.println("❌ Error executing paper order: " + e.getMessage());
            order.setStatus(OrderStatus.REJECTED);
            return order;
        }
    }
    
    @Override
    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null && (order.getStatus() == OrderStatus.PENDING || 
                             order.getStatus() == OrderStatus.SUBMITTED)) {
            order.setStatus(OrderStatus.CANCELLED);
            System.out.println("🚫 [PAPER] Order cancelled: " + orderId);
            return true;
        }
        return false;
    }
    
    @Override
    public BigDecimal getBalance() {
        // FUTURES: Total balance in USDT
        return balance;
    }
    
    @Override
    public BigDecimal getAvailableBalance() {
        // FUTURES: Available balance = Total - Locked margin
        return balance.subtract(lockedMargin);
    }
    
    @Override
    public BigDecimal getAssetBalance(String asset) {
        // FUTURES: Only USDT balance exists
        if ("USDT".equalsIgnoreCase(asset)) {
            return balance;
        }
        return BigDecimal.ZERO;
    }
    
    @Override
    public Map<String, BigDecimal> getAllBalances() {
        // FUTURES: Return balance breakdown
        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put("USDT", balance);
        balances.put("available", getAvailableBalance());
        balances.put("locked", lockedMargin);
        return balances;
    }
    
    @Override
    public BigDecimal getTotalExposure() {
        // FUTURES: Total exposure is the locked margin (value of all open positions)
        return lockedMargin;
    }
    
    @Override
    public BigDecimal getDailyPnL() {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!today.equals(currentDay)) {
            // New day - reset daily tracking
            currentDay = today;
            dailyStartBalance = getBalance();
        }
        
        return getBalance().subtract(dailyStartBalance);
    }
    
    @Override
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }
    
    @Override
    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
    }
    
    @Override
    public List<Order> getOpenOrders() {
        return orders.values().stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING || 
                        o.getStatus() == OrderStatus.SUBMITTED || 
                        o.getStatus() == OrderStatus.PARTIALLY_FILLED)
            .toList();
    }
    
    @Override
    public List<Order> getFilledOrders() {
        return orders.values().stream()
            .filter(o -> o.getStatus() == OrderStatus.FILLED)
            .toList();
    }
    
    @Override
    public BigDecimal getTotalPnL() {
        // Total P&L = Realized P&L (from closed positions) + Unrealized P&L (from open positions)
        BigDecimal unrealizedPnL = positionManager.getTotalUnrealizedPnL();
        return realizedPnL.add(unrealizedPnL);
    }
    
    @Override
    public AccountStats getAccountStats() {
        BigDecimal currentBalance = getBalance();
        BigDecimal pnl = getTotalPnL();
        
        // Calculate win rate
        BigDecimal winRate = totalTrades > 0 ? 
            new BigDecimal(winningTrades).divide(new BigDecimal(totalTrades), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")) : 
            BigDecimal.ZERO;
        
        // Calculate average win/loss
        BigDecimal avgWin = winningTrades > 0 ? 
            largestWin.divide(new BigDecimal(winningTrades), 8, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        BigDecimal avgLoss = losingTrades > 0 ? 
            largestLoss.divide(new BigDecimal(losingTrades), 8, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        // Calculate profit factor
        BigDecimal profitFactor = avgLoss.compareTo(BigDecimal.ZERO) > 0 ? 
            avgWin.divide(avgLoss.abs(), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        return new AccountStats(
            accountId, initialBalance, currentBalance, pnl, realizedPnL, BigDecimal.ZERO,
            totalTrades, winningTrades, losingTrades, winRate,
            largestWin, largestLoss, avgWin, avgLoss, profitFactor,
            createdAt, lastTradeAt
        );
    }
    
    @Override
    public void reset() {
        System.out.println("🔄 Resetting futures trading account: " + accountName);
        balance = initialBalance;
        lockedMargin = BigDecimal.ZERO;
        orders.clear();
        tradeHistory.clear();
        positionManager.reset(); // Reset positions
        totalPnL = BigDecimal.ZERO;
        realizedPnL = BigDecimal.ZERO;
        totalTrades = 0;
        winningTrades = 0;
        losingTrades = 0;
        largestWin = BigDecimal.ZERO;
        largestLoss = BigDecimal.ZERO;
        lastTradeAt = null;
        System.out.println("✅ Account reset complete - Balance: $" + balance);
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        System.out.println(enabled ? "✅" : "🚫" + " Paper trading account " + 
                         (enabled ? "enabled" : "disabled"));
    }
    
    // Position management methods
    
    @Override
    public List<Position> getOpenPositions() {
        return positionManager.getOpenPositions();
    }
    
    @Override
    public List<Position> getOpenPositionsBySymbol(String symbol) {
        return positionManager.getOpenPositionsBySymbol(symbol);
    }
    
    @Override
    public Position getPosition(String positionId) {
        return positionManager.getPosition(positionId);
    }
    
    @Override
    public List<Position> getPositionHistory() {
        return positionManager.getPositionHistory();
    }
    
    @Override
    public PositionManager getPositionManager() {
        return positionManager;
    }
    
    // Helper methods
    
    /**
     * Update account statistics when a position is closed
     */
    private void updateAccountStatsOnClose(BigDecimal positionPnL) {
        realizedPnL = realizedPnL.add(positionPnL);
        
        if (positionPnL.compareTo(BigDecimal.ZERO) > 0) {
            winningTrades++;
            if (positionPnL.compareTo(largestWin) > 0) {
                largestWin = positionPnL;
            }
        } else if (positionPnL.compareTo(BigDecimal.ZERO) < 0) {
            losingTrades++;
            if (positionPnL.compareTo(largestLoss) < 0) {
                largestLoss = positionPnL;
            }
        }
        
        System.out.println("📊 Account stats updated - Realized P&L: " + realizedPnL + 
            " | Win/Loss: " + winningTrades + "/" + losingTrades);
    }
    
    /**
     * FUTURES: Unlock margin and apply P&L when position is automatically closed (stop loss/take profit)
     */
    private void unlockMarginAndApplyPnL(Position position, BigDecimal pnl) {
        if (position.isOpen()) {
            return; // Only process fully closed positions
        }
        
        // Get the original position value (margin that was locked)
        BigDecimal marginToUnlock = position.getEntryValue();
        
        // Unlock the margin
        lockedMargin = lockedMargin.subtract(marginToUnlock);
        
        // Apply P&L to balance
        balance = balance.add(pnl);
        
        // Create synthetic order for order history
        createSyntheticCloseOrder(position);
        
        System.out.println("🔓 Auto-close: Unlocked margin $" + marginToUnlock + 
            " | P&L: $" + pnl + " | New balance: $" + balance + 
            " | Available: $" + getAvailableBalance());
    }
    
    /**
     * Create a synthetic order for automatic position closes (stop loss/take profit)
     * This ensures all position closes are visible in order history
     */
    private void createSyntheticCloseOrder(Position position) {
        // Determine order side based on position side
        // LONG position closes with SELL order
        // SHORT position closes with BUY order
        OrderSide orderSide = position.getSide() == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY;
        
        Order syntheticOrder = new Order(
            java.util.UUID.randomUUID().toString(),
            position.getSymbol(),
            org.cloudvision.trading.bot.model.OrderType.MARKET,
            orderSide,
            position.getOriginalQuantity(),  // Use original quantity
            position.getExitPrice(),
            position.getStrategyId() != null ? position.getStrategyId() : "auto-close"
        );
        
        syntheticOrder.setPositionSide(position.getSide());
        syntheticOrder.setStatus(OrderStatus.FILLED);
        syntheticOrder.setExecutedQuantity(position.getOriginalQuantity());
        syntheticOrder.setExecutedPrice(position.getExitPrice());
        syntheticOrder.setExecutedAt(position.getExitTime());
        
        // Store in order history
        orders.put(syntheticOrder.getId(), syntheticOrder);
        
        String closeReason = position.getStopLoss() != null && 
            position.getExitPrice().compareTo(position.getStopLoss()) <= 0 ? "Stop Loss" : "Take Profit";
        
        System.out.println("📝 Created synthetic order for " + closeReason + ": " + syntheticOrder.getId());
    }
    
    /**
     * Inner class to track individual trades
     */
    private static class Trade {
        private final Order order;
        private final Instant executionTime;
        
        public Trade(Order order, Instant executionTime) {
            this.order = order;
            this.executionTime = executionTime;
        }
        
        public Order getOrder() { return order; }
        public Instant getExecutionTime() { return executionTime; }
    }
    
    @Override
    public void updateCurrentPrices(Map<String, BigDecimal> currentPrices) {
        // Update unrealized P&L for all open positions
        positionManager.updatePrices(currentPrices);
    }
}

