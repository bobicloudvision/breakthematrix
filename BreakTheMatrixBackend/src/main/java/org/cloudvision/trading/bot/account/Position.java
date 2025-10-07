package org.cloudvision.trading.bot.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Represents an open trading position
 */
public class Position {
    
    private final String positionId;
    private final String symbol;
    private final PositionSide side;
    private final BigDecimal entryPrice;
    private final BigDecimal originalQuantity; // Store original quantity for P&L calculation
    private BigDecimal quantity;
    private final Instant entryTime;
    private Instant exitTime;
    private BigDecimal exitPrice;
    
    // P&L tracking
    private BigDecimal realizedPnL = BigDecimal.ZERO;
    private BigDecimal unrealizedPnL = BigDecimal.ZERO;
    
    // Risk management
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    
    // Advanced stop loss/take profit features
    private StopLossType stopLossType = StopLossType.FIXED;
    private TakeProfitType takeProfitType = TakeProfitType.FIXED;
    private BigDecimal trailingStopDistance; // For trailing stops
    private BigDecimal breakevenTriggerPrice; // Price at which to move SL to breakeven
    private boolean breakevenActivated = false;
    private BigDecimal originalStopLoss; // Store original SL for breakeven reference
    private BigDecimal atrMultiplier; // For ATR-based stops
    private BigDecimal atrValue; // Current ATR value
    
    // Metadata
    private String strategyId;
    private boolean isOpen;
    
    public Position(String symbol, PositionSide side, BigDecimal entryPrice, BigDecimal quantity) {
        this.positionId = java.util.UUID.randomUUID().toString();
        this.symbol = symbol;
        this.side = side;
        this.entryPrice = entryPrice;
        this.originalQuantity = quantity;
        this.quantity = quantity;
        this.entryTime = Instant.now();
        this.isOpen = true;
    }
    
    /**
     * Update unrealized P&L based on current market price
     */
    public void updateUnrealizedPnL(BigDecimal currentPrice) {
        if (!isOpen) return;
        
        BigDecimal priceDiff = side == PositionSide.LONG 
            ? currentPrice.subtract(entryPrice)
            : entryPrice.subtract(currentPrice);

        System.out.println("Current price: " + currentPrice);
        System.out.println("Entry price: " + entryPrice);
        System.out.println("Price diff: " + priceDiff + ", Quantity: " + quantity);
            
        this.unrealizedPnL = priceDiff.multiply(quantity).setScale(8, RoundingMode.HALF_UP);

        System.out.println("Unrealized PnL updated: " + this.unrealizedPnL);

    }
    
    /**
     * Close the position (full or partial)
     */
    public void close(BigDecimal closePrice, BigDecimal closeQuantity) {
        if (!isOpen) return;
        
        BigDecimal priceDiff = side == PositionSide.LONG 
            ? closePrice.subtract(entryPrice)
            : entryPrice.subtract(closePrice);
            
        BigDecimal pnl = priceDiff.multiply(closeQuantity).setScale(8, RoundingMode.HALF_UP);
        this.realizedPnL = this.realizedPnL.add(pnl);
        
        this.quantity = this.quantity.subtract(closeQuantity);
        
        if (this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            this.isOpen = false;
            this.exitTime = Instant.now();
            this.exitPrice = closePrice;
            this.unrealizedPnL = BigDecimal.ZERO;
        }
    }
    
    /**
     * Get total P&L (realized + unrealized)
     */
    public BigDecimal getTotalPnL() {
        return realizedPnL.add(unrealizedPnL);
    }
    
    /**
     * Get P&L percentage based on original entry value
     */
    public BigDecimal getPnLPercentage() {
        BigDecimal quantityToUse = originalQuantity;
        
        // Backward compatibility: if originalQuantity is null/zero (old positions), 
        // try to reverse-calculate from realized P&L
        if ((quantityToUse == null || quantityToUse.compareTo(BigDecimal.ZERO) == 0) && 
            !isOpen && exitPrice != null && realizedPnL.compareTo(BigDecimal.ZERO) != 0) {
            
            // For closed positions: calculate original quantity from realized P&L
            // LONG: realizedPnL = (exitPrice - entryPrice) * quantity
            // SHORT: realizedPnL = (entryPrice - exitPrice) * quantity
            BigDecimal priceDiff = side == PositionSide.LONG 
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);
            
            if (priceDiff.compareTo(BigDecimal.ZERO) != 0) {
                quantityToUse = realizedPnL.divide(priceDiff, 8, RoundingMode.HALF_UP);
            }
        }
        
