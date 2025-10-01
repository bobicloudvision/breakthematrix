package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TradingBot {
    
    private final UniversalTradingDataService tradingDataService;
    private final OrderManager orderManager;
    private final RiskManager riskManager;
    private final PortfolioManager portfolioManager;
    
    private final List<TradingStrategy> strategies = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> strategyStatus = new ConcurrentHashMap<>();
    private boolean botEnabled = false;

    public TradingBot(UniversalTradingDataService tradingDataService,
                     OrderManager orderManager,
                     RiskManager riskManager,
                     PortfolioManager portfolioManager) {
        this.tradingDataService = tradingDataService;
        this.orderManager = orderManager;
        this.riskManager = riskManager;
        this.portfolioManager = portfolioManager;
        
        // Set up data handler to process incoming market data
        this.tradingDataService.setGlobalDataHandler(this::processMarketData);
    }

    /**
     * Process incoming market data and execute strategies
     */
    private void processMarketData(TradingData data) {
        if (!botEnabled) {
            return;
        }

        // Process data through all enabled strategies
        for (TradingStrategy strategy : strategies) {
            if (strategy.isEnabled() && strategyStatus.getOrDefault(strategy.getStrategyId(), true)) {
                try {
                    List<Order> orders = strategy.analyze(data);
                    
                    for (Order order : orders) {
                        // Apply risk management
                        if (riskManager.validateOrder(order)) {
                            // Execute order
                            orderManager.submitOrder(order);
                            System.out.println("🤖 Bot executed order: " + order);
                        } else {
                            System.out.println("❌ Risk manager rejected order: " + order);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error in strategy " + strategy.getStrategyId() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Add a trading strategy to the bot
     */
    public void addStrategy(TradingStrategy strategy) {
        strategies.add(strategy);
        strategyStatus.put(strategy.getStrategyId(), true);
        System.out.println("Added strategy: " + strategy.getStrategyName());
    }

    /**
     * Remove a trading strategy from the bot
     */
    public void removeStrategy(String strategyId) {
        strategies.removeIf(s -> s.getStrategyId().equals(strategyId));
        strategyStatus.remove(strategyId);
        System.out.println("Removed strategy: " + strategyId);
    }

    /**
     * Enable/disable a specific strategy
     */
    public void setStrategyEnabled(String strategyId, boolean enabled) {
        strategyStatus.put(strategyId, enabled);
        strategies.stream()
                .filter(s -> s.getStrategyId().equals(strategyId))
                .findFirst()
                .ifPresent(s -> s.setEnabled(enabled));
        System.out.println("Strategy " + strategyId + " " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Start the trading bot
     */
    public void start() {
        botEnabled = true;
        System.out.println("🚀 Trading Bot STARTED");
        
        // Subscribe to market data for all strategy symbols
        for (TradingStrategy strategy : strategies) {
            for (String symbol : strategy.getSymbols()) {
                tradingDataService.connectProvider("Binance");
                tradingDataService.subscribeToSymbol("Binance", symbol);
            }
        }
    }

    /**
     * Stop the trading bot
     */
    public void stop() {
        botEnabled = false;
        System.out.println("🛑 Trading Bot STOPPED");
    }

    /**
     * Emergency stop - cancel all orders and stop bot
     */
    public void emergencyStop() {
        stop();
        orderManager.cancelAllOrders();
        System.out.println("🚨 EMERGENCY STOP - All orders cancelled");
    }

    // Getters
    public boolean isBotEnabled() { return botEnabled; }
    public List<TradingStrategy> getStrategies() { return List.copyOf(strategies); }
    public Map<String, Boolean> getStrategyStatus() { return Map.copyOf(strategyStatus); }
    public OrderManager getOrderManager() { return orderManager; }
    public RiskManager getRiskManager() { return riskManager; }
    public PortfolioManager getPortfolioManager() { return portfolioManager; }
}
