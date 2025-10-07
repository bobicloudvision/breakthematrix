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
    private PositionCloseListener closeListener;
    // Stores the most recent prices seen via updatePrices so we can recompute after snapshots
    private final Map<String, BigDecimal> lastKnownPrices = new ConcurrentHashMap<>();
    
    private static String buildKey(String symbol, PositionSide side) {
        return (symbol == null ? "" : symbol.toUpperCase()) + ":" + (side == null ? "" : side.name());
    }
    
    /**
     * Replace local open positions with an authoritative snapshot from exchange.
     * This is an atomic copy-and-swap to minimize race conditions.
     */
    public void applyExchangeSnapshot(List<Position> remoteOpenPositions) {
        if (remoteOpenPositions == null) return;

        // Build lookup by (symbol + side) for current local positions to preserve local objects/state
        Map<String, Position> localByKey = new HashMap<>();
        for (Position lp : openPositions.values()) {
            localByKey.put(buildKey(lp.getSymbol(), lp.getSide()), lp);
        }

        // Track which local positions are seen in remote snapshot
        Set<String> localIdsToKeep = new HashSet<>();

        Map<String, Position> merged = new ConcurrentHashMap<>();

        // Merge or create positions based on remote snapshot
        for (Position remote : remoteOpenPositions) {
            if (remote == null || !remote.isOpen()) continue;
            String key = buildKey(remote.getSymbol(), remote.getSide());
            Position local = localByKey.get(key);
            if (local != null) {
                // Prefer authoritative remote snapshot for open position state
                merged.put(remote.getPositionId(), remote);
                localIdsToKeep.add(local.getPositionId());
            } else {
                // No local match: adopt remote as new local open position
                merged.put(remote.getPositionId(), remote);
            }
        }

        // Any local positions not present in remote snapshot should be force-closed and moved to history
        for (Position lp : openPositions.values()) {
            if (localIdsToKeep.contains(lp.getPositionId())) continue;
            String key = buildKey(lp.getSymbol(), lp.getSide());
            boolean presentRemotely = remoteOpenPositions.stream()
                .anyMatch(r -> r != null && r.isOpen() && buildKey(r.getSymbol(), r.getSide()).equals(key));
            if (!presentRemotely) {
                // Move local-only open positions into history (treat as closed externally)
                positionHistory.add(lp);
            }
        }

        // Atomic-like swap by clearing then putting all; openPositions is a concurrent map
        openPositions.clear();
        openPositions.putAll(merged);
//        System.out.println("🔄 Applied exchange snapshot (merged): openPositions=" + openPositions.size());

        // Recalculate unrealized PnL using the last known prices but DO NOT trigger SL/TP closes here
        if (!lastKnownPrices.isEmpty()) {
            updatePricesInternal(lastKnownPrices, false);
        }
    }
    
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
        
        BigDecimal pnlBefore = position.getRealizedPnL();
        position.close(closePrice, quantity);
        BigDecimal pnlAfter = position.getRealizedPnL();
        BigDecimal pnlFromThisClose = pnlAfter.subtract(pnlBefore);
        
        System.out.println("🔒 Closed position: " + position.getSymbol() + 
            " @ " + closePrice + " qty: " + quantity + 
            " | Realized P&L: " + position.getRealizedPnL());
        
        // Notify listener about the close (for both partial and full closes)
        if (closeListener != null) {
            closeListener.onPositionClosed(position, pnlFromThisClose, closePrice, quantity);
        }
        
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
        updatePricesInternal(currentPrices, true);
    }

    private void updatePricesInternal(Map<String, BigDecimal> currentPrices, boolean triggerStopsAndTargets) {
        // Normalize incoming prices to uppercase symbols to avoid casing mismatches
        Map<String, BigDecimal> normalizedPrices = new HashMap<>();
        if (currentPrices != null) {
            for (Map.Entry<String, BigDecimal> e : currentPrices.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    normalizedPrices.put(e.getKey().toUpperCase(), e.getValue());
                }
            }
            lastKnownPrices.clear();
            lastKnownPrices.putAll(normalizedPrices);
        }
        for (Position position : openPositions.values()) {
            BigDecimal currentPrice = normalizedPrices.get(position.getSymbol());

            if (currentPrice != null) {
                // Update dynamic stop loss and take profit first
                position.updateStopLossAndTakeProfit(currentPrice);
                
                // Update unrealized P&L
                position.updateUnrealizedPnL(currentPrice);
                
                if (triggerStopsAndTargets) {
                    // Check stop loss / take profit
                    if (position.isStopLossHit(currentPrice)) {
                        System.out.println("⛔ Stop Loss hit for " + position.getSymbol() + 
                            " @ " + currentPrice + " (SL: " + position.getStopLoss() + ")");
                        closePosition(position.getPositionId(), currentPrice, position.getQuantity());
                        continue;
                    }
                    if (position.isTakeProfitHit(currentPrice)) {
                        System.out.println("🎯 Take Profit hit for " + position.getSymbol() + 
                            " @ " + currentPrice + " (TP: " + position.getTakeProfit() + ")");
                        closePosition(position.getPositionId(), currentPrice, position.getQuantity());
                        continue;
                    }
                }

//                System.out.println("📈 Updated " + position.getSymbol() +
//                    " | Current Price: " + currentPrice +
//                    " | Unrealized P&L: " + position.getUnrealizedPnL());
            }
        }
    }
    
    /**
     * Update ATR values for all positions (for ATR-based stops)
     */
    public void updateATRValues(Map<String, BigDecimal> atrValues) {
        for (Position position : openPositions.values()) {
            BigDecimal atrValue = atrValues.get(position.getSymbol());
            if (atrValue != null) {
                position.updateATRValue(atrValue);
            }
        }
    }
    
    /**
     * Set stop loss for a specific position
     */
    public boolean setStopLoss(String positionId, BigDecimal stopLoss, StopLossType type, BigDecimal... parameters) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            System.err.println("❌ Position not found or already closed: " + positionId);
            return false;
        }
        
        position.setStopLoss(stopLoss, type, parameters);
        System.out.println("🛡️ Stop loss set for " + position.getSymbol() + 
            " (Type: " + type + ", Value: " + stopLoss + ")");
        return true;
    }
    
    /**
     * Set take profit for a specific position
     */
    public boolean setTakeProfit(String positionId, BigDecimal takeProfit, TakeProfitType type, BigDecimal... parameters) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            System.err.println("❌ Position not found or already closed: " + positionId);
            return false;
        }
        
        position.setTakeProfit(takeProfit, type, parameters);
        System.out.println("🎯 Take profit set for " + position.getSymbol() + 
            " (Type: " + type + ", Value: " + takeProfit + ")");
        return true;
    }
    
    /**
     * Get positions with specific stop loss type
     */
    public List<Position> getPositionsByStopLossType(StopLossType type) {
        return openPositions.values().stream()
            .filter(p -> p.getStopLossType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Get positions with specific take profit type
     */
    public List<Position> getPositionsByTakeProfitType(TakeProfitType type) {
        return openPositions.values().stream()
            .filter(p -> p.getTakeProfitType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Set trailing stop loss for a position
     */
    public boolean setTrailingStopLoss(String positionId, BigDecimal trailingDistance, BigDecimal currentPrice) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            return false;
        }
        
        // Calculate initial trailing stop level
        BigDecimal initialStopLoss = position.getSide() == PositionSide.LONG 
            ? currentPrice.subtract(trailingDistance)
            : currentPrice.add(trailingDistance);
            
        return setStopLoss(positionId, initialStopLoss, StopLossType.TRAILING, trailingDistance);
    }
    
    /**
     * Set breakeven stop loss for a position
     */
    public boolean setBreakevenStopLoss(String positionId, BigDecimal triggerPrice, boolean includeSmallProfit) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            return false;
        }
        
        StopLossType type = includeSmallProfit ? StopLossType.BREAKEVEN_PLUS : StopLossType.BREAKEVEN;
        return setStopLoss(positionId, position.getEntryPrice(), type, triggerPrice);
    }
    
    /**
     * Set ATR-based stop loss for a position
     */
    public boolean setATRStopLoss(String positionId, BigDecimal atrMultiplier, BigDecimal atrValue, BigDecimal currentPrice) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            return false;
        }
        
        BigDecimal atrDistance = atrValue.multiply(atrMultiplier);
        BigDecimal stopLoss = position.getSide() == PositionSide.LONG 
            ? currentPrice.subtract(atrDistance)
            : currentPrice.add(atrDistance);
            
        return setStopLoss(positionId, stopLoss, StopLossType.ATR_BASED, atrMultiplier);
    }
    
    /**
     * Set ATR-based take profit for a position
     */
    public boolean setATRTakeProfit(String positionId, BigDecimal atrMultiplier, BigDecimal atrValue, BigDecimal currentPrice) {
        Position position = openPositions.get(positionId);
        if (position == null || !position.isOpen()) {
            return false;
        }
        
        BigDecimal atrDistance = atrValue.multiply(atrMultiplier);
        BigDecimal takeProfit = position.getSide() == PositionSide.LONG 
            ? currentPrice.add(atrDistance)
            : currentPrice.subtract(atrDistance);
            
        return setTakeProfit(positionId, takeProfit, TakeProfitType.ATR_BASED, atrMultiplier);
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
     * Set position close listener
     */
    public void setPositionCloseListener(PositionCloseListener listener) {
        this.closeListener = listener;
    }
    
    /**
     * Clear all positions (for testing/reset)
     */
    public void reset() {
        openPositions.clear();
        positionHistory.clear();
        System.out.println("🗑️ Position manager reset");
    }
    
    /**
     * Listener interface for position close events
     */
    public interface PositionCloseListener {
        void onPositionClosed(Position position, BigDecimal realizedPnL, BigDecimal closePrice, BigDecimal closedQuantity);
    }
}

