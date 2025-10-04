package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.FootprintCandle;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.FootprintCandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Delta Divergence Indicator
 * 
 * Identifies divergences between price movement and volume delta.
 * These divergences signal potential reversals or weakening trends.
 * 
 * Divergence types:
 * - Bullish: Price makes lower low, but delta makes higher low (accumulation)
 * - Bearish: Price makes higher high, but delta makes lower high (distribution)
 * - Hidden Bullish: Price makes higher low, delta makes lower low (continuation)
 * - Hidden Bearish: Price makes lower high, delta makes higher high (continuation)
 */
@Component
public class DeltaDivergenceIndicator extends AbstractIndicator {
    
    private final FootprintCandleService footprintService;
    
    @Autowired
    public DeltaDivergenceIndicator(FootprintCandleService footprintService) {
        super(
            "delta_div",
            "Delta Divergence",
            "Detects bullish/bearish divergences between price and volume delta",
            IndicatorCategory.ORDER_FLOW
        );
        this.footprintService = footprintService;
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("interval", IndicatorParameter.builder("interval")
            .displayName("Time Interval")
            .description("Time interval for footprint candles")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("1m")
            .required(false)
            .build());
        
        params.put("swingLookback", IndicatorParameter.builder("swingLookback")
            .displayName("Swing Lookback")
            .description("Number of candles to look back for swing detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .required(false)
            .build());
        
        params.put("divLookback", IndicatorParameter.builder("divLookback")
            .displayName("Divergence Lookback")
            .description("Maximum bars to look back for divergence patterns")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            return Map.of("delta", BigDecimal.ZERO, "divergenceSignal", BigDecimal.ZERO);
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (currentCandle == null) {
            return Map.of("delta", BigDecimal.ZERO, "divergenceSignal", BigDecimal.ZERO);
        }
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("delta", currentCandle.getDelta());
        result.put("divergenceSignal", BigDecimal.ZERO);
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                     Map<String, Object> params,
                                                     Object previousState) {
        if (candles == null || candles.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("delta", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        int swingLookback = getIntParameter(params, "swingLookback", 5);
        int divLookback = getIntParameter(params, "divLookback", 50);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, divLookback);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("delta", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        List<FootprintCandle> allCandles = new ArrayList<>(footprintCandles);
        if (currentCandle != null) {
            allCandles.add(currentCandle);
        }
        
        // Calculate delta for each candle
        List<Map<String, Object>> deltaPoints = new ArrayList<>();
        for (FootprintCandle candle : allCandles) {
            Map<String, Object> point = new HashMap<>();
            point.put("time", candle.getOpenTime());
            point.put("delta", candle.getDelta());
            point.put("price", candle.getClose());
            deltaPoints.add(point);
        }
        
        // Detect swing points
        List<SwingPoint> priceSwings = detectSwings(candles, swingLookback);
        List<SwingPoint> deltaSwings = detectDeltaSwings(allCandles, swingLookback);
        
        // Find divergences
        List<Map<String, Object>> divergences = findDivergences(
            priceSwings, deltaSwings, divLookback
        );
        
        BigDecimal currentDelta = allCandles.isEmpty() ? 
            BigDecimal.ZERO : allCandles.get(allCandles.size() - 1).getDelta();
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("delta", currentDelta);
        values.put("divergenceSignal", divergences.isEmpty() ? BigDecimal.ZERO : BigDecimal.ONE);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("dataPoints", deltaPoints);
        result.put("divergences", divergences);
        result.put("priceSwings", priceSwings);
        result.put("deltaSwings", deltaSwings);
        result.put("state", null);
        
        return result;
    }
    
    private List<SwingPoint> detectSwings(List<CandlestickData> candles, int lookback) {
        List<SwingPoint> swings = new ArrayList<>();
        
        if (candles.size() < lookback * 2 + 1) {
            return swings;
        }
        
        for (int i = lookback; i < candles.size() - lookback; i++) {
            CandlestickData current = candles.get(i);
            boolean isHigh = true;
            boolean isLow = true;
            
            // Check if it's a swing high
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (candles.get(j).getHigh().compareTo(current.getHigh()) > 0) {
                    isHigh = false;
                    break;
                }
            }
            
            // Check if it's a swing low
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (candles.get(j).getLow().compareTo(current.getLow()) < 0) {
                    isLow = false;
                    break;
                }
            }
            
            if (isHigh) {
                swings.add(new SwingPoint(i, current.getHigh(), "high", current.getOpenTime().toEpochMilli()));
            } else if (isLow) {
                swings.add(new SwingPoint(i, current.getLow(), "low", current.getOpenTime().toEpochMilli()));
            }
        }
        
        return swings;
    }
    
    private List<SwingPoint> detectDeltaSwings(List<FootprintCandle> candles, int lookback) {
        List<SwingPoint> swings = new ArrayList<>();
        
        if (candles.size() < lookback * 2 + 1) {
            return swings;
        }
        
        for (int i = lookback; i < candles.size() - lookback; i++) {
            FootprintCandle current = candles.get(i);
            boolean isHigh = true;
            boolean isLow = true;
            
            // Check if it's a swing high in delta
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (candles.get(j).getDelta().compareTo(current.getDelta()) > 0) {
                    isHigh = false;
                    break;
                }
            }
            
            // Check if it's a swing low in delta
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (candles.get(j).getDelta().compareTo(current.getDelta()) < 0) {
                    isLow = false;
                    break;
                }
            }
            
            if (isHigh) {
                swings.add(new SwingPoint(i, current.getDelta(), "high", current.getOpenTime().toEpochMilli()));
            } else if (isLow) {
                swings.add(new SwingPoint(i, current.getDelta(), "low", current.getOpenTime().toEpochMilli()));
            }
        }
        
        return swings;
    }
    
