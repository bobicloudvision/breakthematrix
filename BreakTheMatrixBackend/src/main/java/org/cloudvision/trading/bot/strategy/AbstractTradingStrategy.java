package org.cloudvision.trading.bot.strategy;

import org.cloudvision.trading.bot.account.AccountManager;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderType;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.model.TradingDataType;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Abstract base class for trading strategies
 * Provides common functionality like price extraction, history management, and order creation
 */
public abstract class AbstractTradingStrategy implements TradingStrategy {
    
    protected StrategyConfig config;
    protected StrategyStats stats;
    protected boolean enabled = true;
    protected boolean bootstrapped = false;
    
    @Autowired(required = false)
    protected AccountManager accountManager;
    
    // Price history for each symbol
    protected final Map<String, List<BigDecimal>> priceHistory = new HashMap<>();
    
    // Signal tracking (1 = bullish, -1 = bearish, 0 = neutral)
    protected final Map<String, BigDecimal> lastSignal = new HashMap<>();
    
    // Timestamp of last data point for each symbol
    protected final Map<String, Instant> lastUpdateTime = new HashMap<>();

    @Override
    public final List<Order> analyze(TradingData data) {
        if (!enabled) {
            return Collections.emptyList();
        }

        // Extract price and validate data
        PriceData priceData = extractPriceData(data);
        if (priceData == null || priceData.price == null) {
            return Collections.emptyList();
        }

        // Update price history
        updatePriceHistory(priceData.symbol, priceData.price);
        lastUpdateTime.put(priceData.symbol, priceData.timestamp);

        // Call strategy-specific analysis
        return analyzePrice(priceData);
    }

    /**
     * Strategy-specific analysis logic
     * Subclasses implement this to define their trading logic
     */
    protected abstract List<Order> analyzePrice(PriceData priceData);

    /**
     * Extract price data from TradingData
     * Handles both TICKER and KLINE data types
     */
    protected PriceData extractPriceData(TradingData data) {
        String symbol = data.getSymbol();
        BigDecimal price = null;
        Instant timestamp = data.getTimestamp();
        boolean isClosed = true;

        switch (data.getType()) {
            case TICKER:
                price = data.getPrice();
                break;
            case KLINE:
                if (data.getCandlestickData() != null) {
                    CandlestickData candle = data.getCandlestickData();
                    price = candle.getClose();
                    timestamp = candle.getCloseTime();
                    isClosed = candle.isClosed();
                    
                    // Only process closed candles for strategy decisions
                    if (!isClosed) {
                        return null;
                    }
                }
                break;
            default:
                return null;
        }

        if (price == null) {
            return null;
        }

        return new PriceData(symbol, price, timestamp, data.getType(), data);
    }

    /**
     * Update price history for a symbol
     */
    protected void updatePriceHistory(String symbol, BigDecimal price) {
        List<BigDecimal> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
        prices.add(price);
        
        // Keep history manageable (keep last maxHistorySize prices)
        int maxHistorySize = getMaxHistorySize();
        if (prices.size() > maxHistorySize + 10) {
            prices.subList(0, prices.size() - maxHistorySize - 5).clear();
        }
    }

    /**
     * Get price history for a symbol
     */
    protected List<BigDecimal> getPriceHistory(String symbol) {
        return priceHistory.getOrDefault(symbol, Collections.emptyList());
    }

    /**
     * Check if enough data is available for analysis
     */
    protected boolean hasEnoughData(String symbol, int requiredPeriod) {
        List<BigDecimal> prices = getPriceHistory(symbol);
        return prices.size() >= requiredPeriod;
    }

