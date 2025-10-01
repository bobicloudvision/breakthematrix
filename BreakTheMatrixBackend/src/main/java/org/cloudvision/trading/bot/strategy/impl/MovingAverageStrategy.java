package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.indicators.TechnicalIndicators;
import org.cloudvision.trading.bot.model.*;
import org.cloudvision.trading.bot.strategy.*;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class MovingAverageStrategy extends AbstractTradingStrategy {
    
    @Autowired
    private VisualizationManager visualizationManager;
    
    // Strategy parameters
    private int shortPeriod = 10;
    private int longPeriod = 20;
    
    @Override
    protected List<Order> analyzePrice(PriceData priceData) {
        String symbol = priceData.symbol;
        BigDecimal currentPrice = priceData.price;
        
        // Need enough data for both moving averages
        if (!hasEnoughData(symbol, longPeriod)) {
            return Collections.emptyList();
        }
        
        List<BigDecimal> prices = getPriceHistory(symbol);
        
        // Calculate moving averages using TechnicalIndicators utility
        BigDecimal shortMA = TechnicalIndicators.calculateSMA(prices, shortPeriod);
        BigDecimal longMA = TechnicalIndicators.calculateSMA(prices, longPeriod);
        
        // Generate signals
        List<Order> orders = new ArrayList<>();
        BigDecimal previousSignal = lastSignal.get(symbol);
        String action = "HOLD";
        
        // Golden Cross: Short MA crosses above Long MA = BUY signal
        if (shortMA.compareTo(longMA) > 0 && (previousSignal == null || previousSignal.compareTo(BigDecimal.ZERO) <= 0)) {
            Order buyOrder = createBuyOrder(symbol, currentPrice);
            orders.add(buyOrder);
            lastSignal.put(symbol, BigDecimal.ONE); // Bullish signal
            action = "BUY";
            System.out.println("🟢 MA Strategy: BUY signal for " + symbol + " at " + currentPrice);
        }
        // Death Cross: Short MA crosses below Long MA = SELL signal
        else if (shortMA.compareTo(longMA) < 0 && (previousSignal != null && previousSignal.compareTo(BigDecimal.ZERO) > 0)) {
            Order sellOrder = createSellOrder(symbol, currentPrice);
            orders.add(sellOrder);
            lastSignal.put(symbol, BigDecimal.ONE.negate()); // Bearish signal
            action = "SELL";
            System.out.println("🔴 MA Strategy: SELL signal for " + symbol + " at " + currentPrice);
        }
        
        // Generate visualization data
        generateVisualizationData(symbol, currentPrice, shortMA, longMA, action, priceData.timestamp);
        
        return orders;
    }

    @Override
    public String getStrategyId() {
        return "moving-average-crossover";
    }

    @Override
    public String getStrategyName() {
        return "Moving Average Crossover Strategy";
    }

    @Override
    public List<String> getSymbols() {
        return config != null ? config.getSymbols() : List.of("BTCUSDT");
    }

    @Override
    public void initialize(StrategyConfig config) {
        super.initialize(config);
        
        // Get strategy-specific parameters
        if (config.getParameter("shortPeriod") != null) {
            this.shortPeriod = (Integer) config.getParameter("shortPeriod");
        }
        if (config.getParameter("longPeriod") != null) {
            this.longPeriod = (Integer) config.getParameter("longPeriod");
        }
        
        // Register with visualization manager
        if (visualizationManager != null) {
            visualizationManager.registerStrategy(getStrategyId(), getSymbols());
        }
        
        System.out.println("Initialized MA Strategy: " + shortPeriod + "/" + longPeriod + " periods");
    }
    
    @Override
    protected int getMaxHistorySize() {
        return longPeriod + 50; // Keep enough history for calculations
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        // Calculate initial moving averages and set signal state
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<BigDecimal> prices = getPriceHistory(symbol);
            
            if (prices.size() >= longPeriod) {
                BigDecimal shortMA = TechnicalIndicators.calculateSMA(prices, shortPeriod);
                BigDecimal longMA = TechnicalIndicators.calculateSMA(prices, longPeriod);
                
                // Set initial signal state (no orders, just establish baseline)
                if (shortMA.compareTo(longMA) > 0) {
                    lastSignal.put(symbol, BigDecimal.ONE); // Bullish
                } else {
                    lastSignal.put(symbol, BigDecimal.ONE.negate()); // Bearish
                }
                
                System.out.println("📊 " + symbol + " initial state: Short MA: " + shortMA + 
                                 ", Long MA: " + longMA + " (" + 
                                 (shortMA.compareTo(longMA) > 0 ? "BULLISH" : "BEARISH") + ")");
            }
        }
    }
    
    /**
     * Generate visualization data for the strategy
     */
    private void generateVisualizationData(String symbol, BigDecimal price, 
                                         BigDecimal shortMA, BigDecimal longMA, 
                                         String action, java.time.Instant timestamp) {
        if (visualizationManager == null) return;
        
        // Prepare indicators
        Map<String, BigDecimal> indicators = new HashMap<>();
        indicators.put("shortMA", shortMA);
        indicators.put("longMA", longMA);
        indicators.put("spread", shortMA.subtract(longMA));
        indicators.put("spreadPercent", longMA.compareTo(BigDecimal.ZERO) > 0 ? 
            shortMA.subtract(longMA).divide(longMA, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : 
            BigDecimal.ZERO);
        
        // Prepare signals
        Map<String, Object> signals = new HashMap<>();
        signals.put("goldenCross", shortMA.compareTo(longMA) > 0);
        signals.put("deathCross", shortMA.compareTo(longMA) < 0);
        signals.put("trend", shortMA.compareTo(longMA) > 0 ? "BULLISH" : "BEARISH");
        signals.put("strength", shortMA.subtract(longMA).abs());
        
        // Prepare performance data
        Map<String, BigDecimal> performance = new HashMap<>();
        if (stats != null) {
            performance.put("totalTrades", new BigDecimal(stats.getTotalTrades()));
            performance.put("winRate", stats.getWinRate() != null ? stats.getWinRate() : BigDecimal.ZERO);
            performance.put("netProfit", stats.getNetProfit());
            performance.put("profitFactor", stats.getProfitFactor() != null ? stats.getProfitFactor() : BigDecimal.ZERO);
        }
        
        // Create visualization data
        StrategyVisualizationData vizData = new StrategyVisualizationData(
            getStrategyId(),
            symbol,
            timestamp,
            price,
            indicators,
            signals,
            performance,
            action
        );
        
        // Send to visualization manager
        visualizationManager.addVisualizationData(vizData);
    }
    
    /**
     * Simulate ticker data for testing (public method for TestController)
     */
    public void simulateTickerData(String symbol) {
        if (enabled) {
            BigDecimal simulatedPrice = new BigDecimal("50000").add(
                new BigDecimal(Math.random() * 2000 - 1000).setScale(2, RoundingMode.HALF_UP)
            );
            
            org.cloudvision.trading.model.TradingData simulatedData = 
                new org.cloudvision.trading.model.TradingData(
                    symbol,
                    simulatedPrice,
                    new BigDecimal("1.5"),
                    java.time.Instant.now(),
                    "Simulated",
                    org.cloudvision.trading.model.TradingDataType.TICKER
                );
            
            analyze(simulatedData);
        }
    }
    
    /**
     * Simulate kline data for testing (public method for TestController)
     */
    public void simulateKlineData(String symbol, org.cloudvision.trading.model.TimeInterval interval) {
        if (enabled) {
            java.time.Instant now = java.time.Instant.now();
            BigDecimal basePrice = new BigDecimal("50000");
            BigDecimal open = basePrice.add(new BigDecimal(Math.random() * 100 - 50));
            BigDecimal close = open.add(new BigDecimal(Math.random() * 200 - 100));
            BigDecimal high = open.max(close).add(new BigDecimal(Math.random() * 50));
            BigDecimal low = open.min(close).subtract(new BigDecimal(Math.random() * 50));
            
            org.cloudvision.trading.model.CandlestickData candlestick = 
                new org.cloudvision.trading.model.CandlestickData(
                    symbol,
                    now.minusSeconds(60),
                    now,
                    open,
                    high,
                    low,
                    close,
                    new BigDecimal("125.5"),
                    new BigDecimal("6275000.00"),
                    1250,
                    interval.getValue(),
                    "Simulated",
                    true
                );
            
            org.cloudvision.trading.model.TradingData simulatedData = 
                new org.cloudvision.trading.model.TradingData(
                    symbol,
                    now,
                    "Simulated",
                    org.cloudvision.trading.model.TradingDataType.KLINE,
                    candlestick
                );
            
            analyze(simulatedData);
        }
    }
}

