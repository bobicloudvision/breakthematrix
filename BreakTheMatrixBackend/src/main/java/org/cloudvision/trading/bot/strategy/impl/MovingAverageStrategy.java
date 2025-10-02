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
    
    // Track previous MA values for crossover detection
    private final Map<String, BigDecimal> previousShortMA = new HashMap<>();
    private final Map<String, BigDecimal> previousLongMA = new HashMap<>();
    
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
        
        // Get previous MA values for crossover detection
        BigDecimal prevShortMA = previousShortMA.get(symbol);
        BigDecimal prevLongMA = previousLongMA.get(symbol);
        
        // Generate signals
        List<Order> orders = new ArrayList<>();
        String action = "HOLD";
        
        // Golden Cross: Short MA crosses above Long MA = BUY signal
        // Check for actual crossover using previous MA values
        boolean goldenCross = (prevShortMA != null && prevLongMA != null) && 
                             TechnicalIndicators.isCrossover(shortMA, longMA, prevShortMA, prevLongMA);
        
        if (goldenCross) {
            // Check existing positions
            BigDecimal longPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
            BigDecimal shortPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
            
            // If we have LONG position → Skip (wait for TP/SL)
            if (longPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ MA Strategy: Already have LONG position for " + symbol + 
                    " (Quantity: " + longPositionQuantity + ") - skipping until TP/SL hit");
                lastSignal.put(symbol, BigDecimal.ONE); // Keep bullish signal
            }
            // If we have SHORT position → Skip (wait for TP/SL to close)
            else if (shortPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ MA Strategy: BUY signal but have SHORT position for " + symbol + 
                    " (Quantity: " + shortPositionQuantity + ") - skipping, waiting for TP/SL to close");
                lastSignal.put(symbol, BigDecimal.ONE); // Update to bullish signal
            }
            // No position → Open LONG
            else {
                Order buyOrder = createBuyOrder(symbol, currentPrice);
                
                // SET STOP LOSS: Place stop loss below long MA (support level)
                // Use 98% of long MA to give it a buffer
                BigDecimal stopLoss = longMA.multiply(new BigDecimal("0.98"));
                buyOrder.setSuggestedStopLoss(stopLoss);
                
                // SET TAKE PROFIT: Risk:Reward ratio of 1:2
                BigDecimal riskDistance = currentPrice.subtract(stopLoss);
                BigDecimal takeProfit = currentPrice.add(riskDistance.multiply(new BigDecimal("2")));
                buyOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(buyOrder);
                lastSignal.put(symbol, BigDecimal.ONE); // Bullish signal
                action = "BUY";
                
                // Enhanced logging with stop loss info
                BigDecimal stopLossPercent = stopLoss.subtract(currentPrice)
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                System.out.println(String.format(
                    "🟢 MA Strategy: Opening LONG position for %s at %s | Stop Loss: %s (%.2f%%) | Take Profit: %s",
                    symbol, currentPrice, stopLoss, stopLossPercent, takeProfit
                ));
            }
        }
        // Death Cross: Short MA crosses below Long MA = SELL signal
        // Check for actual crossunder using previous MA values
        else if ((prevShortMA != null && prevLongMA != null) && 
                 TechnicalIndicators.isCrossunder(shortMA, longMA, prevShortMA, prevLongMA)) {
            // Check existing positions
            BigDecimal longPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
            BigDecimal shortPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
            
            // If we have SHORT position → Skip (wait for TP/SL)
            if (shortPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ MA Strategy: Already have SHORT position for " + symbol + 
                    " (Quantity: " + shortPositionQuantity + ") - skipping until TP/SL hit");
                lastSignal.put(symbol, BigDecimal.ONE.negate()); // Keep bearish signal
            }
            // If we have LONG position → Skip (wait for TP/SL to close)
            else if (longPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ MA Strategy: SELL signal but have LONG position for " + symbol + 
                    " (Quantity: " + longPositionQuantity + ") - skipping, waiting for TP/SL to close");
                lastSignal.put(symbol, BigDecimal.ONE.negate()); // Update to bearish signal
            }
            // No position → Open SHORT
            else {
                Order shortOrder = createShortOrder(symbol, currentPrice);
                
                // SET STOP LOSS: Place stop loss above long MA (resistance level)
                // Use 102% of long MA to give it a buffer
                BigDecimal stopLoss = longMA.multiply(new BigDecimal("1.02"));
                shortOrder.setSuggestedStopLoss(stopLoss);
                
                // SET TAKE PROFIT: Risk:Reward ratio of 1:2
                BigDecimal riskDistance = stopLoss.subtract(currentPrice);
                BigDecimal takeProfit = currentPrice.subtract(riskDistance.multiply(new BigDecimal("2")));
                shortOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(shortOrder);
                lastSignal.put(symbol, BigDecimal.ONE.negate()); // Bearish signal
                action = "SELL";
                
                // Enhanced logging with stop loss info
                BigDecimal stopLossPercent = stopLoss.subtract(currentPrice)
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                System.out.println(String.format(
                    "🔴 MA Strategy: Opening SHORT position for %s at %s | Stop Loss: %s (%.2f%%) | Take Profit: %s",
                    symbol, currentPrice, stopLoss, stopLossPercent, takeProfit
                ));
            }
        }
        
        // Update previous MA values for next crossover detection
        previousShortMA.put(symbol, shortMA);
        previousLongMA.put(symbol, longMA);
        
        // Extract volume and open price using base class helper method
        Map<String, BigDecimal> volumeData = extractVolumeAndOpen(priceData);
        
        // Generate visualization data
        generateVisualizationData(symbol, currentPrice, shortMA, longMA, action, 
                                priceData.timestamp, volumeData.get("volume"), volumeData.get("openPrice"));
        
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
    public void reset() {
        // Call parent reset to clear base state
        super.reset();
        
        // Clear MA-specific state
        previousShortMA.clear();
        previousLongMA.clear();
        
        System.out.println("🔄 MA Strategy: Cleared previous MA values");
    }
    
    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Short Moving Average - Red line
        metadata.put("shortMA", IndicatorMetadata.builder("shortMA")
            .displayName("SMA " + shortPeriod)
            .asLine("#FF6B6B", 2)
            .separatePane(false)
            .paneOrder(0) // Main chart
            .build());
        
        // Long Moving Average - Blue line
        metadata.put("longMA", IndicatorMetadata.builder("longMA")
            .displayName("SMA " + longPeriod)
            .asLine("#4ECDC4", 2)
            .separatePane(false)
            .paneOrder(0) // Main chart
            .build());
        
        // Volume - Use reusable base class method
        metadata.put("volume", getVolumeIndicatorMetadata(1));
        
        // Spread - Histogram in separate pane
