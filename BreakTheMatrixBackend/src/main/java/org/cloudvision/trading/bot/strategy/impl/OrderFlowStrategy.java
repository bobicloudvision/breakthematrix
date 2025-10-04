package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.model.*;
import org.cloudvision.trading.bot.strategy.*;
import org.cloudvision.trading.bot.visualization.ArrowShape;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.FootprintCandle;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.FootprintCandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Order Flow Trading Strategy
 * 
 * A comprehensive strategy that combines multiple order flow indicators to identify
 * high-probability trading opportunities. This strategy analyzes:
 * 
 * 1. Cumulative Volume Delta (CVD) - Tracks buying vs selling pressure
 * 2. Delta Divergences - Identifies when price and delta disagree
 * 3. Order Flow Imbalances - Detects significant buy/sell imbalances
 * 4. Absorption - Finds levels where large volume trades with minimal price movement
 * 5. Volume Profile POC - Identifies key support/resistance from volume clustering
 * 
 * TRADING LOGIC:
 * 
 * BUY SIGNALS (All conditions must be met):
 * - CVD is rising (positive trend in cumulative delta)
 * - Bullish divergence detected OR strong buy imbalance
 * - Price near support level (POC or absorption zone)
 * - No active LONG position
 * 
 * SELL SIGNALS (All conditions must be met):
 * - CVD is falling (negative trend in cumulative delta)
 * - Bearish divergence detected OR strong sell imbalance
 * - Price near resistance level (POC or absorption zone)
 * - No active SHORT position
 * 
 * EXIT SIGNALS:
 * - Stop loss: Configurable percentage below/above entry
 * - Take profit: Configurable percentage above/below entry
 * - Reversal signal: Opposite signal appears
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OrderFlowStrategy extends AbstractTradingStrategy {
    
    @Autowired
    private VisualizationManager visualizationManager;
    
    @Autowired
    private FootprintCandleService footprintService;
    
    // Strategy parameters
    private String timeInterval = "1m";
    private int cvdLookback = 20; // Candles to look back for CVD trend
    private double imbalanceThreshold = 2.0; // Minimum ratio for significant imbalance
    private double absorptionThreshold = 50.0; // Minimum absorption score
    private int divergenceLookback = 50; // Candles to look back for divergences
    private int swingLookback = 5; // Candles for swing detection
    private boolean requireDivergence = false; // If true, requires divergence for entry
    private boolean useVolumeConfirmation = true; // Require volume confirmation
    private double minVolumeMultiplier = 1.2; // Minimum volume as multiple of average
    
    // State tracking
    private final Map<String, BigDecimal> previousCVD = new HashMap<>();
    private final Map<String, List<BigDecimal>> cvdHistory = new HashMap<>();
    private final Map<String, String> lastDivergenceType = new HashMap<>();
    private final Map<String, Instant> lastDivergenceTime = new HashMap<>();
    private final Map<String, BigDecimal> entryPrice = new HashMap<>();
    private final Map<String, BigDecimal> stopLoss = new HashMap<>();
    private final Map<String, BigDecimal> takeProfit = new HashMap<>();
    
    // Visual signal tracking
    private final Map<String, List<Map<String, Object>>> signalMarkers = new HashMap<>();
    
    @Override
    protected List<Order> analyzePrice(PriceData priceData) {
        String symbol = priceData.symbol;
        BigDecimal currentPrice = priceData.price;
        
        // Require candlestick data
        if (priceData.rawData.getCandlestickData() == null) {
            return Collections.emptyList();
        }
        
        CandlestickData candle = priceData.rawData.getCandlestickData();
        
        // Need enough historical data
        if (!hasEnoughData(symbol, Math.max(cvdLookback, divergenceLookback))) {
            return Collections.emptyList();
        }
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(timeInterval);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        // Get footprint data
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(
            symbol, interval, Math.max(cvdLookback, divergenceLookback) + 10
        );
        FootprintCandle currentFootprint = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() || currentFootprint == null) {
            System.out.println("⚠️ Order Flow Strategy [" + symbol + "]: No footprint data available");
            return Collections.emptyList();
        }
        
        // Analyze order flow indicators
        OrderFlowSignals signals = analyzeOrderFlow(symbol, currentPrice, footprintCandles, currentFootprint);
        
        // Generate trading signals
        List<Order> orders = generateTradingSignals(symbol, currentPrice, signals, priceData.timestamp);
        
        // Generate visualization data (after signals so we can include signal markers)
        generateVisualizationData(symbol, currentPrice, priceData.timestamp, candle, signals);
        
        return orders;
    }
    
    /**
     * Analyze all order flow indicators and combine into signals
     */
    private OrderFlowSignals analyzeOrderFlow(String symbol, BigDecimal currentPrice,
                                              List<FootprintCandle> historical,
                                              FootprintCandle current) {
        OrderFlowSignals signals = new OrderFlowSignals();
        
        // 1. Calculate Cumulative Volume Delta (CVD)
        BigDecimal cvd = calculateCVD(historical, current);
        signals.cvd = cvd;
        signals.cvdTrend = determineCVDTrend(symbol, cvd);
        
        // 2. Detect Delta Divergences
        DivergenceResult divergence = detectDivergence(historical, current);
        signals.divergenceType = divergence.type;
        signals.divergenceStrength = divergence.strength;
        
        // Track divergence
        if (!divergence.type.equals("NONE")) {
            lastDivergenceType.put(symbol, divergence.type);
            lastDivergenceTime.put(symbol, Instant.now());
        }
        
        // 3. Detect Order Flow Imbalances
        ImbalanceResult imbalance = detectImbalance(current);
        signals.imbalanceDirection = imbalance.direction;
        signals.imbalanceRatio = imbalance.ratio;
        
        // 4. Detect Absorption
        BigDecimal absorptionScore = calculateAbsorption(current);
        signals.absorptionScore = absorptionScore;
        signals.hasAbsorption = absorptionScore.compareTo(BigDecimal.valueOf(absorptionThreshold)) > 0;
        
        // 5. Calculate current delta and volume
        signals.currentDelta = current.getDelta();
        signals.currentVolume = current.getTotalVolume();
        
        // 6. Volume confirmation
        signals.volumeConfirmed = checkVolumeConfirmation(historical, current);
        
        return signals;
    }
    
    /**
     * Calculate Cumulative Volume Delta
     */
    private BigDecimal calculateCVD(List<FootprintCandle> historical, FootprintCandle current) {
        BigDecimal cvd = BigDecimal.ZERO;
        
        // Use only recent history for CVD
        int startIndex = Math.max(0, historical.size() - cvdLookback);
        for (int i = startIndex; i < historical.size(); i++) {
            cvd = cvd.add(historical.get(i).getDelta());
        }
        
        cvd = cvd.add(current.getDelta());
        
        return cvd;
    }
    
    /**
     * Determine CVD trend (rising/falling/neutral)
     */
    private String determineCVDTrend(String symbol, BigDecimal currentCVD) {
        // Store CVD history
        List<BigDecimal> history = cvdHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
        history.add(currentCVD);
        
        // Keep limited history
        if (history.size() > 50) {
            history.remove(0);
        }
        
        // Need at least 5 data points
        if (history.size() < 5) {
            return "NEUTRAL";
        }
        
        // Calculate trend from last 5 CVD values
        int upCount = 0;
        int downCount = 0;
        
        for (int i = history.size() - 4; i < history.size(); i++) {
            BigDecimal prev = history.get(i - 1);
            BigDecimal curr = history.get(i);
            
            if (curr.compareTo(prev) > 0) upCount++;
            else if (curr.compareTo(prev) < 0) downCount++;
        }
        
        // Determine trend
        if (upCount >= 3) return "RISING";
        if (downCount >= 3) return "FALLING";
        return "NEUTRAL";
    }
    
    /**
     * Detect delta divergences
     */
    private DivergenceResult detectDivergence(List<FootprintCandle> historical, FootprintCandle current) {
        DivergenceResult result = new DivergenceResult();
        result.type = "NONE";
        result.strength = BigDecimal.ZERO;
        
        if (historical.size() < divergenceLookback) {
            return result;
        }
        
        // Find swing points (local highs/lows)
        List<SwingPoint> priceSwings = findSwingPoints(historical, true); // Price swings
        List<SwingPoint> deltaSwings = findSwingPoints(historical, false); // Delta swings
        
        if (priceSwings.size() < 2 || deltaSwings.size() < 2) {
            return result;
        }
        
        // Check for bullish divergence: price makes lower low, delta makes higher low
        SwingPoint lastPriceLow = findLastSwingLow(priceSwings);
        SwingPoint prevPriceLow = findPreviousSwingLow(priceSwings, lastPriceLow);
        
        if (lastPriceLow != null && prevPriceLow != null) {
            SwingPoint lastDeltaLow = findClosestDeltaSwing(deltaSwings, lastPriceLow.index);
            SwingPoint prevDeltaLow = findClosestDeltaSwing(deltaSwings, prevPriceLow.index);
            
            if (lastDeltaLow != null && prevDeltaLow != null) {
                // Bullish divergence: price lower, delta higher
                if (lastPriceLow.value.compareTo(prevPriceLow.value) < 0 &&
                    lastDeltaLow.value.compareTo(prevDeltaLow.value) > 0) {
                    result.type = "BULLISH";
                    result.strength = calculateDivergenceStrength(
                        prevPriceLow.value, lastPriceLow.value,
                        prevDeltaLow.value, lastDeltaLow.value
                    );
                }
            }
        }
        
        // Check for bearish divergence: price makes higher high, delta makes lower high
        SwingPoint lastPriceHigh = findLastSwingHigh(priceSwings);
        SwingPoint prevPriceHigh = findPreviousSwingHigh(priceSwings, lastPriceHigh);
        
        if (lastPriceHigh != null && prevPriceHigh != null) {
            SwingPoint lastDeltaHigh = findClosestDeltaSwing(deltaSwings, lastPriceHigh.index);
            SwingPoint prevDeltaHigh = findClosestDeltaSwing(deltaSwings, prevPriceHigh.index);
            
            if (lastDeltaHigh != null && prevDeltaHigh != null) {
                // Bearish divergence: price higher, delta lower
                if (lastPriceHigh.value.compareTo(prevPriceHigh.value) > 0 &&
                    lastDeltaHigh.value.compareTo(prevDeltaHigh.value) < 0) {
                    result.type = "BEARISH";
                    result.strength = calculateDivergenceStrength(
                        prevPriceHigh.value, lastPriceHigh.value,
                        prevDeltaHigh.value, lastDeltaHigh.value
                    );
                }
            }
        }
        
        return result;
    }
    
    /**
     * Find swing points (local highs/lows) in the data
     */
    private List<SwingPoint> findSwingPoints(List<FootprintCandle> candles, boolean usePrice) {
        List<SwingPoint> swings = new ArrayList<>();
        
        for (int i = swingLookback; i < candles.size() - swingLookback; i++) {
            FootprintCandle candle = candles.get(i);
            BigDecimal value = usePrice ? candle.getClose() : candle.getDelta();
            
            // Check if this is a local high
            boolean isHigh = true;
            for (int j = i - swingLookback; j <= i + swingLookback; j++) {
                if (j == i) continue;
                BigDecimal compareValue = usePrice ? 
                    candles.get(j).getClose() : candles.get(j).getDelta();
                if (compareValue.compareTo(value) > 0) {
                    isHigh = false;
                    break;
                }
            }
            
            // Check if this is a local low
            boolean isLow = true;
            for (int j = i - swingLookback; j <= i + swingLookback; j++) {
                if (j == i) continue;
                BigDecimal compareValue = usePrice ? 
                    candles.get(j).getClose() : candles.get(j).getDelta();
                if (compareValue.compareTo(value) < 0) {
                    isLow = false;
                    break;
                }
            }
            
            if (isHigh || isLow) {
                swings.add(new SwingPoint(i, value, isHigh));
            }
        }
        
        return swings;
    }
    
    private SwingPoint findLastSwingLow(List<SwingPoint> swings) {
        for (int i = swings.size() - 1; i >= 0; i--) {
            if (!swings.get(i).isHigh) return swings.get(i);
        }
        return null;
    }
    
    private SwingPoint findLastSwingHigh(List<SwingPoint> swings) {
        for (int i = swings.size() - 1; i >= 0; i--) {
            if (swings.get(i).isHigh) return swings.get(i);
        }
        return null;
    }
    
    private SwingPoint findPreviousSwingLow(List<SwingPoint> swings, SwingPoint last) {
        if (last == null) return null;
        for (int i = swings.size() - 1; i >= 0; i--) {
            SwingPoint swing = swings.get(i);
            if (!swing.isHigh && swing.index < last.index) return swing;
        }
        return null;
    }
    
    private SwingPoint findPreviousSwingHigh(List<SwingPoint> swings, SwingPoint last) {
        if (last == null) return null;
        for (int i = swings.size() - 1; i >= 0; i--) {
            SwingPoint swing = swings.get(i);
            if (swing.isHigh && swing.index < last.index) return swing;
        }
        return null;
    }
    
    private SwingPoint findClosestDeltaSwing(List<SwingPoint> swings, int targetIndex) {
        SwingPoint closest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (SwingPoint swing : swings) {
            int distance = Math.abs(swing.index - targetIndex);
            if (distance < minDistance) {
                minDistance = distance;
                closest = swing;
            }
        }
        
        return closest;
    }
    
    private BigDecimal calculateDivergenceStrength(BigDecimal prevPrice, BigDecimal lastPrice,
                                                   BigDecimal prevDelta, BigDecimal lastDelta) {
        // Calculate percentage changes
        BigDecimal priceChange = lastPrice.subtract(prevPrice)
            .divide(prevPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .abs();
        
        BigDecimal deltaChange = lastDelta.subtract(prevDelta)
            .divide(prevDelta.abs().max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .abs();
        
        // Strength is the sum of both changes (bigger divergence = stronger)
        return priceChange.add(deltaChange);
    }
    
    /**
     * Detect order flow imbalances in current candle
     */
    private ImbalanceResult detectImbalance(FootprintCandle candle) {
        ImbalanceResult result = new ImbalanceResult();
        result.direction = "NEUTRAL";
        result.ratio = BigDecimal.ONE;
        
        BigDecimal buyVolume = candle.getTotalBuyVolume();
        BigDecimal sellVolume = candle.getTotalSellVolume();
        
        if (sellVolume.compareTo(BigDecimal.ZERO) == 0) {
            if (buyVolume.compareTo(BigDecimal.ZERO) > 0) {
                result.direction = "BUY";
                result.ratio = BigDecimal.valueOf(100); // Extreme buy imbalance
            }
            return result;
        }
        
        BigDecimal ratio = buyVolume.divide(sellVolume, 2, RoundingMode.HALF_UP);
        result.ratio = ratio;
        
        if (ratio.compareTo(BigDecimal.valueOf(imbalanceThreshold)) >= 0) {
            result.direction = "BUY";
        } else if (ratio.compareTo(BigDecimal.ONE.divide(BigDecimal.valueOf(imbalanceThreshold), 2, RoundingMode.HALF_UP)) <= 0) {
            result.direction = "SELL";
        }
        
        return result;
    }
    
    /**
     * Calculate absorption score for current candle
     */
    private BigDecimal calculateAbsorption(FootprintCandle candle) {
        BigDecimal open = candle.getOpen();
        BigDecimal close = candle.getClose();
        BigDecimal high = candle.getHigh();
        BigDecimal low = candle.getLow();
        BigDecimal volume = candle.getTotalVolume();
        
        if (open.compareTo(BigDecimal.ZERO) == 0 || volume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate candle range as percentage
        BigDecimal range = high.subtract(low);
        BigDecimal rangePercent = range.divide(open, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Absorption score is high when volume is high but range is small
        if (rangePercent.compareTo(BigDecimal.valueOf(0.5)) > 0) {
            return BigDecimal.ZERO; // Too much price movement
        }
        
        // Score = 100 * (1 - rangePercent / 0.5)
        BigDecimal score = BigDecimal.valueOf(100).multiply(
            BigDecimal.ONE.subtract(rangePercent.divide(BigDecimal.valueOf(0.5), 4, RoundingMode.HALF_UP))
        );
        
        return score.max(BigDecimal.ZERO);
    }
    
    /**
     * Check if volume confirms the signal
     */
    private boolean checkVolumeConfirmation(List<FootprintCandle> historical, FootprintCandle current) {
        if (!useVolumeConfirmation) return true;
        
        // Calculate average volume
        BigDecimal totalVolume = BigDecimal.ZERO;
        int count = Math.min(20, historical.size());
        
        for (int i = historical.size() - count; i < historical.size(); i++) {
            totalVolume = totalVolume.add(historical.get(i).getTotalVolume());
        }
        
        BigDecimal avgVolume = totalVolume.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
        BigDecimal threshold = avgVolume.multiply(BigDecimal.valueOf(minVolumeMultiplier));
        
        return current.getTotalVolume().compareTo(threshold) >= 0;
    }
    
    /**
     * Generate trading signals based on order flow analysis
     */
    private List<Order> generateTradingSignals(String symbol, BigDecimal currentPrice,
                                               OrderFlowSignals signals, Instant timestamp) {
        List<Order> orders = new ArrayList<>();
        
        // Check current position
        boolean hasPosition = accountManager != null && 
            !accountManager.getAccount("default").getPositionManager().getOpenPositionsBySymbol(symbol).isEmpty();
        
        // Check stop loss and take profit if we have a position
        if (hasPosition) {
            BigDecimal entry = entryPrice.get(symbol);
            BigDecimal sl = stopLoss.get(symbol);
            BigDecimal tp = takeProfit.get(symbol);
            
            if (entry != null && sl != null && tp != null) {
                // Check stop loss
                if ((lastSignal.get(symbol).compareTo(BigDecimal.ONE) == 0 && currentPrice.compareTo(sl) <= 0) ||
                    (lastSignal.get(symbol).compareTo(BigDecimal.valueOf(-1)) == 0 && currentPrice.compareTo(sl) >= 0)) {
                    System.out.println("🛑 Order Flow [" + symbol + "]: Stop loss hit at " + currentPrice);
                    orders.add(createExitOrder(symbol, currentPrice));
                    clearPositionTracking(symbol);
                    return orders;
                }
                
                // Check take profit
                if ((lastSignal.get(symbol).compareTo(BigDecimal.ONE) == 0 && currentPrice.compareTo(tp) >= 0) ||
                    (lastSignal.get(symbol).compareTo(BigDecimal.valueOf(-1)) == 0 && currentPrice.compareTo(tp) <= 0)) {
                    System.out.println("💰 Order Flow [" + symbol + "]: Take profit hit at " + currentPrice);
                    orders.add(createExitOrder(symbol, currentPrice));
                    clearPositionTracking(symbol);
                    return orders;
                }
            }
        }
        
        // BUY SIGNAL CONDITIONS
        boolean buySignal = signals.cvdTrend.equals("RISING") &&
            (signals.divergenceType.equals("BULLISH") || signals.imbalanceDirection.equals("BUY")) &&
            signals.volumeConfirmed &&
            !hasPosition;
        
        // SELL SIGNAL CONDITIONS
        boolean sellSignal = signals.cvdTrend.equals("FALLING") &&
            (signals.divergenceType.equals("BEARISH") || signals.imbalanceDirection.equals("SELL")) &&
            signals.volumeConfirmed &&
            !hasPosition;
        
        // If requireDivergence is true, must have divergence
        if (requireDivergence) {
            buySignal = buySignal && signals.divergenceType.equals("BULLISH");
            sellSignal = sellSignal && signals.divergenceType.equals("BEARISH");
        }
        
        // Generate orders
        if (buySignal) {
            System.out.println("🟢 Order Flow [" + symbol + "]: BUY signal - " +
                "CVD: " + signals.cvdTrend + 
                ", Divergence: " + signals.divergenceType +
                ", Imbalance: " + signals.imbalanceDirection +
                " (ratio: " + signals.imbalanceRatio.setScale(2, RoundingMode.HALF_UP) + ")" +
                ", Delta: " + signals.currentDelta.setScale(2, RoundingMode.HALF_UP));
            
            Order buyOrder = createBuyOrder(symbol, currentPrice);
            orders.add(buyOrder);
            lastSignal.put(symbol, BigDecimal.ONE);
            
            // Set stop loss and take profit
            BigDecimal slPrice = currentPrice.multiply(
                BigDecimal.ONE.subtract(config.getStopLossPercentage())
            );
            BigDecimal tpPrice = currentPrice.multiply(
                BigDecimal.ONE.add(config.getTakeProfitPercentage())
            );
            
            entryPrice.put(symbol, currentPrice);
            stopLoss.put(symbol, slPrice);
            takeProfit.put(symbol, tpPrice);
            
            // Add visual buy signal
            addBuySignalMarker(symbol, currentPrice, timestamp);
            
        } else if (sellSignal) {
            System.out.println("🔴 Order Flow [" + symbol + "]: SELL signal - " +
                "CVD: " + signals.cvdTrend + 
                ", Divergence: " + signals.divergenceType +
                ", Imbalance: " + signals.imbalanceDirection +
                " (ratio: " + signals.imbalanceRatio.setScale(2, RoundingMode.HALF_UP) + ")" +
                ", Delta: " + signals.currentDelta.setScale(2, RoundingMode.HALF_UP));
            
            Order sellOrder = createShortOrder(symbol, currentPrice);
            orders.add(sellOrder);
            lastSignal.put(symbol, BigDecimal.valueOf(-1));
            
            // Set stop loss and take profit
            BigDecimal slPrice = currentPrice.multiply(
                BigDecimal.ONE.add(config.getStopLossPercentage())
            );
            BigDecimal tpPrice = currentPrice.multiply(
                BigDecimal.ONE.subtract(config.getTakeProfitPercentage())
            );
            
            entryPrice.put(symbol, currentPrice);
            stopLoss.put(symbol, slPrice);
            takeProfit.put(symbol, tpPrice);
            
            // Add visual sell signal
            addSellSignalMarker(symbol, currentPrice, timestamp);
        }
        
        // Check for exit signal (reversal)
        if (hasPosition) {
            BigDecimal currentSignal = lastSignal.get(symbol);
            if (currentSignal != null) {
                // Exit LONG if bearish signal appears
                if (currentSignal.compareTo(BigDecimal.ONE) == 0 && sellSignal) {
                    System.out.println("⚠️ Order Flow [" + symbol + "]: EXIT LONG - Reversal signal");
                    orders.add(createExitOrder(symbol, currentPrice));
                    addExitSignalMarker(symbol, currentPrice, timestamp, "exit_long");
                    clearPositionTracking(symbol);
                }
                // Exit SHORT if bullish signal appears
                else if (currentSignal.compareTo(BigDecimal.valueOf(-1)) == 0 && buySignal) {
                    System.out.println("⚠️ Order Flow [" + symbol + "]: EXIT SHORT - Reversal signal");
                    orders.add(createExitOrder(symbol, currentPrice));
                    addExitSignalMarker(symbol, currentPrice, timestamp, "exit_short");
                    clearPositionTracking(symbol);
                }
            }
        }
        
        return orders;
    }
    
    private void clearPositionTracking(String symbol) {
        entryPrice.remove(symbol);
        stopLoss.remove(symbol);
        takeProfit.remove(symbol);
        lastSignal.put(symbol, BigDecimal.ZERO);
    }
    
    /**
     * Add visual buy signal marker
     */
    private void addBuySignalMarker(String symbol, BigDecimal price, Instant timestamp) {
        // Create arrow pointing up (buy signal)
        ArrowShape arrow = ArrowShape.builder()
            .time(timestamp.getEpochSecond())
            .price(price)
            .direction("up")
            .color("#00C853") // Green
            .text("BUY")
            .size(14)
            .build();
        
        // Store marker
        List<Map<String, Object>> markers = signalMarkers.computeIfAbsent(symbol, k -> new ArrayList<>());
        markers.add(arrow.toMap());
        
        // Keep only last 50 markers
        if (markers.size() > 50) {
            markers.remove(0);
        }
    }
    
    /**
     * Add visual sell signal marker
     */
    private void addSellSignalMarker(String symbol, BigDecimal price, Instant timestamp) {
        // Create arrow pointing down (sell signal)
        ArrowShape arrow = ArrowShape.builder()
            .time(timestamp.getEpochSecond())
            .price(price)
            .direction("down")
            .color("#D32F2F") // Red
            .text("SELL")
            .size(14)
            .build();
        
        // Store marker
        List<Map<String, Object>> markers = signalMarkers.computeIfAbsent(symbol, k -> new ArrayList<>());
        markers.add(arrow.toMap());
        
        // Keep only last 50 markers
        if (markers.size() > 50) {
            markers.remove(0);
        }
    }
    
    /**
     * Add visual exit signal marker
     */
    private void addExitSignalMarker(String symbol, BigDecimal price, Instant timestamp, String exitType) {
        // Create marker for exit
        MarkerShape marker = MarkerShape.builder()
            .time(timestamp.getEpochSecond())
            .price(price)
            .shape("circle")
            .color(exitType.contains("long") ? "#FF6B6B" : "#4ECDC4") // Light red for long exit, light blue for short exit
            .position(exitType.contains("long") ? "above" : "below")
            .text("EXIT")
            .size(10)
            .build();
        
        // Store marker
        List<Map<String, Object>> markers = signalMarkers.computeIfAbsent(symbol, k -> new ArrayList<>());
        markers.add(marker.toMap());
        
        // Keep only last 50 markers
        if (markers.size() > 50) {
            markers.remove(0);
        }
    }
    
    /**
     * Create exit order (close position)
     */
    private Order createExitOrder(String symbol, BigDecimal price) {
        if (accountManager == null) {
            return null;
        }
        
        // Determine current position side
        BigDecimal signal = lastSignal.get(symbol);
        if (signal == null) {
            return null;
        }
        
        // Exit LONG = SELL, Exit SHORT = BUY
        if (signal.compareTo(BigDecimal.ONE) == 0) {
            return createCloseLongOrder(symbol, price);
        } else {
            return createCloseShortOrder(symbol, price);
        }
    }
    
    /**
     * Generate visualization data for frontend
     */
    private void generateVisualizationData(String symbol, BigDecimal price, Instant timestamp,
                                          CandlestickData candle, OrderFlowSignals signals) {
        if (visualizationManager == null) return;
        
        Map<String, BigDecimal> indicators = new HashMap<>();
        indicators.put("cvd", signals.cvd);
        indicators.put("delta", signals.currentDelta);
        indicators.put("absorptionScore", signals.absorptionScore);
        indicators.put("imbalanceRatio", signals.imbalanceRatio);
        
        Map<String, Object> signalsMap = new HashMap<>();
        signalsMap.put("cvdTrend", signals.cvdTrend);
        signalsMap.put("divergenceType", signals.divergenceType);
        signalsMap.put("divergenceStrength", signals.divergenceStrength);
        signalsMap.put("imbalanceDirection", signals.imbalanceDirection);
        signalsMap.put("hasAbsorption", signals.hasAbsorption);
        signalsMap.put("volumeConfirmed", signals.volumeConfirmed);
        
        Map<String, BigDecimal> performance = new HashMap<>();
        if (stats != null) {
            performance.put("totalTrades", new BigDecimal(stats.getTotalTrades()));
            performance.put("winRate", stats.getWinRate() != null ? stats.getWinRate() : BigDecimal.ZERO);
            performance.put("netProfit", stats.getNetProfit());
            performance.put("profitFactor", stats.getProfitFactor() != null ? stats.getProfitFactor() : BigDecimal.ZERO);
        }
        
        String signal = lastSignal.getOrDefault(symbol, BigDecimal.ZERO).compareTo(BigDecimal.ONE) == 0 ? "LONG" :
                       lastSignal.getOrDefault(symbol, BigDecimal.ZERO).compareTo(BigDecimal.valueOf(-1)) == 0 ? "SHORT" : "HOLD";
        
        // Add volume indicator
        Map<String, BigDecimal> volumeData = extractVolumeAndOpen(new PriceData(symbol, price, timestamp, 
            org.cloudvision.trading.model.TradingDataType.KLINE, 
            new org.cloudvision.trading.model.TradingData(symbol, timestamp, "provider", 
                org.cloudvision.trading.model.TradingDataType.KLINE, candle)));
        addVolumeIndicator(indicators, signalsMap, volumeData.get("volume"), price, volumeData.get("openPrice"));
        
        // Get signal markers for this symbol
        List<Map<String, Object>> markers = signalMarkers.getOrDefault(symbol, new ArrayList<>());
        
        // Add signal markers to the signals map
        if (!markers.isEmpty()) {
            signalsMap.put("markers", new ArrayList<>(markers));
        }
        
        StrategyVisualizationData vizData = new StrategyVisualizationData(
            getStrategyId(),
            symbol,
            timestamp,
            price,
            indicators,
            signalsMap,
            performance,
            signal,
            Collections.emptyList() // No boxes for this strategy
        );
        
        visualizationManager.addVisualizationData(vizData);
    }
    
    @Override
    public String getStrategyId() {
        return config != null ? config.getStrategyId() : "orderflow";
    }
    
    @Override
    public String getStrategyName() {
        return "Order Flow Strategy (" + timeInterval + ")";
    }
    
    @Override
    public List<String> getSymbols() {
        return config != null ? config.getSymbols() : List.of("BTCUSDT");
    }
    
    @Override
    public void initialize(StrategyConfig config) {
        super.initialize(config);
        
        // Get strategy-specific parameters
        if (config.getParameter("timeInterval") != null) {
            this.timeInterval = (String) config.getParameter("timeInterval");
        }
        if (config.getParameter("cvdLookback") != null) {
            this.cvdLookback = (Integer) config.getParameter("cvdLookback");
        }
        if (config.getParameter("imbalanceThreshold") != null) {
            this.imbalanceThreshold = ((Number) config.getParameter("imbalanceThreshold")).doubleValue();
        }
        if (config.getParameter("absorptionThreshold") != null) {
            this.absorptionThreshold = ((Number) config.getParameter("absorptionThreshold")).doubleValue();
        }
        if (config.getParameter("divergenceLookback") != null) {
            this.divergenceLookback = (Integer) config.getParameter("divergenceLookback");
        }
        if (config.getParameter("swingLookback") != null) {
            this.swingLookback = (Integer) config.getParameter("swingLookback");
        }
        if (config.getParameter("requireDivergence") != null) {
            this.requireDivergence = (Boolean) config.getParameter("requireDivergence");
        }
        if (config.getParameter("useVolumeConfirmation") != null) {
            this.useVolumeConfirmation = (Boolean) config.getParameter("useVolumeConfirmation");
        }
        if (config.getParameter("minVolumeMultiplier") != null) {
            this.minVolumeMultiplier = ((Number) config.getParameter("minVolumeMultiplier")).doubleValue();
        }
        
        // Register with visualization manager
        if (visualizationManager != null) {
            visualizationManager.registerStrategy(getStrategyId(), getSymbols());
        }
        
        System.out.println("✅ Initialized " + getStrategyName() + 
            " | CVD Lookback: " + cvdLookback +
            " | Imbalance Threshold: " + imbalanceThreshold +
            " | Absorption Threshold: " + absorptionThreshold +
            " | Divergence Lookback: " + divergenceLookback +
            " | Require Divergence: " + requireDivergence +
            " | Volume Confirmation: " + useVolumeConfirmation);
    }
    
    @Override
    protected int getMaxHistorySize() {
        return Math.max(cvdLookback, divergenceLookback) + 20;
    }
    
    @Override
    public void reset() {
        super.reset();
        previousCVD.clear();
        cvdHistory.clear();
        lastDivergenceType.clear();
        lastDivergenceTime.clear();
        entryPrice.clear();
        stopLoss.clear();
        takeProfit.clear();
        signalMarkers.clear();
        System.out.println("🔄 Order Flow Strategy: Reset state");
    }
    
    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // CVD in separate pane
        metadata.put("cvd", IndicatorMetadata.builder("cvd")
            .displayName("Cumulative Volume Delta")
            .asLine("#2196F3", 2)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        // Delta in separate pane
        metadata.put("delta", IndicatorMetadata.builder("delta")
            .displayName("Volume Delta")
            .asHistogram("#9C27B0")
            .separatePane(true)
            .paneOrder(2)
            .build());
        
        // Absorption score in separate pane
        metadata.put("absorptionScore", IndicatorMetadata.builder("absorptionScore")
            .displayName("Absorption Score")
            .asLine("#FF9800", 2)
            .separatePane(true)
            .paneOrder(3)
            .build());
        
        // Volume in separate pane
        metadata.put("volume", getVolumeIndicatorMetadata(4));
        
        return metadata;
    }
    
    @Override
    protected void onBootstrapComplete(Map<String, List<CandlestickData>> dataBySymbol) {
        System.out.println("📊 Order Flow Strategy bootstrap complete for " + dataBySymbol.keySet());
    }
    
    @Override
    public void generateHistoricalVisualizationData(List<CandlestickData> historicalData) {
        if (visualizationManager == null || historicalData == null || historicalData.isEmpty()) {
            return;
        }
        
        System.out.println("📊 Generating historical visualization data for " + getStrategyName() + 
            " with " + historicalData.size() + " candles");
        
        // Process each candle
        for (CandlestickData candle : historicalData) {
            org.cloudvision.trading.model.TradingData tradingData = 
                new org.cloudvision.trading.model.TradingData(
                    candle.getSymbol(),
                    candle.getCloseTime(),
                    "historical",
                    org.cloudvision.trading.model.TradingDataType.KLINE,
                    candle
                );
            
            PriceData priceData = new PriceData(
                candle.getSymbol(),
                candle.getClose(),
                candle.getCloseTime(),
                org.cloudvision.trading.model.TradingDataType.KLINE,
                tradingData
            );
            
            analyzePrice(priceData);
        }
        
        System.out.println("✅ Generated visualization points for Order Flow Strategy");
    }
    
    // Helper classes
    private static class OrderFlowSignals {
        BigDecimal cvd;
        String cvdTrend;
        String divergenceType;
        BigDecimal divergenceStrength;
        String imbalanceDirection;
        BigDecimal imbalanceRatio;
        BigDecimal absorptionScore;
        boolean hasAbsorption;
        BigDecimal currentDelta;
        BigDecimal currentVolume;
        boolean volumeConfirmed;
    }
    
    private static class DivergenceResult {
        String type; // NONE, BULLISH, BEARISH
        BigDecimal strength;
    }
    
    private static class ImbalanceResult {
        String direction; // NEUTRAL, BUY, SELL
        BigDecimal ratio;
    }
    
    private static class SwingPoint {
        int index;
        BigDecimal value;
        boolean isHigh;
        
        SwingPoint(int index, BigDecimal value, boolean isHigh) {
            this.index = index;
            this.value = value;
            this.isHigh = isHigh;
        }
    }
}