    /**
     * Create a buy order
     */
    protected Order createBuyOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = calculateOrderQuantity(symbol, price);
        return new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            OrderSide.BUY,
            quantity,
            price,
            getStrategyId()
        );
    }

    /**
     * Create a sell order - sells the entire position
     */
    protected Order createSellOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = calculateSellQuantity(symbol);
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("⚠️ No position to sell for " + symbol);
        }
        
        return new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            OrderSide.SELL,
            quantity,
            price,
            getStrategyId()
        );
    }

    /**
     * Calculate order quantity for BUY orders based on position size and price
     */
    protected BigDecimal calculateOrderQuantity(String symbol, BigDecimal price) {
        if (config == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return config.getMaxPositionSize().divide(price, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * FUTURES: Calculate sell quantity - returns the total quantity of open positions
     */
    protected BigDecimal calculateSellQuantity(String symbol) {
        if (accountManager == null) {
            System.out.println("⚠️ AccountManager not available, using default quantity calculation");
            return BigDecimal.ZERO;
        }
        
        try {
            TradingAccount activeAccount = accountManager.getActiveAccount();
            if (activeAccount == null) {
                System.out.println("⚠️ No active account");
                return BigDecimal.ZERO;
            }
            
            // FUTURES: Get total quantity from open positions
            List<org.cloudvision.trading.bot.account.Position> openPositions = 
                activeAccount.getOpenPositionsBySymbol(symbol);
            
            if (openPositions.isEmpty()) {
                System.out.println("⚠️ No open positions for " + symbol);
                return BigDecimal.ZERO;
            }
            
            // Sum up all position quantities for this symbol
            BigDecimal totalQuantity = openPositions.stream()
                .map(org.cloudvision.trading.bot.account.Position::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            System.out.println("📊 Total open position for " + symbol + ": " + totalQuantity);
            return totalQuantity;
            
        } catch (Exception e) {
            System.err.println("❌ Error calculating sell quantity: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Extract base asset from trading pair symbol
     * Examples: BTCUSDT -> BTC, ETHBTC -> ETH
     */
    protected String extractBaseAsset(String symbol) {
        // Common quote assets
        String[] quoteAssets = {"USDT", "BUSD", "USDC", "BTC", "ETH", "BNB"};
        
        for (String quote : quoteAssets) {
            if (symbol.endsWith(quote)) {
                return symbol.substring(0, symbol.length() - quote.length());
            }
        }
        
        // Default: assume last 4 characters are quote asset
        if (symbol.length() > 4) {
            return symbol.substring(0, symbol.length() - 4);
        }
        
        return symbol;
    }

    @Override
    public void bootstrapWithHistoricalData(List<CandlestickData> historicalData) {
        if (historicalData == null || historicalData.isEmpty()) {
            System.out.println("⚠️ No historical data provided for bootstrapping " + getStrategyName());
            return;
        }
        
        System.out.println("🔄 Bootstrapping " + getStrategyName() + " with " + 
                         historicalData.size() + " historical candles...");
        
        // Group data by symbol
        Map<String, List<CandlestickData>> dataBySymbol = new HashMap<>();
        for (CandlestickData candle : historicalData) {
            dataBySymbol.computeIfAbsent(candle.getSymbol(), k -> new ArrayList<>()).add(candle);
        }
        
        // Process each symbol's historical data
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<CandlestickData> candles = entry.getValue();
            
            // Sort by timestamp to ensure chronological order
            candles.sort(Comparator.comparing(CandlestickData::getCloseTime));
            
            // Initialize price history for this symbol
            List<BigDecimal> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
            
            // Add close prices from historical data
            for (CandlestickData candle : candles) {
                prices.add(candle.getClose());
            }
            
            System.out.println("✅ " + symbol + " loaded with " + prices.size() + " historical prices");
        }
        
        // Allow strategy-specific bootstrap logic
        onBootstrapComplete(dataBySymbol);
        
        bootstrapped = true;
        System.out.println("✅ " + getStrategyName() + " bootstrapping complete!");
    }

    /**
     * Called after bootstrap is complete
     * Subclasses can override to perform additional initialization
     */
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        // Default implementation does nothing
        // Subclasses can override to calculate initial indicators
    }

    /**
     * Get the maximum size of price history to maintain
     * Subclasses can override to specify their requirements
     */
    protected int getMaxHistorySize() {
        return 200; // Default: keep last 200 prices
    }

    @Override
    public boolean isBootstrapped() {
        return bootstrapped;
    }

    @Override
    public List<String> getSymbols() {
        return config != null ? config.getSymbols() : List.of();
    }

    @Override
    public void initialize(StrategyConfig config) {
        this.config = config;
        this.stats = new StrategyStats();
        System.out.println("Initialized " + getStrategyName());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public StrategyStats getStats() {
        return stats;
    }

    /**
     * Inner class to hold extracted price data
     */
    protected static class PriceData {
        public final String symbol;
        public final BigDecimal price;
        public final Instant timestamp;
        public final TradingDataType dataType;
        public final TradingData rawData;

        public PriceData(String symbol, BigDecimal price, Instant timestamp, 
                        TradingDataType dataType, TradingData rawData) {
            this.symbol = symbol;
            this.price = price;
            this.timestamp = timestamp;
            this.dataType = dataType;
            this.rawData = rawData;
        }
    }

    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        // Default implementation - subclasses should override to define their indicators
        return new HashMap<>();
    }

    @Override
    public void generateHistoricalVisualizationData(List<CandlestickData> historicalData) {
        // Default implementation does nothing
        // Subclasses should override to generate visualization data from historical candles
        System.out.println("⚠️ " + getStrategyName() + " does not implement historical visualization generation");
    }
}

