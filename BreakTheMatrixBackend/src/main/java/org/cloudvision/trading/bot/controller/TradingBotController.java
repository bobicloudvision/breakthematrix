package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.OrderManager;
import org.cloudvision.trading.bot.PortfolioManager;
import org.cloudvision.trading.bot.RiskManager;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.StrategyConfig;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
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
    public String enable() {
        tradingBot.enable();
        return "Bot enabled - Analysis mode active";
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
        return Map.of(
            "enabled", tradingBot.isBotEnabled(),
            "tradingStarted", tradingBot.isTradingStarted(),
            "mode", tradingBot.getBotMode(),
            "strategies", tradingBot.getStrategies().size(),
            "activeStrategies", tradingBot.getStrategyStatus().values().stream().mapToLong(b -> b ? 1 : 0).sum()
        );
    }

    // Strategy Management
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
    public PortfolioManager.PortfolioSummary getPortfolio() {
        return tradingBot.getPortfolioManager().getPortfolioSummary();
    }

    @GetMapping("/portfolio/positions")
    public Map<String, PortfolioManager.Position> getPositions() {
        return tradingBot.getPortfolioManager().getPositions();
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
        PortfolioManager.PortfolioSummary portfolio = tradingBot.getPortfolioManager().getPortfolioSummary();
        RiskManager.RiskMetrics risk = tradingBot.getRiskManager().getRiskMetrics();
        OrderManager.OrderStats orders = tradingBot.getOrderManager().getOrderStats();
        
        return Map.of(
            "bot", Map.of(
                "enabled", tradingBot.isBotEnabled(),
                "tradingStarted", tradingBot.isTradingStarted(),
                "mode", tradingBot.getBotMode(),
                "strategies", tradingBot.getStrategies().size()
            ),
            "portfolio", Map.of(
                "totalValue", portfolio.getTotalValue(),
                "dailyPnL", portfolio.getDailyPnL(),
                "unrealizedPnL", portfolio.getUnrealizedPnL(),
                "activePositions", portfolio.getActivePositions()
            ),
            "risk", Map.of(
                "exposureUtilization", risk.getExposureUtilization(),
                "dailyPnL", risk.getDailyPnL(),
                "maxDailyLoss", risk.getMaxDailyLoss()
            ),
            "orders", Map.of(
                "totalOrders", orders.getTotalOrders(),
                "fillRate", orders.getFillRate()
            )
        );
    }
}
