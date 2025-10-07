package org.cloudvision.trading.bot.account;

import org.cloudvision.trading.bot.model.Order;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account Manager
 * Manages multiple trading accounts (paper and live)
 */
@Service
public class AccountManager {
    
    private final Map<String, TradingAccount> accounts = new ConcurrentHashMap<>();
    private String activeAccountId;
    
    public AccountManager() {
        // Create default paper trading account
//        PaperTradingAccount defaultAccount = new PaperTradingAccount();
//        registerAccount(defaultAccount);
//        setActiveAccount(defaultAccount.getAccountId());
        
//        System.out.println("💼 Account Manager initialized with default paper trading account");

        // Connect AlpacaTradingAccount
        AlpacaTradingAccount alpacaAccount = new AlpacaTradingAccount(
            "live-alpaca-001",
            "Alpaca Live Account",
            "PKXPLINRD5ABM788429X",
            "T0lMfFzkeHcnrnJdnTmy6BXLNFmqsehTPOEWBlas",
            "https://paper-api.alpaca.markets/v2"
        );
        registerAccount(alpacaAccount);
        setActiveAccount(alpacaAccount.getAccountId());
        System.out.println("💼 Account Manager initialized with Alpaca live trading account");

    }
    
    /**
     * Register a trading account
     */
    public void registerAccount(TradingAccount account) {
        accounts.put(account.getAccountId(), account);
        System.out.println("✅ Registered account: " + account.getAccountName() + 
                         " (" + account.getAccountType().getDisplayName() + ")");
    }
    
    /**
     * Get account by ID
     */
    public TradingAccount getAccount(String accountId) {
        return accounts.get(accountId);
    }
    
    /**
     * Get active account
     */
    public TradingAccount getActiveAccount() {
        return accounts.get(activeAccountId);
    }
    
    /**
     * Set active account
     */
    public void setActiveAccount(String accountId) {
        if (!accounts.containsKey(accountId)) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        this.activeAccountId = accountId;
        System.out.println("🎯 Active account set to: " + accounts.get(accountId).getAccountName());
    }
    
    /**
     * Deactivate a specific account
     * If it's the active account, clear the active account
     * @return true if the deactivated account was the active account
     */
    public boolean deactivateAccount(String accountId) {
        if (!accounts.containsKey(accountId)) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        
        boolean wasActive = accountId.equals(activeAccountId);
        
        if (wasActive) {
            this.activeAccountId = null;
            System.out.println("🚫 Active account deactivated: " + accounts.get(accountId).getAccountName());
        } else {
            System.out.println("🚫 Account deactivated: " + accounts.get(accountId).getAccountName());
        }
        
        return wasActive;
    }
    
    /**
     * Check if there is an active account
     */
    public boolean hasActiveAccount() {
        return activeAccountId != null && accounts.containsKey(activeAccountId);
    }
    
    /**
     * Get all accounts
     */
    public List<TradingAccount> getAllAccounts() {
        return List.copyOf(accounts.values());
    }
    
    /**
     * Get all paper trading accounts
     */
    public List<TradingAccount> getPaperAccounts() {
        return accounts.values().stream()
            .filter(a -> a.getAccountType() == AccountType.PAPER_TRADING)
            .toList();
    }
    
    /**
     * Get all live trading accounts
     */
    public List<TradingAccount> getLiveAccounts() {
        return accounts.values().stream()
            .filter(a -> a.getAccountType() == AccountType.LIVE_TRADING)
            .toList();
    }
    
    /**
     * Create a new paper trading account
     */
    public TradingAccount createPaperAccount(String accountName, BigDecimal initialBalance) {
        String accountId = "paper-" + System.currentTimeMillis();
        PaperTradingAccount account = new PaperTradingAccount(accountId, accountName, initialBalance);
        registerAccount(account);
        return account;
    }
    
    /**
     * Create a new live trading account
     */
    public TradingAccount createLiveAccount(String accountName, String exchangeName, 
                                           String apiKey, String apiSecret) {
        String accountId = "live-" + exchangeName.toLowerCase() + "-" + System.currentTimeMillis();
        LiveTradingAccount account = new LiveTradingAccount(
            accountId, accountName, exchangeName, apiKey, apiSecret
        );
        registerAccount(account);
        return account;
    }
    
    /**
     * Remove an account
     */
    public boolean removeAccount(String accountId) {
        if (accountId.equals(activeAccountId)) {
            throw new IllegalStateException("Cannot remove active account");
        }
        TradingAccount removed = accounts.remove(accountId);
        if (removed != null) {
            System.out.println("🗑️ Removed account: " + removed.getAccountName());
            return true;
        }
        return false;
    }
    
    /**
     * Execute order on active account
     */
    public Order executeOrder(Order order) {
        if (!hasActiveAccount()) {
            throw new IllegalStateException("No active account set. Please activate an account first.");
        }
        TradingAccount account = getActiveAccount();
        if (account == null) {
            throw new IllegalStateException("Active account not found. Please activate a valid account.");
        }
        return account.executeOrder(order);
    }
    
    /**
     * Execute order on specific account
     */
    public Order executeOrder(String accountId, Order order) {
        TradingAccount account = getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account.executeOrder(order);
    }
    
    /**
     * Get account summary
     */
    public Map<String, Object> getAccountSummary(String accountId) {
        TradingAccount account = getAccount(accountId);
        if (account == null) {
            return Map.of("error", "Account not found");
        }
        
        AccountStats stats = account.getAccountStats();
        
        return Map.of(
            "accountId", account.getAccountId(),
            "accountName", account.getAccountName(),
            "accountType", account.getAccountType().getDisplayName(),
            "balance", account.getBalance(),
            "totalPnL", account.getTotalPnL(),
            "totalTrades", stats.getTotalTrades(),
            "winRate", stats.getWinRate(),
            "isEnabled", account.isEnabled(),
            "isActive", accountId.equals(activeAccountId)
        );
    }
    
    /**
     * Get summary of all accounts
     */
    public List<Map<String, Object>> getAllAccountsSummary() {
        return accounts.keySet().stream()
            .map(this::getAccountSummary)
            .toList();
    }
}

