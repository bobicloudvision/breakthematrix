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
 * Absorption Indicator
 * 
 * Identifies "absorption" zones where large volume is traded with minimal price movement.
 * This indicates strong buyers/sellers defending a price level.
 * 
 * Key concepts:
 * - Buy Absorption: High volume but price doesn't rise = sellers are present
 * - Sell Absorption: High volume but price doesn't fall = buyers are present
 * - Absorption at resistance: Potential reversal (buyers absorbing sells)
 * - Absorption at support: Potential reversal (sellers absorbing buys)
 * 
 * Signals:
 * - Absorption score > threshold: Strong absorption detected
 * - Multiple consecutive absorptions: Very strong level
 * - Absorption + price reversal = High probability trade
 */
@Component
public class AbsorptionIndicator extends AbstractIndicator {
    
    private final FootprintCandleService footprintService;
    
    @Autowired
    public AbsorptionIndicator(FootprintCandleService footprintService) {
        super(
            "absorption",
            "Order Absorption",
            "Detects absorption zones where large volume trades with minimal price movement",
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
        
        params.put("volumeThreshold", IndicatorParameter.builder("volumeThreshold")
            .displayName("Volume Threshold")
            .description("Multiplier for average volume to detect high volume (e.g., 1.5 = 150% of average)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(1.5)
            .required(false)
            .build());
        
        params.put("priceMovementMax", IndicatorParameter.builder("priceMovementMax")
            .displayName("Max Price Movement %")
            .description("Maximum price movement % for absorption (e.g., 0.5 = 0.5%)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.5)
            .required(false)
            .build());
        
        params.put("lookbackAvg", IndicatorParameter.builder("lookbackAvg")
            .displayName("Average Lookback")
            .description("Number of candles to calculate average volume")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(20)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles == null || candles.isEmpty()) {
            return Map.of("absorptionScore", BigDecimal.ZERO);
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
            return Map.of("absorptionScore", BigDecimal.ZERO);
        }
        
        double priceMovementMax = params.containsKey("priceMovementMax") ? 
            Double.parseDouble(params.get("priceMovementMax").toString()) : 0.5;
        
        BigDecimal absorptionScore = calculateAbsorptionScore(currentCandle, priceMovementMax);
        
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("absorptionScore", absorptionScore);
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles,
                                                     Map<String, Object> params,
                                                     Object previousState) {
        if (candles == null || candles.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("absorptionScore", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        params = mergeWithDefaults(params);
        String symbol = candles.get(0).getSymbol();
        String intervalStr = getStringParameter(params, "interval", "1m");
        double volumeThreshold = params.containsKey("volumeThreshold") ? 
            Double.parseDouble(params.get("volumeThreshold").toString()) : 1.5;
        double priceMovementMax = params.containsKey("priceMovementMax") ? 
            Double.parseDouble(params.get("priceMovementMax").toString()) : 0.5;
        int lookbackAvg = getIntParameter(params, "lookbackAvg", 20);
        
        TimeInterval interval;
        try {
            interval = TimeInterval.fromString(intervalStr);
        } catch (IllegalArgumentException e) {
            interval = TimeInterval.ONE_MINUTE;
        }
        
        List<FootprintCandle> footprintCandles = footprintService.getHistoricalCandles(symbol, interval, lookbackAvg + 50);
        FootprintCandle currentCandle = footprintService.getCurrentCandle(symbol, interval);
        
        if (footprintCandles.isEmpty() && currentCandle == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("values", Map.of("absorptionScore", BigDecimal.ZERO));
            result.put("state", null);
            return result;
        }
        
        List<FootprintCandle> allCandles = new ArrayList<>(footprintCandles);
        if (currentCandle != null) {
            allCandles.add(currentCandle);
        }
        
        // Calculate average volume
        BigDecimal totalVolume = BigDecimal.ZERO;
        int startIdx = Math.max(0, allCandles.size() - lookbackAvg);
        
        for (int i = startIdx; i < allCandles.size(); i++) {
            totalVolume = totalVolume.add(allCandles.get(i).getTotalVolume());
        }
        
        BigDecimal avgVolume = BigDecimal.ZERO;
        if (allCandles.size() > 0) {
            avgVolume = totalVolume.divide(
                BigDecimal.valueOf(Math.min(lookbackAvg, allCandles.size())), 
                4, 
                RoundingMode.HALF_UP
            );
        }
        
        BigDecimal volumeThresholdValue = avgVolume.multiply(BigDecimal.valueOf(volumeThreshold));
        
        // Detect absorption events
        List<Map<String, Object>> absorptionEvents = new ArrayList<>();
        List<Map<String, Object>> absorptionPoints = new ArrayList<>();
        
        for (FootprintCandle candle : allCandles) {
            BigDecimal absorptionScore = calculateAbsorptionScore(candle, priceMovementMax);
            
            Map<String, Object> point = new HashMap<>();
            point.put("time", candle.getOpenTime());
            point.put("absorptionScore", absorptionScore);
            absorptionPoints.add(point);
            
            // Check if this is a significant absorption event
            if (candle.getTotalVolume().compareTo(volumeThresholdValue) > 0 && 
                absorptionScore.compareTo(BigDecimal.valueOf(50)) > 0) {
                
                Map<String, Object> event = new HashMap<>();
                event.put("time", candle.getOpenTime());
                event.put("price", candle.getClose());
                event.put("volume", candle.getTotalVolume());
                event.put("absorptionScore", absorptionScore);
                event.put("delta", candle.getDelta());
                
                // Determine absorption direction
                String direction = "neutral";
                if (candle.getDelta().compareTo(BigDecimal.ZERO) > 0) {
                    direction = "buy"; // Buyers absorbing
                } else if (candle.getDelta().compareTo(BigDecimal.ZERO) < 0) {
                    direction = "sell"; // Sellers absorbing
                }
                event.put("direction", direction);
                
                absorptionEvents.add(event);
            }
        }
        
        BigDecimal currentAbsorptionScore = allCandles.isEmpty() ? 
            BigDecimal.ZERO : calculateAbsorptionScore(allCandles.get(allCandles.size() - 1), priceMovementMax);
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("absorptionScore", currentAbsorptionScore);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("dataPoints", absorptionPoints);
        result.put("absorptionEvents", absorptionEvents);
        result.put("avgVolume", avgVolume);
        result.put("state", null);
        
        return result;
    }
    
    private BigDecimal calculateAbsorptionScore(FootprintCandle candle, double priceMovementMaxPercent) {
        BigDecimal open = candle.getOpen();
        BigDecimal close = candle.getClose();
        BigDecimal volume = candle.getTotalVolume();
        
        if (open.compareTo(BigDecimal.ZERO) == 0 || volume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate price movement percentage
        BigDecimal priceChange = close.subtract(open).abs();
        BigDecimal priceMovementPercent = priceChange
            .divide(open, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Calculate candle range as percentage of open
        BigDecimal range = candle.getHigh().subtract(candle.getLow());
        BigDecimal rangePercent = range
            .divide(open, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Score is higher when:
        // 1. Price movement is small
        // 2. Volume is high (relative to candle range)
        
        BigDecimal maxMovement = BigDecimal.valueOf(priceMovementMaxPercent);
        
        // Movement score: 100 when no movement, decreases as movement increases
        BigDecimal movementScore;
        if (rangePercent.compareTo(maxMovement) <= 0) {
            movementScore = BigDecimal.valueOf(100).subtract(
                rangePercent.divide(maxMovement, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
            );
        } else {
            movementScore = BigDecimal.ZERO;
        }
        
        // Volume concentration: how much volume vs price movement
        // Higher score when lots of volume with little price movement
        BigDecimal volumePerPriceMove;
        if (rangePercent.compareTo(BigDecimal.ZERO) > 0) {
            volumePerPriceMove = volume.divide(rangePercent, 2, RoundingMode.HALF_UP);
        } else {
            volumePerPriceMove = volume; // All volume with no price movement = maximum absorption
        }
        
        // Normalize volume per price move (this is simplified, could be enhanced)
        BigDecimal volumeScore = volumePerPriceMove.compareTo(BigDecimal.valueOf(100)) > 0 ?
            BigDecimal.valueOf(100) : volumePerPriceMove;
        
        // Combined absorption score (weighted average)
        BigDecimal absorptionScore = movementScore
            .multiply(BigDecimal.valueOf(0.6))
            .add(volumeScore.multiply(BigDecimal.valueOf(0.4)));
        
        return absorptionScore.setScale(2, RoundingMode.HALF_UP);
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("absorptionScore", IndicatorMetadata.builder("absorptionScore")
            .displayName("Absorption Score")
            .asHistogram("#9C27B0")
            .addConfig("priceFormat", Map.of("type", "volume"))
            .addConfig("priceScaleId", "absorption")
            .separatePane(true)
            .paneOrder(5)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        return getIntParameter(params, "lookbackAvg", 20);
    }
}

