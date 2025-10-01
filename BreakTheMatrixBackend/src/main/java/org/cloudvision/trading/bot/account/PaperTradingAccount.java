package org.cloudvision.trading.bot.account;

import org.cloudvision.trading.bot.model.Order;
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
    private final Map<String, BigDecimal> balances; // Asset -> Balance
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
        this.balances = new ConcurrentHashMap<>();
        this.orders = new ConcurrentHashMap<>();
        this.tradeHistory = Collections.synchronizedList(new ArrayList<>());
        this.positionManager = new PositionManager(); // Initialize position manager
        this.createdAt = Instant.now();
        this.enabled = true;
        this.dailyStartBalance = initialBalance;
        this.currentDay = java.time.LocalDate.now();
        
        // Initialize with USDT balance
        balances.put("USDT", initialBalance);
        
        System.out.println("💰 Paper Trading Account created: " + accountName + 
                         " with $" + initialBalance);
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
            
            // Extract base and quote assets (e.g., BTCUSDT -> BTC, USDT)
            String baseAsset = extractBaseAsset(order.getSymbol());
            String quoteAsset = extractQuoteAsset(order.getSymbol());
            
            BigDecimal orderValue = order.getPrice().multiply(order.getQuantity());
            
            // Check if we have sufficient balance
            switch (order.getSide()) {
                case BUY:
                    BigDecimal quoteBalance = balances.getOrDefault(quoteAsset, BigDecimal.ZERO);
                    if (quoteBalance.compareTo(orderValue) < 0) {
                        order.setStatus(OrderStatus.REJECTED);
                        System.err.println("❌ Insufficient " + quoteAsset + " balance");
                        return order;
                    }
                    
                    // Execute buy
                    balances.put(quoteAsset, quoteBalance.subtract(orderValue));
                    balances.put(baseAsset, 
                        balances.getOrDefault(baseAsset, BigDecimal.ZERO).add(order.getQuantity()));
                    
                    // Open LONG position
                    Position longPosition = positionManager.openPosition(order.getSymbol(), PositionSide.LONG, 
                        order.getPrice(), order.getQuantity());
                    
                    // Set stop loss and take profit if suggested by strategy
                    if (order.getSuggestedStopLoss() != null) {
                        longPosition.setStopLoss(order.getSuggestedStopLoss());
                        System.out.println("🛡️ Stop loss set at: " + order.getSuggestedStopLoss());
                    }
                    if (order.getSuggestedTakeProfit() != null) {
                        longPosition.setTakeProfit(order.getSuggestedTakeProfit());
                        System.out.println("🎯 Take profit set at: " + order.getSuggestedTakeProfit());
                    }
                    
                    longPosition.setStrategyId(order.getStrategyId());
                    break;
                    
                case SELL:
                    BigDecimal baseBalance = balances.getOrDefault(baseAsset, BigDecimal.ZERO);
                    if (baseBalance.compareTo(order.getQuantity()) < 0) {
                        order.setStatus(OrderStatus.REJECTED);
                        System.err.println("❌ Insufficient " + baseAsset + " balance");
                        return order;
                    }
                    
                    // Execute sell
                    balances.put(baseAsset, baseBalance.subtract(order.getQuantity()));
                    balances.put(quoteAsset, 
                        balances.getOrDefault(quoteAsset, BigDecimal.ZERO).add(orderValue));
                    
                    // Close positions (FIFO - close oldest first)
                    List<Position> openPositions = positionManager.getOpenPositionsBySymbol(order.getSymbol());
                    BigDecimal remainingQty = order.getQuantity();
                    for (Position position : openPositions) {
                        if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                        
                        BigDecimal closeQty = remainingQty.min(position.getQuantity());
                        positionManager.closePosition(position.getPositionId(), order.getPrice(), closeQty);
                        remainingQty = remainingQty.subtract(closeQty);
                    }
                    break;
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
        return balances.getOrDefault("USDT", BigDecimal.ZERO);
    }
    
    @Override
    public BigDecimal getAvailableBalance() {
        // Available balance is the USDT balance (free cash)
        return balances.getOrDefault("USDT", BigDecimal.ZERO);
    }
    
    @Override
    public BigDecimal getAssetBalance(String asset) {
        return balances.getOrDefault(asset, BigDecimal.ZERO);
    }
    
    @Override
    public Map<String, BigDecimal> getAllBalances() {
        return new HashMap<>(balances);
    }
    
    @Override
    public BigDecimal getTotalExposure() {
        // Total exposure is the sum of all non-USDT positions
        // Note: For accurate calculation, we'd need current market prices
        // For now, return 0 as positions are tracked in crypto amounts
        // In a real implementation, this would multiply each asset balance by current price
        return BigDecimal.ZERO;
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
        // Calculate current balance vs initial balance
        BigDecimal currentValue = getBalance();
        return currentValue.subtract(initialBalance);
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
        System.out.println("🔄 Resetting paper trading account: " + accountName);
        balances.clear();
        balances.put("USDT", initialBalance);
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
        System.out.println("✅ Account reset complete");
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
    private String extractBaseAsset(String symbol) {
        // BTCUSDT -> BTC
        if (symbol.endsWith("USDT")) {
            return symbol.substring(0, symbol.length() - 4);
        }
        // Add more logic for other quote assets if needed
        return symbol.substring(0, symbol.length() / 2);
    }
    
    private String extractQuoteAsset(String symbol) {
        // BTCUSDT -> USDT
        if (symbol.endsWith("USDT")) {
            return "USDT";
        }
        // Add more logic for other quote assets if needed
        return "USDT";
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

