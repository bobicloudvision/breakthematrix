package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.indicators.TechnicalIndicators;
import org.cloudvision.trading.bot.model.*;
import org.cloudvision.trading.bot.strategy.*;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SuperTrendStrategy extends AbstractTradingStrategy {
    
    @Autowired
    private VisualizationManager visualizationManager;
    
    // Strategy parameters
    private int atrPeriod = 10;
    private BigDecimal atrMultiplier = new BigDecimal("3");
    
    // Store candle data (high, low, close) for each symbol
    private final Map<String, List<BigDecimal>> highHistory = new HashMap<>();
    private final Map<String, List<BigDecimal>> lowHistory = new HashMap<>();
    
    // Store previous SuperTrend values for calculation
    private final Map<String, BigDecimal> previousSuperTrend = new HashMap<>();
    private final Map<String, BigDecimal> previousDirection = new HashMap<>();
    
    @Override
    protected List<Order> analyzePrice(PriceData priceData) {
        String symbol = priceData.symbol;
        BigDecimal currentPrice = priceData.price;
        
        // SuperTrend requires candlestick data (high, low, close)
        if (priceData.rawData.getCandlestickData() == null) {
            return Collections.emptyList();
        }
        
        CandlestickData candle = priceData.rawData.getCandlestickData();
        
        // Update high/low history
        updateCandleHistory(symbol, candle.getHigh(), candle.getLow());
        
        // Need enough data for SuperTrend calculation
        if (!hasEnoughData(symbol, atrPeriod + 1)) {
            return Collections.emptyList();
        }
        
        List<BigDecimal> highs = highHistory.get(symbol);
        List<BigDecimal> lows = lowHistory.get(symbol);
        List<BigDecimal> closes = getPriceHistory(symbol);
        
        // Calculate SuperTrend
        BigDecimal[] superTrendResult = TechnicalIndicators.calculateSuperTrend(
            highs, lows, closes, atrPeriod, atrMultiplier,
            previousSuperTrend.get(symbol),
            previousDirection.get(symbol)
        );
        
        if (superTrendResult == null) {
            return Collections.emptyList();
        }
        
        BigDecimal superTrend = superTrendResult[0];
        BigDecimal direction = superTrendResult[1];
        
        // Update stored values for next calculation
        previousSuperTrend.put(symbol, superTrend);
        BigDecimal oldDirection = previousDirection.get(symbol);
        previousDirection.put(symbol, direction);
        
        // Generate trading signals
        List<Order> orders = new ArrayList<>();
        String action = "HOLD";
        
        // BULLISH: Price crosses above SuperTrend (direction changed from +1 to -1 per Pine Script convention)
        // In Pine Script: direction < 0 means UPTREND (green)
        if (direction.compareTo(BigDecimal.ZERO) < 0 && 
            (oldDirection == null || oldDirection.compareTo(BigDecimal.ZERO) > 0)) {
            
            // Check existing positions
            BigDecimal longPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
            BigDecimal shortPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
            
            // If we have LONG position → Skip (wait for TP/SL)
            if (longPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ SuperTrend Strategy: Already have LONG position for " + symbol + 
                    " (Quantity: " + longPositionQuantity + ") - skipping until TP/SL hit");
            }
            // If we have SHORT position → Skip (wait for TP/SL to close)
            else if (shortPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ SuperTrend Strategy: BUY signal but have SHORT position for " + symbol + 
                    " (Quantity: " + shortPositionQuantity + ") - skipping, waiting for TP/SL to close");
            }
            // No position → Open LONG
            else {
                Order buyOrder = createBuyOrder(symbol, currentPrice);
                
                // SET STOP LOSS: Place stop loss at SuperTrend level (it acts as dynamic support)
                BigDecimal stopLoss = superTrend.multiply(new BigDecimal("0.995")); // 0.5% below SuperTrend
                buyOrder.setSuggestedStopLoss(stopLoss);
                
                // SET TAKE PROFIT: Risk:Reward ratio of 1:2
                BigDecimal riskDistance = currentPrice.subtract(stopLoss);
                BigDecimal takeProfit = currentPrice.add(riskDistance.multiply(new BigDecimal("2")));
                buyOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(buyOrder);
                action = "BUY";
                
                // Enhanced logging with stop loss info
                BigDecimal stopLossPercent = stopLoss.subtract(currentPrice)
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                System.out.println(String.format(
                    "🟢 SuperTrend Strategy: Opening LONG position for %s at %s | SuperTrend: %s | Stop Loss: %s (%.2f%%) | Take Profit: %s",
                    symbol, currentPrice, superTrend, stopLoss, stopLossPercent, takeProfit
                ));
            }
        }
        // BEARISH: Price crosses below SuperTrend (direction changed from -1 to +1 per Pine Script convention)
        // In Pine Script: direction > 0 means DOWNTREND (red)
        else if (direction.compareTo(BigDecimal.ZERO) > 0 && 
                 (oldDirection != null && oldDirection.compareTo(BigDecimal.ZERO) < 0)) {
            
            // Check existing positions
            BigDecimal longPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.LONG);
            BigDecimal shortPositionQuantity = calculateCloseQuantity(symbol, org.cloudvision.trading.bot.account.PositionSide.SHORT);
            
            // If we have SHORT position → Skip (wait for TP/SL)
            if (shortPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ SuperTrend Strategy: Already have SHORT position for " + symbol + 
                    " (Quantity: " + shortPositionQuantity + ") - skipping until TP/SL hit");
            }
            // If we have LONG position → Skip (wait for TP/SL to close)
            else if (longPositionQuantity.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("⏸️ SuperTrend Strategy: SELL signal but have LONG position for " + symbol + 
                    " (Quantity: " + longPositionQuantity + ") - skipping, waiting for TP/SL to close");
            }
            // No position → Open SHORT
            else {
                Order shortOrder = createShortOrder(symbol, currentPrice);
                
                // SET STOP LOSS: Place stop loss at SuperTrend level (it acts as dynamic resistance)
                BigDecimal stopLoss = superTrend.multiply(new BigDecimal("1.005")); // 0.5% above SuperTrend
                shortOrder.setSuggestedStopLoss(stopLoss);
                
                // SET TAKE PROFIT: Risk:Reward ratio of 1:2
                BigDecimal riskDistance = stopLoss.subtract(currentPrice);
                BigDecimal takeProfit = currentPrice.subtract(riskDistance.multiply(new BigDecimal("2")));
                shortOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(shortOrder);
                action = "SELL";
                
                // Enhanced logging with stop loss info
                BigDecimal stopLossPercent = stopLoss.subtract(currentPrice)
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                System.out.println(String.format(
                    "🔴 SuperTrend Strategy: Opening SHORT position for %s at %s | SuperTrend: %s | Stop Loss: %s (%.2f%%) | Take Profit: %s",
                    symbol, currentPrice, superTrend, stopLoss, stopLossPercent, takeProfit
                ));
            }
        }
        
        // Generate visualization data
        generateVisualizationData(symbol, currentPrice, superTrend, direction, action, priceData.timestamp);
        
        return orders;
    }

    @Override
    public String getStrategyId() {
        return config != null ? config.getStrategyId() : "supertrend";
    }

    @Override
    public String getStrategyName() {
        // Generate a descriptive name based on parameters
        if (config != null) {
            String baseName = config.getStrategyId();
            return String.format("SuperTrend Strategy (%d, %.1f) - %s", 
                atrPeriod, atrMultiplier, baseName);
        }
        return "SuperTrend Strategy";
    }

    @Override
    public List<String> getSymbols() {
        return config != null ? config.getSymbols() : List.of("BTCUSDT");
    }

    @Override
    public void initialize(StrategyConfig config) {
        super.initialize(config);
        
        // Get strategy-specific parameters
        if (config.getParameter("atrPeriod") != null) {
            this.atrPeriod = (Integer) config.getParameter("atrPeriod");
        }
        if (config.getParameter("atrMultiplier") != null) {
            Object multiplier = config.getParameter("atrMultiplier");
            if (multiplier instanceof Integer) {
                this.atrMultiplier = new BigDecimal((Integer) multiplier);
            } else if (multiplier instanceof Double) {
                this.atrMultiplier = new BigDecimal((Double) multiplier);
            } else if (multiplier instanceof String) {
                this.atrMultiplier = new BigDecimal((String) multiplier);
            }
        }
        
        // Register with visualization manager
        if (visualizationManager != null) {
            visualizationManager.registerStrategy(getStrategyId(), getSymbols());
        }
        
        System.out.println("Initialized " + getStrategyName() + ": ATR Period=" + atrPeriod + 
                         ", Multiplier=" + atrMultiplier);
    }
    
    @Override
    protected int getMaxHistorySize() {
        return atrPeriod + 100; // Keep enough history for calculations
    }
    
    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // SuperTrend Line - Dynamic color based on direction (green=bullish, red=bearish)
        // We use addConfig to specify both colors for the frontend to handle
        metadata.put("superTrend", IndicatorMetadata.builder("superTrend")
            .displayName("SuperTrend (" + atrPeriod + ", " + atrMultiplier + ")")
            .asLine("#26a69a", 3) // Default green for uptrend
            .addConfig("upColor", "#26a69a")    // Green for uptrend
            .addConfig("downColor", "#ef5350")  // Red for downtrend
            .addConfig("lineStyle", 0)          // Solid line
            .addConfig("dynamicColor", true)    // Flag for frontend to use dynamic coloring
            .separatePane(false)
            .paneOrder(0) // Main chart
            .build());
        
        return metadata;
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        // Calculate initial SuperTrend values and set signal state
        for (Map.Entry<String, List<CandlestickData>> entry : dataBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<CandlestickData> candles = entry.getValue();
            
            if (candles.size() >= atrPeriod + 1) {
                List<BigDecimal> highs = highHistory.get(symbol);
                List<BigDecimal> lows = lowHistory.get(symbol);
                List<BigDecimal> closes = getPriceHistory(symbol);
                
                BigDecimal[] result = TechnicalIndicators.calculateSuperTrend(
                    highs, lows, closes, atrPeriod, atrMultiplier, null, null
                );
                
                if (result != null) {
                    previousSuperTrend.put(symbol, result[0]);
                    previousDirection.put(symbol, result[1]);
                    
                    // Pine Script convention: direction < 0 = uptrend (bullish), direction > 0 = downtrend (bearish)
                    System.out.println("📊 " + symbol + " initial SuperTrend: " + result[0] + 
                                     " (" + (result[1].compareTo(BigDecimal.ZERO) < 0 ? "BULLISH" : "BEARISH") + ")");
                }
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
            
            // Sort chronologically by openTime (matches CandlestickHistoryService order)
            candles.sort(Comparator.comparing(CandlestickData::getOpenTime));
            
            // Build price history progressively and calculate indicators
            List<BigDecimal> progressiveHighs = new ArrayList<>();
            List<BigDecimal> progressiveLows = new ArrayList<>();
            List<BigDecimal> progressiveCloses = new ArrayList<>();
            BigDecimal prevST = null;
            BigDecimal prevDir = null;
            
            for (CandlestickData candle : candles) {
                progressiveHighs.add(candle.getHigh());
                progressiveLows.add(candle.getLow());
                progressiveCloses.add(candle.getClose());
                
                // Only calculate indicators once we have enough data
                if (progressiveCloses.size() >= atrPeriod + 1) {
                    BigDecimal[] result = TechnicalIndicators.calculateSuperTrend(
                        progressiveHighs, progressiveLows, progressiveCloses, 
                        atrPeriod, atrMultiplier, prevST, prevDir
                    );
                    
                    if (result != null) {
                        BigDecimal superTrend = result[0];
                        BigDecimal direction = result[1];
                        
                        // Determine action based on direction change
                        // Pine Script convention: direction < 0 = uptrend, direction > 0 = downtrend
                        String action = "HOLD";
                        if (prevDir != null) {
                            if (direction.compareTo(BigDecimal.ZERO) < 0 && 
                                prevDir.compareTo(BigDecimal.ZERO) > 0) {
                                action = "BUY"; // Switched from downtrend to uptrend
                            } else if (direction.compareTo(BigDecimal.ZERO) > 0 && 
                                      prevDir.compareTo(BigDecimal.ZERO) < 0) {
                                action = "SELL"; // Switched from uptrend to downtrend
                            }
                        }
                        
                        // Generate visualization data
                        generateVisualizationData(symbol, candle.getClose(), superTrend, 
                                                direction, action, candle.getCloseTime());
                        
                        prevST = superTrend;
                        prevDir = direction;
                    }
                }
            }
            
            System.out.println("✅ Generated " + (candles.size() - atrPeriod) + 
                             " visualization points for " + symbol);
        }
    }
    
    /**
     * Update high/low history for a symbol
     */
    private void updateCandleHistory(String symbol, BigDecimal high, BigDecimal low) {
        List<BigDecimal> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
        List<BigDecimal> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
        
        highs.add(high);
        lows.add(low);
        
        // Keep history manageable
        int maxHistorySize = getMaxHistorySize();
        if (highs.size() > maxHistorySize + 10) {
            highs.subList(0, highs.size() - maxHistorySize - 5).clear();
            lows.subList(0, lows.size() - maxHistorySize - 5).clear();
        }
    }
    
    /**
     * Generate visualization data for the strategy
     */
    private void generateVisualizationData(String symbol, BigDecimal price, 
                                         BigDecimal superTrend, BigDecimal direction,
                                         String action, java.time.Instant timestamp) {
        if (visualizationManager == null) return;
        
        // Prepare indicators
        Map<String, BigDecimal> indicators = new HashMap<>();
        indicators.put("superTrend", superTrend);
        // indicators.put("atr", calculateCurrentATR(symbol)); 
        
        // Prepare signals
        // Pine Script convention: direction < 0 = UPTREND (bullish), direction > 0 = DOWNTREND (bearish)
        boolean isBullish = direction.compareTo(BigDecimal.ZERO) < 0;
        Map<String, Object> signals = new HashMap<>();
        signals.put("trend", isBullish ? "BULLISH" : "BEARISH");
        signals.put("direction", direction);
        signals.put("bullish", isBullish);
        signals.put("bearish", !isBullish);
        signals.put("distance", price.subtract(superTrend).abs());
        signals.put("distancePercent", superTrend.compareTo(BigDecimal.ZERO) > 0 ?
            price.subtract(superTrend).divide(superTrend, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) :
            BigDecimal.ZERO);
        // Add color information for dynamic coloring on frontend
        signals.put("superTrendColor", isBullish ? "#26a69a" : "#ef5350"); // Green for bullish, Red for bearish
        
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
     * Calculate current ATR for a symbol (for visualization)
     */
    private BigDecimal calculateCurrentATR(String symbol) {
        List<BigDecimal> highs = highHistory.get(symbol);
        List<BigDecimal> lows = lowHistory.get(symbol);
        List<BigDecimal> closes = getPriceHistory(symbol);
        
        if (highs != null && lows != null && closes != null && 
            closes.size() >= atrPeriod + 1) {
            return TechnicalIndicators.calculateATR(highs, lows, closes, atrPeriod);
        }
        
        return BigDecimal.ZERO;
    }
}

