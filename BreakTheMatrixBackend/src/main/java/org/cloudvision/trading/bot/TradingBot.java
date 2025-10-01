package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class TradingBot {
    
    private final UniversalTradingDataService tradingDataService;
    private final OrderManager orderManager;
    private final RiskManager riskManager;
    private final org.cloudvision.trading.bot.account.AccountManager accountManager;
    
    private final List<TradingStrategy> strategies = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> strategyStatus = new ConcurrentHashMap<>();
    private boolean botEnabled = false;
    private boolean tradingEnabled = false; // Separate flag for trading execution
    
    // Additional handler to forward data to (e.g., WebSocket handler)
    private Consumer<TradingData> additionalDataHandler;

    public TradingBot(UniversalTradingDataService tradingDataService,
                     OrderManager orderManager,
                     RiskManager riskManager,
                     org.cloudvision.trading.bot.account.AccountManager accountManager) {
        this.tradingDataService = tradingDataService;
        this.orderManager = orderManager;
        this.riskManager = riskManager;
        this.accountManager = accountManager;
        
        // Set up data handler to process incoming market data
        this.tradingDataService.setGlobalDataHandler(this::processMarketData);
    }

    /**
     * Set additional data handler (e.g., for WebSocket broadcasting)
     */
    public void setAdditionalDataHandler(Consumer<TradingData> handler) {
        this.additionalDataHandler = handler;
        System.out.println("🔗 Additional data handler set in TradingBot");
    }
    
    /**
     * Process incoming market data and execute strategies
     */
    private void processMarketData(TradingData data) {
        System.out.println("🤖 TradingBot processing market data: " + data.getSymbol());
        
        // Forward to additional handler FIRST (e.g., WebSocket)
        if (additionalDataHandler != null) {
            try {
                System.out.println("🔄 Forwarding data to additional handler (WebSocket)...");
                additionalDataHandler.accept(data);
            } catch (Exception e) {
                System.err.println("❌ Error in additional data handler: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Then process for trading bot
        if (!botEnabled) {
            return;
        }

        // Process data through all enabled strategies
        for (TradingStrategy strategy : strategies) {
            if (strategy.isEnabled() && strategyStatus.getOrDefault(strategy.getStrategyId(), true)) {
                try {
                    List<Order> orders = strategy.analyze(data);
                    
                    // Always process orders for analysis, but only execute if trading is enabled
                    for (Order order : orders) {
                        if (tradingEnabled) {
                            // Apply risk management and execute through active account
                            if (riskManager.validateOrder(order)) {
                                // Execute order through AccountManager (uses active account)
                                Order executedOrder = accountManager.executeOrder(order);
                                
                                if (executedOrder.getStatus() == org.cloudvision.trading.bot.model.OrderStatus.FILLED) {
                                    // Also track in OrderManager for backward compatibility
                                    orderManager.submitOrder(executedOrder);
                                    
                                    String accountName = accountManager.getActiveAccount().getAccountName();
                                    System.out.println("🤖 Bot executed order on [" + accountName + "]: " + executedOrder);
                                } else {
                                    System.out.println("❌ Order execution failed: " + executedOrder.getStatus());
                                }
                            } else {
                                System.out.println("❌ Risk manager rejected order: " + order);
                            }
                        } else {
                            // Analysis mode - log signals but don't execute
                            System.out.println("📊 Analysis mode - Signal generated: " + order.getSide() + 
                                             " " + order.getSymbol() + " @ " + order.getPrice() + 
                                             " (Trading disabled)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error in strategy " + strategy.getStrategyId() + ": " + e.getMessage());
                    e.printStackTrace();
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
     * Bootstrap strategies with historical data
     * @param provider Provider name (e.g., "Binance")
     * @param interval Time interval for historical data
     * @param limit Number of historical candles to fetch
     */
    public void bootstrapStrategies(String provider, TimeInterval interval, int limit) {
        System.out.println("🔄 Bootstrapping strategies with historical data...");
        
        for (TradingStrategy strategy : strategies) {
            for (String symbol : strategy.getSymbols()) {
                try {
                    System.out.println("📊 Fetching " + limit + " historical candles for " + symbol + " (" + interval.getValue() + ")");
                    
                    List<CandlestickData> historicalData = tradingDataService.getHistoricalKlines(
                        provider, symbol, interval, limit
                    );
                    
                    if (!historicalData.isEmpty()) {
                        strategy.bootstrapWithHistoricalData(historicalData);
                        System.out.println("✅ Strategy " + strategy.getStrategyName() + " bootstrapped for " + symbol);
                    } else {
                        System.err.println("❌ No historical data available for " + symbol);
                    }
                    
                } catch (Exception e) {
                    System.err.println("❌ Error bootstrapping strategy for " + symbol + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("✅ Strategy bootstrapping complete!");
    }
    
    /**
     * Bootstrap strategies with historical data using time range
     * @param provider Provider name (e.g., "Binance")
     * @param interval Time interval for historical data
     * @param startTime Start time for historical data
     * @param endTime End time for historical data
     */
    public void bootstrapStrategies(String provider, TimeInterval interval, Instant startTime, Instant endTime) {
        System.out.println("🔄 Bootstrapping strategies with historical data from " + startTime + " to " + endTime);
        
        for (TradingStrategy strategy : strategies) {
            for (String symbol : strategy.getSymbols()) {
                try {
                    System.out.println("📊 Fetching historical data for " + symbol + " (" + interval.getValue() + ")");
                    
                    List<CandlestickData> historicalData = tradingDataService.getHistoricalKlines(
                        provider, symbol, interval, startTime, endTime
                    );
                    
                    if (!historicalData.isEmpty()) {
                        strategy.bootstrapWithHistoricalData(historicalData);
                        System.out.println("✅ Strategy " + strategy.getStrategyName() + " bootstrapped for " + symbol);
                    } else {
                        System.err.println("❌ No historical data available for " + symbol);
                    }
                    
                } catch (Exception e) {
                    System.err.println("❌ Error bootstrapping strategy for " + symbol + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("✅ Strategy bootstrapping complete!");
    }

    /**
     * Enable the bot (analysis mode by default)
     * @param bootstrapHistorical Whether to bootstrap with historical data first
     * @param interval Time interval for kline subscriptions and historical data
     * @param historicalLimit Number of historical candles to fetch (if bootstrapping)
     */
    public void enable(boolean bootstrapHistorical, TimeInterval interval, int historicalLimit) {
        // Connect to provider first
        tradingDataService.connectProvider("Binance");
        
        // Bootstrap with historical data if requested
        if (bootstrapHistorical) {
            System.out.println("📊 Bootstrapping with " + historicalLimit + " historical candles...");
            bootstrapStrategies("Binance", interval, historicalLimit);
        }
        
        botEnabled = true;
        System.out.println("🚀 Bot ENABLED - Analysis mode active");
        
        // Subscribe to real-time kline data for all strategy symbols
        for (TradingStrategy strategy : strategies) {
            for (String symbol : strategy.getSymbols()) {
                tradingDataService.subscribeToKlines("Binance", symbol, interval);
                System.out.println("📡 Subscribed to " + symbol + " klines (" + interval.getValue() + ")");
            }
        }
    }
    
    /**
     * Enable the bot (analysis mode by default) - simple version without bootstrapping
     */
    public void enable() {
        enable(false, TimeInterval.ONE_MINUTE, 0);
    }

    /**
     * Disable the bot completely (stops analysis and trading)
     */
    public void disable() {
        botEnabled = false;
        tradingEnabled = false;
        System.out.println("🛑 Bot DISABLED");
    }

    /**
     * Start trading execution (bot must be enabled first)
     */
    public void startTrading() {
        if (!botEnabled) {
            throw new IllegalStateException("Bot must be enabled before starting trading");
        }
        tradingEnabled = true;
        System.out.println("💰 Trading STARTED - Bot will now execute orders");
    }

    /**
     * Stop trading execution (keeps analysis running)
     */
    public void stopTrading() {
        tradingEnabled = false;
        System.out.println("📊 Trading STOPPED - Analysis mode continues");
    }

    /**
     * Emergency disable - cancel all orders and disable bot
     */
    public void emergencyDisable() {
        disable();
        orderManager.cancelAllOrders();
        System.out.println("🚨 EMERGENCY DISABLE - All orders cancelled");
    }

    // Getters
    public boolean isBotEnabled() { return botEnabled; }
    public boolean isTradingStarted() { return tradingEnabled; }
    public List<TradingStrategy> getStrategies() { return List.copyOf(strategies); }
    public Map<String, Boolean> getStrategyStatus() { return Map.copyOf(strategyStatus); }
    public OrderManager getOrderManager() { return orderManager; }
    public RiskManager getRiskManager() { return riskManager; }
    public org.cloudvision.trading.bot.account.AccountManager getAccountManager() { return accountManager; }
    
    /**
     * Get bot mode description
     */
    public String getBotMode() {
        if (!botEnabled) {
            return "DISABLED";
        } else if (tradingEnabled) {
            return "TRADING";
        } else {
            return "ANALYSIS_ONLY";
        }
    }
    
    /**
     * Get active trading account info
     */
    public String getActiveAccountInfo() {
        org.cloudvision.trading.bot.account.TradingAccount account = accountManager.getActiveAccount();
        if (account == null) {
            return "No active account";
        }
        return String.format("%s (%s) - Balance: $%.2f", 
            account.getAccountName(), 
            account.getAccountType().getDisplayName(),
            account.getBalance());
    }
}
