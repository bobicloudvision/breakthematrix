package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Trend Navigator Indicator
 * 
 * K-Nearest Neighbors (KNN) based trend prediction indicator
 * Uses machine learning concepts to predict market trends
 * 
 * Based on the Pine Script indicator by Zeiierman
 * Licensed under CC BY-NC-SA 4.0
 * 
 * Features:
 * - KNN-based moving average calculation
 * - Trend prediction using historical pattern matching
 * - Multiple price and target value options
 * - Configurable smoothing and sensitivity
 * 
 * Trading Signals (Crossover Strategy):
 * 
 * - 🟢 BULLISH: When KNN Classifier crosses above Average KNN (knn_crossover_avg)
 *   Visual: Green circle with "BUY" label below the indicator
 * 
 * - 🔴 BEARISH: When KNN Classifier crosses below Average KNN (knn_crossunder_avg)
 *   Visual: Red circle with "SELL" label above the indicator
 * 
 * These crossover signals are the most reliable and should be used as primary entry/exit points.
 */
@Component
public class AITrendNavigatorIndicator extends AbstractIndicator {
    
    public AITrendNavigatorIndicator() {
        super(
            "ai_trend_navigator",
            "AI Trend Navigator",
            "K-Nearest Neighbors based trend prediction indicator that uses machine learning to analyze price patterns. Generates clear BUY/SELL signals when the KNN Classifier crosses the Average KNN line",
            IndicatorCategory.TREND
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("priceValue", IndicatorParameter.builder("priceValue")
            .displayName("Price Value")
            .description("Method of price computation for KNN input")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("hl2")
            .required(false)
            .build());
        
        params.put("priceValueLength", IndicatorParameter.builder("priceValueLength")
            .displayName("Price Value Length")
            .description("Smoothing period for the price value")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(2)
            .maxValue(200)
            .required(false)
            .build());
        
        params.put("targetValue", IndicatorParameter.builder("targetValue")
            .displayName("Target Value")
            .description("Target to evaluate for KNN distance calculation")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Price Action")
            .required(false)
            .build());
        
        params.put("targetValueLength", IndicatorParameter.builder("targetValueLength")
            .displayName("Target Value Length")
            .description("Smoothing period for the target value")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(2)
            .maxValue(200)
            .required(false)
            .build());
        
        params.put("numberOfClosestValues", IndicatorParameter.builder("numberOfClosestValues")
            .displayName("Number of Closest Values (k)")
            .description("Number of nearest neighbors to consider (k parameter)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(2)
            .maxValue(200)
            .required(false)
            .build());
        
        params.put("smoothingPeriod", IndicatorParameter.builder("smoothingPeriod")
            .displayName("Smoothing Period")
            .description("Period for moving average applied to KNN classifier")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .minValue(2)
            .maxValue(500)
            .required(false)
            .build());
        
        // Color parameters
        params.put("upColor", IndicatorParameter.builder("upColor")
            .displayName("Up Color")
            .description("Color for bullish KNN line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#00FF00")
            .required(false)
            .build());
        
        params.put("downColor", IndicatorParameter.builder("downColor")
            .displayName("Down Color")
            .description("Color for bearish KNN line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#FF0000")
            .required(false)
            .build());
        
        params.put("neutralColor", IndicatorParameter.builder("neutralColor")
            .displayName("Neutral Color")
            .description("Color for neutral KNN line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#FFA500")
            .required(false)
            .build());
        
        params.put("avgColor", IndicatorParameter.builder("avgColor")
            .displayName("Average Color")
            .description("Color for average KNN line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#008080")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int minRequired = getMinRequiredCandles(params);
        if (candles == null || candles.size() < minRequired) {
            return createEmptyResult();
        }
        
        // Get parameters
        String priceValue = getStringParameter(params, "priceValue", "hl2");
        int priceValueLength = getIntParameter(params, "priceValueLength", 5);
        String targetValue = getStringParameter(params, "targetValue", "Price Action");
        int targetValueLength = getIntParameter(params, "targetValueLength", 5);
        int numberOfClosestValues = getIntParameter(params, "numberOfClosestValues", 3);
        int smoothingPeriod = getIntParameter(params, "smoothingPeriod", 50);
        int windowSize = Math.max(numberOfClosestValues, 30);
        
        // Calculate value_in based on priceValue selection
        List<BigDecimal> valueIn = calculateValueInput(candles, priceValue, priceValueLength);
        
        // Calculate target_in based on targetValue selection
        List<BigDecimal> targetIn = calculateTargetInput(candles, targetValue, targetValueLength);
        
        if (valueIn.isEmpty() || targetIn.isEmpty()) {
            return createEmptyResult();
        }
        
        // Calculate KNN MA
        BigDecimal knnMA = meanOfKClosest(valueIn, targetIn.get(targetIn.size() - 1), 
                                         numberOfClosestValues, windowSize);
        
        // Calculate knnMA_ (weighted moving average of knnMA)
        // In Pine Script: ta.wma(knnMA, 5)
        // For single point calculation, we approximate
        BigDecimal knnMA_ = knnMA; // Simplified - in full implementation, maintain history
        
        // Calculate MAknn_ (running moving average)
        BigDecimal MAknn_ = knnMA; // Simplified - would need full history for accurate RMA
        
        // Calculate KNN prediction
        BigDecimal close = candles.get(candles.size() - 1).getClose();
        BigDecimal price = knnMA.add(close).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        
        // KNN prediction (simplified - would need more history for full implementation)
        BigDecimal knnPrediction = BigDecimal.ZERO;
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("knnClassifier", knnMA_);
        result.put("avgKnnClassifier", MAknn_);
        result.put("prediction", knnPrediction);
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                   Map<String, Object> params,
                                                   Object previousState) {
        params = mergeWithDefaults(params);
        
        int minRequired = getMinRequiredCandles(params);
        if (candles == null || candles.size() < minRequired) {
            return Map.of(
                "values", createEmptyResult(),
                "state", new KNNState()
            );
        }
        
        // Get parameters
        String priceValue = getStringParameter(params, "priceValue", "hl2");
        int priceValueLength = getIntParameter(params, "priceValueLength", 5);
        String targetValue = getStringParameter(params, "targetValue", "Price Action");
        int targetValueLength = getIntParameter(params, "targetValueLength", 5);
        int numberOfClosestValues = getIntParameter(params, "numberOfClosestValues", 3);
        int smoothingPeriod = getIntParameter(params, "smoothingPeriod", 50);
        int windowSize = Math.max(numberOfClosestValues, 30);
        
        // Initialize or get state
        KNNState state = (previousState instanceof KNNState) ? 
            (KNNState) previousState : new KNNState();
        
        // Calculate value_in for all candles
        List<BigDecimal> valueIn = calculateValueInput(candles, priceValue, priceValueLength);
        
        // Calculate target_in for all candles
        List<BigDecimal> targetIn = calculateTargetInput(candles, targetValue, targetValueLength);
        
        if (valueIn.isEmpty() || targetIn.isEmpty()) {
            return Map.of(
                "values", createEmptyResult(),
                "state", state
            );
        }
        
        // Calculate KNN MA for current candle
        BigDecimal currentTarget = targetIn.get(targetIn.size() - 1);
        BigDecimal knnMA = meanOfKClosest(valueIn, currentTarget, numberOfClosestValues, windowSize);
        
        // Update KNN MA history
        state.knnMAHistory.add(knnMA);
        if (state.knnMAHistory.size() > smoothingPeriod + 10) {
            state.knnMAHistory.remove(0);
        }
        
        // Calculate knnMA_ (weighted moving average)
        BigDecimal knnMA_;
        if (state.knnMAHistory.size() >= 5) {
            knnMA_ = TechnicalIndicators.calculateWMA(state.knnMAHistory, Math.min(5, state.knnMAHistory.size()));
        } else {
            knnMA_ = knnMA;
        }
        
        // Calculate MAknn_ (running moving average)
        BigDecimal MAknn_;
        if (state.knnMAHistory.size() >= smoothingPeriod) {
            MAknn_ = TechnicalIndicators.calculateRMA(state.knnMAHistory, smoothingPeriod);
        } else {
            MAknn_ = knnMA_;
        }
        
        // Calculate KNN prediction
        BigDecimal close = candles.get(candles.size() - 1).getClose();
        BigDecimal price = knnMA.add(close).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        
        // Update price history for KNN classifier
        state.priceHistory.add(price);
        if (state.priceHistory.size() > 20) {
            state.priceHistory.remove(0);
        }
        
        // Update smoothed values for KNN prediction
        state.closeHistory.add(knnMA);
        state.openHistory.add(knnMA_);
        if (state.closeHistory.size() > smoothingPeriod + 10) {
            state.closeHistory.remove(0);
            state.openHistory.remove(0);
        }
        
        // Calculate c and o for KNN prediction
        BigDecimal c = state.closeHistory.size() >= smoothingPeriod ?
            TechnicalIndicators.calculateRMA(state.closeHistory, smoothingPeriod) : knnMA;
        BigDecimal o = state.openHistory.size() >= smoothingPeriod ?
            TechnicalIndicators.calculateRMA(state.openHistory, smoothingPeriod) : knnMA_;
        
        // KNN prediction
        BigDecimal knnPredictionRaw = knnClassifier(state.priceHistory, state.closeHistory, 
                                                   state.openHistory, price, smoothingPeriod);
        
        // Smooth prediction
        state.predictionHistory.add(knnPredictionRaw);
        if (state.predictionHistory.size() > 5) {
            state.predictionHistory.remove(0);
        }
        
        BigDecimal knnPrediction = state.predictionHistory.size() >= 3 ?
            TechnicalIndicators.calculateWMA(state.predictionHistory, Math.min(3, state.predictionHistory.size())) :
            knnPredictionRaw;
        
        // Determine color based on trend
        String color;
        BigDecimal prevKnnMA = state.prevKnnMA;
        if (prevKnnMA != null) {
            if (knnMA_.compareTo(prevKnnMA) > 0) {
                color = getStringParameter(params, "upColor", "#00FF00");
            } else if (knnMA_.compareTo(prevKnnMA) < 0) {
                color = getStringParameter(params, "downColor", "#FF0000");
            } else {
                color = getStringParameter(params, "neutralColor", "#FFA500");
            }
        } else {
            color = getStringParameter(params, "neutralColor", "#FFA500");
        }
        
        // Detect trading signals
        Map<String, Object> signals = new HashMap<>();
        signals.put("knnColor", color);
        signals.put("trendDirection", knnMA_.compareTo(MAknn_) > 0 ? "up" : "down");
        
        // List to store marker shapes
        List<Map<String, Object>> markers = new ArrayList<>();
        CandlestickData currentCandle = candles.get(candles.size() - 1);
        
        // Signal: KNN crossed over Average KNN (BULLISH)
        if (state.prevKnnMA != null && state.prevMAknn != null) {
            boolean crossoverMA = state.prevKnnMA.compareTo(state.prevMAknn) <= 0 && 
                                 knnMA_.compareTo(MAknn_) > 0;
            if (crossoverMA) {
                signals.put("signal", "BULLISH");
                signals.put("signalType", "knn_crossover_avg");
                signals.put("signalStrength", "strong");
                
                // Calculate exact crossover price using linear interpolation
                BigDecimal crossPrice = calculateCrossoverPrice(
                    state.prevKnnMA, knnMA_,
                    state.prevMAknn, MAknn_
                );
                
                // Add bullish marker at exact crossover point
                MarkerShape marker = MarkerShape.builder()
                    .time(currentCandle.getCloseTime().getEpochSecond())
                    .price(crossPrice)
                    .shape("circle")
                    .color("#00FF00")
                    .position("inPlace")
                    .text("BUY")
                    .size(12)
                    .build();
                markers.add(marker.toMap());
            }
            
            // Signal: KNN crossed under Average KNN (BEARISH)
            boolean crossunderMA = state.prevKnnMA.compareTo(state.prevMAknn) >= 0 && 
                                  knnMA_.compareTo(MAknn_) < 0;
            if (crossunderMA) {
                signals.put("signal", "BEARISH");
                signals.put("signalType", "knn_crossunder_avg");
                signals.put("signalStrength", "strong");
                
                // Calculate exact crossover price using linear interpolation
                BigDecimal crossPrice = calculateCrossoverPrice(
                    state.prevKnnMA, knnMA_,
                    state.prevMAknn, MAknn_
                );
                
                // Add bearish marker at exact crossover point
                MarkerShape marker = MarkerShape.builder()
                    .time(currentCandle.getCloseTime().getEpochSecond())
                    .price(crossPrice)
                    .shape("circle")
                    .color("#FF0000")
                    .position("inPlace")
                    .text("SELL")
                    .size(12)
                    .build();
                markers.add(marker.toMap());
            }
        }
        
        // Update state with previous values
        state.prevKnnMA = knnMA_;
        state.prevMAknn = MAknn_;
        state.currentColor = color;
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("knnClassifier", knnMA_);
        values.put("avgKnnClassifier", MAknn_);
        values.put("prediction", knnPrediction);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", state);
        result.put("signals", signals);
        
        // Add markers if any were created
        if (!markers.isEmpty()) {
            result.put("markers", markers);
        }
        
        return result;
    }
    
    /**
     * Calculate the exact crossover price using linear interpolation
     * 
     * Given two lines crossing between two points:
     * Line 1: from prevLine1 to currentLine1
     * Line 2: from prevLine2 to currentLine2
     * 
     * Find the Y value (price) where they intersect
     */
    private BigDecimal calculateCrossoverPrice(BigDecimal prevLine1, BigDecimal currentLine1,
                                              BigDecimal prevLine2, BigDecimal currentLine2) {
        try {
            // Calculate slopes
            BigDecimal line1Change = currentLine1.subtract(prevLine1);
            BigDecimal line2Change = currentLine2.subtract(prevLine2);
            
            // If lines are parallel (same slope), return the average of current values
            if (line1Change.subtract(line2Change).abs().compareTo(new BigDecimal("0.0001")) < 0) {
                return currentLine1.add(currentLine2)
                    .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
            }
            
            // Calculate intersection point ratio (where between 0 and 1 does the cross occur)
            BigDecimal numerator = prevLine2.subtract(prevLine1);
            BigDecimal denominator = line1Change.subtract(line2Change);
            
            BigDecimal ratio = numerator.divide(denominator, 8, RoundingMode.HALF_UP);
            
            // Clamp ratio between 0 and 1
            if (ratio.compareTo(BigDecimal.ZERO) < 0) {
                ratio = BigDecimal.ZERO;
            } else if (ratio.compareTo(BigDecimal.ONE) > 0) {
                ratio = BigDecimal.ONE;
            }
            
            // Calculate intersection price: prevLine1 + (ratio * line1Change)
            BigDecimal crossPrice = prevLine1.add(ratio.multiply(line1Change));
            
            return crossPrice;
        } catch (Exception e) {
            // Fallback: return average of current values
            return currentLine1.add(currentLine2)
                .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Calculate the mean of k closest values using KNN algorithm
     */
    private BigDecimal meanOfKClosest(List<BigDecimal> values, BigDecimal target, 
                                     int k, int windowSize) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int dataSize = values.size();
        int actualWindowSize = Math.min(windowSize, dataSize - 1);
        
        if (actualWindowSize < 1) {
            return values.get(dataSize - 1);
        }
        
        // Arrays to store k closest distances and values
        BigDecimal[] closestDistances = new BigDecimal[k];
        BigDecimal[] closestValues = new BigDecimal[k];
        Arrays.fill(closestDistances, new BigDecimal("1e10"));
        Arrays.fill(closestValues, BigDecimal.ZERO);
        
        // Search through window for k nearest neighbors
        for (int i = 1; i <= actualWindowSize; i++) {
            int index = dataSize - 1 - i;
            if (index < 0) break;
            
            BigDecimal value = values.get(index);
            BigDecimal distance = target.subtract(value).abs();
            
            // Find the maximum distance in closestDistances
            int maxDistIndex = 0;
            BigDecimal maxDistValue = closestDistances[0];
            for (int j = 1; j < k; j++) {
                if (closestDistances[j].compareTo(maxDistValue) > 0) {
                    maxDistIndex = j;
                    maxDistValue = closestDistances[j];
                }
            }
            
            // Replace if current distance is smaller
            if (distance.compareTo(maxDistValue) < 0) {
                closestDistances[maxDistIndex] = distance;
                closestValues[maxDistIndex] = value;
            }
        }
        
        // Calculate mean of closest values
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : closestValues) {
            sum = sum.add(value);
        }
        
        return sum.divide(new BigDecimal(k), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * KNN Classifier to predict trend direction
     */
    private BigDecimal knnClassifier(List<BigDecimal> priceHistory, 
                                    List<BigDecimal> closeHistory,
                                    List<BigDecimal> openHistory,
                                    BigDecimal currentPrice,
                                    int smoothingPeriod) {
        if (priceHistory.size() < 10) {
            return BigDecimal.ZERO;
        }
        
        int posCount = 0;
        int negCount = 0;
        BigDecimal minDistance = new BigDecimal("1e10");
        int nearestIndex = 0;
        
        int historySize = priceHistory.size();
        int lookback = Math.min(10, historySize - 1);
        
        // Find nearest neighbors
        for (int j = 1; j <= lookback; j++) {
            int index = historySize - 1 - j;
            if (index < 0) break;
            
            BigDecimal historicalPrice = priceHistory.get(index);
            
            // Calculate Euclidean distance
            BigDecimal diff = historicalPrice.subtract(currentPrice);
            BigDecimal distance = diff.multiply(diff).sqrt(new java.math.MathContext(8));
            
            if (distance.compareTo(minDistance) < 0) {
                minDistance = distance;
                nearestIndex = index;
                
                // Check if it was positive or negative trend at that point
                if (nearestIndex < closeHistory.size() && nearestIndex < openHistory.size()) {
                    BigDecimal c = closeHistory.get(nearestIndex);
                    BigDecimal o = openHistory.get(nearestIndex);
                    
                    boolean neg = c.compareTo(o) > 0;
                    boolean pos = c.compareTo(o) < 0;
                    
                    if (pos) posCount++;
                    if (neg) negCount++;
                }
            }
        }
        
        // Return prediction: 1 for positive, -1 for negative
        return posCount > negCount ? BigDecimal.ONE : BigDecimal.ONE.negate();
    }
    
    /**
     * Calculate value input based on selected method
     */
    private List<BigDecimal> calculateValueInput(List<CandlestickData> candles, 
                                                 String method, int length) {
        List<BigDecimal> prices = new ArrayList<>();
        
        for (CandlestickData candle : candles) {
            BigDecimal price = switch (method.toLowerCase()) {
                case "vwap" -> {
                    // For VWAP, we need volume data
                    List<BigDecimal> hl2Prices = candles.stream()
                        .map(c -> c.getHigh().add(c.getLow())
                            .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP))
                        .collect(Collectors.toList());
                    List<BigDecimal> volumes = candles.stream()
                        .map(CandlestickData::getVolume)
                        .collect(Collectors.toList());
                    yield TechnicalIndicators.calculateVWAP(hl2Prices, volumes);
                }
                case "sma" -> candle.getClose();
                case "wma" -> candle.getClose();
                case "ema" -> candle.getClose();
                case "hma" -> candle.getClose();
                default -> candle.getHigh().add(candle.getLow())
                    .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP); // hl2
            };
            prices.add(price);
        }
        
        // Apply smoothing based on method
        if (prices.size() < length) {
            return prices;
        }
        
        List<BigDecimal> smoothed = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            if (i < length - 1) {
                smoothed.add(prices.get(i));
                continue;
            }
            
            List<BigDecimal> window = prices.subList(i - length + 1, i + 1);
            BigDecimal value = switch (method.toLowerCase()) {
                case "sma", "hl2", "vwap" -> TechnicalIndicators.calculateSMA(window, length);
                case "wma" -> TechnicalIndicators.calculateWMA(window, length);
                case "ema" -> TechnicalIndicators.calculateEMA(window, length);
                case "hma" -> TechnicalIndicators.calculateHMA(window, length);
                default -> TechnicalIndicators.calculateSMA(window, length);
            };
            smoothed.add(value);
        }
        
        return smoothed;
    }
    
    /**
     * Calculate target input based on selected method
     */
    private List<BigDecimal> calculateTargetInput(List<CandlestickData> candles, 
                                                  String method, int length) {
        List<BigDecimal> values = new ArrayList<>();
        
        for (CandlestickData candle : candles) {
            values.add(candle.getClose());
        }
        
        if (values.size() < length) {
            return values;
        }
        
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (i < length - 1) {
                result.add(values.get(i));
                continue;
            }
            
            List<BigDecimal> window = values.subList(i - length + 1, i + 1);
            
            BigDecimal value = switch (method.toLowerCase()) {
                case "price action" -> TechnicalIndicators.calculateRMA(window, length);
                case "vwap" -> {
                    List<BigDecimal> hl2Prices = new ArrayList<>();
                    List<BigDecimal> volumes = new ArrayList<>();
                    for (int j = i - length + 1; j <= i; j++) {
                        CandlestickData c = candles.get(j);
                        hl2Prices.add(c.getHigh().add(c.getLow())
                            .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP));
                        volumes.add(c.getVolume());
                    }
                    yield TechnicalIndicators.calculateVWAP(hl2Prices, volumes);
                }
                case "volatility" -> {
                    List<BigDecimal> highs = new ArrayList<>();
                    List<BigDecimal> lows = new ArrayList<>();
                    List<BigDecimal> closes = new ArrayList<>();
                    for (int j = i - length; j <= i; j++) {
                        if (j < 0) continue;
                        CandlestickData c = candles.get(j);
                        highs.add(c.getHigh());
                        lows.add(c.getLow());
                        closes.add(c.getClose());
                    }
                    yield TechnicalIndicators.calculateATR(highs, lows, closes, 14);
                }
                case "sma" -> TechnicalIndicators.calculateSMA(window, length);
                case "wma" -> TechnicalIndicators.calculateWMA(window, length);
                case "ema" -> TechnicalIndicators.calculateEMA(window, length);
                case "hma" -> TechnicalIndicators.calculateHMA(window, length);
                default -> TechnicalIndicators.calculateRMA(window, length);
            };
            
            result.add(value);
        }
        
        return result;
    }
    
