package org.cloudvision.trading.bot.account;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages trading positions
 * Tracks open and closed positions, P&L, and position history
 */
public class PositionManager {
    
    private final Map<String, Position> openPositions = new ConcurrentHashMap<>();
    private final List<Position> positionHistory = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * Open a new position
     */
    public Position openPosition(String symbol, PositionSide side, BigDecimal entryPrice, BigDecimal quantity) {
        Position position = new Position(symbol, side, entryPrice, quantity);
        openPositions.put(position.getPositionId(), position);
        
        System.out.println("📍 Opened " + side + " position: " + symbol + 
            " @ " + entryPrice + " qty: " + quantity + " (ID: " + position.getPositionId() + ")");
        
        return position;
    }
    
    /**
     * Close a position (full or partial)
     */
    public void closePosition(String positionId, BigDecimal closePrice, BigDecimal quantity) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            System.err.println("❌ Position not found or already closed: " + positionId);
            return;
        }
        
        position.close(closePrice, quantity);
        
        System.out.println("🔒 Closed position: " + position.getSymbol() + 
            " @ " + closePrice + " qty: " + quantity + 
            " | Realized P&L: " + position.getRealizedPnL());
        
        // If fully closed, move to history
        if (!position.isOpen()) {
            openPositions.remove(positionId);
            positionHistory.add(position);
        }
    }
    
    /**
     * Close all positions for a symbol
     */
    public void closeAllPositions(String symbol, BigDecimal closePrice) {
        List<Position> symbolPositions = getOpenPositionsBySymbol(symbol);
        for (Position position : symbolPositions) {
            closePosition(position.getPositionId(), closePrice, position.getQuantity());
        }
    }
    
    /**
     * Update unrealized P&L for all open positions
     */
    public void updatePrices(Map<String, BigDecimal> currentPrices) {
        for (Position position : openPositions.values()) {
            BigDecimal currentPrice = currentPrices.get(position.getSymbol());
            if (currentPrice != null) {
                position.updateUnrealizedPnL(currentPrice);
                
                // Check stop loss / take profit
                if (position.isStopLossHit(currentPrice)) {
                    System.out.println("⛔ Stop Loss hit for " + position.getSymbol());
                    closePosition(position.getPositionId(), currentPrice, position.getQuantity());
                } else if (position.isTakeProfitHit(currentPrice)) {
                    System.out.println("🎯 Take Profit hit for " + position.getSymbol());
                    closePosition(position.getPositionId(), currentPrice, position.getQuantity());
                }
            }
        }
    }
    
    /**
     * Get all open positions
     */
    public List<Position> getOpenPositions() {
        return new ArrayList<>(openPositions.values());
    }
    
    /**
     * Get open positions for a specific symbol
     */
    public List<Position> getOpenPositionsBySymbol(String symbol) {
        return openPositions.values().stream()
            .filter(p -> p.getSymbol().equals(symbol))
            .collect(Collectors.toList());
    }
    
    /**
     * Get position by ID
     */
    public Position getPosition(String positionId) {
        Position openPos = openPositions.get(positionId);
        if (openPos != null) return openPos;
        
        // Search in history
        return positionHistory.stream()
            .filter(p -> p.getPositionId().equals(positionId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get position history
     */
    public List<Position> getPositionHistory() {
        return new ArrayList<>(positionHistory);
    }
    
    /**
     * Get total unrealized P&L across all open positions
     */
    public BigDecimal getTotalUnrealizedPnL() {
        return openPositions.values().stream()
            .map(Position::getUnrealizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get total realized P&L from all closed positions
     */
    public BigDecimal getTotalRealizedPnL() {
        return positionHistory.stream()
            .map(Position::getRealizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get total P&L (realized + unrealized)
     */
    public BigDecimal getTotalPnL() {
        return getTotalRealizedPnL().add(getTotalUnrealizedPnL());
    }
    
    /**
     * Get position count
     */
    public int getOpenPositionCount() {
        return openPositions.size();
    }
    
    /**
     * Get total number of trades (closed positions)
     */
    public int getTotalTradeCount() {
        return positionHistory.size();
    }
    
    /**
     * Get win rate
     */
    public double getWinRate() {
        long winningTrades = positionHistory.stream()
            .filter(p -> p.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .count();
        
        int totalTrades = positionHistory.size();
        return totalTrades > 0 ? (double) winningTrades / totalTrades * 100 : 0.0;
    }
    
    /**
     * Get position summary
     */
    public Map<String, Object> getPositionSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("openPositions", getOpenPositionCount());
        summary.put("totalTrades", getTotalTradeCount());
        summary.put("totalPnL", getTotalPnL());
        summary.put("realizedPnL", getTotalRealizedPnL());
        summary.put("unrealizedPnL", getTotalUnrealizedPnL());
        summary.put("winRate", getWinRate());
        
        return summary;
    }
    
    /**
     * Clear all positions (for testing/reset)
     */
    public void reset() {
        openPositions.clear();
        positionHistory.clear();
        System.out.println("🗑️ Position manager reset");
    }
}

