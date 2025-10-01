package org.cloudvision.trading.bot.controller;

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
public class TradingBotController {
    
    private final TradingBot tradingBot;

    public TradingBotController(TradingBot tradingBot) {
        this.tradingBot = tradingBot;
    }

    // Bot Control Endpoints
    @PostMapping("/start")
    public String startBot() {
        tradingBot.start();
        return "Trading bot started";
    }

    @PostMapping("/stop")
    public String stopBot() {
        tradingBot.stop();
        return "Trading bot stopped";
    }

    @PostMapping("/emergency-stop")
    public String emergencyStop() {
        tradingBot.emergencyStop();
        return "Emergency stop executed";
    }

    @GetMapping("/status")
    public Map<String, Object> getBotStatus() {
        return Map.of(
            "enabled", tradingBot.isBotEnabled(),
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
