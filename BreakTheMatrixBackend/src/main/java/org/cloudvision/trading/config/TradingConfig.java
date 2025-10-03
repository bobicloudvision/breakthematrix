package org.cloudvision.trading.config;

import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Core Trading Infrastructure Configuration
 * Handles provider registration, connection, and market data subscriptions
 */
@Configuration("tradingConfig")
@Order(100) // Run after TradingBotConfig (which registers strategies)
public class TradingConfig {

    @Autowired
    private UniversalTradingDataService tradingService;

    @Autowired
    private List<TradingDataProvider> providers;
    
    @Autowired
    private TradingBot tradingBot;
    
    // Configuration
    private static final String DEFAULT_PROVIDER = "Binance";
    private static final TimeInterval DEFAULT_INTERVAL = TimeInterval.ONE_MINUTE;

    @PostConstruct
    public void initializeTradingInfrastructure() {
        System.out.println("\n🔧 Initializing Trading Infrastructure...\n");
        
        // 1. Register all providers and set data handlers
        registerProviders();
        
        // 2. Connect to the trading provider
        connectToProvider();
        
        // 3. Subscribe to market data for all strategy symbols
        subscribeToMarketData();
        
        System.out.println("✅ Trading Infrastructure initialized!\n");
    }
    
    /**
     * Register all trading data providers
     */
    private void registerProviders() {
        providers.forEach(tradingService::registerProvider);
        System.out.println("✅ Auto-registered " + providers.size() + " trading providers");
    }
    
    /**
     * Connect to the default trading provider
     */
    private void connectToProvider() {
        System.out.println("\n🌐 Connecting to " + DEFAULT_PROVIDER + "...");
        try {
            tradingService.connectProvider(DEFAULT_PROVIDER);
            System.out.println("✅ Connected to " + DEFAULT_PROVIDER);
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to " + DEFAULT_PROVIDER + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Subscribe to kline data for all symbols used by strategies
     */
    private void subscribeToMarketData() {
        // Collect all unique symbols from all registered strategies
        Set<String> allSymbols = new HashSet<>();
        for (TradingStrategy strategy : tradingBot.getStrategies()) {
            allSymbols.addAll(strategy.getSymbols());
        }
        
        if (allSymbols.isEmpty()) {
            System.out.println("\n⚠️  No symbols found in strategies. Skipping kline subscriptions.");
            return;
        }
        
        // Subscribe to klines for all symbols
        System.out.println("\n📡 Subscribing to klines for " + allSymbols.size() + " symbols...");
        for (String symbol : allSymbols) {
            try {
                tradingService.subscribeToKlines(DEFAULT_PROVIDER, symbol, DEFAULT_INTERVAL);
                System.out.println("  ✅ Subscribed to " + symbol + " (" + DEFAULT_INTERVAL.getValue() + ")");
            } catch (Exception e) {
                System.err.println("  ❌ Failed to subscribe to " + symbol + ": " + e.getMessage());
            }
        }
    }
}
