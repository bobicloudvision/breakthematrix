package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.bot.visualization.ShapeCollection;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradeData;
import org.cloudvision.trading.model.TradingDataType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * ============================================================
 * CVD (CUMULATIVE VOLUME DELTA) INDICATOR
 * ============================================================
 * 
 * Tracks the cumulative difference between buy volume and sell volume over time.
 * This shows the net directional flow of volume and helps identify:
 * - Trend strength (rising CVD = strong buying, falling CVD = strong selling)
 * - Divergences (price makes new high but CVD doesn't = bearish divergence)
 * - Institutional accumulation/distribution
 * - Support/resistance zones where volume shifts
 * 
 * HOW IT WORKS:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. For each trade:
 *    - If aggressive buy: Add volume to cumulative delta
 *    - If aggressive sell: Subtract volume from cumulative delta
 * 
 * 2. Track cumulative delta over time:
 *    CVD = Σ (Buy Volume - Sell Volume) from start
 * 
 * 3. Output per candle:
 *    - Current CVD value
 *    - Delta for this candle
 *    - Rate of change (momentum)
 * 
 * TRADING SIGNALS:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 🟢 BULLISH SIGNALS:
 * - CVD trending upward (buying pressure)
 * - CVD breaks above previous high
 * - Bullish divergence: Price makes lower low, CVD makes higher low
 * 
 * 🔴 BEARISH SIGNALS:
 * - CVD trending downward (selling pressure)
 * - CVD breaks below previous low
 * - Bearish divergence: Price makes higher high, CVD makes lower high
 * 
 * EXAMPLE OUTPUT:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * {
 *   "values": {
 *     "cvd": 12345.67,           // Current cumulative delta
 *     "candleDelta": 123.45,     // Delta for this candle only
 *     "deltaChange": 45.67       // Change from previous candle
 *   },
 *   "color": "#26a69a"           // Green = uptrend, Red = downtrend
 * }
 */
@Component
public class CVDIndicator extends AbstractIndicator {
    
    /**
     * CVD State - tracks cumulative volume delta
     */
    public static class CVDState {
        // Cumulative volume delta (the main CVD value)
        private BigDecimal cumulativeDelta;
        
        // Current candle's delta (for per-candle display)
        private BigDecimal candleDelta;
        
        // Previous candle's delta (for rate of change)
        private BigDecimal previousCandleDelta;
        
        // Previous CVD value (for divergence detection)
        private BigDecimal previousCVD;
        
        // Configuration
        private final boolean resetOnNewSession;
        private final int smoothingPeriod;
        
        // Live update tracking
        private long lastBroadcastTime;
        private CandlestickData lastCandle;
        
        public CVDState(boolean resetOnNewSession, int smoothingPeriod) {
            this.cumulativeDelta = BigDecimal.ZERO;
            this.candleDelta = BigDecimal.ZERO;
            this.previousCandleDelta = BigDecimal.ZERO;
            this.previousCVD = BigDecimal.ZERO;
            this.resetOnNewSession = resetOnNewSession;
            this.smoothingPeriod = smoothingPeriod;
            this.lastBroadcastTime = 0;
            this.lastCandle = null;
        }
        
        // Getters
        public BigDecimal getCumulativeDelta() { return cumulativeDelta; }
        public BigDecimal getCandleDelta() { return candleDelta; }
        public BigDecimal getPreviousCandleDelta() { return previousCandleDelta; }
        public BigDecimal getPreviousCVD() { return previousCVD; }
        public boolean isResetOnNewSession() { return resetOnNewSession; }
        public int getSmoothingPeriod() { return smoothingPeriod; }
        public long getLastBroadcastTime() { return lastBroadcastTime; }
        public CandlestickData getLastCandle() { return lastCandle; }
        
        // Setters
        public void setCumulativeDelta(BigDecimal cumulativeDelta) {
            this.cumulativeDelta = cumulativeDelta;
        }
        
        public void setCandleDelta(BigDecimal candleDelta) {
            this.candleDelta = candleDelta;
        }
        
        public void setPreviousCandleDelta(BigDecimal previousCandleDelta) {
            this.previousCandleDelta = previousCandleDelta;
        }
        
        public void setPreviousCVD(BigDecimal previousCVD) {
            this.previousCVD = previousCVD;
        }
        
        public void setLastBroadcastTime(long lastBroadcastTime) {
            this.lastBroadcastTime = lastBroadcastTime;
        }
        
        public void setLastCandle(CandlestickData lastCandle) {
            this.lastCandle = lastCandle;
        }
        
        /**
         * Add volume to delta (positive for buy, negative for sell)
         */
        public void addVolume(BigDecimal volume, boolean isBuy) {
            BigDecimal delta = isBuy ? volume : volume.negate();
            this.candleDelta = this.candleDelta.add(delta);
        }
        
        /**
         * Finalize candle and update cumulative delta
         */
        public void finalizeCandle() {
            this.previousCVD = this.cumulativeDelta;
            this.cumulativeDelta = this.cumulativeDelta.add(this.candleDelta);
            this.previousCandleDelta = this.candleDelta;
            this.candleDelta = BigDecimal.ZERO; // Reset for next candle
        }
        
        /**
         * Reset cumulative delta (for new session)
         */
        public void reset() {
            this.cumulativeDelta = BigDecimal.ZERO;
            this.previousCVD = BigDecimal.ZERO;
        }
    }
    
    public CVDIndicator() {
        super(
            "cvd",
            "Cumulative Volume Delta (CVD)",
            "Tracks cumulative buy-sell volume difference to show net directional flow and identify trend strength",
            IndicatorCategory.ORDER_FLOW
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("resetOnNewSession", IndicatorParameter.builder("resetOnNewSession")
            .displayName("Reset on New Session")
            .description("Reset CVD at the start of each new trading session (daily)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("false")
            .required(false)
            .build());
        
        params.put("smoothingPeriod", IndicatorParameter.builder("smoothingPeriod")
            .displayName("Smoothing Period")
            .description("Number of candles for smoothing CVD line (0 = no smoothing)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(0)
            .minValue(0)
            .maxValue(50)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Set<TradingDataType> getRequiredDataTypes() {
        return Set.of(
            TradingDataType.AGGREGATE_TRADE,  // Need trades for delta calculation
            TradingDataType.KLINE             // Need candles for time structure
        );
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        System.out.println("📊 CVDIndicator.onInit - Initializing CVD tracking");
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        // Parse parameters
        boolean resetOnNewSession = getBooleanParameter(params, "resetOnNewSession", false);
        int smoothingPeriod = getIntParameter(params, "smoothingPeriod", 0);
        
        // Create initial state
        CVDState state = new CVDState(resetOnNewSession, smoothingPeriod);
        
        System.out.println("✅ CVD state initialized (resetOnNewSession=" + resetOnNewSession + 
                         ", smoothingPeriod=" + smoothingPeriod + ")");
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        CVDState cvdState = (CVDState) state;
        
        // Save candle reference for live updates
        cvdState.setLastCandle(candle);
        
        // Debug: Log before finalization
        System.out.println("📊 CVD onNewCandle: " + candle.getOpenTime() + 
                         " | candleDelta (pre-finalize): " + cvdState.getCandleDelta() + 
                         " | cumulativeDelta: " + cvdState.getCumulativeDelta());
        
        // Finalize the previous candle's delta and add to cumulative
        cvdState.finalizeCandle();
        
        // Calculate delta change (momentum)
        BigDecimal deltaChange = cvdState.getCandleDelta().subtract(cvdState.getPreviousCandleDelta());
        
        // Determine color based on CVD direction
        String color = cvdState.getCumulativeDelta().compareTo(cvdState.getPreviousCVD()) >= 0 
                      ? "#26a69a"  // Green - buying
                      : "#ef5350"; // Red - selling
        
        // Build values
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("cvd", cvdState.getCumulativeDelta());
        values.put("candleDelta", cvdState.getPreviousCandleDelta()); // Use finalized delta
        values.put("deltaChange", deltaChange);
        
        System.out.println("📈 CVD output: cvd=" + cvdState.getCumulativeDelta() + 
                         " | candleDelta=" + cvdState.getPreviousCandleDelta() + 
                         " | color=" + color);
        
        // Build result
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", cvdState);
        result.put("color", color);
        
        return result;
    }
    
    @Override
    public Map<String, Object> onTradeUpdate(TradeData trade, Map<String, Object> params, Object state) {
        CVDState cvdState = (CVDState) state;
        
        // Add trade volume to current candle delta
        cvdState.addVolume(trade.getQuantity(), trade.isAggressiveBuy());
        
        // Debug: Log every 10th trade to avoid spam
        if (Math.random() < 0.1) {
            System.out.println("💱 CVD trade: " + trade.getTimestamp() + 
                             " | qty=" + trade.getQuantity() + 
                             " | buy=" + trade.isAggressiveBuy() + 
                             " | current candleDelta=" + cvdState.getCandleDelta());
        }
        
        // Return empty result - we'll output on candle close
        return Map.of(
            "values", Map.of(),
            "state", cvdState
        );
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // CVD line - main indicator
        metadata.put("cvd", IndicatorMetadata.builder("cvd")
            .displayName("CVD")
            .seriesType("line")
            .addConfig("lineWidth", 2)
            .addConfig("priceLineVisible", false)
            .separatePane(true)  // Show in separate pane below chart
            .paneOrder(1)
            .build());
        
        // Candle delta - optional histogram
        metadata.put("candleDelta", IndicatorMetadata.builder("candleDelta")
            .displayName("Delta")
            .seriesType("histogram")
            .addConfig("color", "#2196F3")
            .addConfig("priceLineVisible", false)
            .separatePane(true)
            .paneOrder(1)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1; // CVD works from first candle
    }
}