        // If still no quantity, use current quantity (for open positions)
        if (quantityToUse == null || quantityToUse.compareTo(BigDecimal.ZERO) == 0) {
            quantityToUse = quantity;
        }
            
        BigDecimal entryValue = entryPrice.multiply(quantityToUse);
        if (entryValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getTotalPnL().divide(entryValue, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    /**
     * Get position value at entry (based on original quantity)
     */
    public BigDecimal getEntryValue() {
        BigDecimal quantityToUse = originalQuantity;
        
        // Backward compatibility: reverse-calculate from realized P&L for old closed positions
        if ((quantityToUse == null || quantityToUse.compareTo(BigDecimal.ZERO) == 0) && 
            !isOpen && exitPrice != null && realizedPnL.compareTo(BigDecimal.ZERO) != 0) {
            
            BigDecimal priceDiff = side == PositionSide.LONG 
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);
            
            if (priceDiff.compareTo(BigDecimal.ZERO) != 0) {
                quantityToUse = realizedPnL.divide(priceDiff, 8, RoundingMode.HALF_UP);
            }
        }
        
        // If still no quantity, use current quantity
        if (quantityToUse == null || quantityToUse.compareTo(BigDecimal.ZERO) == 0) {
            quantityToUse = quantity;
        }
        
        return entryPrice.multiply(quantityToUse).setScale(8, RoundingMode.HALF_UP);
    }
    
    /**
     * Get current position value
     */
    public BigDecimal getCurrentValue(BigDecimal currentPrice) {
        return currentPrice.multiply(quantity).setScale(8, RoundingMode.HALF_UP);
    }
    
    /**
     * Get position duration
     */
    public Duration getDuration() {
        Instant end = isOpen ? Instant.now() : exitTime;
        return Duration.between(entryTime, end);
    }
    
    /**
     * Check if stop loss is hit
     */
    public boolean isStopLossHit(BigDecimal currentPrice) {
        if (stopLoss == null) return false;
        
        return side == PositionSide.LONG 
            ? currentPrice.compareTo(stopLoss) <= 0
            : currentPrice.compareTo(stopLoss) >= 0;
    }
    
    /**
     * Check if take profit is hit
     */
    public boolean isTakeProfitHit(BigDecimal currentPrice) {
        if (takeProfit == null) return false;
        
        return side == PositionSide.LONG 
            ? currentPrice.compareTo(takeProfit) >= 0
            : currentPrice.compareTo(takeProfit) <= 0;
    }
    
    /**
     * Update stop loss and take profit based on current price and type
     */
    public void updateStopLossAndTakeProfit(BigDecimal currentPrice) {
        updateStopLoss(currentPrice);
        updateTakeProfit(currentPrice);
    }
    
    /**
     * Update stop loss based on type
     */
    private void updateStopLoss(BigDecimal currentPrice) {
        if (stopLossType == StopLossType.TRAILING && trailingStopDistance != null) {
            updateTrailingStopLoss(currentPrice);
        } else if (stopLossType == StopLossType.ATR_BASED && atrValue != null && atrMultiplier != null) {
            updateATRStopLoss(currentPrice);
        } else if (stopLossType == StopLossType.BREAKEVEN || stopLossType == StopLossType.BREAKEVEN_PLUS) {
            updateBreakevenStopLoss(currentPrice);
        }
    }
    
    /**
     * Update take profit based on type
     */
    private void updateTakeProfit(BigDecimal currentPrice) {
        if (takeProfitType == TakeProfitType.ATR_BASED && atrValue != null && atrMultiplier != null) {
            updateATRTakeProfit(currentPrice);
        }
    }
    
    /**
     * Update trailing stop loss
     */
    private void updateTrailingStopLoss(BigDecimal currentPrice) {
        if (side == PositionSide.LONG) {
            // For long positions, trail stop loss upward
            BigDecimal newStopLoss = currentPrice.subtract(trailingStopDistance);
            if (stopLoss == null || newStopLoss.compareTo(stopLoss) > 0) {
                stopLoss = newStopLoss;
            }
        } else {
            // For short positions, trail stop loss downward
            BigDecimal newStopLoss = currentPrice.add(trailingStopDistance);
            if (stopLoss == null || newStopLoss.compareTo(stopLoss) < 0) {
                stopLoss = newStopLoss;
            }
        }
    }
    
    /**
     * Update ATR-based stop loss
     */
    private void updateATRStopLoss(BigDecimal currentPrice) {
        BigDecimal atrDistance = atrValue.multiply(atrMultiplier);
        
        if (side == PositionSide.LONG) {
            stopLoss = currentPrice.subtract(atrDistance);
        } else {
            stopLoss = currentPrice.add(atrDistance);
        }
    }
    
    /**
     * Update breakeven stop loss
     */
    private void updateBreakevenStopLoss(BigDecimal currentPrice) {
        if (breakevenTriggerPrice == null || breakevenActivated) {
            return;
        }
        
        boolean shouldActivate = side == PositionSide.LONG 
            ? currentPrice.compareTo(breakevenTriggerPrice) >= 0
            : currentPrice.compareTo(breakevenTriggerPrice) <= 0;
            
        if (shouldActivate) {
            if (stopLossType == StopLossType.BREAKEVEN) {
                stopLoss = entryPrice; // Move to breakeven
            } else if (stopLossType == StopLossType.BREAKEVEN_PLUS) {
                // Move to breakeven + small profit (e.g., 0.1% of entry price)
                BigDecimal smallProfit = entryPrice.multiply(new BigDecimal("0.001"));
                stopLoss = side == PositionSide.LONG 
                    ? entryPrice.add(smallProfit)
                    : entryPrice.subtract(smallProfit);
            }
            breakevenActivated = true;
        }
    }
    
    /**
     * Update ATR-based take profit
     */
    private void updateATRTakeProfit(BigDecimal currentPrice) {
        BigDecimal atrDistance = atrValue.multiply(atrMultiplier);
        
        if (side == PositionSide.LONG) {
            takeProfit = currentPrice.add(atrDistance);
        } else {
            takeProfit = currentPrice.subtract(atrDistance);
        }
    }
    
    /**
     * Set stop loss with type and parameters
     */
    public void setStopLoss(BigDecimal stopLoss, StopLossType type, BigDecimal... parameters) {
        this.stopLoss = stopLoss;
        this.stopLossType = type;
        this.originalStopLoss = stopLoss; // Store original for breakeven reference
        
        if (type == StopLossType.TRAILING && parameters.length > 0) {
            this.trailingStopDistance = parameters[0];
        } else if (type == StopLossType.ATR_BASED && parameters.length > 0) {
            this.atrMultiplier = parameters[0];
        } else if ((type == StopLossType.BREAKEVEN || type == StopLossType.BREAKEVEN_PLUS) && parameters.length > 0) {
            this.breakevenTriggerPrice = parameters[0];
        }
    }
    
    /**
     * Set take profit with type and parameters
     */
    public void setTakeProfit(BigDecimal takeProfit, TakeProfitType type, BigDecimal... parameters) {
        this.takeProfit = takeProfit;
        this.takeProfitType = type;
        
        if (type == TakeProfitType.ATR_BASED && parameters.length > 0) {
            this.atrMultiplier = parameters[0];
        }
    }
    
    /**
     * Update ATR value for ATR-based stops
     */
    public void updateATRValue(BigDecimal atrValue) {
        this.atrValue = atrValue;
    }
    
    /**
     * Get current stop loss type
     */
    public StopLossType getStopLossType() {
        return stopLossType;
    }
    
    /**
     * Get current take profit type
     */
    public TakeProfitType getTakeProfitType() {
        return takeProfitType;
    }
    
    /**
     * Check if breakeven has been activated
     */
    public boolean isBreakevenActivated() {
        return breakevenActivated;
    }
    
    // Getters and setters
    
    public String getPositionId() {
        return positionId;
    }
    
    public String getSymbol() {
        return symbol.toUpperCase();
    }
    
    public PositionSide getSide() {
        return side;
    }
    
    public BigDecimal getEntryPrice() {
        return entryPrice;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public BigDecimal getOriginalQuantity() {
        return originalQuantity;
    }
    
    public Instant getEntryTime() {
        return entryTime;
    }
    
    public Instant getExitTime() {
        return exitTime;
    }
    
    public BigDecimal getExitPrice() {
        return exitPrice;
    }
    
    public BigDecimal getRealizedPnL() {
        return realizedPnL;
    }
    
    public BigDecimal getUnrealizedPnL() {
        return unrealizedPnL;
    }
    
    public BigDecimal getStopLoss() {
        return stopLoss;
    }
    
    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }
    
    public BigDecimal getTakeProfit() {
        return takeProfit;
    }
    
    public void setTakeProfit(BigDecimal takeProfit) {
        this.takeProfit = takeProfit;
    }
    
    public String getStrategyId() {
        return strategyId;
    }
    
    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }
    
    public boolean isOpen() {
        return isOpen;
    }
}

