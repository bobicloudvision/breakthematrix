package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.OrderManager;
import org.cloudvision.trading.bot.RiskManager;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.account.AccountStats;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bot")
@Tag(name = "Trading Bot", description = "Main trading bot control and monitoring endpoints")
public class TradingBotController {
    
    private final TradingBot tradingBot;

    public TradingBotController(TradingBot tradingBot) {
        this.tradingBot = tradingBot;
    }

    // Bot Control Endpoints
    @Operation(summary = "Enable Bot", description = "Enable the trading bot in analysis mode. Bot will analyze market data and generate signals but won't execute trades.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot enabled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enable() {
        try {
            tradingBot.enable();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Bot enabled in analysis mode",
                "mode", "analysis",
                "note", "Historical data will be loaded automatically when strategies are enabled"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to enable bot: " + e.getMessage()
            ));
        }
    }
    

    @Operation(summary = "Disable Bot", description = "Completely disable the trading bot. Stops all analysis and trading activities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot disabled successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disable() {
        tradingBot.disable();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Bot disabled successfully"
        ));
    }

    @Operation(summary = "Start Trading", description = "Start trading execution. Bot must be enabled first. Will execute buy/sell orders based on strategy signals.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trading started successfully"),
        @ApiResponse(responseCode = "400", description = "Bot must be enabled before starting trading"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/start-trading")
    public ResponseEntity<Map<String, Object>> startTrading() {
        try {
            tradingBot.startTrading();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Trading started successfully",
                "tradingActive", true
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Stop Trading", description = "Stop trading execution while keeping analysis active. No more orders will be executed.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trading stopped successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/stop-trading")
    public ResponseEntity<Map<String, Object>> stopTrading() {
        tradingBot.stopTrading();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Trading stopped - Analysis continues",
            "tradingActive", false,
            "analysisActive", true
        ));
    }

    @Operation(summary = "Emergency Disable", description = "Emergency shutdown: immediately disable bot and cancel all pending orders.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Emergency disable executed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/emergency-disable")
    public ResponseEntity<Map<String, Object>> emergencyDisable() {
        tradingBot.emergencyDisable();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Emergency disable executed successfully",
            "botEnabled", false,
            "tradingActive", false
        ));
    }

    @Operation(summary = "Get Bot Status", description = "Get current status of the trading bot including enabled state, trading status, and strategy information.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/status")
    public Map<String, Object> getBotStatus() {
        org.cloudvision.trading.bot.account.TradingAccount activeAccount = 
            tradingBot.getAccountManager().getActiveAccount();
        
        return Map.of(
            "enabled", tradingBot.isBotEnabled(),
            "tradingStarted", tradingBot.isTradingStarted(),
            "mode", tradingBot.getBotMode(),
            "activeAccount", activeAccount != null ? Map.of(
                "id", activeAccount.getAccountId(),
                "name", activeAccount.getAccountName(),
                "type", activeAccount.getAccountType().getDisplayName(),
                "balance", activeAccount.getBalance(),
                "totalPnL", activeAccount.getTotalPnL()
            ) : "None",
            "strategies", tradingBot.getStrategies().size(),
            "activeStrategies", tradingBot.getStrategyStatus().values().stream().mapToLong(b -> b ? 1 : 0).sum()
        );
    }

    // Strategy Management
    @Operation(summary = "Get All Strategies", description = "Get all registered strategies with their status and statistics")
    @GetMapping("/strategies")
    public List<Map<String, Object>> getStrategies() {
        return tradingBot.getStrategies().stream()
                .map(strategy -> Map.of(
                    "id", strategy.getStrategyId(),
                    "name", strategy.getStrategyName(),
                    "enabled", strategy.isEnabled(),
                    "symbols", strategy.getSymbols(),
                    "stats", strategy.getStats()
                ))
                .toList();
    }

    @Operation(summary = "Get Active Strategies", description = "Get only enabled/active strategies")
    @GetMapping("/strategies/active")
    public List<Map<String, Object>> getActiveStrategies() {
        return tradingBot.getStrategies().stream()
                .filter(strategy -> strategy.isEnabled())
                .map(strategy -> Map.of(
                    "id", strategy.getStrategyId(),
                    "name", strategy.getStrategyName(),
                    "enabled", strategy.isEnabled(),
                    "symbols", strategy.getSymbols(),
                    "stats", strategy.getStats()
                ))
                .toList();
    }

    @Operation(summary = "Enable Strategy", description = "Enable a trading strategy. Only ONE strategy can be enabled per account at a time to prevent conflicts. Strategy will start with a clean state (all memory cleared).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Strategy enabled successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot enable - another strategy is already active")
    })
    @PostMapping("/strategies/{strategyId}/enable")
    public ResponseEntity<Map<String, Object>> enableStrategy(@PathVariable String strategyId) {
        try {
            // Reset strategy state before enabling to ensure clean start
            tradingBot.getStrategies().stream()
                    .filter(s -> s.getStrategyId().equals(strategyId))
                    .findFirst()
                    .ifPresent(TradingStrategy::reset);
            
            tradingBot.setStrategyEnabled(strategyId, true);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Strategy enabled successfully with clean state",
                "strategyId", strategyId
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "strategyId", strategyId
            ));
        }
    }

    @Operation(summary = "Disable Strategy", description = "Disable a trading strategy and reset its state (clears all memory)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Strategy disabled successfully")
    })
    @PostMapping("/strategies/{strategyId}/disable")
    public ResponseEntity<Map<String, Object>> disableStrategy(@PathVariable String strategyId) {
        tradingBot.setStrategyEnabled(strategyId, false);
        
        // Reset strategy state after disabling to clear memory
        tradingBot.getStrategies().stream()
                .filter(s -> s.getStrategyId().equals(strategyId))
                .findFirst()
                .ifPresent(TradingStrategy::reset);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Strategy disabled and state reset",
            "strategyId", strategyId
        ));
    }

    // Order Management
    @GetMapping("/orders")
    public List<Order> getActiveOrders() {
        return tradingBot.getOrderManager().getActiveOrders();
    }

    @GetMapping("/orders/history")
    public List<Order> getOrderHistory() {
        return tradingBot.getOrderManager().getOrderHistory();
    }

    @GetMapping("/orders/stats")
    public OrderManager.OrderStats getOrderStats() {
        return tradingBot.getOrderManager().getOrderStats();
    }

    @Operation(summary = "Cancel Order", description = "Cancel a pending order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order cancellation response"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable String orderId) {
        boolean cancelled = tradingBot.getOrderManager().cancelOrder(orderId);
        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Order cancelled successfully",
                "orderId", orderId
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Order not found or cannot be cancelled",
                "orderId", orderId
            ));
        }
    }

    // Portfolio Management
    @GetMapping("/portfolio")
    public Map<String, Object> getPortfolio() {
        org.cloudvision.trading.bot.account.TradingAccount activeAccount = 
            tradingBot.getAccountManager().getActiveAccount();
        
        if (activeAccount == null) {
            return Map.of(
                "error", "No active account",
                "message", "Please activate an account first"
            );
        }
        
        AccountStats stats = activeAccount.getAccountStats();
        
        return Map.of(
            "accountId", activeAccount.getAccountId(),
            "accountName", activeAccount.getAccountName(),
            "accountType", activeAccount.getAccountType().getDisplayName(),
            "balance", activeAccount.getBalance(),
            "totalPnL", activeAccount.getTotalPnL(),
            "stats", Map.of(
                "totalTrades", stats.getTotalTrades(),
                "winningTrades", stats.getWinningTrades(),
                "losingTrades", stats.getLosingTrades(),
                "winRate", stats.getWinRate(),
                "profitFactor", stats.getProfitFactor()
            ),
            "balances", activeAccount.getAllBalances()
        );
    }

    @GetMapping("/portfolio/positions")
    public Map<String, BigDecimal> getPositions() {
        org.cloudvision.trading.bot.account.TradingAccount activeAccount = 
            tradingBot.getAccountManager().getActiveAccount();
        
        if (activeAccount == null) {
            return Map.of();
        }
        
        return activeAccount.getAllBalances();
    }

    // Risk Management
    @GetMapping("/risk")
    public RiskManager.RiskMetrics getRiskMetrics() {
        return tradingBot.getRiskManager().getRiskMetrics();
    }

    @Operation(summary = "Set Max Position Size", description = "Set the maximum position size for risk management")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Max position size updated successfully")
    })
    @PostMapping("/risk/max-position-size")
    public ResponseEntity<Map<String, Object>> setMaxPositionSize(@RequestParam BigDecimal amount) {
        tradingBot.getRiskManager().setMaxPositionSize(amount);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Max position size updated successfully",
            "maxPositionSize", amount
        ));
    }

    @Operation(summary = "Set Max Daily Loss", description = "Set the maximum daily loss limit for risk management")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Max daily loss updated successfully")
    })
    @PostMapping("/risk/max-daily-loss")
    public ResponseEntity<Map<String, Object>> setMaxDailyLoss(@RequestParam BigDecimal amount) {
        tradingBot.getRiskManager().setMaxDailyLoss(amount);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Max daily loss updated successfully",
            "maxDailyLoss", amount
        ));
    }

    // Dashboard Data
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        org.cloudvision.trading.bot.account.TradingAccount activeAccount = 
            tradingBot.getAccountManager().getActiveAccount();
        RiskManager.RiskMetrics risk = tradingBot.getRiskManager().getRiskMetrics();
        OrderManager.OrderStats orders = tradingBot.getOrderManager().getOrderStats();
        
        Map<String, Object> dashboard = new java.util.HashMap<>();
        
        dashboard.put("bot", Map.of(
            "enabled", tradingBot.isBotEnabled(),
            "tradingStarted", tradingBot.isTradingStarted(),
            "mode", tradingBot.getBotMode(),
            "strategies", tradingBot.getStrategies().size()
        ));
        
        if (activeAccount != null) {
            AccountStats stats = activeAccount.getAccountStats();
            dashboard.put("portfolio", Map.of(
                "balance", activeAccount.getBalance(),
                "totalPnL", activeAccount.getTotalPnL(),
                "totalTrades", stats.getTotalTrades(),
                "winRate", stats.getWinRate(),
                "accountName", activeAccount.getAccountName(),
                "accountType", activeAccount.getAccountType().getDisplayName()
            ));
        } else {
            dashboard.put("portfolio", Map.of(
                "error", "No active account"
            ));
        }
        
        dashboard.put("risk", Map.of(
            "exposureUtilization", risk.getExposureUtilization(),
            "dailyPnL", risk.getDailyPnL(),
            "maxDailyLoss", risk.getMaxDailyLoss()
        ));
        
        dashboard.put("orders", Map.of(
            "totalOrders", orders.getTotalOrders(),
            "fillRate", orders.getFillRate()
        ));
        
        return dashboard;
    }
}
