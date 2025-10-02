package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.OrderManager;
import org.cloudvision.trading.bot.RiskManager;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.account.AccountStats;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.StrategyConfig;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.model.TimeInterval;
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
    public ResponseEntity<Map<String, Object>> enable(
            @RequestParam(required = false, defaultValue = "false") boolean bootstrap,
            @RequestParam(required = false, defaultValue = "1m") String interval,
            @RequestParam(required = false, defaultValue = "100") int historicalLimit) {
        try {
            if (bootstrap) {
                TimeInterval timeInterval = TimeInterval.fromString(interval);
                tradingBot.enable(true, timeInterval, historicalLimit);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Bot enabled with historical bootstrapping",
                    "mode", "analysis",
                    "bootstrap", true,
                    "historicalLimit", historicalLimit,
                    "interval", interval
                ));
            } else {
                tradingBot.enable();
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Bot enabled - Analysis mode active",
                    "mode", "analysis",
                    "bootstrap", false
                ));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid interval: " + interval
            ));
        }
    }
    
    @Operation(summary = "Bootstrap Strategies", description = "Bootstrap strategies with historical data before starting real-time analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Strategies bootstrapped successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrapStrategies(
            @RequestParam(defaultValue = "Binance") String provider,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            tradingBot.bootstrapStrategies(provider, timeInterval, limit);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Strategies bootstrapped successfully",
                "provider", provider,
                "interval", interval,
                "historicalCandles", limit
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid interval: " + interval
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

    @Operation(summary = "Enable Strategy", description = "Enable a trading strategy. Only ONE strategy can be enabled per account at a time to prevent conflicts.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Strategy enabled successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot enable - another strategy is already active")
    })
    @PostMapping("/strategies/{strategyId}/enable")
    public ResponseEntity<Map<String, Object>> enableStrategy(@PathVariable String strategyId) {
        try {
            tradingBot.setStrategyEnabled(strategyId, true);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Strategy enabled successfully",
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

    @Operation(summary = "Disable Strategy", description = "Disable a trading strategy")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Strategy disabled successfully")
    })
    @PostMapping("/strategies/{strategyId}/disable")
    public ResponseEntity<Map<String, Object>> disableStrategy(@PathVariable String strategyId) {
        tradingBot.setStrategyEnabled(strategyId, false);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Strategy disabled successfully",
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