    private List<Map<String, Object>> findDivergences(List<SwingPoint> priceSwings, 
                                                       List<SwingPoint> deltaSwings,
                                                       int maxLookback) {
        List<Map<String, Object>> divergences = new ArrayList<>();
        
        if (priceSwings.size() < 2 || deltaSwings.size() < 2) {
            return divergences;
        }
        
        // Look for bullish divergence: price lower low, delta higher low
        for (int i = 1; i < priceSwings.size(); i++) {
            SwingPoint currentPrice = priceSwings.get(i);
            if (!currentPrice.type.equals("low")) continue;
            
            for (int j = i - 1; j >= 0 && j >= i - maxLookback / 10; j--) {
                SwingPoint prevPrice = priceSwings.get(j);
                if (!prevPrice.type.equals("low")) continue;
                
                // Price made lower low
                if (currentPrice.value.compareTo(prevPrice.value) < 0) {
                    // Find corresponding delta swings
                    SwingPoint currentDelta = findClosestSwing(deltaSwings, currentPrice.index, "low");
                    SwingPoint prevDelta = findClosestSwing(deltaSwings, prevPrice.index, "low");
                    
                    if (currentDelta != null && prevDelta != null) {
                        // Delta made higher low = bullish divergence
                        if (currentDelta.value.compareTo(prevDelta.value) > 0) {
                            Map<String, Object> div = new HashMap<>();
                            div.put("type", "bullish");
                            div.put("priceStart", prevPrice.value);
                            div.put("priceEnd", currentPrice.value);
                            div.put("deltaStart", prevDelta.value);
                            div.put("deltaEnd", currentDelta.value);
                            div.put("time", currentPrice.time);
                            divergences.add(div);
                        }
                    }
                }
            }
        }
        
        // Look for bearish divergence: price higher high, delta lower high
        for (int i = 1; i < priceSwings.size(); i++) {
            SwingPoint currentPrice = priceSwings.get(i);
            if (!currentPrice.type.equals("high")) continue;
            
            for (int j = i - 1; j >= 0 && j >= i - maxLookback / 10; j--) {
                SwingPoint prevPrice = priceSwings.get(j);
                if (!prevPrice.type.equals("high")) continue;
                
                // Price made higher high
                if (currentPrice.value.compareTo(prevPrice.value) > 0) {
                    // Find corresponding delta swings
                    SwingPoint currentDelta = findClosestSwing(deltaSwings, currentPrice.index, "high");
                    SwingPoint prevDelta = findClosestSwing(deltaSwings, prevPrice.index, "high");
                    
                    if (currentDelta != null && prevDelta != null) {
                        // Delta made lower high = bearish divergence
                        if (currentDelta.value.compareTo(prevDelta.value) < 0) {
                            Map<String, Object> div = new HashMap<>();
                            div.put("type", "bearish");
                            div.put("priceStart", prevPrice.value);
                            div.put("priceEnd", currentPrice.value);
                            div.put("deltaStart", prevDelta.value);
                            div.put("deltaEnd", currentDelta.value);
                            div.put("time", currentPrice.time);
                            divergences.add(div);
                        }
                    }
                }
            }
        }
        
        return divergences;
    }
    
    private SwingPoint findClosestSwing(List<SwingPoint> swings, int targetIndex, String type) {
        SwingPoint closest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (SwingPoint swing : swings) {
            if (!swing.type.equals(type)) continue;
            
            int distance = Math.abs(swing.index - targetIndex);
            if (distance < minDistance) {
                minDistance = distance;
                closest = swing;
            }
        }
        
        return closest;
    }
    
    public static class SwingPoint {
        private final int index;
        private final BigDecimal value;
        private final String type; // "high" or "low"
        private final long time;
        
        public SwingPoint(int index, BigDecimal value, String type, long time) {
            this.index = index;
            this.value = value;
            this.type = type;
            this.time = time;
        }
        
        public int getIndex() {
            return index;
        }
        
        public BigDecimal getValue() {
            return value;
        }
        
        public String getType() {
            return type;
        }
        
        public long getTime() {
            return time;
        }
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("delta", IndicatorMetadata.builder("delta")
            .displayName("Delta")
            .asHistogram("#00BCD4")
            .addConfig("priceFormat", Map.of("type", "volume"))
            .addConfig("priceScaleId", "delta")
            .separatePane(true)
            .paneOrder(4)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int swingLookback = getIntParameter(params, "swingLookback", 5);
        return swingLookback * 2 + 1;
    }
}

