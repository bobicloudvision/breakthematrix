package org.cloudvision.trading.bot.account;

/**
 * Stop Loss Types
 */
public enum StopLossType {
    FIXED,           // Fixed price level
    TRAILING,        // Trails behind price
    ATR_BASED,       // Based on ATR (Average True Range)
    PERCENTAGE,      // Percentage-based
    BREAKEVEN,       // Moves to breakeven at trigger price
    BREAKEVEN_PLUS   // Moves to breakeven + small profit
}
