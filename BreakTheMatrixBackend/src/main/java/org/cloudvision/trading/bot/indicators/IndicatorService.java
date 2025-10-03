package org.cloudvision.trading.bot.indicators;

import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.service.CandlestickHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing and calculating technical indicators
 * 
 * Provides:
 * - Registry of all available indicators
 * - Calculation of indicator values on demand
 * - Integration with candlestick history service
 * - Support for both strategies and REST API
 */
@Service
public class IndicatorService {
    
    private final Map<String, Indicator> indicators = new HashMap<>();
    private final CandlestickHistoryService historyService;
    
    @Autowired
    public IndicatorService(List<Indicator> indicatorList, 
                          CandlestickHistoryService historyService) {
        this.historyService = historyService;
        
        // Register all indicators
        for (Indicator indicator : indicatorList) {
            indicators.put(indicator.getId(), indicator);
            System.out.println("📊 Registered indicator: " + indicator.getName() + " (" + indicator.getId() + ")");
        }
        
        System.out.println("✅ IndicatorService initialized with " + indicators.size() + " indicators");
    }
    
    /**
     * Get all available indicators
     */
    public List<Indicator> getAllIndicators() {
        return new ArrayList<>(indicators.values());
    }
    
    /**
     * Get indicators by category
     */
    public List<Indicator> getIndicatorsByCategory(Indicator.IndicatorCategory category) {
        return indicators.values().stream()
            .filter(ind -> ind.getCategory() == category)
            .collect(Collectors.toList());
    }
    
    /**
     * Get specific indicator by ID
     */
    public Indicator getIndicator(String indicatorId) {
        Indicator indicator = indicators.get(indicatorId);
        if (indicator == null) {
            throw new IllegalArgumentException("Indicator not found: " + indicatorId);
        }
        return indicator;
    }
    
    /**
     * Check if indicator exists
     */
    public boolean hasIndicator(String indicatorId) {
        return indicators.containsKey(indicatorId);
    }
    
    /**
     * Calculate indicator values for a symbol
     * 
     * @param indicatorId The indicator to calculate
     * @param provider Data provider (e.g., "Binance")
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @param interval Time interval (e.g., "1m", "5m", "1h")
     * @param params Indicator parameters
     * @return Map of indicator output names to values
     */
    public Map<String, BigDecimal> calculate(String indicatorId, String provider, 
                                            String symbol, String interval,
                                            Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        
        // Get required historical data
        int requiredCandles = indicator.getMinRequiredCandles(params);
        List<CandlestickData> candles = historyService.getLastNCandlesticks(
            provider, symbol, interval, requiredCandles
        );
        
        if (candles.isEmpty()) {
            throw new IllegalStateException("No historical data available for " + symbol);
        }
        
        // Calculate indicator
        return indicator.calculate(candles, params);
    }
    
    /**
     * Calculate indicator for provided candlestick data (used by strategies)
     */
    public Map<String, BigDecimal> calculate(String indicatorId, 
                                            List<CandlestickData> candles,
                                            Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        return indicator.calculate(candles, params);
    }
    
    /**
     * Calculate multiple data points for charting (historical values)
     * 
     * @param indicatorId The indicator to calculate
     * @param provider Data provider
     * @param symbol Trading symbol
     * @param interval Time interval
     * @param params Indicator parameters
     * @param count Number of data points to return
     * @return List of calculated values with timestamps (includes boxes if indicator supports them)
     */
    public List<IndicatorDataPoint> calculateHistorical(String indicatorId, String provider,
                                                        String symbol, String interval,
                                                        Map<String, Object> params, int count) {
        Indicator indicator = getIndicator(indicatorId);
        int requiredCandles = indicator.getMinRequiredCandles(params);
        
        // Get enough candles for progressive calculation
        List<CandlestickData> allCandles = historyService.getLastNCandlesticks(
            provider, symbol, interval, count + requiredCandles
        );
        
        if (allCandles.size() < requiredCandles) {
            throw new IllegalStateException("Insufficient data for " + symbol);
        }
        
        List<IndicatorDataPoint> dataPoints = new ArrayList<>();
        Object previousState = null;
        
        // Calculate indicator progressively for each candle
        for (int i = requiredCandles; i <= allCandles.size(); i++) {
            List<CandlestickData> subset = allCandles.subList(0, i);
            CandlestickData currentCandle = allCandles.get(i - 1);
            
            // Use progressive calculation (all indicators support it via interface)
            Map<String, Object> progressiveResult = indicator.calculateProgressive(
                subset, params, previousState
            );
            
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> values = (Map<String, BigDecimal>) progressiveResult.get("values");
            previousState = progressiveResult.get("state");
            
            // Extract additional data (boxes, orderBlocks, shapes, etc.)
            Map<String, Object> additionalData = new HashMap<>();
            for (Map.Entry<String, Object> entry : progressiveResult.entrySet()) {
                if (!entry.getKey().equals("values") && !entry.getKey().equals("state")) {
                    additionalData.put(entry.getKey(), entry.getValue());
                }
            }
            
            dataPoints.add(new IndicatorDataPoint(
                currentCandle.getCloseTime(),
                values,
                currentCandle,
                additionalData
            ));
        }
        
        // Return only the requested count (most recent)
        if (dataPoints.size() > count) {
            return dataPoints.subList(dataPoints.size() - count, dataPoints.size());
        }
        
        return dataPoints;
    }
    
    /**
     * Get visualization metadata for an indicator
     */
    public Map<String, IndicatorMetadata> getVisualizationMetadata(String indicatorId,
                                                                   Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        return indicator.getVisualizationMetadata(params);
    }
    
    /**
     * Data point returned by calculateHistorical
     */
    public static class IndicatorDataPoint {
        private final Instant timestamp;
        private final Map<String, BigDecimal> values;
        private final CandlestickData candle;
        private final Map<String, Object> additionalData; // For boxes, orderBlocks, etc.
        
        public IndicatorDataPoint(Instant timestamp, Map<String, BigDecimal> values, 
                                CandlestickData candle) {
            this.timestamp = timestamp;
            this.values = values;
            this.candle = candle;
            this.additionalData = new HashMap<>();
        }
        
        public IndicatorDataPoint(Instant timestamp, Map<String, BigDecimal> values, 
                                CandlestickData candle, Map<String, Object> additionalData) {
            this.timestamp = timestamp;
            this.values = values;
            this.candle = candle;
            this.additionalData = additionalData != null ? additionalData : new HashMap<>();
        }
        
        public Instant getTimestamp() { return timestamp; }
        public Map<String, BigDecimal> getValues() { return values; }
        public CandlestickData getCandle() { return candle; }
        public Map<String, Object> getAdditionalData() { return additionalData; }
    }
}

