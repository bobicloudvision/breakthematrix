package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.account.AccountManager;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskManager {
    
    private final Map<String, BigDecimal> symbolExposure = new ConcurrentHashMap<>();
    private final AccountManager accountManager;
    
    // Risk parameters - Position limits
    private BigDecimal maxPositionSize = new BigDecimal("10000"); // $10,000 max per position
    private BigDecimal maxTotalExposure = new BigDecimal("50000"); // $50,000 max total exposure
    private BigDecimal maxSymbolExposure = new BigDecimal("20000"); // $20,000 max per symbol
    private BigDecimal maxDailyLoss = new BigDecimal("5000"); // $5,000 max daily loss
    
    // Trade frequency limits
    private int maxDailyTrades = 50; // Max 50 trades per day
    private int maxConcurrentPositions = 10; // Max 10 open positions at once
    private int dailyTradeCount = 0;
    private java.time.LocalDate currentTradeDay = java.time.LocalDate.now();

    public RiskManager(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    /**
     * Validate if an order meets risk management criteria
     */
    public boolean validateOrder(Order order) {
        try {
            TradingAccount activeAccount = accountManager.getActiveAccount();
            if (activeAccount == null) {
                System.out.println("❌ Risk: No active account");
                return false;
            }
            
            // Reset daily counter if new day
            resetDailyCounterIfNewDay();
            
            // Check daily trade limit (only for BUY orders that open new positions)
            if (order.getSide() == OrderSide.BUY) {
                if (dailyTradeCount >= maxDailyTrades) {
                    System.out.println("❌ Risk: Daily trade limit reached (" + maxDailyTrades + " trades)");
                    return false;
                }
            }
            
            // Check concurrent position limit (only for BUY orders)
            if (order.getSide() == OrderSide.BUY) {
                int openPositions = activeAccount.getOpenPositions().size();
                if (openPositions >= maxConcurrentPositions) {
                    System.out.println("❌ Risk: Max concurrent positions reached (" + maxConcurrentPositions + " positions)");
                    return false;
                }
            }
            
            // Check position size limit
            BigDecimal orderValue = order.getQuantity().multiply(order.getPrice());
            if (orderValue.compareTo(maxPositionSize) > 0) {
                System.out.println("❌ Risk: Order exceeds max position size: " + orderValue);
                return false;
            }

            // Check symbol exposure limit
            BigDecimal currentSymbolExposure = symbolExposure.getOrDefault(order.getSymbol(), BigDecimal.ZERO);
            BigDecimal newSymbolExposure = order.getSide() == OrderSide.BUY ? 
                currentSymbolExposure.add(orderValue) : 
                currentSymbolExposure.subtract(orderValue);
            
            if (newSymbolExposure.abs().compareTo(maxSymbolExposure) > 0) {
                System.out.println("❌ Risk: Symbol exposure limit exceeded for " + order.getSymbol());
                return false;
            }

            // Check total portfolio exposure
            BigDecimal totalExposure = activeAccount.getTotalExposure();
            if (order.getSide() == OrderSide.BUY && totalExposure.add(orderValue).compareTo(maxTotalExposure) > 0) {
                System.out.println("❌ Risk: Total exposure limit exceeded");
                return false;
            }

            // Check daily loss limit
            BigDecimal dailyPnL = activeAccount.getDailyPnL();
            if (dailyPnL.compareTo(maxDailyLoss.negate()) < 0) {
                System.out.println("❌ Risk: Daily loss limit reached");
                return false;
            }

            // Check account balance
            BigDecimal availableBalance = activeAccount.getAvailableBalance();
            if (order.getSide() == OrderSide.BUY && orderValue.compareTo(availableBalance) > 0) {
                System.out.println("❌ Risk: Insufficient balance for order");
                return false;
            }

            // Update symbol exposure tracking
            symbolExposure.put(order.getSymbol(), newSymbolExposure);
            
            // Increment daily trade counter for BUY orders (new positions)
            if (order.getSide() == OrderSide.BUY) {
                dailyTradeCount++;
                System.out.println("📊 Daily trades: " + dailyTradeCount + "/" + maxDailyTrades);
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Risk validation error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Reset daily trade counter if it's a new day
     */
    private void resetDailyCounterIfNewDay() {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!today.equals(currentTradeDay)) {
            dailyTradeCount = 0;
            currentTradeDay = today;
            System.out.println("🔄 Daily trade counter reset for new day: " + today);
        }
    }

    /**
     * Calculate position size based on risk parameters
     */
    public BigDecimal calculatePositionSize(String symbol, BigDecimal price, BigDecimal riskPercentage) {
        TradingAccount activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal accountBalance = activeAccount.getBalance();
        BigDecimal riskAmount = accountBalance.multiply(riskPercentage);
        
        // Don't exceed max position size
        BigDecimal maxRisk = maxPositionSize.min(riskAmount);
        
        return maxRisk.divide(price, 8, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate stop loss price based on risk parameters
     * This calculates a default stop loss based on risk percentage
     * 
     * @param entryPrice Entry price of the position
     * @param side Position side (LONG/SHORT)
     * @param riskPercentage Maximum risk percentage (e.g., 0.02 for 2%)
     * @return Calculated stop loss price
     */
    public BigDecimal calculateStopLoss(BigDecimal entryPrice, OrderSide side, BigDecimal riskPercentage) {
        if (side == OrderSide.BUY) {
            // For LONG positions: stop loss below entry price
            return entryPrice.multiply(BigDecimal.ONE.subtract(riskPercentage))
                .setScale(8, BigDecimal.ROUND_HALF_UP);
        } else {
            // For SHORT positions: stop loss above entry price
            return entryPrice.multiply(BigDecimal.ONE.add(riskPercentage))
                .setScale(8, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Validate and adjust stop loss to meet risk management requirements
     * This ensures stop loss doesn't exceed maximum allowed risk
     * 
     * @param entryPrice Entry price of the position
     * @param stopLoss Proposed stop loss price from strategy
     * @param quantity Position quantity
     * @param side Position side (LONG/SHORT)
     * @return Validated and adjusted stop loss price
     */
    public BigDecimal validateStopLoss(BigDecimal entryPrice, BigDecimal stopLoss, 
                                      BigDecimal quantity, OrderSide side) {
        if (stopLoss == null) {
            // If no stop loss provided, calculate default 2% risk
            return calculateStopLoss(entryPrice, side, new BigDecimal("0.02"));
        }
        
        // Calculate risk amount
        BigDecimal priceDiff = side == OrderSide.BUY 
            ? entryPrice.subtract(stopLoss)  // For LONG
            : stopLoss.subtract(entryPrice);  // For SHORT
            
        BigDecimal riskAmount = priceDiff.multiply(quantity);
        
        // Check if risk exceeds position size limit
        TradingAccount activeAccount = accountManager.getActiveAccount();
        if (activeAccount != null) {
            BigDecimal maxRiskPerPosition = activeAccount.getBalance()
                .multiply(new BigDecimal("0.02")); // Max 2% risk per trade
            
            if (riskAmount.compareTo(maxRiskPerPosition) > 0) {
                // Adjust stop loss to meet maximum risk
                BigDecimal adjustedDiff = maxRiskPerPosition.divide(quantity, 8, BigDecimal.ROUND_HALF_UP);
                
                if (side == OrderSide.BUY) {
                    stopLoss = entryPrice.subtract(adjustedDiff);
                } else {
                    stopLoss = entryPrice.add(adjustedDiff);
                }
                
                System.out.println("⚠️ Stop loss adjusted to meet risk limits: " + stopLoss);
            }
        }
        
        return stopLoss;
    }

    /**
     * Calculate position size based on stop loss distance
     * This is the preferred method for position sizing
     * 
     * @param accountBalance Account balance
     * @param entryPrice Entry price
     * @param stopLoss Stop loss price
     * @param riskPercentage Risk percentage of account (e.g., 0.01 for 1%)
     * @return Position size
     */
    public BigDecimal calculatePositionSizeFromStopLoss(BigDecimal accountBalance, 
                                                       BigDecimal entryPrice,
                                                       BigDecimal stopLoss,
                                                       BigDecimal riskPercentage) {
        BigDecimal riskAmount = accountBalance.multiply(riskPercentage);
        BigDecimal stopDistance = entryPrice.subtract(stopLoss).abs();
        
        if (stopDistance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return riskAmount.divide(stopDistance, 8, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Check if we should apply emergency stop
     */
    public boolean shouldEmergencyStop() {
        TradingAccount activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            return false;
        }
        
        BigDecimal dailyPnL = activeAccount.getDailyPnL();
        BigDecimal totalBalance = activeAccount.getBalance();
        
        // Emergency  if daily loss exceeds 10% of account
        BigDecimal emergencyThreshold = totalBalance.multiply(new BigDecimal("0.10"));
        
        return dailyPnL.compareTo(emergencyThreshold.negate()) < 0;
    }

    // Getters and setters for risk parameters
    public BigDecimal getMaxPositionSize() { return maxPositionSize; }
    public void setMaxPositionSize(BigDecimal maxPositionSize) { this.maxPositionSize = maxPositionSize; }
    
    public BigDecimal getMaxTotalExposure() { return maxTotalExposure; }
    public void setMaxTotalExposure(BigDecimal maxTotalExposure) { this.maxTotalExposure = maxTotalExposure; }
    
    public BigDecimal getMaxSymbolExposure() { return maxSymbolExposure; }
    public void setMaxSymbolExposure(BigDecimal maxSymbolExposure) { this.maxSymbolExposure = maxSymbolExposure; }
    
    public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
    public void setMaxDailyLoss(BigDecimal maxDailyLoss) { this.maxDailyLoss = maxDailyLoss; }
    
    public int getMaxDailyTrades() { return maxDailyTrades; }
    public void setMaxDailyTrades(int maxDailyTrades) { this.maxDailyTrades = maxDailyTrades; }
    
    public int getMaxConcurrentPositions() { return maxConcurrentPositions; }
    public void setMaxConcurrentPositions(int maxConcurrentPositions) { this.maxConcurrentPositions = maxConcurrentPositions; }
    
    public int getDailyTradeCount() { 
        resetDailyCounterIfNewDay();
        return dailyTradeCount; 
    }

    public Map<String, BigDecimal> getSymbolExposures() {
        return Map.copyOf(symbolExposure);
    }

    public RiskMetrics getRiskMetrics() {
        TradingAccount activeAccount = accountManager.getActiveAccount();
        
        BigDecimal totalExposure = activeAccount != null ? activeAccount.getTotalExposure() : BigDecimal.ZERO;
        BigDecimal dailyPnL = activeAccount != null ? activeAccount.getDailyPnL() : BigDecimal.ZERO;
        int openPositions = activeAccount != null ? activeAccount.getOpenPositions().size() : 0;
        
        resetDailyCounterIfNewDay();
        
        return new RiskMetrics(
            totalExposure,
            maxTotalExposure,
            dailyPnL,
            maxDailyLoss,
            openPositions,
            dailyTradeCount,
            maxDailyTrades,
            maxConcurrentPositions
        );
    }

    public static class RiskMetrics {
        private final BigDecimal currentExposure;
        private final BigDecimal maxExposure;
        private final BigDecimal dailyPnL;
        private final BigDecimal maxDailyLoss;
        private final int activePositions;
        private final int dailyTradeCount;
        private final int maxDailyTrades;
        private final int maxConcurrentPositions;

        public RiskMetrics(BigDecimal currentExposure, BigDecimal maxExposure, 
                          BigDecimal dailyPnL, BigDecimal maxDailyLoss, int activePositions,
                          int dailyTradeCount, int maxDailyTrades, int maxConcurrentPositions) {
            this.currentExposure = currentExposure;
            this.maxExposure = maxExposure;
            this.dailyPnL = dailyPnL;
            this.maxDailyLoss = maxDailyLoss;
            this.activePositions = activePositions;
            this.dailyTradeCount = dailyTradeCount;
            this.maxDailyTrades = maxDailyTrades;
            this.maxConcurrentPositions = maxConcurrentPositions;
        }

        public BigDecimal getCurrentExposure() { return currentExposure; }
        public BigDecimal getMaxExposure() { return maxExposure; }
        public BigDecimal getDailyPnL() { return dailyPnL; }
        public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
        public int getActivePositions() { return activePositions; }
        public int getDailyTradeCount() { return dailyTradeCount; }
        public int getMaxDailyTrades() { return maxDailyTrades; }
        public int getMaxConcurrentPositions() { return maxConcurrentPositions; }

        public BigDecimal getExposureUtilization() {
            if (maxExposure.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return currentExposure.divide(maxExposure, 4, BigDecimal.ROUND_HALF_UP);
        }
    }
}
