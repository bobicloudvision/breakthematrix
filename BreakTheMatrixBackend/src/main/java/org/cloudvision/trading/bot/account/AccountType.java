package org.cloudvision.trading.bot.account;

/**
 * Types of trading accounts
 */
public enum AccountType {
    PAPER_TRADING("Paper Trading", "Simulated trading with virtual money"),
    LIVE_TRADING("Live Trading", "Real trading with actual funds"),
    TESTNET("Testnet", "Exchange testnet account");
    
    private final String displayName;
    private final String description;
    
    AccountType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}

