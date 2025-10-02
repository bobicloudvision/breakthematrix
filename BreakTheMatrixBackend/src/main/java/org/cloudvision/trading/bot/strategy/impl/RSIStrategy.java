package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.indicators.TechnicalIndicators;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.strategy.AbstractTradingStrategy;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
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
            
            // Check existing positions
            BigDecimal longPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
            BigDecimal shortPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
            
            // If we have LONG position → Skip (wait for TP/SL)
            if (longPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ RSI Strategy: Already have LONG position for " + symbol + 
                    " (Quantity: " + longPositionQuantity + ") - skipping until TP/SL hit");
                lastSignal.put(symbol, BigDecimal.ONE); // Keep bullish signal
            }
            // If we have SHORT position → Close it ONLY (don't open LONG yet)
            else if (shortPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                Order closeShortOrder = createCloseShortOrder(symbol, currentPrice);
                orders.add(closeShortOrder);
                lastSignal.put(symbol, BigDecimal.ONE); // Update to bullish signal
                System.out.println("🔄 RSI Strategy: Closing SHORT position for " + symbol + 
                    " at " + currentPrice + " (RSI: " + rsi + ") | Quantity: " + shortPositionQuantity);
            }
            // No position → Open LONG
            else {
                Order buyOrder = createBuyOrder(symbol, currentPrice);
                
                // SET STOP LOSS: For RSI oversold, use 3% stop loss
                // RSI strategies typically need tighter stops since they trade reversals
                BigDecimal stopLoss = currentPrice.multiply(new BigDecimal("0.97")); // 3% stop
                buyOrder.setSuggestedStopLoss(stopLoss);
                
                // SET TAKE PROFIT: RSI target is the overbought level
                // Estimate ~5% move from oversold to neutral (risk:reward ~1.7:1)
                BigDecimal takeProfit = currentPrice.multiply(new BigDecimal("1.05")); // 5% target
                buyOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(buyOrder);
                lastSignal.put(symbol, BigDecimal.ONE); // Bullish
                
                System.out.println(String.format(
                    "🟢 RSI Strategy: Opening LONG position for %s at %s (RSI: %.2f) | Stop Loss: %s (-3%%) | Take Profit: %s (+5%%)",
                    symbol, currentPrice, rsi, stopLoss, takeProfit
                ));
            }
        }
        // Overbought - SELL signal
        else if (rsi.compareTo(new BigDecimal(overboughtThreshold)) > 0 && 
                 (previousSignal != null && previousSignal.compareTo(BigDecimal.ZERO) > 0)) {
            
            // Check existing positions
            BigDecimal longPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
            BigDecimal shortPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
            
            // If we have SHORT position → Skip (wait for TP/SL)
            if (shortPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ RSI Strategy: Already have SHORT position for " + symbol + 
                    " (Quantity: " + shortPositionQuantity + ") - skipping until TP/SL hit");
                lastSignal.put(symbol, BigDecimal.ONE.negate()); // Keep bearish signal
            }
            // If we have LONG position → Close it ONLY (don't open SHORT yet)
            else if (longPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                Order closeLongOrder = createCloseLongOrder(symbol, currentPrice);
                orders.add(closeLongOrder);
                lastSignal.put(symbol, BigDecimal.ONE.negate()); // Update to bearish signal
                System.out.println("🔄 RSI Strategy: Closing LONG position for " + symbol + 
                    " at " + currentPrice + " (RSI: " + rsi + ") | Quantity: " + longPositionQuantity);
            }
            // No position → Open SHORT
            else {
                Order shortOrder = createShortOrder(symbol, currentPrice);
                
                // SET STOP LOSS: For RSI overbought, use 3% stop loss above
                BigDecimal stopLoss = currentPrice.multiply(new BigDecimal("1.03")); // 3% stop
                shortOrder.setSuggestedStopLoss(stopLoss);
                
                // SET TAKE PROFIT: RSI target is the oversold level
                // Estimate ~5% move from overbought to neutral
                BigDecimal takeProfit = currentPrice.multiply(new BigDecimal("0.95")); // 5% target
                shortOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(shortOrder);
                lastSignal.put(symbol, BigDecimal.ONE.negate()); // Bearish
                
                System.out.println(String.format(
                    "🔴 RSI Strategy: Opening SHORT position for %s at %s (RSI: %.2f) | Stop Loss: %s (+3%%) | Take Profit: %s (-5%%)",
                    symbol, currentPrice, rsi, stopLoss, takeProfit
                ));
            }
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
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // RSI - Area chart in separate pane
        metadata.put("RSI", IndicatorMetadata.builder("RSI")
            .displayName("RSI(" + rsiPeriod + ")")
            .asArea("rgba(76, 175, 80, 0.4)", "rgba(76, 175, 80, 0.0)", "rgba(76, 175, 80, 1)")
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        return metadata;
    }
    
    @Override
    public void generateHistoricalVisualizationData(List<CandlestickData> historicalData) {
        if (historicalData == null || historicalData.isEmpty()) {
            return;
        }
        
        System.out.println("📊 Generating historical visualization data for RSI Strategy with " + 
                         historicalData.size() + " candles");
        
        // Group by symbol
        Map<String, List<CandlestickData>> dataBySymbol = new HashMap<>();
        for (CandlestickData candle : historicalData) {
            dataBySymbol.computeIfAbsent(candle.getSymbol(), k -> new ArrayList<>()).add(candle);
        }
        
        // Process each symbol
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<CandlestickData> candles = entry.getValue();
            
            // Sort chronologically
            candles.sort(Comparator.comparing(CandlestickData::getCloseTime));
            
            // Build price history progressively and calculate RSI
            List<BigDecimal> progressivePrices = new ArrayList<>();
            BigDecimal previousSignal = null;
            
            for (CandlestickData candle : candles) {
                progressivePrices.add(candle.getClose());
                
                // Only calculate RSI once we have enough data
                if (progressivePrices.size() >= rsiPeriod + 1) {
                    BigDecimal rsi = TechnicalIndicators.calculateRSI(progressivePrices, rsiPeriod);
                    
                    if (rsi != null) {
                        // Determine action based on RSI levels
                        String action = "HOLD";
                        if (rsi.compareTo(new BigDecimal(oversoldThreshold)) < 0 && 
                            (previousSignal == null || previousSignal.compareTo(BigDecimal.ZERO) <= 0)) {
                            action = "BUY";
                            previousSignal = BigDecimal.ONE;
                        } else if (rsi.compareTo(new BigDecimal(overboughtThreshold)) > 0 && 
                                   (previousSignal != null && previousSignal.compareTo(BigDecimal.ZERO) > 0)) {
                            action = "SELL";
                            previousSignal = BigDecimal.ONE.negate();
                        } else if (previousSignal == null) {
                            previousSignal = BigDecimal.ZERO;
                        }
                        
                        // Create visualization data (you'll need to implement this method)
                        // For now, just showing the structure
                        // generateVisualizationData(symbol, candle.getClose(), rsi, action, candle.getCloseTime());
                    }
                }
            }
            
            System.out.println("✅ Generated " + (candles.size() - rsiPeriod) + 
                             " visualization points for " + symbol);
        }
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        System.out.println("✅ RSI Strategy bootstrapped for " + dataBySymbol.size() + " symbols");
    }
}

