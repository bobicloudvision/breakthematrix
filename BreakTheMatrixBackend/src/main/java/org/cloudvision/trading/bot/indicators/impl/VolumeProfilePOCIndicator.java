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
import java.util.*;

/**
 * Volume Profile Point of Control (POC) Indicator
 * 
 * Tracks the price level with the highest traded volume (Point of Control).
 * POC acts as a strong support/resistance level and indicates where most trading activity occurred.
 * 
 * Key concepts:
 * - POC: Price level with maximum volume
 * - Value Area High (VAH): Upper boundary of 70% volume area
 * - Value Area Low (VAL): Lower boundary of 70% volume area
 * - Price above POC: Bullish control
 * - Price below POC: Bearish control
 */
@Component
public class VolumeProfilePOCIndicator extends AbstractIndicator {
    
    private final FootprintCandleService footprintService;
    
    @Autowired
    public VolumeProfilePOCIndicator(FootprintCandleService footprintService) {
        super(
            "vpoc",
            "Volume Profile POC",
            "Shows Point of Control (highest volume price level) and Value Area boundaries",
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
            .description("Number of candles to include in volume profile")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .required(false)
            .build());
        
        params.put("valueAreaPercent", IndicatorParameter.builder("valueAreaPercent")
            .displayName("Value Area Percentage")
            .description("Percentage of volume to include in value area (typically 70%)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(70.0)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            return Map.of(
                "poc", BigDecimal.ZERO,
                "vah", BigDecimal.ZERO,
                "val", BigDecimal.ZERO
            );
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        int lookback = getIntParameter(params, "lookback", 50);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, lookback);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            return Map.of(
                "poc", BigDecimal.ZERO,
                "vah", BigDecimal.ZERO,
                "val", BigDecimal.ZERO
            );
        }
        
        List<FootprintCandle> allCandles = new ArrayList<>(footprintCandles);
        if (currentCandle != null) {
            allCandles.add(currentCandle);
        }
        
        // Use only recent candles based on lookback
        int startIdx = Math.max(0, allCandles.size() - lookback);
        List<FootprintCandle> recentCandles = allCandles.subList(startIdx, allCandles.size());
        
