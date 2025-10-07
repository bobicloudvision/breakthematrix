package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderStatus;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    
    // Multiple additional handlers to forward data to (e.g., WebSocket handlers, footprint builder, etc.)
    private final List<Consumer<TradingData>> additionalDataHandlers = new CopyOnWriteArrayList<>();

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
     * Add an additional data handler (e.g., for WebSocket broadcasting, footprint building)
     * Supports multiple handlers without overwriting
     */
    public void addDataHandler(Consumer<TradingData> handler) {
        this.additionalDataHandlers.add(handler);
        System.out.println("🔗 Additional data handler added to TradingBot (total: " + additionalDataHandlers.size() + ")");
    }
    
    /**
     * Legacy method for backward compatibility - now adds to list instead of replacing
     * @deprecated Use addDataHandler instead
     */
    @Deprecated
    public void setAdditionalDataHandler(Consumer<TradingData> handler) {
        addDataHandler(handler);
        System.out.println("⚠️  setAdditionalDataHandler is deprecated. Use addDataHandler() instead.");
    }
    
    /**
     * Process incoming market data and execute strategies
     */
    private void processMarketData(TradingData data) {
        // Note: Candlestick storage is now handled by UniversalTradingDataService
        // TradingBot focuses only on trading logic
        
        // Update current prices for all accounts to calculate unrealized P&L
        updateAccountPrices(data);
        
        // Forward to all additional handlers FIRST (e.g., WebSocket, footprint builder, etc.)
        if (!additionalDataHandlers.isEmpty()) {
            for (Consumer<TradingData> handler : additionalDataHandlers) {
                try {
                    handler.accept(data);
                } catch (Exception e) {
                    System.err.println("❌ Error in additional data handler: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // Then process for trading bot
        if (!botEnabled) {
            return;
        }

        // Process data through all enabled strategies using event-driven methods
        for (TradingStrategy strategy : strategies) {
            if (strategy.isEnabled() && strategyStatus.getOrDefault(strategy.getStrategyId(), true)) {
                try {
                    // Call appropriate event-driven method based on data type
                    Map<String, Object> result = processStrategyEvent(strategy, data);
                    
                    // Extract orders from result
                    List<Order> orders = extractOrders(result);
                    
                    // Log when strategy generates orders
                    if (!orders.isEmpty()) {
                        System.out.println("📈 Strategy [" + strategy.getStrategyId() + "] generated " + 
                            orders.size() + " order(s) for " + data.getSymbol());
                    }
                    
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
                                }
                                else if (executedOrder.getStatus() == org.cloudvision.trading.bot.model.OrderStatus.REJECTED) {
                                    System.out.println("❌ Order was rejected by the exchange: " + executedOrder);
                                } else if (executedOrder.getStatus() == OrderStatus.SUBMITTED) {
                                    System.out.println("⏳ Order submitted but not yet filled: " + executedOrder);
                                }
                                else {
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
                    System.err.println("❌ Error in strategy " + strategy.getStrategyId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Process a strategy event by calling the appropriate event-driven method
     */
    private Map<String, Object> processStrategyEvent(TradingStrategy strategy, TradingData data) {
        String symbol = data.getSymbol();
        
        switch (data.getType()) {
            case KLINE:
                if (data.getCandlestickData() != null) {
                    CandlestickData candle = data.getCandlestickData();
                    // Get strategy params (empty map if not available)
                    Map<String, Object> params = new java.util.HashMap<>();
                    return strategy.onNewCandle(candle, params, null);
                }
                break;
                
            case TICKER:
                if (data.getPrice() != null) {
                    // Get strategy params (empty map if not available)
                    Map<String, Object> params = new java.util.HashMap<>();
                    return strategy.onNewTick(symbol, data.getPrice(), params, null);
                }
                break;
                
            default:
                // Other data types not yet supported for trading
                break;
        }
        
        // Return empty result if no event was processed
        return Map.of("orders", List.of());
    }
    
    /**
     * Extract orders from strategy result with safe type checking
     */
    private List<Order> extractOrders(Map<String, Object> result) {
        if (result == null) {
            return List.of();
        }
        
        Object ordersObj = result.get("orders");
        if (ordersObj instanceof List<?>) {
            List<?> ordersList = (List<?>) ordersObj;
            if (ordersList.isEmpty()) {
                return List.of();
            }
            if (ordersList.get(0) instanceof Order) {
                @SuppressWarnings("unchecked")
                List<Order> orders = (List<Order>) ordersObj;
                return orders;
            }
        }
        
        return List.of();
    }
    
    /**
     * Add a trading strategy to the bot
     * Strategies are registered but DISABLED by default for safety
     * Use setStrategyEnabled() or the /api/bot/strategies/{id}/enable endpoint to enable them
     */
    public void addStrategy(TradingStrategy strategy) {
        strategies.add(strategy);
        strategyStatus.put(strategy.getStrategyId(), false); // Disabled by default
        strategy.setEnabled(false); // Ensure strategy itself is also disabled
        System.out.println("📝 Registered strategy: " + strategy.getStrategyName() + " (disabled by default)");
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
     * Only one strategy can be enabled per account to prevent conflicts
     * When enabling, loads historical data if not already loaded
     */
    public void setStrategyEnabled(String strategyId, boolean enabled) {
        if (enabled) {
            // Check if any other strategy is already enabled
            List<String> enabledStrategies = strategies.stream()
                    .filter(s -> !s.getStrategyId().equals(strategyId))
                    .filter(TradingStrategy::isEnabled)
                    .map(TradingStrategy::getStrategyName)
                    .toList();
            
            if (!enabledStrategies.isEmpty()) {
                String message = "❌ Cannot enable multiple strategies on the same account! " +
                        "Currently enabled: " + String.join(", ", enabledStrategies) + ". " +
                        "Please disable the active strategy before enabling a new one.";
                System.out.println(message);
                throw new IllegalStateException(message);
            }
            
            // Load historical data for the strategy if not already loaded
            TradingStrategy strategy = strategies.stream()
                    .filter(s -> s.getStrategyId().equals(strategyId))
                    .findFirst()
                    .orElse(null);
            
            if (strategy != null && !strategy.isBootstrapped()) {
                loadHistoricalDataForStrategy(strategy);
            }
        }
        
        strategyStatus.put(strategyId, enabled);
        strategies.stream()
                .filter(s -> s.getStrategyId().equals(strategyId))
                .findFirst()
                .ifPresent(s -> s.setEnabled(enabled));

        if (enabled) {
            System.out.println("✅ Strategy " + strategyId + " enabled - This is the only active strategy");
        } else {
            System.out.println("🛑 Strategy " + strategyId + " disabled");
        }
    }

    /**
     * Load historical data for a specific strategy
     * @param strategy The strategy to load data for
     */
    private void loadHistoricalDataForStrategy(TradingStrategy strategy) {
        System.out.println("📊 Loading historical data for strategy: " + strategy.getStrategyName());
        
        // Collect all historical data for this strategy
        List<CandlestickData> allHistoricalData = new ArrayList<>();
        
        for (String symbol : strategy.getSymbols()) {
            try {
                System.out.println("📊 Fetching 5000 historical candles for " + symbol + " (1m)");
                
                List<CandlestickData> historicalData = tradingDataService.getHistoricalKlines(
                    "Binance", symbol, TimeInterval.ONE_MINUTE, 5000
                );
                
                if (!historicalData.isEmpty()) {
                    allHistoricalData.addAll(historicalData);
                    System.out.println("✅ Fetched " + historicalData.size() + " candles for " + symbol);
                } else {
                    System.err.println("❌ No historical data available for " + symbol);
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error fetching historical data for " + symbol + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Initialize strategy with historical data using event-driven onInit()
        if (!allHistoricalData.isEmpty()) {
            // Prepare params map (empty for now, could be from strategy config later)
            Map<String, Object> params = new java.util.HashMap<>();
            
            strategy.onInit(allHistoricalData, params);
            System.out.println("✅ Strategy " + strategy.getStrategyName() + " initialized with " + allHistoricalData.size() + " candles");
            
            // Generate historical visualization data
            try {
                strategy.generateHistoricalVisualizationData(allHistoricalData);
                System.out.println("✅ Generated historical visualization data for " + strategy.getStrategyName());
            } catch (Exception e) {
                System.err.println("⚠️ Error generating historical visualization data: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    /**
     * Enable the bot (analysis mode by default)
     * Note: Provider connection and kline subscriptions must be set up at application startup
     * Historical data will be loaded automatically when strategies are enabled
     */
    public void enable() {
        botEnabled = true;
        System.out.println("🚀 Bot ENABLED - Analysis mode active");
        System.out.println("ℹ️  Bot assumes provider is already connected and subscriptions are set up");
        System.out.println("ℹ️  Historical data will be loaded when strategies are enabled");
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
    
    /**
     * Update current prices for all trading accounts
     * This updates unrealized P&L for open positions
     */
    private void updateAccountPrices(TradingData data) {

//        System.out.println("🔄 Updating account prices for " + data.getSymbol());

        // Extract current price from the data
        java.math.BigDecimal currentPrice = null;
        String symbol = data.getSymbol();
        
        switch (data.getType()) {
            case TICKER:
                currentPrice = data.getPrice();
                break;
            case KLINE:
                if (data.getCandlestickData() != null) {
                    currentPrice = data.getCandlestickData().getClose();
                }
                break;
            default:
//                System.err.println("Unknown trading type " + data.getType());
                return; // Unknown data type
        }
        
        if (currentPrice == null) {
            System.err.println("Current price is null");
            return;
        }
        
        // Create price map
        Map<String, java.math.BigDecimal> currentPrices = new ConcurrentHashMap<>();
        currentPrices.put(symbol, currentPrice);
        
        // Update prices for all accounts
        for (org.cloudvision.trading.bot.account.TradingAccount account : accountManager.getAllAccounts()) {
            try {
                account.updateCurrentPrices(currentPrices);
            } catch (Exception e) {
                System.err.println("❌ Error updating prices for account " + account.getAccountName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Print storage statistics for debugging
     * Delegates to UniversalTradingDataService which manages CandlestickHistoryService
     */
    public void printStorageStats() {
        tradingDataService.printStorageStats();
    }
}