//        metadata.put("spread", IndicatorMetadata.builder("spread")
//            .displayName("MA Spread")
//            .asHistogram("#26a69a")
//            .separatePane(true)
//            .paneOrder(2)
//            .build());
//
//        // Spread Percentage - Not displayed by default (but available)
//        metadata.put("spreadPercent", IndicatorMetadata.builder("spreadPercent")
//            .displayName("Spread %")
//            .asLine("#95A5A6", 1)
//            .separatePane(true)
//            .paneOrder(2)
//            .build());
        
        return metadata;
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        // Calculate initial moving averages and store them for crossover detection
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<BigDecimal> prices = getPriceHistory(symbol);
            
            if (prices.size() >= longPeriod) {
                BigDecimal shortMA = TechnicalIndicators.calculateSMA(prices, shortPeriod);
                BigDecimal longMA = TechnicalIndicators.calculateSMA(prices, longPeriod);
                
                // Store initial MA values for crossover detection
                previousShortMA.put(symbol, shortMA);
                previousLongMA.put(symbol, longMA);
                
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
    
    @Override
    public void generateHistoricalVisualizationData(List<CandlestickData> historicalData) {
        if (visualizationManager == null || historicalData == null || historicalData.isEmpty()) {
            return;
        }
        
        System.out.println("📊 Generating historical visualization data for " + getStrategyName() + 
                         " with " + historicalData.size() + " candles");
        
        // Group by symbol
        Map<String, List<CandlestickData>> dataBySymbol = new HashMap<>();
        for (CandlestickData candle : historicalData) {
            dataBySymbol.computeIfAbsent(candle.getSymbol(), k -> new ArrayList<>()).add(candle);
        }
        
        // Process each symbol
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<CandlestickData> candles = entry.getValue();
            
            // Sort chronologically by openTime to ensure proper order
            candles.sort(Comparator.comparing(CandlestickData::getOpenTime));
            
            // Track provider and interval for this symbol
            if (!candles.isEmpty()) {
                symbolProviders.put(symbol, candles.get(0).getProvider());
                symbolIntervals.put(symbol, candles.get(0).getInterval());
            }
            
            // Build price history progressively - simulating how candles arrive in real-time
            int generatedCount = 0;
            BigDecimal prevShortMA = null;
            BigDecimal prevLongMA = null;
            
            // For each candle, calculate indicators based ONLY on candles up to that point
            for (int i = 0; i < candles.size(); i++) {
                CandlestickData currentCandle = candles.get(i);
                
                // Get all close prices from start up to current candle (inclusive)
                List<BigDecimal> progressivePrices = new ArrayList<>();
                for (int j = 0; j <= i; j++) {
                    progressivePrices.add(candles.get(j).getClose());
                }
                
                // Only calculate indicators once we have enough data
                if (progressivePrices.size() >= longPeriod) {
                    // Calculate indicators using the same method as real-time
                    BigDecimal shortMA = TechnicalIndicators.calculateSMA(progressivePrices, shortPeriod);
                    BigDecimal longMA = TechnicalIndicators.calculateSMA(progressivePrices, longPeriod);
                    
                    // Determine action based on ACTUAL crossover detection
                    String action = "HOLD";
                    if (prevShortMA != null && prevLongMA != null) {
                        // Check for golden cross (short MA crosses above long MA)
                        if (TechnicalIndicators.isCrossover(shortMA, longMA, prevShortMA, prevLongMA)) {
                            action = "BUY";
                        }
                        // Check for death cross (short MA crosses below long MA)
                        else if (TechnicalIndicators.isCrossunder(shortMA, longMA, prevShortMA, prevLongMA)) {
                            action = "SELL";
                        }
                    }
                    
                    // Use closeTime to match real-time behavior - indicator is calculated at candle close
                    // This ensures historical and real-time visualization data are aligned
                    generateVisualizationData(symbol, currentCandle.getClose(), shortMA, longMA, 
                                            action, currentCandle.getCloseTime(), 
                                            currentCandle.getVolume(), currentCandle.getOpen());
                    generatedCount++;
                    
                    // Store current MAs as previous for next iteration
                    prevShortMA = shortMA;
                    prevLongMA = longMA;
                }
            }
            
            System.out.println("✅ Generated " + generatedCount + " visualization points for " + symbol);
        }
    }
    
    /**
     * Generate visualization data for the strategy
     */
    private void generateVisualizationData(String symbol, BigDecimal price, 
                                         BigDecimal shortMA, BigDecimal longMA, 
                                         String action, java.time.Instant timestamp, 
                                         BigDecimal volume, BigDecimal openPrice) {
        if (visualizationManager == null) return;
        
        // Prepare indicators
        Map<String, BigDecimal> indicators = new HashMap<>();
        indicators.put("shortMA", shortMA);
        indicators.put("longMA", longMA);
//        indicators.put("spread", shortMA.subtract(longMA));
//        indicators.put("spreadPercent", longMA.compareTo(BigDecimal.ZERO) > 0 ?
//            shortMA.subtract(longMA).divide(longMA, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) :
//            BigDecimal.ZERO);
        
        // Prepare signals
        Map<String, Object> signals = new HashMap<>();
        signals.put("goldenCross", shortMA.compareTo(longMA) > 0);
        signals.put("deathCross", shortMA.compareTo(longMA) < 0);
        signals.put("trend", shortMA.compareTo(longMA) > 0 ? "BULLISH" : "BEARISH");
        signals.put("strength", shortMA.subtract(longMA).abs());
        
        // Add volume indicator using reusable base class method
        addVolumeIndicator(indicators, signals, volume, price, openPrice);
        
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

