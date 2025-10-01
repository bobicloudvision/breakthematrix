package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PortfolioManager {
    
    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private BigDecimal totalBalance = new BigDecimal("100000"); // Starting with $100,000
    private BigDecimal availableBalance = new BigDecimal("100000");
    private BigDecimal dailyStartBalance = new BigDecimal("100000");
    private LocalDate currentDay = LocalDate.now();

    /**
     * Update portfolio when an order is executed
     */
    public void updatePortfolio(Order order) {
        if (order.getStatus() != OrderStatus.FILLED) {
            return;
        }

        String symbol = order.getSymbol();
        Position position = positions.computeIfAbsent(symbol, k -> new Position(symbol));
        
        BigDecimal orderValue = order.getExecutedQuantity().multiply(order.getExecutedPrice());
        
        if (order.getSide() == OrderSide.BUY) {
            // Add to position
            BigDecimal newQuantity = position.getQuantity().add(order.getExecutedQuantity());
            BigDecimal newCost = position.getTotalCost().add(orderValue);
            BigDecimal newAvgPrice = newQuantity.compareTo(BigDecimal.ZERO) > 0 ? 
                newCost.divide(newQuantity, 8, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
            
            position.setQuantity(newQuantity);
            position.setAveragePrice(newAvgPrice);
            position.setTotalCost(newCost);
            
            // Reduce available balance
            availableBalance = availableBalance.subtract(orderValue);
            
        } else { // SELL
            // Reduce position
            BigDecimal newQuantity = position.getQuantity().subtract(order.getExecutedQuantity());
            BigDecimal soldCost = position.getAveragePrice().multiply(order.getExecutedQuantity());
            BigDecimal pnl = orderValue.subtract(soldCost);
            
            position.setQuantity(newQuantity);
            position.setTotalCost(position.getTotalCost().subtract(soldCost));
            position.setRealizedPnL(position.getRealizedPnL().add(pnl));
            
            // Add to available balance
            availableBalance = availableBalance.add(orderValue);
            totalBalance = totalBalance.add(pnl);
            
            // Remove position if quantity is zero
            if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
                positions.remove(symbol);
            }
        }
        
        System.out.println("📊 Portfolio updated: " + symbol + " position = " + 
                         (positions.containsKey(symbol) ? positions.get(symbol) : "CLOSED"));
    }

    /**
     * Update position with current market price for unrealized PnL calculation
     */
    public void updateMarketPrice(String symbol, BigDecimal currentPrice) {
        Position position = positions.get(symbol);
        if (position != null) {
            position.setCurrentPrice(currentPrice);
        }
    }

    /**
     * Get total portfolio exposure (sum of all position values)
     */
    public BigDecimal getTotalExposure() {
        return positions.values().stream()
                .map(Position::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total unrealized PnL
     */
    public BigDecimal getUnrealizedPnL() {
        return positions.values().stream()
                .map(Position::getUnrealizedPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total realized PnL
     */
    public BigDecimal getRealizedPnL() {
        return positions.values().stream()
                .map(Position::getRealizedPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get daily PnL (resets each day)
     */
    public BigDecimal getDailyPnL() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            // New day - reset daily tracking
            currentDay = today;
            dailyStartBalance = totalBalance;
        }
        
        BigDecimal currentTotalValue = totalBalance.add(getUnrealizedPnL());
        return currentTotalValue.subtract(dailyStartBalance);
    }

    // Getters
    public BigDecimal getTotalBalance() { return totalBalance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public Map<String, Position> getPositions() { return Map.copyOf(positions); }
    public Position getPosition(String symbol) { return positions.get(symbol); }

    public PortfolioSummary getPortfolioSummary() {
        return new PortfolioSummary(
            totalBalance,
            availableBalance,
            getTotalExposure(),
            getUnrealizedPnL(),
            getRealizedPnL(),
            getDailyPnL(),
            positions.size()
        );
    }

    /**
     * Position class to track individual symbol positions
     */
    public static class Position {
        private final String symbol;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal averagePrice = BigDecimal.ZERO;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal currentPrice = BigDecimal.ZERO;
        private BigDecimal realizedPnL = BigDecimal.ZERO;

        public Position(String symbol) {
            this.symbol = symbol;
        }

        public BigDecimal getCurrentValue() {
            return quantity.multiply(currentPrice);
        }

        public BigDecimal getUnrealizedPnL() {
            return getCurrentValue().subtract(totalCost);
        }

        // Getters and setters
        public String getSymbol() { return symbol; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getAveragePrice() { return averagePrice; }
        public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
        public BigDecimal getTotalCost() { return totalCost; }
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getRealizedPnL() { return realizedPnL; }
        public void setRealizedPnL(BigDecimal realizedPnL) { this.realizedPnL = realizedPnL; }

        @Override
        public String toString() {
            return String.format("Position{%s: qty=%s, avgPrice=%s, currentPrice=%s, unrealizedPnL=%s}",
                    symbol, quantity, averagePrice, currentPrice, getUnrealizedPnL());
        }
    }

    public static class PortfolioSummary {
        private final BigDecimal totalBalance;
        private final BigDecimal availableBalance;
        private final BigDecimal totalExposure;
        private final BigDecimal unrealizedPnL;
        private final BigDecimal realizedPnL;
        private final BigDecimal dailyPnL;
        private final int activePositions;

        public PortfolioSummary(BigDecimal totalBalance, BigDecimal availableBalance, 
                               BigDecimal totalExposure, BigDecimal unrealizedPnL, 
                               BigDecimal realizedPnL, BigDecimal dailyPnL, int activePositions) {
            this.totalBalance = totalBalance;
            this.availableBalance = availableBalance;
            this.totalExposure = totalExposure;
            this.unrealizedPnL = unrealizedPnL;
            this.realizedPnL = realizedPnL;
            this.dailyPnL = dailyPnL;
            this.activePositions = activePositions;
        }

        public BigDecimal getTotalBalance() { return totalBalance; }
        public BigDecimal getAvailableBalance() { return availableBalance; }
        public BigDecimal getTotalExposure() { return totalExposure; }
        public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
        public BigDecimal getRealizedPnL() { return realizedPnL; }
        public BigDecimal getDailyPnL() { return dailyPnL; }
        public int getActivePositions() { return activePositions; }

        public BigDecimal getTotalValue() {
            return totalBalance.add(unrealizedPnL);
        }
    }
}