    /**
     * State class for progressive calculation
     */
    private static class KNNState {
        List<BigDecimal> knnMAHistory = new ArrayList<>();
        List<BigDecimal> priceHistory = new ArrayList<>();
        List<BigDecimal> closeHistory = new ArrayList<>();
        List<BigDecimal> openHistory = new ArrayList<>();
        List<BigDecimal> predictionHistory = new ArrayList<>();
        BigDecimal prevKnnMA = null;
        BigDecimal prevMAknn = null;
        String currentColor = null;
    }
    
    private Map<String, BigDecimal> createEmptyResult() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("knnClassifier", BigDecimal.ZERO);
        result.put("avgKnnClassifier", BigDecimal.ZERO);
        result.put("prediction", BigDecimal.ZERO);
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        String upColor = getStringParameter(params, "upColor", "#00FF00");
        String downColor = getStringParameter(params, "downColor", "#FF0000");
        String avgColor = getStringParameter(params, "avgColor", "#008080");
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // KNN Classifier Line
        metadata.put("knnClassifier", IndicatorMetadata.builder("knnClassifier")
            .displayName("KNN Classifier")
            .asLine(upColor, 2)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        // Average KNN Classifier Line
        metadata.put("avgKnnClassifier", IndicatorMetadata.builder("avgKnnClassifier")
            .displayName("Avg KNN Classifier")
            .asLine(avgColor, 2)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        // Prediction (displayed as separate indicator in its own pane)
        metadata.put("prediction", IndicatorMetadata.builder("prediction")
            .displayName("KNN Prediction")
            .asLine("#9C27B0", 1)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        // Buy Signal Marker (Crossover Average KNN)
        metadata.put("buySignal", IndicatorMetadata.builder("buySignal")
            .displayName("Buy Signal")
            .seriesType("marker")
            .separatePane(false)
            .paneOrder(0)
            .addConfig("shape", "circle")
            .addConfig("color", upColor)
            .addConfig("position", "inPlace")
            .addConfig("size", 12)
            .addConfig("text", "BUY")
            .addConfig("description", "Positioned at exact crossover point")
            .build());
        
        // Sell Signal Marker (Crossunder Average KNN)
        metadata.put("sellSignal", IndicatorMetadata.builder("sellSignal")
            .displayName("Sell Signal")
            .seriesType("marker")
            .separatePane(false)
            .paneOrder(0)
            .addConfig("shape", "circle")
            .addConfig("color", downColor)
            .addConfig("position", "inPlace")
            .addConfig("size", 12)
            .addConfig("text", "SELL")
            .addConfig("description", "Positioned at exact crossover point")
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int smoothingPeriod = getIntParameter(params, "smoothingPeriod", 50);
        int numberOfClosestValues = getIntParameter(params, "numberOfClosestValues", 3);
        int windowSize = Math.max(numberOfClosestValues, 30);
        
        // Need enough candles for smoothing + window + some buffer
        return smoothingPeriod + windowSize + 20;
    }
}

