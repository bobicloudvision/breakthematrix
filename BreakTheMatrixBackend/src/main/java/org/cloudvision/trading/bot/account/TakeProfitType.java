package org.cloudvision.trading.bot.account;

/**
 * Take Profit Types
 */
public enum TakeProfitType {
    FIXED,           // Fixed price level
    PERCENTAGE,      // Percentage-based
    ATR_BASED,       // Based on ATR
    RATIO_BASED      // Risk:Reward ratio based
}
