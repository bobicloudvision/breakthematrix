package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Trend Regularity Adaptive Moving Average (TRAMA) Indicator
 *
 * TRAMA is an adaptive moving average that adjusts its responsiveness based on trend regularity.
 * 
 * Key Features:
 * - Responds quickly during strong trends (when highs/lows are making new extremes)
 * - Responds slowly during sideways/choppy markets
 * - Uses trend consistency factor (TC) to measure trend strength
 * 
 * Calculation:
 * 1. Track highest and lowest values over the specified period
 * 2. Detect when new highs or new lows are being made
 * 3. Calculate trend consistency (TC) as the squared SMA of trend signals
 * 4. Apply adaptive smoothing: AMA = AMA[prev] + TC * (price - AMA[prev])
 * 
 * When TC is high (strong trend), the indicator follows price closely.
 * When TC is low (sideways), the indicator is more stable/flat.
 */
@Component
public class TRAMAIndicator extends AbstractIndicator {
    
    /**
     * Internal state class to track TRAMA calculation data
     */
    public static class TRAMAState {
        private final int length;
        
        // Price buffer for tracking highs/lows
        private final LinkedList<BigDecimal> priceBuffer;
        
        // Buffer for tracking trend consistency signals (1 or 0)
        private final LinkedList<Integer> trendSignalBuffer;
        
        // Previous highest and lowest values
        private BigDecimal prevHighest;
        private BigDecimal prevLowest;
        
        // Current AMA value
        private BigDecimal ama;
        
        // Sum of trend signals for efficient TC calculation
        private int trendSignalSum;
        
        public TRAMAState(int length) {
            this.length = length;
            this.priceBuffer = new LinkedList<>();
            this.trendSignalBuffer = new LinkedList<>();
            this.prevHighest = null;
            this.prevLowest = null;
            this.ama = null;
            this.trendSignalSum = 0;
        }
        
        public synchronized void addPrice(BigDecimal price) {
            priceBuffer.add(price);
            
            // Keep buffer size at length + 1 to detect changes
            if (priceBuffer.size() > length) {
                priceBuffer.removeFirst();
            }
        }
        
        public synchronized void addTrendSignal(int signal) {
            trendSignalBuffer.add(signal);
            trendSignalSum += signal;
            
            // Keep buffer at specified length
            if (trendSignalBuffer.size() > length) {
                int removed = trendSignalBuffer.removeFirst();
                trendSignalSum -= removed;
            }
        }
        
