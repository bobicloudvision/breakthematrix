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
 * Cumulative Volume Delta (CVD) Indicator
 * 
 * Tracks the cumulative difference between buying and selling volume over time.
 * Rising CVD = accumulation (buying pressure)
 * Falling CVD = distribution (selling pressure)
 * 
 * Key signals:
 * - CVD making higher highs while price makes lower lows = bullish divergence
 * - CVD making lower lows while price makes higher highs = bearish divergence
 */
@Component
public class CumulativeVolumeDeltaIndicator extends AbstractIndicator {
    
    private final FootprintCandleService footprintService;
    
    @Autowired
    public CumulativeVolumeDeltaIndicator(FootprintCandleService footprintService) {
        super(
            "cvd",
            "Cumulative Volume Delta",
            "Tracks cumulative buying vs selling pressure. Divergences signal potential reversals.",
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
        
        params.put("lookback", IndicatorParameter.builder("lookback")
            .displayName("Lookback Period")
            .description("Number of candles to look back for calculations")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(100)
            .required(false)
            .build());
        
        params.put("showDivergences", IndicatorParameter.builder("showDivergences")
            .displayName("Show Divergences")
            .description("Highlight price-CVD divergences")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            return Map.of("cvd", BigDecimal.ZERO);
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        int lookback = getIntParameter(params, "lookback", 100);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        // Get historical footprint candles
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, lookback);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            return Map.of("cvd", BigDecimal.ZERO);
        }
        
        // Calculate cumulative delta
        BigDecimal cumulativeDelta = BigDecimal.ZERO;
        for (FootprintCandle candle : footprintCandles) {
            cumulativeDelta = cumulativeDelta.add(candle.getDelta());
        }
        
        // Add current candle delta
        if (currentCandle != null) {
            cumulativeDelta = cumulativeDelta.add(currentCandle.getDelta());
        }
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("cvd", cumulativeDelta);
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                     Map<String, Object> params,
                                                     Object previousState) {
        if (candles == null || candles.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("cvd", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        boolean showDivergences = getBooleanParameter(params, "showDivergences", true);
        int lookback = getIntParameter(params, "lookback", 100);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        // Get footprint data
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, lookback);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("cvd", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        // Combine historical and current
        List<FootprintCandle> allCandles = new ArrayList<>(footprintCandles);
        if (currentCandle != null) {
            allCandles.add(currentCandle);
        }
        
        // Calculate CVD for each point
        List<Map<String, Object>> dataPoints = new ArrayList<>();
        BigDecimal cumulativeDelta = BigDecimal.ZERO;
        
        int startIdx = Math.max(0, allCandles.size() - lookback);
        for (int i = startIdx; i < allCandles.size(); i++) {
            FootprintCandle candle = allCandles.get(i);
            cumulativeDelta = cumulativeDelta.add(candle.getDelta());
            
            Map<String, Object> point = new HashMap<>();
            point.put("time", candle.getOpenTime());
            point.put("cvd", cumulativeDelta);
            point.put("delta", candle.getDelta());
            dataPoints.add(point);
        }
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("cvd", cumulativeDelta);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("dataPoints", dataPoints);
        result.put("state", cumulativeDelta);
        
        // Detect divergences if enabled
        if (showDivergences && dataPoints.size() >= 20) {
            List<Map<String, Object>> divergences = detectDivergences(candles, dataPoints);
            if (!divergences.isEmpty()) {
                result.put("divergences", divergences);
            }
        }
        
        return result;
    }
    
    private List<Map<String, Object>> detectDivergences(List<CandlestickData> priceCandles, 
                                                         List<Map<String, Object>> cvdPoints) {
        List<Map<String, Object>> divergences = new ArrayList<>();
        
        if (priceCandles.size() < 20 || cvdPoints.size() < 20) {
            return divergences;
        }
        
        // Look for divergences in the last 20 candles
        int lookback = Math.min(20, priceCandles.size());
        
        // Find recent swing highs/lows in price
        List<CandlestickData> recentCandles = priceCandles.subList(
            priceCandles.size() - lookback, 
            priceCandles.size()
        );
        
        // Simple divergence detection: compare last 2 swing points
        // Bullish divergence: price lower low, CVD higher low
        // Bearish divergence: price higher high, CVD lower high
        
        // This is a simplified version - you can enhance with more sophisticated swing detection
        
        return divergences;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("cvd", IndicatorMetadata.builder("cvd")
            .displayName("CVD")
            .asLine("#2962FF", 2)
            .addConfig("priceFormat", Map.of("type", "volume"))
            .addConfig("priceScaleId", "cvd")
            .separatePane(true)
            .paneOrder(2)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1;
    }
}

