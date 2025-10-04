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
 * Order Block Detector Strategy (Visualization Only)
 * Based on LuxAlgo's Order Block Detector
 * 
 * **PURPOSE**: This is a VISUALIZATION-ONLY strategy that displays institutional order blocks
 * on the chart. It does NOT generate trading signals or create orders. Traders use this as a
 * discretionary tool to identify key support/resistance zones for manual trading decisions.
 * 
 * Order blocks are zones where institutional traders have placed significant orders.
 * - Bullish Order Blocks: Form during downtrends at volume pivots (support zones)
 * - Bearish Order Blocks: Form during uptrends at volume pivots (resistance zones)
 * 
 * ALGORITHM:
 * 1. Detect market structure (uptrend vs downtrend) by tracking higher highs/lower lows
 * 2. Identify volume pivots (local volume highs indicating institutional activity)
 * 3. Create order blocks at volume pivots:
 *    - Bullish OB in downtrend: support zone from low to HL2
 *    - Bearish OB in uptrend: resistance zone from HL2 to high
 * 4. Track volume strength (pivot volume / average volume)
 * 5. Mark OBs as "touched" when price returns to them (for visual reference)
 * 6. Mark OBs as "mitigated" when price breaks through them:
 *    - Bullish OB: invalidated when low breaks below OB bottom
 *    - Bearish OB: invalidated when high breaks above OB top
 * 
 * FEATURES:
 * - Volume strength analysis for quality assessment
 * - Touched indicator (✓) shows zones that have been tested
 * - Mitigated boxes shown in gray (configurable)
 * - Maximum width limit for historical boxes (prevents clutter)
 * - Complete historical data preservation
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
    private int mitigatedBoxMaxWidthBars = 50; // Maximum width for mitigated boxes in number of candles
    private boolean showMitigatedBoxes = false; // Show/hide mitigated (historical) order blocks
    
    // Order Block data class
    private static class OrderBlock {
        BigDecimal top;
        BigDecimal bottom;
        BigDecimal average;
        Instant timestamp;
        int barIndex;
        BigDecimal volumeStrength; // Volume at formation relative to recent average
        boolean touched; // Has price returned to this OB zone?
        boolean mitigated; // Has this OB been invalidated?
        Instant mitigationTime; // When was it mitigated?
        
        OrderBlock(BigDecimal top, BigDecimal bottom, Instant timestamp, int barIndex, BigDecimal volumeStrength) {
            this.top = top;
            this.bottom = bottom;
            this.average = top.add(bottom).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
            this.timestamp = timestamp;
            this.barIndex = barIndex;
            this.volumeStrength = volumeStrength;
            this.touched = false;
            this.mitigated = false;
            this.mitigationTime = null;
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
            
            // Calculate volume strength (ratio of pivot volume to average volume)
            BigDecimal volumeStrength = calculateVolumeStrength(volumes, pivotIndex);
            
            // Bullish Order Block (downtrend + volume pivot)
            if (os == 1) {
                BigDecimal top = getHL2(highs, lows, pivotIndex);
                BigDecimal bottom = lows.get(lows.size() - 1 - pivotIndex);
                Instant obTimestamp = timestamps.get(timestamps.size() - 1 - pivotIndex);
                
                // Create new bullish order block
                OrderBlock bullOB = new OrderBlock(top, bottom, obTimestamp, barIndex - pivotIndex, volumeStrength);
                addBullishOrderBlock(symbol, bullOB);
                
                System.out.println("🟢 Order Block: Bullish OB detected for " + symbol + 
                    " | Top: " + top + " | Bottom: " + bottom + " | Avg: " + bullOB.average + 
                    " | Volume Strength: " + volumeStrength.setScale(2, RoundingMode.HALF_UP) + "x");
            }
            // Bearish Order Block (uptrend + volume pivot)
            else if (os == 0) {
                BigDecimal top = highs.get(highs.size() - 1 - pivotIndex);
                BigDecimal bottom = getHL2(highs, lows, pivotIndex);
                Instant obTimestamp = timestamps.get(timestamps.size() - 1 - pivotIndex);
                
                // Create new bearish order block
                OrderBlock bearOB = new OrderBlock(top, bottom, obTimestamp, barIndex - pivotIndex, volumeStrength);
                addBearishOrderBlock(symbol, bearOB);
                
                System.out.println("🔴 Order Block: Bearish OB detected for " + symbol + 
                    " | Top: " + top + " | Bottom: " + bottom + " | Avg: " + bearOB.average +
                    " | Volume Strength: " + volumeStrength.setScale(2, RoundingMode.HALF_UP) + "x");
            }
        }
        
        // Mark touched order blocks (for visualization purposes only)
        markTouchedOrderBlocks(symbol, currentPrice, candle);
        
        // Remove mitigated order blocks
        // For bullish OBs: check if low broke below the block
        // For bearish OBs: check if high broke above the block
        BigDecimal lowPrice = "Wick".equals(mitigationMethod) ? candle.getLow() : candle.getClose();
        BigDecimal highPrice = "Wick".equals(mitigationMethod) ? candle.getHigh() : candle.getClose();
        
        boolean bullMitigated = removeMitigatedBullishOrderBlocks(symbol, lowPrice);
        boolean bearMitigated = removeMitigatedBearishOrderBlocks(symbol, highPrice);
        
        if (bullMitigated) {
            System.out.println("⚠️ Order Block: Bullish OB mitigated for " + symbol);
        }
        if (bearMitigated) {
            System.out.println("⚠️ Order Block: Bearish OB mitigated for " + symbol);
        }
        
        // Extract volume and open price using base class helper method
        Map<String, BigDecimal> volumeData = extractVolumeAndOpen(priceData);
        
        // Generate visualization data
        generateVisualizationData(symbol, currentPrice, priceData.timestamp, 
                                volumeData.get("volume"), volumeData.get("openPrice"));
        
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
     * 
     * This creates a simple trend filter that switches between bullish and bearish bias.
     * The lookback excludes recent bars to avoid look-ahead bias during pivot detection.
     */
    private void updateMarketStructure(String symbol, List<BigDecimal> highs, List<BigDecimal> lows) {
        int size = highs.size();
        if (size < volumePivotLength + 1) return;
        
        // Get values at the pivot point (looking back to avoid look-ahead bias)
        BigDecimal currentHigh = highs.get(size - 1 - volumePivotLength);
        BigDecimal currentLow = lows.get(size - 1 - volumePivotLength);
        
        // Calculate highest high and lowest low over the historical period
        BigDecimal upper = calculateHighest(highs, volumePivotLength);
        BigDecimal lower = calculateLowest(lows, volumePivotLength);
        
        // Update market structure based on breakouts
        int os = marketStructure.getOrDefault(symbol, 0);
        if (currentHigh.compareTo(upper) > 0) {
            os = 0; // Uptrend: making higher highs
        } else if (currentLow.compareTo(lower) < 0) {
            os = 1; // Downtrend: making lower lows
        }
        // Otherwise maintain current structure
        marketStructure.put(symbol, os);
    }
    
    /**
     * Detect volume pivot high
     * Returns volume value if pivot is detected, null otherwise
     * 
     * A volume pivot is a local maximum in volume - the center bar must have higher
     * volume than all bars within 'length' bars on both sides.
     * This identifies significant volume spikes where institutions likely placed orders.
     */
    private BigDecimal detectVolumePivot(List<BigDecimal> volumes, int length) {
        int size = volumes.size();
        if (size < length * 2 + 1) return null;
        
        // Check if center point is pivot high (looking back to maintain causality)
        int centerIndex = size - 1 - length;
        BigDecimal centerVolume = volumes.get(centerIndex);
        
        // Check left side: all bars before center must have lower volume
        for (int i = 1; i <= length; i++) {
            if (volumes.get(centerIndex - i).compareTo(centerVolume) >= 0) {
                return null; // Not a pivot high
            }
        }
        
        // Check right side: all bars after center must have lower volume
        for (int i = 1; i <= length; i++) {
            if (volumes.get(centerIndex + i).compareTo(centerVolume) >= 0) {
                return null; // Not a pivot high
            }
        }
        
        return centerVolume; // Valid pivot detected
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
     * Looks back from the pivot point (excludes the most recent 'volumePivotLength' bars)
     */
    private BigDecimal calculateHighest(List<BigDecimal> values, int period) {
        int size = values.size();
        int startIdx = size - 1 - volumePivotLength - period;
        BigDecimal max = values.get(startIdx);
        
        for (int i = 0; i < period; i++) {
            BigDecimal val = values.get(startIdx + i);
            if (val.compareTo(max) > 0) {
                max = val;
            }
        }
        return max;
    }
    
    /**
     * Calculate lowest value over period
     * Looks back from the pivot point (excludes the most recent 'volumePivotLength' bars)
     */
    private BigDecimal calculateLowest(List<BigDecimal> values, int period) {
        int size = values.size();
        int startIdx = size - 1 - volumePivotLength - period;
        BigDecimal min = values.get(startIdx);
        
        for (int i = 0; i < period; i++) {
            BigDecimal val = values.get(startIdx + i);
            if (val.compareTo(min) < 0) {
                min = val;
            }
        }
        return min;
    }
    
    /**
     * Calculate volume strength at pivot point relative to recent average
     * Returns ratio of pivot volume to average volume over the period
     */
    private BigDecimal calculateVolumeStrength(List<BigDecimal> volumes, int pivotIndex) {
        int size = volumes.size();
        int pivotIdx = size - 1 - pivotIndex;
        BigDecimal pivotVolume = volumes.get(pivotIdx);
        
        // Calculate average volume over the lookback period
        BigDecimal sumVolume = BigDecimal.ZERO;
        int period = Math.min(20, size - 1); // Use up to 20 bars or available data
        
        for (int i = 0; i < period; i++) {
            sumVolume = sumVolume.add(volumes.get(size - 1 - i));
        }
        
        BigDecimal avgVolume = sumVolume.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        
        // Return ratio (strength)
        if (avgVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return pivotVolume.divide(avgVolume, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Mark order blocks as touched when price returns to them (for visualization purposes only)
     * Does not generate trading signals
     */
    private void markTouchedOrderBlocks(String symbol, BigDecimal currentPrice, CandlestickData candle) {
        // Check bullish order blocks
        LinkedList<OrderBlock> bullBlocks = bullishOrderBlocks.get(symbol);
        if (bullBlocks != null) {
            for (OrderBlock ob : bullBlocks) {
                // Skip mitigated order blocks
                if (ob.mitigated) continue;
                
                // Check if price is within or touching the OB zone
                boolean touching = currentPrice.compareTo(ob.bottom) >= 0 && currentPrice.compareTo(ob.top) <= 0;
                
                // Also check if wick touched the zone
                if (!touching && candle != null) {
                    touching = candle.getLow().compareTo(ob.bottom) <= 0 && candle.getHigh().compareTo(ob.bottom) >= 0;
                }
                
                if (touching && !ob.touched) {
                    ob.touched = true;
                    System.out.println("📍 Order Block: Price touched Bullish OB for " + symbol + 
                        " | Price: " + currentPrice + " | OB Zone: [" + ob.bottom + " - " + ob.top + "]" +
                        " | Volume Strength: " + ob.volumeStrength.setScale(2, RoundingMode.HALF_UP) + "x");
                }
            }
        }
        
        // Check bearish order blocks
        LinkedList<OrderBlock> bearBlocks = bearishOrderBlocks.get(symbol);
        if (bearBlocks != null) {
            for (OrderBlock ob : bearBlocks) {
                // Skip mitigated order blocks
                if (ob.mitigated) continue;
                
                // Check if price is within or touching the OB zone
                boolean touching = currentPrice.compareTo(ob.bottom) >= 0 && currentPrice.compareTo(ob.top) <= 0;
                
                // Also check if wick touched the zone
                if (!touching && candle != null) {
                    touching = candle.getHigh().compareTo(ob.top) >= 0 && candle.getLow().compareTo(ob.top) <= 0;
                }
                
                if (touching && !ob.touched) {
                    ob.touched = true;
                    System.out.println("📍 Order Block: Price touched Bearish OB for " + symbol + 
                        " | Price: " + currentPrice + " | OB Zone: [" + ob.bottom + " - " + ob.top + "]" +
                        " | Volume Strength: " + ob.volumeStrength.setScale(2, RoundingMode.HALF_UP) + "x");
                }
            }
        }
    }
    
    /**
     * Add bullish order block
     * Limits only ACTIVE (non-mitigated) order blocks, keeps all mitigated ones for history
     */
    private void addBullishOrderBlock(String symbol, OrderBlock ob) {
        LinkedList<OrderBlock> blocks = bullishOrderBlocks.computeIfAbsent(symbol, k -> new LinkedList<>());
        blocks.addFirst(ob);
        
        // Count active (non-mitigated) blocks
        long activeCount = blocks.stream().filter(block -> !block.mitigated).count();
        
        // Remove oldest ACTIVE blocks if we exceed the limit (keep all mitigated for history)
        if (activeCount > maxBullishOrderBlocks) {
            Iterator<OrderBlock> iterator = blocks.descendingIterator();
            while (iterator.hasNext() && activeCount > maxBullishOrderBlocks) {
                OrderBlock block = iterator.next();
                if (!block.mitigated) {
                    iterator.remove();
                    activeCount--;
                }
            }
        }
    }
    
    /**
     * Add bearish order block
     * Limits only ACTIVE (non-mitigated) order blocks, keeps all mitigated ones for history
     */
    private void addBearishOrderBlock(String symbol, OrderBlock ob) {
        LinkedList<OrderBlock> blocks = bearishOrderBlocks.computeIfAbsent(symbol, k -> new LinkedList<>());
        blocks.addFirst(ob);
        
        // Count active (non-mitigated) blocks
        long activeCount = blocks.stream().filter(block -> !block.mitigated).count();
        
        // Remove oldest ACTIVE blocks if we exceed the limit (keep all mitigated for history)
        if (activeCount > maxBearishOrderBlocks) {
            Iterator<OrderBlock> iterator = blocks.descendingIterator();
            while (iterator.hasNext() && activeCount > maxBearishOrderBlocks) {
                OrderBlock block = iterator.next();
                if (!block.mitigated) {
                    iterator.remove();
                    activeCount--;
                }
            }
        }
    }
    
    /**
     * Mark mitigated bullish order blocks as invalid
     * Bullish OB is mitigated (invalidated) when price breaks below the support zone.
     * This means the institutions' buy orders have been filled or the support has failed.
     * Keep ALL mitigated OBs for complete historical visualization.
     */
    private boolean removeMitigatedBullishOrderBlocks(String symbol, BigDecimal targetPrice) {
        LinkedList<OrderBlock> blocks = bullishOrderBlocks.get(symbol);
        if (blocks == null || blocks.isEmpty()) return false;
        
        boolean newMitigation = false;
        for (OrderBlock ob : blocks) {
            // Mark as mitigated if price broke below the OB bottom (support failed)
            if (!ob.mitigated && targetPrice.compareTo(ob.bottom) < 0) {
                ob.mitigated = true;
                ob.mitigationTime = Instant.now();
                newMitigation = true;
                System.out.println("💔 Order Block: Bullish OB mitigated for " + symbol + 
                    " | OB Zone: [" + ob.bottom + " - " + ob.top + "]");
            }
        }
        
        // Keep ALL mitigated order blocks for complete historical context
        // No removal of mitigated OBs - they stay for the entire dataset
        
        return newMitigation;
    }
    
    /**
     * Mark mitigated bearish order blocks as invalid
     * Bearish OB is mitigated (invalidated) when price breaks above the resistance zone.
     * This means the institutions' sell orders have been filled or the resistance has failed.
     * Keep ALL mitigated OBs for complete historical visualization.
     */
    private boolean removeMitigatedBearishOrderBlocks(String symbol, BigDecimal targetPrice) {
        LinkedList<OrderBlock> blocks = bearishOrderBlocks.get(symbol);
        if (blocks == null || blocks.isEmpty()) return false;
        
        boolean newMitigation = false;
        for (OrderBlock ob : blocks) {
            // Mark as mitigated if price broke above the OB top (resistance failed)
            if (!ob.mitigated && targetPrice.compareTo(ob.top) > 0) {
                ob.mitigated = true;
                ob.mitigationTime = Instant.now();
                newMitigation = true;
                System.out.println("💔 Order Block: Bearish OB mitigated for " + symbol + 
                    " | OB Zone: [" + ob.bottom + " - " + ob.top + "]");
            }
        }
        
        // Keep ALL mitigated order blocks for complete historical context
        // No removal of mitigated OBs - they stay for the entire dataset
        
        return newMitigation;
    }
    
    /**
     * Generate visualization data
     */
    private void generateVisualizationData(String symbol, BigDecimal price, Instant timestamp,
                                         BigDecimal volume, BigDecimal openPrice) {
        if (visualizationManager == null) return;
        
        // Prepare indicators
        Map<String, BigDecimal> indicators = new HashMap<>();
        
        // Prepare boxes for order blocks
        List<Map<String, Object>> boxes = new ArrayList<>();
        
        LinkedList<OrderBlock> bullBlocks = bullishOrderBlocks.get(symbol);
        int bullCount = (bullBlocks != null) ? bullBlocks.size() : 0;
        int activeBullCount = 0;
        int mitigatedBullCount = 0;
        
        if (bullBlocks != null && !bullBlocks.isEmpty()) {
            for (OrderBlock ob : bullBlocks) {
                // Skip mitigated boxes if showMitigatedBoxes is false
                if (ob.mitigated && !showMitigatedBoxes) {
                    mitigatedBullCount++;
                    continue;
                }
                
                Map<String, Object> box = new HashMap<>();
                box.put("time1", ob.timestamp.getEpochSecond());
                
                // Calculate end time
                long endTime;
                if (ob.mitigated && ob.mitigationTime != null) {
                    // For mitigated blocks: use mitigation time but cap at max width
                    long mitigationEndTime = ob.mitigationTime.getEpochSecond();
                    long duration = mitigationEndTime - ob.timestamp.getEpochSecond();
                    
                    // Assume 1-minute candles (60 seconds) - can be made configurable
                    long maxDuration = mitigatedBoxMaxWidthBars * 60L;
                    
                    if (duration > maxDuration) {
                        // Cap the width to maximum
                        endTime = ob.timestamp.getEpochSecond() + maxDuration;
                    } else {
                        endTime = mitigationEndTime;
                    }
                } else {
                    // Active blocks: extend to current time
                    endTime = timestamp.getEpochSecond();
                }
                box.put("time2", endTime);
                
                box.put("price1", ob.top.doubleValue());
                box.put("price2", ob.bottom.doubleValue());
                
                // Style based on mitigation status
                if (ob.mitigated) {
                    // Mitigated: Gray with very low opacity
                    box.put("backgroundColor", "rgba(128, 128, 128, 0.08)"); // Gray, very transparent
                    // box.put("borderColor", "#808080");
                    // box.put("borderWidth", 1);
                    // box.put("borderStyle", "dashed");
                    box.put("text", "Bullish OB (Mitigated)");
                    box.put("textColor", "rgba(128, 128, 128, 0.08)");
                    mitigatedBullCount++;
                } else {
                    // Active: Green with normal opacity
                    box.put("backgroundColor", "rgba(22, 148, 0, 0.15)"); // Green with transparency
                    // box.put("borderColor", "#169400");
                    // box.put("borderWidth", 1);
                    // box.put("borderStyle", "solid");
                    box.put("text", ob.touched ? "Bullish OB ✓" : "Bullish OB");
                    box.put("textColor", "rgba(22, 148, 0, 0.15)");
                    activeBullCount++;
                }
                
                boxes.add(box);
            }
        }
        
        LinkedList<OrderBlock> bearBlocks = bearishOrderBlocks.get(symbol);
        int bearCount = (bearBlocks != null) ? bearBlocks.size() : 0;
        int activeBearCount = 0;
        int mitigatedBearCount = 0;
        
        if (bearBlocks != null && !bearBlocks.isEmpty()) {
            for (OrderBlock ob : bearBlocks) {
                // Skip mitigated boxes if showMitigatedBoxes is false
                if (ob.mitigated && !showMitigatedBoxes) {
                    mitigatedBearCount++;
                    continue;
                }
                
                Map<String, Object> box = new HashMap<>();
                box.put("time1", ob.timestamp.getEpochSecond());
                
                // Calculate end time
                long endTime;
                if (ob.mitigated && ob.mitigationTime != null) {
                    // For mitigated blocks: use mitigation time but cap at max width
                    long mitigationEndTime = ob.mitigationTime.getEpochSecond();
                    long duration = mitigationEndTime - ob.timestamp.getEpochSecond();
                    
                    // Assume 1-minute candles (60 seconds) - can be made configurable
                    long maxDuration = mitigatedBoxMaxWidthBars * 60L;
                    
                    if (duration > maxDuration) {
                        // Cap the width to maximum
                        endTime = ob.timestamp.getEpochSecond() + maxDuration;
                    } else {
                        endTime = mitigationEndTime;
                    }
                } else {
                    // Active blocks: extend to current time
                    endTime = timestamp.getEpochSecond();
                }
                box.put("time2", endTime);
                
                box.put("price1", ob.top.doubleValue());
                box.put("price2", ob.bottom.doubleValue());
                
                // Style based on mitigation status
                if (ob.mitigated) {
                    // Mitigated: Gray with very low opacity
                    box.put("backgroundColor", "rgba(128, 128, 128, 0.08)"); // Gray, very transparent
                    // box.put("borderColor", "#808080");
                    // box.put("borderWidth", 1);
                    // box.put("borderStyle", "dashed");
                    box.put("text", "Bearish OB (Mitigated)");
                    box.put("textColor", "rgba(128, 128, 128, 0.08)");
                    mitigatedBearCount++;
                } else {
                    // Active: Red with normal opacity
                    box.put("backgroundColor", "rgba(255, 17, 0, 0.15)"); // Red with transparency
                    // box.put("borderColor", "#ff1100");
                    // box.put("borderWidth", 1);
                    // box.put("borderStyle", "solid");
                    box.put("text", ob.touched ? "Bearish OB ✓" : "Bearish OB");
                    box.put("textColor", "rgba(255, 17, 0, 0.15)");
                    activeBearCount++;
                }
                
                boxes.add(box);
            }
        }
        
        // Debug log to verify boxes are being generated
//        System.out.println(String.format("📦 Order Block [%s]: Active Bull=%d, Mitigated Bull=%d, Active Bear=%d, Mitigated Bear=%d, Total Boxes=%d",
//            symbol, activeBullCount, mitigatedBullCount, activeBearCount, mitigatedBearCount, boxes.size()));
//
        // Prepare signals
        Map<String, Object> signals = new HashMap<>();
        signals.put("marketStructure", marketStructure.getOrDefault(symbol, 0) == 0 ? "UPTREND" : "DOWNTREND");
        signals.put("bullishOrderBlocks", activeBullCount);
        signals.put("bearishOrderBlocks", activeBearCount);
        signals.put("mitigatedBullishOrderBlocks", mitigatedBullCount);
        signals.put("mitigatedBearishOrderBlocks", mitigatedBearCount);
        
        // Check if price is near any active (non-mitigated) order block
        boolean nearBullOB = false;
        boolean nearBearOB = false;
        
        if (bullBlocks != null) {
            for (OrderBlock ob : bullBlocks) {
                if (!ob.mitigated && price.compareTo(ob.bottom) >= 0 && price.compareTo(ob.top) <= 0) {
                    nearBullOB = true;
                    break;
                }
            }
        }
        
        if (bearBlocks != null) {
            for (OrderBlock ob : bearBlocks) {
                if (!ob.mitigated && price.compareTo(ob.bottom) >= 0 && price.compareTo(ob.top) <= 0) {
                    nearBearOB = true;
                    break;
                }
            }
        }
        
        signals.put("nearBullishOB", nearBullOB);
        signals.put("nearBearishOB", nearBearOB);
        
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
        
        // No trading signals - always HOLD (visualization only)
        String signal = "HOLD";
        
        // Create visualization data with boxes
        StrategyVisualizationData vizData = new StrategyVisualizationData(
            getStrategyId(),
            symbol,
            timestamp,
            price,
            indicators,
            signals,
            performance,
            signal,
            boxes
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
        if (config.getParameter("mitigatedBoxMaxWidthBars") != null) {
            this.mitigatedBoxMaxWidthBars = (Integer) config.getParameter("mitigatedBoxMaxWidthBars");
        }
        if (config.getParameter("showMitigatedBoxes") != null) {
            this.showMitigatedBoxes = (Boolean) config.getParameter("showMitigatedBoxes");
        }
        
        // Register with visualization manager
        if (visualizationManager != null) {
            visualizationManager.registerStrategy(getStrategyId(), getSymbols());
        }
        
        System.out.println("Initialized " + getStrategyName() + 
            ": Volume Pivot Length=" + volumePivotLength + 
            ", Max Bull OBs=" + maxBullishOrderBlocks + 
            ", Max Bear OBs=" + maxBearishOrderBlocks +
            ", Mitigation=" + mitigationMethod +
            ", Mitigated Box Max Width=" + mitigatedBoxMaxWidthBars + " bars" +
            ", Show Mitigated=" + showMitigatedBoxes);
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
        
        // Volume - Use reusable base class method
        // Place it in pane 1 (main chart is 0, volume will be in separate pane below)
        metadata.put("volume", getVolumeIndicatorMetadata(1));
        
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
                
                // This will update internal state and generate viz data (including volume)
                analyzePrice(priceData);
            }
            
            System.out.println("✅ Generated visualization points for " + symbol);
        }
    }
}