        VolumeProfile profile = calculateVolumeProfile(recentCandles, params);
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("poc", profile.poc);
        result.put("vah", profile.vah);
        result.put("val", profile.val);
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                     Map<String, Object> params,
                                                     Object previousState) {
        if (candles == null || candles.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("poc", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        int lookback = getIntParameter(params, "lookback", 50);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, lookback);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("poc", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        List<FootprintCandle> allCandles = new ArrayList<>(footprintCandles);
        if (currentCandle != null) {
            allCandles.add(currentCandle);
        }
        
        // Calculate POC for each period
        List<Map<String, Object>> pocPoints = new ArrayList<>();
        
        for (int i = lookback; i <= allCandles.size(); i++) {
            int startIdx = Math.max(0, i - lookback);
            List<FootprintCandle> windowCandles = allCandles.subList(startIdx, i);
            
            VolumeProfile profile = calculateVolumeProfile(windowCandles, params);
            
            Map<String, Object> point = new HashMap<>();
            point.put("time", windowCandles.get(windowCandles.size() - 1).getOpenTime());
            point.put("poc", profile.poc);
            point.put("vah", profile.vah);
            point.put("val", profile.val);
            pocPoints.add(point);
        }
        
        VolumeProfile currentProfile = calculateVolumeProfile(
            allCandles.subList(Math.max(0, allCandles.size() - lookback), allCandles.size()),
            params
        );
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("poc", currentProfile.poc);
        values.put("vah", currentProfile.vah);
        values.put("val", currentProfile.val);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("dataPoints", pocPoints);
        result.put("volumeProfile", currentProfile.priceVolumes);
        result.put("state", null);
        
        return result;
    }
    
    private VolumeProfile calculateVolumeProfile(List<FootprintCandle> candles, Map<String, Object> params) {
        double valueAreaPercent = params.containsKey("valueAreaPercent") ? 
            Double.parseDouble(params.get("valueAreaPercent").toString()) : 70.0;
        
        // Aggregate volume by price level
        Map<BigDecimal, BigDecimal> priceVolumes = new TreeMap<>();
        
        for (FootprintCandle candle : candles) {
            Map<BigDecimal, FootprintCandle.PriceLevelVolume> volumeProfile = candle.getVolumeProfile();
            
            for (Map.Entry<BigDecimal, FootprintCandle.PriceLevelVolume> entry : volumeProfile.entrySet()) {
                BigDecimal price = entry.getKey();
                BigDecimal volume = entry.getValue().getTotalVolume();
                
                priceVolumes.merge(price, volume, BigDecimal::add);
            }
        }
        
        if (priceVolumes.isEmpty()) {
            return new VolumeProfile(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, priceVolumes);
        }
        
        // Find POC (price with max volume)
        BigDecimal poc = BigDecimal.ZERO;
        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        
        for (Map.Entry<BigDecimal, BigDecimal> entry : priceVolumes.entrySet()) {
            totalVolume = totalVolume.add(entry.getValue());
            
            if (entry.getValue().compareTo(maxVolume) > 0) {
                maxVolume = entry.getValue();
                poc = entry.getKey();
            }
        }
        
        // Calculate Value Area (70% of volume around POC)
        BigDecimal targetVolume = totalVolume.multiply(BigDecimal.valueOf(valueAreaPercent / 100.0));
        BigDecimal valueAreaVolume = priceVolumes.get(poc);
        
        BigDecimal vah = poc;
        BigDecimal val = poc;
        
        List<BigDecimal> prices = new ArrayList<>(priceVolumes.keySet());
        int pocIdx = prices.indexOf(poc);
        
        int upperIdx = pocIdx;
        int lowerIdx = pocIdx;
        
        // Expand value area until we reach target volume
        while (valueAreaVolume.compareTo(targetVolume) < 0 && (upperIdx < prices.size() - 1 || lowerIdx > 0)) {
            BigDecimal upperVolume = upperIdx < prices.size() - 1 ? priceVolumes.get(prices.get(upperIdx + 1)) : BigDecimal.ZERO;
            BigDecimal lowerVolume = lowerIdx > 0 ? priceVolumes.get(prices.get(lowerIdx - 1)) : BigDecimal.ZERO;
            
            if (upperVolume.compareTo(lowerVolume) >= 0 && upperIdx < prices.size() - 1) {
                upperIdx++;
                valueAreaVolume = valueAreaVolume.add(upperVolume);
                vah = prices.get(upperIdx);
            } else if (lowerIdx > 0) {
                lowerIdx--;
                valueAreaVolume = valueAreaVolume.add(lowerVolume);
                val = prices.get(lowerIdx);
            } else {
                break;
            }
        }
        
        return new VolumeProfile(poc, vah, val, priceVolumes);
    }
    
    private static class VolumeProfile {
        final BigDecimal poc;
        final BigDecimal vah;
        final BigDecimal val;
        final Map<BigDecimal, BigDecimal> priceVolumes;
        
        VolumeProfile(BigDecimal poc, BigDecimal vah, BigDecimal val, Map<BigDecimal, BigDecimal> priceVolumes) {
            this.poc = poc;
            this.vah = vah;
            this.val = val;
            this.priceVolumes = priceVolumes;
        }
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("poc", IndicatorMetadata.builder("poc")
            .displayName("POC")
            .asLine("#FF6D00", 2)
            .addConfig("priceFormat", Map.of("type", "price"))
            .separatePane(false) // Overlay on main chart
            .paneOrder(0)
            .build());
        
        metadata.put("vah", IndicatorMetadata.builder("vah")
            .displayName("Value Area High")
            .asLine("#FFA726", 1)
            .addConfig("lineStyle", 2) // Dashed
            .separatePane(false) // Overlay on main chart
            .paneOrder(0)
            .build());
        
        metadata.put("val", IndicatorMetadata.builder("val")
            .displayName("Value Area Low")
            .asLine("#FFA726", 1)
            .addConfig("lineStyle", 2) // Dashed
            .separatePane(false) // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        return getIntParameter(params, "lookback", 50);
    }
}

