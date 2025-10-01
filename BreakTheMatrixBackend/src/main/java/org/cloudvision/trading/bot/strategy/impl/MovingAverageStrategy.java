package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.model.*;
import org.cloudvision.trading.bot.strategy.*;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.cloudvision.trading.model.TradingData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class MovingAverageStrategy implements TradingStrategy {
    
    @Autowired
    private VisualizationManager visualizationManager;
    
    private StrategyConfig config;
    private StrategyStats stats;
    private boolean enabled = true;
    private final Map<String, List<BigDecimal>> priceHistory = new HashMap<>();
    private final Map<String, BigDecimal> lastSignal = new HashMap<>();
    
    // Strategy parameters
    private int shortPeriod = 10;
    private int longPeriod = 20;

    @Override
    public List<Order> analyze(TradingData data) {
        if (!enabled || !data.getType().toString().equals("TICKER")) {
            return Collections.emptyList();
        }

        String symbol = data.getSymbol();
        BigDecimal currentPrice = data.getPrice();
        
        // Store price history
        priceHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(currentPrice);
        List<BigDecimal> prices = priceHistory.get(symbol);
        
        // Keep only necessary history
        if (prices.size() > longPeriod + 10) {
            prices.subList(0, prices.size() - longPeriod - 5).clear();
        }
        
        // Need enough data for both moving averages
        if (prices.size() < longPeriod) {
            return Collections.emptyList();
        }
        
        // Calculate moving averages
        BigDecimal shortMA = calculateMA(prices, shortPeriod);
        BigDecimal longMA = calculateMA(prices, longPeriod);
        
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
        generateVisualizationData(symbol, currentPrice, shortMA, longMA, action, data.getTimestamp());
        
        return orders;
    }
    
    private BigDecimal calculateMA(List<BigDecimal> prices, int period) {
        if (prices.size() < period) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        
        return sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
    }
    
    private Order createBuyOrder(String symbol, BigDecimal price) {
        BigDecimal quantity = config.getMaxPositionSize().divide(price, 8, RoundingMode.HALF_UP);
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
    
    private Order createSellOrder(String symbol, BigDecimal price) {
        // For simplicity, assume we're selling the same amount we bought
        BigDecimal quantity = config.getMaxPositionSize().divide(price, 8, RoundingMode.HALF_UP);
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
        this.config = config;
        this.stats = new StrategyStats();
        
        // Get strategy-specific parameters
        if (config.getParameter("shortPeriod") != null) {
            this.shortPeriod = (Integer) config.getParameter("shortPeriod");
        }
        if (config.getParameter("longPeriod") != null) {
            this.longPeriod = (Integer) config.getParameter("longPeriod");
        }
        
        System.out.println("Initialized MA Strategy: " + shortPeriod + "/" + longPeriod + " periods");
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
}
