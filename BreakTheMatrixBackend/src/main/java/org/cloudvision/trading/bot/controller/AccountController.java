package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.account.AccountManager;
import org.cloudvision.trading.bot.account.AccountStats;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.cloudvision.trading.bot.model.Order;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account Management Controller
 * Manage multiple trading accounts (paper trading and live)
 */
@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Account Management", description = "Manage trading accounts and track performance")
public class AccountController {
    
    private final TradingBot tradingBot;
    private final AccountManager accountManager;
    
    public AccountController(TradingBot tradingBot) {
        this.tradingBot = tradingBot;
        this.accountManager = tradingBot.getAccountManager();
    }
    
    // ==================== Account Management ====================
    
    @Operation(summary = "Get All Accounts", description = "Get list of all trading accounts")
    @GetMapping
    public List<Map<String, Object>> getAllAccounts() {
        return accountManager.getAllAccountsSummary();
    }
    
    @Operation(summary = "Get Account Details", description = "Get detailed information about a specific account")
    @GetMapping("/{accountId}")
    public Map<String, Object> getAccountDetails(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            return Map.of("error", "Account not found");
        }
        
        Map<String, Object> details = new HashMap<>();
        details.put("accountId", account.getAccountId());
        details.put("accountName", account.getAccountName());
        details.put("accountType", account.getAccountType());
        details.put("balance", account.getBalance());
        details.put("balances", account.getAllBalances());
        details.put("totalPnL", account.getTotalPnL());
        details.put("isEnabled", account.isEnabled());
        details.put("isActive", account.equals(accountManager.getActiveAccount()));
        
        return details;
    }
    
    @Operation(summary = "Get Account Statistics", description = "Get performance statistics for an account")
    @GetMapping("/{accountId}/stats")
    public AccountStats getAccountStats(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account.getAccountStats();
    }
    
    @Operation(summary = "Create Paper Trading Account", description = "Create a new paper trading account")
    @PostMapping("/paper")
    public Map<String, Object> createPaperAccount(
            @RequestParam String accountName,
            @RequestParam(defaultValue = "100000") BigDecimal initialBalance) {
        
        TradingAccount account = accountManager.createPaperAccount(accountName, initialBalance);
        
        return Map.of(
            "success", true,
            "accountId", account.getAccountId(),
            "accountName", account.getAccountName(),
            "accountType", account.getAccountType(),
            "initialBalance", initialBalance,
            "message", "Paper trading account created successfully"
        );
    }
    
    @Operation(summary = "Create Live Trading Account", description = "Create a new live trading account (USE WITH CAUTION)")
    @PostMapping("/live")
    public Map<String, Object> createLiveAccount(
            @RequestParam String accountName,
            @RequestParam String exchangeName,
            @RequestParam String apiKey,
            @RequestParam String apiSecret) {
        
        TradingAccount account = accountManager.createLiveAccount(
            accountName, exchangeName, apiKey, apiSecret
        );
        
        return Map.of(
            "success", true,
            "accountId", account.getAccountId(),
            "accountName", account.getAccountName(),
            "accountType", account.getAccountType(),
            "exchangeName", exchangeName,
            "warning", "⚠️ LIVE TRADING ACCOUNT - USE WITH EXTREME CAUTION",
            "message", "Live trading account created (disabled by default)"
        );
    }
    
    @Operation(summary = "Set Active Account", description = "Set which account the bot will use for trading")
    @PostMapping("/{accountId}/activate")
    public Map<String, Object> setActiveAccount(@PathVariable String accountId) {
        accountManager.setActiveAccount(accountId);
        TradingAccount account = accountManager.getAccount(accountId);
        
        return Map.of(
            "success", true,
            "activeAccount", account.getAccountName(),
            "accountType", account.getAccountType(),
            "message", "Active account set to: " + account.getAccountName()
        );
    }
    
    @Operation(summary = "Deactivate Account", description = "Deactivate a specific account - if it's the active account, bot will not execute trades until another account is activated")
    @PostMapping("/{accountId}/deactivate")
    public Map<String, Object> deactivateAccount(@PathVariable String accountId) {
        boolean wasActive = accountManager.deactivateAccount(accountId);
        TradingAccount account = accountManager.getAccount(accountId);
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("accountId", accountId);
        response.put("accountName", account.getAccountName());
        response.put("message", "Account deactivated: " + account.getAccountName());
        
        if (wasActive) {
            response.put("warning", "⚠️ This was the active account. Bot will not execute trades until you activate another account.");
            response.put("activeAccount", "None");
        }
        
        return response;
    }
    
    @Operation(summary = "Enable/Disable Account", description = "Enable or disable an account")
    @PutMapping("/{accountId}/enabled")
    public Map<String, Object> setAccountEnabled(
            @PathVariable String accountId,
            @RequestParam boolean enabled) {
        
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            return Map.of("error", "Account not found");
        }
        
        account.setEnabled(enabled);
        
        return Map.of(
            "success", true,
            "accountId", accountId,
            "accountName", account.getAccountName(),
            "enabled", enabled,
            "message", "Account " + (enabled ? "enabled" : "disabled")
        );
    }
    
    @Operation(summary = "Reset Paper Account", description = "Reset a paper trading account to initial balance")
    @PostMapping("/{accountId}/reset")
    public Map<String, Object> resetAccount(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            return Map.of("error", "Account not found");
        }
        
        try {
            account.reset();
            return Map.of(
                "success", true,
                "accountId", accountId,
                "message", "Account reset successfully"
            );
        } catch (UnsupportedOperationException e) {
            return Map.of(
                "error", "Cannot reset this account type",
                "message", e.getMessage()
            );
        }
    }
    
    @Operation(summary = "Delete Account", description = "Delete a trading account")
    @DeleteMapping("/{accountId}")
    public Map<String, Object> deleteAccount(@PathVariable String accountId) {
        boolean removed = accountManager.removeAccount(accountId);
        
        if (removed) {
            return Map.of(
                "success", true,
                "message", "Account deleted successfully"
            );
        } else {
            return Map.of(
                "error", "Failed to delete account",
                "message", "Account may be active or not found"
            );
        }
    }
    
    // ==================== Orders & Trades ====================
    
    @Operation(summary = "Get Account Orders", description = "Get all orders for an account")
    @GetMapping("/{accountId}/orders")
    public List<Order> getAccountOrders(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account.getAllOrders();
    }
    
    @Operation(summary = "Get Open Orders", description = "Get open orders for an account")
    @GetMapping("/{accountId}/orders/open")
    public List<Order> getOpenOrders(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account.getOpenOrders();
    }
    
    @Operation(summary = "Get Filled Orders", description = "Get filled orders (trade history) for an account")
    @GetMapping("/{accountId}/orders/filled")
    public List<Order> getFilledOrders(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account.getFilledOrders();
    }
    
    // ==================== Account Balances ====================
    
    @Operation(summary = "Get Account Balance", description = "Get current balance for an account")
    @GetMapping("/{accountId}/balance")
    public Map<String, BigDecimal> getAccountBalance(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        
        return Map.of(
            "USDT", account.getBalance(),
            "totalPnL", account.getTotalPnL()
        );
    }
    
    @Operation(summary = "Get All Asset Balances", description = "Get balances for all assets in an account")
    @GetMapping("/{accountId}/balances")
    public Map<String, BigDecimal> getAllBalances(@PathVariable String accountId) {
        TradingAccount account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account.getAllBalances();
    }
}

