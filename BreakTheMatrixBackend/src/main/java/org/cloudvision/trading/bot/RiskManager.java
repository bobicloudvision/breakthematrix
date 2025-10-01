package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskManager {
    
    private final Map<String, BigDecimal> symbolExposure = new ConcurrentHashMap<>();
    private final PortfolioManager portfolioManager;
    
    // Risk parameters
    private BigDecimal maxPositionSize = new BigDecimal("10000"); // $10,000 max per position
    private BigDecimal maxTotalExposure = new BigDecimal("50000"); // $50,000 max total exposure
    private BigDecimal maxSymbolExposure = new BigDecimal("20000"); // $20,000 max per symbol
    private BigDecimal maxDailyLoss = new BigDecimal("5000"); // $5,000 max daily loss

    public RiskManager(PortfolioManager portfolioManager) {
        this.portfolioManager = portfolioManager;
    }

    /**
     * Validate if an order meets risk management criteria
     */
    public boolean validateOrder(Order order) {
        try {
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
            BigDecimal totalExposure = portfolioManager.getTotalExposure();
            if (order.getSide() == OrderSide.BUY && totalExposure.add(orderValue).compareTo(maxTotalExposure) > 0) {
                System.out.println("❌ Risk: Total exposure limit exceeded");
                return false;
            }

            // Check daily loss limit
            BigDecimal dailyPnL = portfolioManager.getDailyPnL();
            if (dailyPnL.compareTo(maxDailyLoss.negate()) < 0) {
                System.out.println("❌ Risk: Daily loss limit reached");
                return false;
            }

            // Check account balance
            BigDecimal availableBalance = portfolioManager.getAvailableBalance();
            if (order.getSide() == OrderSide.BUY && orderValue.compareTo(availableBalance) > 0) {
                System.out.println("❌ Risk: Insufficient balance for order");
                return false;
            }

            // Update symbol exposure tracking
            symbolExposure.put(order.getSymbol(), newSymbolExposure);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Risk validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Calculate position size based on risk parameters
     */
    public BigDecimal calculatePositionSize(String symbol, BigDecimal price, BigDecimal riskPercentage) {
        BigDecimal accountBalance = portfolioManager.getTotalBalance();
        BigDecimal riskAmount = accountBalance.multiply(riskPercentage);
        
        // Don't exceed max position size
        BigDecimal maxRisk = maxPositionSize.min(riskAmount);
        
        return maxRisk.divide(price, 8, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Check if we should apply emergency stop
     */
    public boolean shouldEmergencyStop() {
        BigDecimal dailyPnL = portfolioManager.getDailyPnL();
        BigDecimal totalBalance = portfolioManager.getTotalBalance();
        
        // Emergency stop if daily loss exceeds 10% of account
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

    public Map<String, BigDecimal> getSymbolExposures() {
        return Map.copyOf(symbolExposure);
    }

    public RiskMetrics getRiskMetrics() {
        return new RiskMetrics(
            portfolioManager.getTotalExposure(),
            maxTotalExposure,
            portfolioManager.getDailyPnL(),
            maxDailyLoss,
            symbolExposure.size()
        );
    }

    public static class RiskMetrics {
        private final BigDecimal currentExposure;
        private final BigDecimal maxExposure;
        private final BigDecimal dailyPnL;
        private final BigDecimal maxDailyLoss;
        private final int activePositions;

        public RiskMetrics(BigDecimal currentExposure, BigDecimal maxExposure, 
                          BigDecimal dailyPnL, BigDecimal maxDailyLoss, int activePositions) {
            this.currentExposure = currentExposure;
            this.maxExposure = maxExposure;
            this.dailyPnL = dailyPnL;
            this.maxDailyLoss = maxDailyLoss;
            this.activePositions = activePositions;
        }

        public BigDecimal getCurrentExposure() { return currentExposure; }
        public BigDecimal getMaxExposure() { return maxExposure; }
        public BigDecimal getDailyPnL() { return dailyPnL; }
        public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
        public int getActivePositions() { return activePositions; }

        public BigDecimal getExposureUtilization() {
            if (maxExposure.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return currentExposure.divide(maxExposure, 4, BigDecimal.ROUND_HALF_UP);
        }
    }
}
