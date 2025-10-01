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
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/enable")
    public String enable(
            @RequestParam(required = false, defaultValue = "false") boolean bootstrap,
            @RequestParam(required = false, defaultValue = "1m") String interval,
            @RequestParam(required = false, defaultValue = "100") int historicalLimit) {
        try {
            if (bootstrap) {
                TimeInterval timeInterval = TimeInterval.fromString(interval);
                tradingBot.enable(true, timeInterval, historicalLimit);
                return "Bot enabled with historical bootstrapping - Analysis mode active (" + 
                       historicalLimit + " candles, " + interval + " interval)";
            } else {
                tradingBot.enable();
                return "Bot enabled - Analysis mode active";
            }
        } catch (IllegalArgumentException e) {
            return "Invalid interval: " + interval;
        }
    }
    
    @Operation(summary = "Bootstrap Strategies", description = "Bootstrap strategies with historical data before starting real-time analysis")
    @PostMapping("/bootstrap")
    public String bootstrapStrategies(
            @RequestParam(defaultValue = "Binance") String provider,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            tradingBot.bootstrapStrategies(provider, timeInterval, limit);
            return "Strategies bootstrapped with " + limit + " historical candles (" + interval + " interval)";
        } catch (IllegalArgumentException e) {
            return "Invalid interval: " + interval;
        }
    }

    @Operation(summary = "Disable Bot", description = "Completely disable the trading bot. Stops all analysis and trading activities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot disabled successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/disable")
    public String disable() {
        tradingBot.disable();
        return "Bot disabled";
    }

    @Operation(summary = "Start Trading", description = "Start trading execution. Bot must be enabled first. Will execute buy/sell orders based on strategy signals.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trading started successfully"),
        @ApiResponse(responseCode = "400", description = "Bot must be enabled before starting trading"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/start-trading")
    public String startTrading() {
        try {
            tradingBot.startTrading();
            return "Trading started";
        } catch (IllegalStateException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Operation(summary = "Stop Trading", description = "Stop trading execution while keeping analysis active. No more orders will be executed.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trading stopped successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/stop-trading")
    public String stopTrading() {
        tradingBot.stopTrading();
        return "Trading stopped - Analysis continues";
    }

    @Operation(summary = "Emergency Disable", description = "Emergency shutdown: immediately disable bot and cancel all pending orders.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Emergency disable executed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/emergency-disable")
    public String emergencyDisable() {
        tradingBot.emergencyDisable();
        return "Emergency disable executed";
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

    @PostMapping("/strategies/{strategyId}/enable")
    public String enableStrategy(@PathVariable String strategyId) {
        tradingBot.setStrategyEnabled(strategyId, true);
        return "Strategy " + strategyId + " enabled";
    }

    @PostMapping("/strategies/{strategyId}/disable")
    public String disableStrategy(@PathVariable String strategyId) {
        tradingBot.setStrategyEnabled(strategyId, false);
        return "Strategy " + strategyId + " disabled";
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

    @PostMapping("/orders/{orderId}/cancel")
    public String cancelOrder(@PathVariable String orderId) {
        boolean cancelled = tradingBot.getOrderManager().cancelOrder(orderId);
        return cancelled ? "Order cancelled" : "Order not found or cannot be cancelled";
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

    @PostMapping("/risk/max-position-size")
    public String setMaxPositionSize(@RequestParam BigDecimal amount) {
        tradingBot.getRiskManager().setMaxPositionSize(amount);
        return "Max position size set to " + amount;
    }

    @PostMapping("/risk/max-daily-loss")
    public String setMaxDailyLoss(@RequestParam BigDecimal amount) {
        tradingBot.getRiskManager().setMaxDailyLoss(amount);
        return "Max daily loss set to " + amount;
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
