package org.cloudvision.trading.bot.account;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trading account statistics
 */
public class AccountStats {
    private final String accountId;
    private final BigDecimal initialBalance;
    private final BigDecimal currentBalance;
    private final BigDecimal totalPnL;
    private final BigDecimal realizedPnL;
    private final BigDecimal unrealizedPnL;
    private final int totalTrades;
    private final int winningTrades;
    private final int losingTrades;
    private final BigDecimal winRate;
    private final BigDecimal largestWin;
    private final BigDecimal largestLoss;
    private final BigDecimal averageWin;
    private final BigDecimal averageLoss;
    private final BigDecimal profitFactor;
    private final Instant createdAt;
    private final Instant lastTradeAt;
    
    public AccountStats(String accountId, BigDecimal initialBalance, BigDecimal currentBalance,
                       BigDecimal totalPnL, BigDecimal realizedPnL, BigDecimal unrealizedPnL,
                       int totalTrades, int winningTrades, int losingTrades,
                       BigDecimal winRate, BigDecimal largestWin, BigDecimal largestLoss,
                       BigDecimal averageWin, BigDecimal averageLoss, BigDecimal profitFactor,
                       Instant createdAt, Instant lastTradeAt) {
        this.accountId = accountId;
        this.initialBalance = initialBalance;
        this.currentBalance = currentBalance;
        this.totalPnL = totalPnL;
        this.realizedPnL = realizedPnL;
        this.unrealizedPnL = unrealizedPnL;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.winRate = winRate;
        this.largestWin = largestWin;
        this.largestLoss = largestLoss;
        this.averageWin = averageWin;
        this.averageLoss = averageLoss;
        this.profitFactor = profitFactor;
        this.createdAt = createdAt;
        this.lastTradeAt = lastTradeAt;
    }
    
    // Getters
    public String getAccountId() { return accountId; }
    public BigDecimal getInitialBalance() { return initialBalance; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getTotalPnL() { return totalPnL; }
    public BigDecimal getRealizedPnL() { return realizedPnL; }
    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public BigDecimal getWinRate() { return winRate; }
    public BigDecimal getLargestWin() { return largestWin; }
    public BigDecimal getLargestLoss() { return largestLoss; }
    public BigDecimal getAverageWin() { return averageWin; }
    public BigDecimal getAverageLoss() { return averageLoss; }
    public BigDecimal getProfitFactor() { return profitFactor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastTradeAt() { return lastTradeAt; }
}