        public synchronized BigDecimal getHighest() {
            if (priceBuffer.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return priceBuffer.stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        }
        
        public synchronized BigDecimal getLowest() {
            if (priceBuffer.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return priceBuffer.stream()
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        }
        
        public synchronized BigDecimal calculateTC() {
            if (trendSignalBuffer.isEmpty()) {
                return BigDecimal.ZERO;
            }
            
            // Calculate SMA of trend signals
            BigDecimal sma = BigDecimal.valueOf(trendSignalSum)
                .divide(BigDecimal.valueOf(trendSignalBuffer.size()), 8, RoundingMode.HALF_UP);
            
            // Square it to get TC (trend consistency)
            return sma.multiply(sma);
        }
        
        public synchronized BigDecimal calculateAMA(BigDecimal src, BigDecimal tc) {
            if (ama == null) {
                // First initialization
                ama = src;
                return ama;
            }
            
            // AMA = AMA[prev] + TC * (src - AMA[prev])
            BigDecimal diff = src.subtract(ama);
            BigDecimal change = tc.multiply(diff);
            ama = ama.add(change);
            
            return ama;
        }
        
        public void setPrevHighest(BigDecimal value) {
            this.prevHighest = value;
        }
        
        public void setPrevLowest(BigDecimal value) {
            this.prevLowest = value;
        }
        
        public BigDecimal getPrevHighest() {
            return prevHighest;
        }
        
        public BigDecimal getPrevLowest() {
            return prevLowest;
        }
        
        public BigDecimal getAMA() {
            return ama;
        }
        
        public int getBufferSize() {
            return priceBuffer.size();
        }
    }
    
    public TRAMAIndicator() {
        super(
            "trama",
            "Trend Regularity Adaptive Moving Average",
            "Adaptive moving average that responds quickly to trends and slowly to sideways markets.",
            IndicatorCategory.TREND
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("length", IndicatorParameter.builder("length")
            .displayName("Length")
            .description("Period for highest/lowest calculation and trend consistency")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(99)
            .minValue(1)
            .maxValue(500)
            .required(true)
            .build());
        
        params.put("source", IndicatorParameter.builder("source")
            .displayName("Price Source")
            .description("Price type to use for calculation")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("close")
            .required(false)
            .build());
        
        params.put("color", IndicatorParameter.builder("color")
            .displayName("Line Color")
            .description("Color for the TRAMA line on the chart")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ff1100")
            .required(false)
            .build());
        
        params.put("lineWidth", IndicatorParameter.builder("lineWidth")
            .displayName("Line Width")
            .description("Width of the TRAMA line")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(2)
            .minValue(1)
            .maxValue(10)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        System.out.println("TRAMAIndicator.onInit - historicalCandles: " + 
            (historicalCandles != null ? historicalCandles.size() : 0) + " candles");
        
        // Validate and merge parameters
        validateParameters(params);
        params = mergeWithDefaults(params);
        
        int length = getIntParameter(params, "length", 99);
        String source = getStringParameter(params, "source", "close");
        
        // Create initial state
        TRAMAState state = new TRAMAState(length);
        
        // Process historical candles to build initial state
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            for (CandlestickData candle : historicalCandles) {
                BigDecimal price = extractPrice(candle, source);
                processCandle(price, state);
            }
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        System.out.println("TRAMAIndicator.onNewCandle");
        
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        int length = getIntParameter(params, "length", 99);
        String source = getStringParameter(params, "source", "close");
        
        // Cast or create state
        TRAMAState tramaState = (state instanceof TRAMAState) ? (TRAMAState) state : new TRAMAState(length);
        
        // Extract price and process
        BigDecimal price = extractPrice(candle, source);
        BigDecimal trama = processCandle(price, tramaState);
        
        // Build result
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("trama", trama);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", tramaState);
        
        return result;
    }
    
    @Override
    public Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        // If no state yet, can't calculate tick value
        if (state == null || !(state instanceof TRAMAState)) {
            return Map.of(
                "values", Map.of(),
                "state", state != null ? state : new TRAMAState(getIntParameter(params, "length", 99))
            );
        }
        
        TRAMAState tramaState = (TRAMAState) state;
        
        // For real-time tick, just return current AMA value
        // We don't want to update state on every tick
        BigDecimal currentAMA = tramaState.getAMA();
        if (currentAMA == null) {
            currentAMA = price;
        }
        
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("trama", currentAMA);
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", state);  // State unchanged
        
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        int length = getIntParameter(params, "length", 99);
        String color = getStringParameter(params, "color", "#ff1100");
        int lineWidth = getIntParameter(params, "lineWidth", 2);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("trama", IndicatorMetadata.builder("trama")
            .displayName("TRAMA(" + length + ")")
            .asLine(color, lineWidth)
            .separatePane(false)  // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        // Need at least 'length' candles for proper calculation
        return getIntParameter(params, "length", 99);
    }
    
    /**
     * Process a single candle and update TRAMA calculation
     * 
     * Pine Script logic:
     * hh = max(sign(change(highest(length))),0)
     * ll = max(sign(change(lowest(length))*-1),0)
     * tc = pow(sma(hh or ll ? 1 : 0,length),2)
     * ama := nz(ama[1]+tc*(src-ama[1]),src)
     */
    private BigDecimal processCandle(BigDecimal price, TRAMAState state) {
        // Add price to buffer
        state.addPrice(price);
        
        // Need at least some data before calculating
        if (state.getBufferSize() < 2) {
            // Initialize with first price
            BigDecimal tc = BigDecimal.ZERO;
            BigDecimal ama = state.calculateAMA(price, tc);
            state.setPrevHighest(price);
            state.setPrevLowest(price);
            state.addTrendSignal(0);
            return ama;
        }
        
        // Get current highest and lowest over the period
        BigDecimal currentHighest = state.getHighest();
        BigDecimal currentLowest = state.getLowest();
        
        // Calculate trend signals
        int hh = 0;  // Higher high signal
        int ll = 0;  // Lower low signal
        
        if (state.getPrevHighest() != null) {
            // hh = max(sign(change(highest(length))),0)
            // If highest increased, hh = 1, else 0
            if (currentHighest.compareTo(state.getPrevHighest()) > 0) {
                hh = 1;
            }
        }
        
        if (state.getPrevLowest() != null) {
            // ll = max(sign(change(lowest(length))*-1),0)
            // If lowest decreased, ll = 1, else 0
            if (currentLowest.compareTo(state.getPrevLowest()) < 0) {
                ll = 1;
            }
        }
        
        // Trend signal: 1 if either hh or ll, else 0
        int trendSignal = (hh == 1 || ll == 1) ? 1 : 0;
        state.addTrendSignal(trendSignal);
        
        // Calculate trend consistency (TC)
        BigDecimal tc = state.calculateTC();
        
        // Calculate adaptive moving average
        BigDecimal ama = state.calculateAMA(price, tc);
        
        // Update previous highest/lowest for next iteration
        state.setPrevHighest(currentHighest);
        state.setPrevLowest(currentLowest);
        
        return ama;
    }
    
    /**
     * Extract price from candle based on source type
     */
    private BigDecimal extractPrice(CandlestickData candle, String source) {
        return switch (source.toLowerCase()) {
            case "open" -> candle.getOpen();
            case "high" -> candle.getHigh();
            case "low" -> candle.getLow();
            case "close" -> candle.getClose();
            case "hl2" -> candle.getHigh().add(candle.getLow())
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            case "hlc3" -> candle.getHigh().add(candle.getLow()).add(candle.getClose())
                .divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
            case "ohlc4" -> candle.getOpen().add(candle.getHigh()).add(candle.getLow()).add(candle.getClose())
                .divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP);
            default -> candle.getClose();
        };
    }
}

