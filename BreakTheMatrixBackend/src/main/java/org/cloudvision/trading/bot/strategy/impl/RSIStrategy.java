package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.indicators.TechnicalIndicators;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.AbstractTradingStrategy;
import org.cloudvision.trading.bot.strategy.StrategyConfig;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * RSI (Relative Strength Index) Strategy
 * Simple example showing how easy it is to create new strategies
 * 
 * Trading Logic:
 * - RSI < 30 (Oversold) → BUY signal
 * - RSI > 70 (Overbought) → SELL signal
 */
@Component
public class RSIStrategy extends AbstractTradingStrategy {
    
    // Strategy parameters
    private int rsiPeriod = 14;
    private int oversoldThreshold = 30;
    private int overboughtThreshold = 70;

    @Override
    protected List<Order> analyzePrice(PriceData priceData) {
        String symbol = priceData.symbol;
        BigDecimal currentPrice = priceData.price;
        
        // Need enough data for RSI calculation
        if (!hasEnoughData(symbol, rsiPeriod + 1)) {
            return Collections.emptyList();
        }
        
        // Calculate RSI using TechnicalIndicators utility
        List<BigDecimal> prices = getPriceHistory(symbol);
        BigDecimal rsi = TechnicalIndicators.calculateRSI(prices, rsiPeriod);
        
        if (rsi == null) {
            return Collections.emptyList();
        }
        
        List<Order> orders = new ArrayList<>();
        BigDecimal previousSignal = lastSignal.get(symbol);
        
        // Oversold - BUY signal
        if (rsi.compareTo(new BigDecimal(oversoldThreshold)) < 0 && 
            (previousSignal == null || previousSignal.compareTo(BigDecimal.ZERO) <= 0)) {
            
            Order buyOrder = createBuyOrder(symbol, currentPrice);
            orders.add(buyOrder);
            lastSignal.put(symbol, BigDecimal.ONE); // Bullish
            System.out.println("🟢 RSI Strategy: BUY signal for " + symbol + 
                             " at " + currentPrice + " (RSI: " + rsi + ")");
        }
        // Overbought - SELL signal
        else if (rsi.compareTo(new BigDecimal(overboughtThreshold)) > 0 && 
                 (previousSignal != null && previousSignal.compareTo(BigDecimal.ZERO) > 0)) {
            
            Order sellOrder = createSellOrder(symbol, currentPrice);
            orders.add(sellOrder);
            lastSignal.put(symbol, BigDecimal.ONE.negate()); // Bearish
            System.out.println("🔴 RSI Strategy: SELL signal for " + symbol + 
                             " at " + currentPrice + " (RSI: " + rsi + ")");
        }
        
        return orders;
    }

    @Override
    public String getStrategyId() {
        return "rsi-strategy";
    }

    @Override
    public String getStrategyName() {
        return "RSI Strategy";
    }

    @Override
    public void initialize(StrategyConfig config) {
        super.initialize(config);
        
        // Get strategy-specific parameters
        if (config.getParameter("rsiPeriod") != null) {
            this.rsiPeriod = (Integer) config.getParameter("rsiPeriod");
        }
        if (config.getParameter("oversoldThreshold") != null) {
            this.oversoldThreshold = (Integer) config.getParameter("oversoldThreshold");
        }
        if (config.getParameter("overboughtThreshold") != null) {
            this.overboughtThreshold = (Integer) config.getParameter("overboughtThreshold");
        }
        
        System.out.println("Initialized RSI Strategy: Period=" + rsiPeriod + 
                         ", Oversold=" + oversoldThreshold + 
                         ", Overbought=" + overboughtThreshold);
    }
    
    @Override
    protected int getMaxHistorySize() {
        return rsiPeriod + 50;
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        System.out.println("✅ RSI Strategy bootstrapped for " + dataBySymbol.size() + " symbols");
    }
}

