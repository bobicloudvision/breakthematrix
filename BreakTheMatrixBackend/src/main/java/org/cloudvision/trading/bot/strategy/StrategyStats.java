package org.cloudvision.trading.bot.strategy;

import java.math.BigDecimal;
import java.time.Instant;

public class StrategyStats {
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal totalProfit;
    private BigDecimal totalLoss;
    private BigDecimal maxDrawdown;
    private BigDecimal winRate;
    private BigDecimal profitFactor;
    private Instant lastTradeTime;
    private Instant startTime;

    public StrategyStats() {
        this.totalTrades = 0;
        this.winningTrades = 0;
        this.losingTrades = 0;
        this.totalProfit = BigDecimal.ZERO;
        this.totalLoss = BigDecimal.ZERO;
        this.maxDrawdown = BigDecimal.ZERO;
        this.startTime = Instant.now();
    }

    public void addTrade(BigDecimal pnl) {
        totalTrades++;
        if (pnl.compareTo(BigDecimal.ZERO) > 0) {
            winningTrades++;
            totalProfit = totalProfit.add(pnl);
        } else {
            losingTrades++;
            totalLoss = totalLoss.add(pnl.abs());
        }
        lastTradeTime = Instant.now();
        updateCalculatedStats();
    }

    private void updateCalculatedStats() {
        if (totalTrades > 0) {
            winRate = new BigDecimal(winningTrades).divide(new BigDecimal(totalTrades), 4, BigDecimal.ROUND_HALF_UP);
        }
        if (totalLoss.compareTo(BigDecimal.ZERO) > 0) {
            profitFactor = totalProfit.divide(totalLoss, 4, BigDecimal.ROUND_HALF_UP);
        }
    }

    // Getters
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public BigDecimal getTotalProfit() { return totalProfit; }
    public BigDecimal getTotalLoss() { return totalLoss; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public BigDecimal getWinRate() { return winRate; }
    public BigDecimal getProfitFactor() { return profitFactor; }
    public Instant getLastTradeTime() { return lastTradeTime; }
    public Instant getStartTime() { return startTime; }

    public BigDecimal getNetProfit() {
        return totalProfit.subtract(totalLoss);
    }

    @Override
    public String toString() {
        return String.format("StrategyStats{trades=%d, winRate=%.2f%%, netProfit=%s, profitFactor=%s}",
                totalTrades, winRate != null ? winRate.multiply(new BigDecimal(100)) : BigDecimal.ZERO,
                getNetProfit(), profitFactor);
    }
}
