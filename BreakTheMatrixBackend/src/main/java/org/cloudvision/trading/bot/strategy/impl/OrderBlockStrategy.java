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
import java.time.Instant;
import java.util.*;

/**
 * Order Block Detector Strategy
 * Based on LuxAlgo's Order Block Detector
 * 
 * Order blocks are zones where institutional traders have placed significant orders.
 * - Bullish Order Blocks: Form during downtrends at volume pivots (support zones)
 * - Bearish Order Blocks: Form during uptrends at volume pivots (resistance zones)
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OrderBlockStrategy extends AbstractTradingStrategy {
    
    @Autowired
    private VisualizationManager visualizationManager;
    
    // Strategy parameters
    private int volumePivotLength = 5;
    private int maxBullishOrderBlocks = 3;
    private int maxBearishOrderBlocks = 3;
    private String mitigationMethod = "Wick"; // "Wick" or "Close"
    
    // Order Block data class
    private static class OrderBlock {
        BigDecimal top;
        BigDecimal bottom;
        BigDecimal average;
        Instant timestamp;
        int barIndex;
        
        OrderBlock(BigDecimal top, BigDecimal bottom, Instant timestamp, int barIndex) {
            this.top = top;
            this.bottom = bottom;
            this.average = top.add(bottom).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
            this.timestamp = timestamp;
            this.barIndex = barIndex;
        }
    }
    
    // Store order blocks for each symbol
    private final Map<String, LinkedList<OrderBlock>> bullishOrderBlocks = new HashMap<>();
    private final Map<String, LinkedList<OrderBlock>> bearishOrderBlocks = new HashMap<>();
    
    // Store candlestick history for each symbol
    private final Map<String, List<BigDecimal>> highHistory = new HashMap<>();
    private final Map<String, List<BigDecimal>> lowHistory = new HashMap<>();
    private final Map<String, List<BigDecimal>> volumeHistory = new HashMap<>();
    private final Map<String, List<Instant>> timestampHistory = new HashMap<>();
    
    // Market structure oscillator (0 = uptrend, 1 = downtrend)
    private final Map<String, Integer> marketStructure = new HashMap<>();
    
    // Bar index counter
    private final Map<String, Integer> barIndexCounter = new HashMap<>();
    
    @Override
    protected List<Order> analyzePrice(PriceData priceData) {
        String symbol = priceData.symbol;
        BigDecimal currentPrice = priceData.price;
        
        // Order blocks require candlestick data
        if (priceData.rawData.getCandlestickData() == null) {
            return Collections.emptyList();
        }
        
        CandlestickData candle = priceData.rawData.getCandlestickData();
        
        // Update history
        updateHistory(symbol, candle);
        
        // Increment bar index
        int barIndex = barIndexCounter.compute(symbol, (k, v) -> (v == null ? 0 : v + 1));
        
        // Need enough data for pivot detection
        if (!hasEnoughData(symbol, volumePivotLength * 2 + 1)) {
            return Collections.emptyList();
        }
        
        // Get data arrays
        List<BigDecimal> highs = highHistory.get(symbol);
        List<BigDecimal> lows = lowHistory.get(symbol);
        List<BigDecimal> closes = getPriceHistory(symbol);
        List<BigDecimal> volumes = volumeHistory.get(symbol);
        List<Instant> timestamps = timestampHistory.get(symbol);
        
        // Update market structure
        updateMarketStructure(symbol, highs, lows);
        
        // Detect volume pivot
        BigDecimal pivotVolume = detectVolumePivot(volumes, volumePivotLength);
        
        List<Order> orders = new ArrayList<>();
        
        if (pivotVolume != null) {
            int os = marketStructure.getOrDefault(symbol, 0);
            int pivotIndex = volumePivotLength; // Pivot is at the center
            
            // Bullish Order Block (downtrend + volume pivot)
            if (os == 1) {
                BigDecimal top = getHL2(highs, lows, pivotIndex);
                BigDecimal bottom = lows.get(lows.size() - 1 - pivotIndex);
                Instant obTimestamp = timestamps.get(timestamps.size() - 1 - pivotIndex);
                
                // Create new bullish order block
                OrderBlock bullOB = new OrderBlock(top, bottom, obTimestamp, barIndex - pivotIndex);
                addBullishOrderBlock(symbol, bullOB);
                
                System.out.println("🟢 Order Block: Bullish OB detected for " + symbol + 
                    " | Top: " + top + " | Bottom: " + bottom + " | Avg: " + bullOB.average);
                
                // Trading signal: Enter LONG when bullish OB forms (or wait for price to return to OB zone)
                // For this implementation, we'll signal when OB forms
                Order buyOrder = createBuyOrder(symbol, currentPrice);
                
                // Set stop loss below the order block
                BigDecimal stopLoss = bottom.multiply(new BigDecimal("0.998")); // 0.2% below bottom
                buyOrder.setSuggestedStopLoss(stopLoss);
                
                // Set take profit with 2:1 risk/reward
                BigDecimal riskDistance = currentPrice.subtract(stopLoss);
                BigDecimal takeProfit = currentPrice.add(riskDistance.multiply(new BigDecimal("2")));
                buyOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(buyOrder);
            }
            // Bearish Order Block (uptrend + volume pivot)
            else if (os == 0) {
                BigDecimal top = highs.get(highs.size() - 1 - pivotIndex);
                BigDecimal bottom = getHL2(highs, lows, pivotIndex);
                Instant obTimestamp = timestamps.get(timestamps.size() - 1 - pivotIndex);
                
                // Create new bearish order block
                OrderBlock bearOB = new OrderBlock(top, bottom, obTimestamp, barIndex - pivotIndex);
                addBearishOrderBlock(symbol, bearOB);
                
                System.out.println("🔴 Order Block: Bearish OB detected for " + symbol + 
                    " | Top: " + top + " | Bottom: " + bottom + " | Avg: " + bearOB.average);
                
                // Trading signal: Enter SHORT when bearish OB forms
                Order shortOrder = createShortOrder(symbol, currentPrice);
                
                // Set stop loss above the order block
                BigDecimal stopLoss = top.multiply(new BigDecimal("1.002")); // 0.2% above top
                shortOrder.setSuggestedStopLoss(stopLoss);
                
                // Set take profit with 2:1 risk/reward
                BigDecimal riskDistance = stopLoss.subtract(currentPrice);
                BigDecimal takeProfit = currentPrice.subtract(riskDistance.multiply(new BigDecimal("2")));
                shortOrder.setSuggestedTakeProfit(takeProfit);
                
                orders.add(shortOrder);
            }
        }
        
        // Remove mitigated order blocks
        BigDecimal targetPrice = getMitigationTarget(candle);
        boolean bullMitigated = removeMitigatedBullishOrderBlocks(symbol, targetPrice);
        boolean bearMitigated = removeMitigatedBearishOrderBlocks(symbol, targetPrice);
        
        if (bullMitigated) {
            System.out.println("⚠️ Order Block: Bullish OB mitigated for " + symbol);
        }
        if (bearMitigated) {
            System.out.println("⚠️ Order Block: Bearish OB mitigated for " + symbol);
        }
        
        // Generate visualization data
        generateVisualizationData(symbol, currentPrice, priceData.timestamp);
        
        return orders;
    }
    
    /**
     * Update candlestick history
     */
    private void updateHistory(String symbol, CandlestickData candle) {
        highHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(candle.getHigh());
        lowHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(candle.getLow());
        volumeHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(candle.getVolume());
        timestampHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(candle.getCloseTime());
        
        List<BigDecimal> highs = highHistory.get(symbol);
        List<BigDecimal> lows = lowHistory.get(symbol);
        List<BigDecimal> volumes = volumeHistory.get(symbol);
        List<Instant> timestamps = timestampHistory.get(symbol);
        
        // Keep history manageable
        int maxHistorySize = getMaxHistorySize();
        if (highs.size() > maxHistorySize + 10) {
            highs.subList(0, 10).clear();
            lows.subList(0, 10).clear();
            volumes.subList(0, 10).clear();
            timestamps.subList(0, 10).clear();
        }
    }
    
    /**
     * Update market structure oscillator
     * os = 0 (uptrend) when high breaks above highest high
     * os = 1 (downtrend) when low breaks below lowest low
     */
    private void updateMarketStructure(String symbol, List<BigDecimal> highs, List<BigDecimal> lows) {
        int size = highs.size();
        if (size < volumePivotLength + 1) return;
        
        // Get current values
        BigDecimal currentHigh = highs.get(size - 1 - volumePivotLength);
        BigDecimal currentLow = lows.get(size - 1 - volumePivotLength);
        
        // Calculate highest high and lowest low over the period
        BigDecimal upper = calculateHighest(highs, volumePivotLength);
        BigDecimal lower = calculateLowest(lows, volumePivotLength);
        
        // Update market structure
        int os = marketStructure.getOrDefault(symbol, 0);
        if (currentHigh.compareTo(upper) > 0) {
            os = 0; // Uptrend
        } else if (currentLow.compareTo(lower) < 0) {
            os = 1; // Downtrend
        }
        marketStructure.put(symbol, os);
    }
    
    /**
     * Detect volume pivot high
     * Returns volume value if pivot is detected, null otherwise
     */
    private BigDecimal detectVolumePivot(List<BigDecimal> volumes, int length) {
        int size = volumes.size();
        if (size < length * 2 + 1) return null;
        
        // Check if center point is pivot high
        int centerIndex = size - 1 - length;
        BigDecimal centerVolume = volumes.get(centerIndex);
        
        // Check left side
        for (int i = 1; i <= length; i++) {
            if (volumes.get(centerIndex - i).compareTo(centerVolume) >= 0) {
                return null; // Not a pivot high
            }
        }
        
        // Check right side
        for (int i = 1; i <= length; i++) {
            if (volumes.get(centerIndex + i).compareTo(centerVolume) >= 0) {
                return null; // Not a pivot high
            }
        }
        
        return centerVolume;
    }
    
    /**
     * Calculate HL2 (average of high and low) at offset
     */
    private BigDecimal getHL2(List<BigDecimal> highs, List<BigDecimal> lows, int offset) {
        int index = highs.size() - 1 - offset;
        BigDecimal high = highs.get(index);
        BigDecimal low = lows.get(index);
        return high.add(low).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate highest value over period
     */
    private BigDecimal calculateHighest(List<BigDecimal> values, int period) {
        int size = values.size();
        BigDecimal max = values.get(size - 1 - period);
        for (int i = 1; i <= period; i++) {
            BigDecimal val = values.get(size - 1 - i);
            if (val.compareTo(max) > 0) {
                max = val;
            }
        }
        return max;
    }
    
    /**
     * Calculate lowest value over period
     */
    private BigDecimal calculateLowest(List<BigDecimal> values, int period) {
        int size = values.size();
        BigDecimal min = values.get(size - 1 - period);
        for (int i = 1; i <= period; i++) {
            BigDecimal val = values.get(size - 1 - i);
            if (val.compareTo(min) < 0) {
                min = val;
            }
        }
        return min;
    }
    
    /**
     * Get mitigation target based on method
     */
    private BigDecimal getMitigationTarget(CandlestickData candle) {
        if ("Close".equals(mitigationMethod)) {
            return candle.getClose();
        } else {
            // "Wick" method - use high or low depending on direction
            return candle.getHigh(); // We'll check both high and low in mitigation logic
        }
    }
    
    /**
     * Add bullish order block
     */
    private void addBullishOrderBlock(String symbol, OrderBlock ob) {
        LinkedList<OrderBlock> blocks = bullishOrderBlocks.computeIfAbsent(symbol, k -> new LinkedList<>());
        blocks.addFirst(ob);
        
        // Keep only the most recent blocks
        while (blocks.size() > maxBullishOrderBlocks) {
            blocks.removeLast();
        }
    }
    
    /**
     * Add bearish order block
     */
    private void addBearishOrderBlock(String symbol, OrderBlock ob) {
        LinkedList<OrderBlock> blocks = bearishOrderBlocks.computeIfAbsent(symbol, k -> new LinkedList<>());
        blocks.addFirst(ob);
        
        // Keep only the most recent blocks
        while (blocks.size() > maxBearishOrderBlocks) {
            blocks.removeLast();
        }
    }
    
    /**
     * Remove mitigated bullish order blocks
     * Bullish OB is mitigated when price goes below the bottom
     */
    private boolean removeMitigatedBullishOrderBlocks(String symbol, BigDecimal targetPrice) {
        LinkedList<OrderBlock> blocks = bullishOrderBlocks.get(symbol);
        if (blocks == null || blocks.isEmpty()) return false;
        
        boolean mitigated = false;
        Iterator<OrderBlock> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            OrderBlock ob = iterator.next();
            if (targetPrice.compareTo(ob.bottom) < 0) {
                iterator.remove();
                mitigated = true;
            }
        }
        
        return mitigated;
    }
    
    /**
     * Remove mitigated bearish order blocks
     * Bearish OB is mitigated when price goes above the top
     */
    private boolean removeMitigatedBearishOrderBlocks(String symbol, BigDecimal targetPrice) {
        LinkedList<OrderBlock> blocks = bearishOrderBlocks.get(symbol);
        if (blocks == null || blocks.isEmpty()) return false;
        
        boolean mitigated = false;
        Iterator<OrderBlock> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            OrderBlock ob = iterator.next();
            if (targetPrice.compareTo(ob.top) > 0) {
                iterator.remove();
                mitigated = true;
            }
        }
        
        return mitigated;
    }
    
    /**
     * Generate visualization data
     */
    private void generateVisualizationData(String symbol, BigDecimal price, Instant timestamp) {
        if (visualizationManager == null) return;
        
        // Prepare indicators - show active order blocks
        Map<String, BigDecimal> indicators = new HashMap<>();
        
        LinkedList<OrderBlock> bullBlocks = bullishOrderBlocks.get(symbol);
        if (bullBlocks != null && !bullBlocks.isEmpty()) {
            int idx = 0;
            for (OrderBlock ob : bullBlocks) {
                indicators.put("bullOB" + idx + "_top", ob.top);
                indicators.put("bullOB" + idx + "_bottom", ob.bottom);
                indicators.put("bullOB" + idx + "_avg", ob.average);
                idx++;
                if (idx >= 3) break;
            }
        }
        
        LinkedList<OrderBlock> bearBlocks = bearishOrderBlocks.get(symbol);
        if (bearBlocks != null && !bearBlocks.isEmpty()) {
            int idx = 0;
            for (OrderBlock ob : bearBlocks) {
                indicators.put("bearOB" + idx + "_top", ob.top);
                indicators.put("bearOB" + idx + "_bottom", ob.bottom);
                indicators.put("bearOB" + idx + "_avg", ob.average);
                idx++;
                if (idx >= 3) break;
            }
        }
        
        // Prepare signals
        Map<String, Object> signals = new HashMap<>();
        signals.put("marketStructure", marketStructure.getOrDefault(symbol, 0) == 0 ? "UPTREND" : "DOWNTREND");
        signals.put("bullishOrderBlocks", bullBlocks != null ? bullBlocks.size() : 0);
        signals.put("bearishOrderBlocks", bearBlocks != null ? bearBlocks.size() : 0);
        
        // Check if price is near any order block
        boolean nearBullOB = false;
        boolean nearBearOB = false;
        
        if (bullBlocks != null) {
            for (OrderBlock ob : bullBlocks) {
                if (price.compareTo(ob.bottom) >= 0 && price.compareTo(ob.top) <= 0) {
                    nearBullOB = true;
                    break;
                }
            }
        }
        
        if (bearBlocks != null) {
            for (OrderBlock ob : bearBlocks) {
                if (price.compareTo(ob.bottom) >= 0 && price.compareTo(ob.top) <= 0) {
                    nearBearOB = true;
                    break;
                }
            }
        }
        
        signals.put("nearBullishOB", nearBullOB);
        signals.put("nearBearishOB", nearBearOB);
        
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
            "HOLD"
        );
        
        // Send to visualization manager
        visualizationManager.addVisualizationData(vizData);
    }
    
    @Override
    public String getStrategyId() {
        return config != null ? config.getStrategyId() : "orderblock";
    }
    
    @Override
    public String getStrategyName() {
        if (config != null) {
            String baseName = config.getStrategyId();
            return String.format("Order Block Strategy (Pivot: %d) - %s", 
                volumePivotLength, baseName);
        }
        return "Order Block Strategy";
    }
    
    @Override
    public List<String> getSymbols() {
        return config != null ? config.getSymbols() : List.of("BTCUSDT");
    }
    
    @Override
    public void initialize(StrategyConfig config) {
        super.initialize(config);
        
        // Get strategy-specific parameters
        if (config.getParameter("volumePivotLength") != null) {
            this.volumePivotLength = (Integer) config.getParameter("volumePivotLength");
        }
        if (config.getParameter("maxBullishOrderBlocks") != null) {
            this.maxBullishOrderBlocks = (Integer) config.getParameter("maxBullishOrderBlocks");
        }
        if (config.getParameter("maxBearishOrderBlocks") != null) {
            this.maxBearishOrderBlocks = (Integer) config.getParameter("maxBearishOrderBlocks");
        }
        if (config.getParameter("mitigationMethod") != null) {
            this.mitigationMethod = (String) config.getParameter("mitigationMethod");
        }
        
        // Register with visualization manager
        if (visualizationManager != null) {
            visualizationManager.registerStrategy(getStrategyId(), getSymbols());
        }
        
        System.out.println("Initialized " + getStrategyName() + 
            ": Volume Pivot Length=" + volumePivotLength + 
            ", Max Bull OBs=" + maxBullishOrderBlocks + 
            ", Max Bear OBs=" + maxBearishOrderBlocks +
            ", Mitigation=" + mitigationMethod);
    }
    
    @Override
    protected int getMaxHistorySize() {
        return volumePivotLength * 2 + 100;
    }
    
    @Override
    public void reset() {
        // Call parent reset to clear base state
        super.reset();
        
        // Clear Order Block-specific state
        bullishOrderBlocks.clear();
        bearishOrderBlocks.clear();
        highHistory.clear();
        lowHistory.clear();
        volumeHistory.clear();
        timestampHistory.clear();
        marketStructure.clear();
        barIndexCounter.clear();
        
        System.out.println("🔄 Order Block Strategy: Cleared order blocks and all historical data");
    }
    
    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Bullish Order Blocks (green zones)
        for (int i = 0; i < maxBullishOrderBlocks; i++) {
            metadata.put("bullOB" + i + "_avg", IndicatorMetadata.builder("bullOB" + i + "_avg")
                .displayName("Bullish OB " + (i + 1) + " Average")
                .asLine("#169400", 2)
                .addConfig("zoneTop", "bullOB" + i + "_top")
                .addConfig("zoneBottom", "bullOB" + i + "_bottom")
                .addConfig("zoneColor", "#169400")
                .addConfig("zoneOpacity", 0.2)
                .separatePane(false)
                .paneOrder(0)
                .build());
        }
        
        // Bearish Order Blocks (red zones)
        for (int i = 0; i < maxBearishOrderBlocks; i++) {
            metadata.put("bearOB" + i + "_avg", IndicatorMetadata.builder("bearOB" + i + "_avg")
                .displayName("Bearish OB " + (i + 1) + " Average")
                .asLine("#ff1100", 2)
                .addConfig("zoneTop", "bearOB" + i + "_top")
                .addConfig("zoneBottom", "bearOB" + i + "_bottom")
                .addConfig("zoneColor", "#ff1100")
                .addConfig("zoneOpacity", 0.2)
                .separatePane(false)
                .paneOrder(0)
                .build());
        }
        
        return metadata;
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        System.out.println("📊 Order Block Strategy bootstrap complete for " + dataBySymbol.keySet());
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
            
            // Process each candle
            for (CandlestickData candle : candles) {
                // Create TradingData for this candle
                org.cloudvision.trading.model.TradingData tradingData = 
                    new org.cloudvision.trading.model.TradingData(
                        symbol,
                        candle.getCloseTime(),
                        "historical",
                        org.cloudvision.trading.model.TradingDataType.KLINE,
                        candle
                    );
                
                // Simulate price update
                PriceData priceData = new PriceData(
                    symbol,
                    candle.getClose(),
                    candle.getCloseTime(),
                    org.cloudvision.trading.model.TradingDataType.KLINE,
                    tradingData
                );
                
                // This will update internal state and generate viz data
                analyzePrice(priceData);
            }
            
            System.out.println("✅ Generated visualization points for " + symbol);
        }
    }
}

