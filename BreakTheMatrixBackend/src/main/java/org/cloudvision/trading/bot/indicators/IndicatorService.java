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
//            System.out.println("📊 Registered indicator: " + indicator.getName() + " (" + indicator.getId() + ")");
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
    
    // ============================================================
    // Event-Driven API Methods
    // ============================================================
    
    /**
     * Initialize an indicator with historical data and return initial state
     * 
     * This is the first step in using an indicator. Call this method to:
     * - Warm up the indicator with historical candles
     * - Get initial state object that will be used in subsequent updates
     * 
     * @param indicatorId The indicator to initialize
     * @param provider Data provider (e.g., "Binance")
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @param interval Time interval (e.g., "1m", "5m", "1h")
     * @param params Indicator parameters
     * @return IndicatorState object containing initial state and metadata
     */
    public IndicatorState initializeIndicator(String indicatorId, String provider,
                                             String symbol, String interval,
                                             Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        
        // Get required historical data for warm-up
        int requiredCandles = indicator.getMinRequiredCandles(params);
        List<CandlestickData> candles = historyService.getLastNCandlesticks(
            provider, symbol, interval, Math.max(requiredCandles, 50)
        );
        
        // Initialize indicator with historical candles
        Object state = indicator.onInit(candles, params);
        
        return new IndicatorState(indicatorId, provider, symbol, interval, params, state, candles.size());
    }
    
    /**
     * Initialize an indicator with provided candlestick data
     * 
     * @param indicatorId The indicator to initialize
     * @param candles Historical candlestick data for initialization
     * @param params Indicator parameters
     * @return IndicatorState object containing initial state and metadata
     */
    public IndicatorState initializeIndicator(String indicatorId,
                                             List<CandlestickData> candles,
                                             Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("Candles cannot be null or empty for initialization");
        }
        
        // Initialize indicator with provided candles
        Object state = indicator.onInit(candles, params);
        
        String symbol = candles.get(0).getSymbol();
        String provider = candles.get(0).getProvider();
        String interval = candles.get(0).getInterval();
        
        return new IndicatorState(indicatorId, provider, symbol, interval, params, state, candles.size());
    }
    
    /**
     * Update indicator with a new candle
     * 
     * Call this method when a new candle is completed. It will:
     * - Process the candle using the current state
     * - Return updated values and new state
     * - Include additional data like colors, boxes, shapes, etc.
     * 
     * @param indicatorState Current indicator state from initialization or previous update
     * @param candle New candle to process
     * @return IndicatorResult containing updated values, new state, and additional data
     */
    public IndicatorResult updateWithCandle(IndicatorState indicatorState, CandlestickData candle) {
        if (indicatorState == null) {
            throw new IllegalArgumentException("IndicatorState cannot be null. Call initializeIndicator first.");
        }
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        Indicator indicator = getIndicator(indicatorState.getIndicatorId());
        
        // Process the new candle with current state
        Map<String, Object> result = indicator.onNewCandle(candle, indicatorState.getParams(), indicatorState.getState());
        
        // Extract values and new state
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        Object newState = result.get("state");
        
        // Extract additional data (boxes, colors, shapes, etc.)
        Map<String, Object> additionalData = extractAdditionalData(result);
        
        // Update state for next iteration
        indicatorState.setState(newState);
        indicatorState.incrementCandleCount();
        
        return new IndicatorResult(
            candle.getOpenTime(),
            values != null ? values : Map.of(),
            candle,
            additionalData
        );
    }
    
    /**
     * Update indicator with a price tick
     * 
     * Call this method for real-time price updates within the current candle.
     * Most indicators don't need tick updates and will return empty values.
     * 
     * @param indicatorState Current indicator state
     * @param price Current tick price
     * @return IndicatorResult containing updated values (usually empty for tick updates)
     */
    public IndicatorResult updateWithTick(IndicatorState indicatorState, BigDecimal price) {
        if (indicatorState == null) {
            throw new IllegalArgumentException("IndicatorState cannot be null. Call initializeIndicator first.");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        
        Indicator indicator = getIndicator(indicatorState.getIndicatorId());
        
        // Process the tick with current state
        Map<String, Object> result = indicator.onNewTick(price, indicatorState.getParams(), indicatorState.getState());
        
        // Extract values and new state
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        Object newState = result.get("state");
        
        // Extract additional data
        Map<String, Object> additionalData = extractAdditionalData(result);
        
        // Update state (though usually unchanged for ticks)
        if (newState != null) {
            indicatorState.setState(newState);
        }
        
        return new IndicatorResult(
            Instant.now(),
            values != null ? values : Map.of(),
            null,  // No candle for tick updates
            additionalData
        );
    }
    
    // ============================================================
    // Helper Methods
    // ============================================================
    
    /**
     * Extract additional data from indicator result (excluding values and state)
     * This includes: boxes, orderBlocks, shapes, color, etc.
     */
    private Map<String, Object> extractAdditionalData(Map<String, Object> result) {
        Map<String, Object> additionalData = new HashMap<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String key = entry.getKey();
            // Skip internal fields
            if (!key.equals("values") && !key.equals("state")) {
                additionalData.put(key, entry.getValue());
            }
        }
        return additionalData;
    }
    
    /**
     * Get visualization metadata for an indicator
     */
    public Map<String, IndicatorMetadata> getVisualizationMetadata(String indicatorId,
                                                                   Map<String, Object> params) {
        Indicator indicator = getIndicator(indicatorId);
        return indicator.getVisualizationMetadata(params);
    }
    
    // ============================================================
    // Data Classes
    // ============================================================
    
    /**
     * Represents the state of an indicator instance
     * 
     * This object tracks:
     * - Indicator configuration (id, params)
     * - Trading context (symbol, provider, interval)
     * - Internal state (for stateful indicators)
     * - Metadata (candle count processed)
     * 
     * Used with initializeIndicator, updateWithCandle, and updateWithTick methods.
     */
    public static class IndicatorState {
        private final String indicatorId;
        private final String provider;
        private final String symbol;
        private final String interval;
        private final Map<String, Object> params;
        private Object state;
        private int candleCount;
        
        public IndicatorState(String indicatorId, String provider, String symbol, 
                            String interval, Map<String, Object> params, 
                            Object state, int candleCount) {
            this.indicatorId = indicatorId;
            this.provider = provider;
            this.symbol = symbol;
            this.interval = interval;
            this.params = params != null ? params : Map.of();
            this.state = state;
            this.candleCount = candleCount;
        }
        
        public String getIndicatorId() { return indicatorId; }
        public String getProvider() { return provider; }
        public String getSymbol() { return symbol; }
        public String getInterval() { return interval; }
        public Map<String, Object> getParams() { return params; }
        public Object getState() { return state; }
        public int getCandleCount() { return candleCount; }
        
        public void setState(Object state) { this.state = state; }
        public void incrementCandleCount() { this.candleCount++; }
    }
    
    /**
     * Result returned by updateWithCandle and updateWithTick
     * 
     * Contains:
     * - Timestamp of the update
     * - Indicator values (e.g., {"sma": 50000.00})
     * - Original candle data (if available)
     * - Additional data (colors, boxes, shapes, etc.)
     */
    public static class IndicatorResult {
        private final Instant timestamp;
        private final Map<String, BigDecimal> values;
        private final CandlestickData candle;
        private final Map<String, Object> additionalData;
        
        public IndicatorResult(Instant timestamp, Map<String, BigDecimal> values, 
                             CandlestickData candle, Map<String, Object> additionalData) {
            this.timestamp = timestamp;
            this.values = values != null ? values : Map.of();
            this.candle = candle;
            this.additionalData = additionalData != null ? additionalData : Map.of();
        }
        
        public Instant getTimestamp() { return timestamp; }
        public Map<String, BigDecimal> getValues() { return values; }
        public CandlestickData getCandle() { return candle; }
        public Map<String, Object> getAdditionalData() { return additionalData; }
        
        /**
         * Get a specific value by name
         * @param name Value name (e.g., "sma", "rsi", "supertrend")
         * @return The value, or null if not found
         */
        public BigDecimal getValue(String name) {
            return values.get(name);
        }
        
        /**
         * Check if result has a specific additional data field
         */
        public boolean hasAdditionalData(String key) {
            return additionalData.containsKey(key);
        }
        
        /**
         * Get additional data by key (e.g., "color", "boxes", "orderBlocks")
         */
        public Object getAdditionalData(String key) {
            return additionalData.get(key);
        }
    }
    
    /**
     * Data point returned by calculateHistorical
     * @deprecated Use IndicatorResult instead
     */
    @Deprecated
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

