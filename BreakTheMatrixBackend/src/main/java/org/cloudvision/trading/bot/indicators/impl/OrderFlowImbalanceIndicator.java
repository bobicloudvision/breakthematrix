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
 * Order Flow Imbalance Indicator
 * 
 * Identifies significant imbalances between buying and selling volume at specific price levels.
 * Large imbalances indicate strong directional pressure and potential continuation.
 * 
 * Key signals:
 * - Imbalance > threshold: Strong buying/selling pressure
 * - Consecutive imbalances: Trend strength confirmation
 * - Imbalance at support/resistance: High probability trade setup
 */
@Component
public class OrderFlowImbalanceIndicator extends AbstractIndicator {
    
    private final FootprintCandleService footprintService;
    
    @Autowired
    public OrderFlowImbalanceIndicator(FootprintCandleService footprintService) {
        super(
            "ofi",
            "Order Flow Imbalance",
            "Detects significant buy/sell volume imbalances at price levels",
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
        
        params.put("threshold", IndicatorParameter.builder("threshold")
            .displayName("Imbalance Threshold")
            .description("Minimum ratio for significant imbalance (e.g., 2.0 = 2:1 ratio)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(2.0)
            .required(false)
            .build());
        
        params.put("minVolume", IndicatorParameter.builder("minVolume")
            .displayName("Minimum Volume")
            .description("Minimum volume required to consider imbalance significant")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(10.0)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            return Map.of(
                "buyImbalance", BigDecimal.ZERO,
                "sellImbalance", BigDecimal.ZERO,
                "imbalanceRatio", BigDecimal.ZERO
            );
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
            return Map.of(
                "buyImbalance", BigDecimal.ZERO,
                "sellImbalance", BigDecimal.ZERO,
                "imbalanceRatio", BigDecimal.ZERO
            );
        }
        
        // Calculate imbalance metrics
        BigDecimal buyVol = currentCandle.getTotalBuyVolume();
        BigDecimal sellVol = currentCandle.getTotalSellVolume();
        BigDecimal totalVol = buyVol.add(sellVol);
        
        BigDecimal imbalanceRatio = BigDecimal.ZERO;
        if (totalVol.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal buyPercent = buyVol.divide(totalVol, 4, RoundingMode.HALF_UP);
            BigDecimal sellPercent = sellVol.divide(totalVol, 4, RoundingMode.HALF_UP);
            
            // Ratio > 1 = buy imbalance, < 1 = sell imbalance
            if (sellPercent.compareTo(BigDecimal.ZERO) > 0) {
                imbalanceRatio = buyPercent.divide(sellPercent, 4, RoundingMode.HALF_UP);
            }
        }
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("buyImbalance", buyVol);
        result.put("sellImbalance", sellVol);
        result.put("imbalanceRatio", imbalanceRatio);
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                     Map<String, Object> params,
                                                     Object previousState) {
        if (candles == null || candles.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("imbalance", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        double thresholdParam = params.containsKey("threshold") ? 
            Double.parseDouble(params.get("threshold").toString()) : 2.0;
        double minVolumeParam = params.containsKey("minVolume") ? 
            Double.parseDouble(params.get("minVolume").toString()) : 10.0;
        
        BigDecimal threshold = BigDecimal.valueOf(thresholdParam);
        BigDecimal minVolume = BigDecimal.valueOf(minVolumeParam);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        // Get footprint data
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, 100);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("imbalance", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        List<FootprintCandle> allCandles = new ArrayList<>(footprintCandles);
        if (currentCandle != null) {
            allCandles.add(currentCandle);
        }
        
        // Calculate imbalances for each candle
        List<Map<String, Object>> imbalancePoints = new ArrayList<>();
        List<Map<String, Object>> significantImbalances = new ArrayList<>();
        
        for (FootprintCandle candle : allCandles) {
            BigDecimal buyVol = candle.getTotalBuyVolume();
            BigDecimal sellVol = candle.getTotalSellVolume();
            BigDecimal totalVol = buyVol.add(sellVol);
            
            BigDecimal imbalanceValue = BigDecimal.ZERO;
            String direction = "neutral";
            
            // Calculate ratio if we have volume
            if (totalVol.compareTo(BigDecimal.ZERO) > 0 && sellVol.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = buyVol.divide(sellVol, 4, RoundingMode.HALF_UP);
                
                // Check if volume meets minimum threshold for significant imbalances
                boolean meetsVolumeThreshold = totalVol.compareTo(minVolume) >= 0;
                
                if (ratio.compareTo(threshold) >= 0) {
                    // Buy imbalance detected
                    imbalanceValue = ratio;
                    direction = "buy";
                    
                    // Only add to significant if meets volume threshold
                    if (meetsVolumeThreshold) {
                        Map<String, Object> imbalance = new HashMap<>();
                        imbalance.put("time", candle.getOpenTime());
                        imbalance.put("price", candle.getClose());
                        imbalance.put("ratio", ratio);
                        imbalance.put("direction", "buy");
                        imbalance.put("buyVolume", buyVol);
                        imbalance.put("sellVolume", sellVol);
                        significantImbalances.add(imbalance);
                    }
                    
                } else if (ratio.compareTo(BigDecimal.ONE.divide(threshold, 4, RoundingMode.HALF_UP)) <= 0) {
                    // Sell imbalance detected
                    imbalanceValue = ratio;
                    direction = "sell";
                    
                    // Only add to significant if meets volume threshold
                    if (meetsVolumeThreshold) {
                        Map<String, Object> imbalance = new HashMap<>();
                        imbalance.put("time", candle.getOpenTime());
                        imbalance.put("price", candle.getClose());
                        imbalance.put("ratio", ratio);
                        imbalance.put("direction", "sell");
                        imbalance.put("buyVolume", buyVol);
                        imbalance.put("sellVolume", sellVol);
                        significantImbalances.add(imbalance);
                    }
                }
            }
            
            // Always add data point for every candle
            Map<String, Object> point = new HashMap<>();
            point.put("time", candle.getOpenTime());
            point.put("imbalanceValue", imbalanceValue);
            point.put("direction", direction);
            point.put("buyVolume", buyVol);
            point.put("sellVolume", sellVol);
            imbalancePoints.add(point);
        }
        
        // Get current imbalance value
        BigDecimal currentImbalanceValue = BigDecimal.ZERO;
        if (!imbalancePoints.isEmpty()) {
            Map<String, Object> lastPoint = imbalancePoints.get(imbalancePoints.size() - 1);
            currentImbalanceValue = (BigDecimal) lastPoint.get("imbalanceValue");
        }
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("imbalance", currentImbalanceValue);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("dataPoints", imbalancePoints);
        result.put("significantImbalances", significantImbalances);
        result.put("state", null);
        
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("imbalance", IndicatorMetadata.builder("imbalance")
            .displayName("Order Flow Imbalance")
            .asHistogram("#FF6D00")
            .addConfig("priceFormat", Map.of("type", "volume"))
            .addConfig("priceScaleId", "ofi")
            .separatePane(true)
            .paneOrder(3)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1;
    }
}

