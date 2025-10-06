package org.cloudvision.trading.config;

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
public class TradingConfig {

    @Autowired
    private UniversalTradingDataService tradingService;

    @Autowired
    private List<TradingDataProvider> providers;

    // Configuration
    private static final String DEFAULT_PROVIDER = "Binance";
    private static final TimeInterval DEFAULT_INTERVAL = TimeInterval.ONE_MINUTE;
    private static final String DEFAULT_SYMBOL = "ETHUSDT"; // Default trading symbol

    // Order Flow Configuration
    private static final boolean ENABLE_ORDER_FLOW = false;
    private static final boolean SUBSCRIBE_TO_TRADES = false; // Individual trades (high frequency)
    private static final boolean SUBSCRIBE_TO_AGGREGATE_TRADES = false; // Aggregate trades (recommended)
    private static final boolean SUBSCRIBE_TO_ORDER_BOOK = false; // Full order book depth
    private static final boolean SUBSCRIBE_TO_BOOK_TICKER = false; // Best bid/ask only (lightweight)
    private static final int ORDER_BOOK_DEPTH = 20; // 5, 10, or 20 levels

    @PostConstruct
    public void initializeTradingInfrastructure() {
        System.out.println("\n🔧 Initializing Trading Infrastructure...\n");

        // 1. Register the trading provider
        registerProvider();

        // 2. Connect to the trading provider
        connectToProvider();

        // 3. Subscribe to market data for all strategy symbols
        subscribeToMarketData();

        // 4. Subscribe to order flow data (if enabled)
        if (ENABLE_ORDER_FLOW) {
            subscribeToOrderFlow();
        }

        System.out.println("✅ Trading Infrastructure initialized!\n");
    }

    /**
     * Register all trading data providers
     */
    private void registerProvider() {
        System.out.println("🔗 Registering default trading provider: " + DEFAULT_PROVIDER);
        try {
            for (TradingDataProvider provider : providers) {
                tradingService.registerProvider(provider);
            }
            System.out.println("✅ Connected to " + DEFAULT_PROVIDER);
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to " + DEFAULT_PROVIDER + ": " + e.getMessage());
            e.printStackTrace();
        }
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
     * Subscribe to kline data for all symbols
     */
    private void subscribeToMarketData() {

        try {
            tradingService.subscribeToKlines(DEFAULT_PROVIDER, this.DEFAULT_SYMBOL, DEFAULT_INTERVAL);
            System.out.println("  ✅ Subscribed to " + this.DEFAULT_SYMBOL + " (" + DEFAULT_INTERVAL.getValue() + ")");
        } catch (Exception e) {
            System.err.println("  ❌ Failed to subscribe to " + this.DEFAULT_SYMBOL + ": " + e.getMessage());
        }

    }

    /**
     * Subscribe to order flow data for all symbols
     * Order flow includes: trades, aggregate trades, order book depth, and book ticker
     */
    private void subscribeToOrderFlow() {

        try {
            // Subscribe to individual trades (high frequency - use with caution)
            if (SUBSCRIBE_TO_TRADES) {
                tradingService.getProvider(DEFAULT_PROVIDER).subscribeToTrades(this.DEFAULT_SYMBOL);
                System.out.println("  💹 Subscribed to " + this.DEFAULT_SYMBOL + " trades");
            }

            // Subscribe to aggregate trades (recommended - lower frequency, compressed data)
            if (SUBSCRIBE_TO_AGGREGATE_TRADES) {
                tradingService.getProvider(DEFAULT_PROVIDER).subscribeToAggregateTrades(this.DEFAULT_SYMBOL);
                System.out.println("  📊 Subscribed to " + this.DEFAULT_SYMBOL + " aggregate trades");
            }

            // Subscribe to order book depth (shows market depth)
            if (SUBSCRIBE_TO_ORDER_BOOK) {
                tradingService.getProvider(DEFAULT_PROVIDER).subscribeToOrderBook(this.DEFAULT_SYMBOL, ORDER_BOOK_DEPTH);
                System.out.println("  📚 Subscribed to " + this.DEFAULT_SYMBOL + " order book (depth: " + ORDER_BOOK_DEPTH + ")");
            }

            // Subscribe to book ticker (best bid/ask - lightweight)
            if (SUBSCRIBE_TO_BOOK_TICKER) {
                tradingService.getProvider(DEFAULT_PROVIDER).subscribeToBookTicker(this.DEFAULT_SYMBOL);
                System.out.println("  📖 Subscribed to " + this.DEFAULT_SYMBOL + " book ticker");
            }

        } catch (Exception e) {
            System.err.println("  ❌ Failed to subscribe to order flow for " + this.DEFAULT_SYMBOL + ": " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ Order flow subscriptions completed!");
        System.out.println("📊 Active order flow feeds:");
        if (SUBSCRIBE_TO_TRADES) System.out.println("   • Individual Trades");
        if (SUBSCRIBE_TO_AGGREGATE_TRADES) System.out.println("   • Aggregate Trades");
        if (SUBSCRIBE_TO_ORDER_BOOK) System.out.println("   • Order Book (depth: " + ORDER_BOOK_DEPTH + ")");
        if (SUBSCRIBE_TO_BOOK_TICKER) System.out.println("   • Book Ticker (Best Bid/Ask)");
    }
}
