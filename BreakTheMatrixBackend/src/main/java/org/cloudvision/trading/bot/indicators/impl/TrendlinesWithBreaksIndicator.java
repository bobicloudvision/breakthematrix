package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Trendlines with Breaks Indicator
 * 
 * Detects swing highs and lows, then draws dynamic trendlines with configurable slopes.
 * The trendlines adjust on each bar based on the slope calculation method (ATR, StDev, or LinReg).
 * Detects and marks breakouts when price crosses these trendlines.
 * 
 * ALGORITHM:
 * 1. Detect pivot highs (for down-trendlines) and pivot lows (for up-trendlines)
 * 2. Calculate slope based on ATR, Standard Deviation, or Linear Regression
 * 3. Draw trendlines that dynamically adjust from the pivot points
 * 4. Detect breakouts when price crosses above/below the trendlines
 * 5. Optionally show extended lines projecting into the future
 * 
 * OUTPUT VALUES:
 * - upperTrendline: Current value of the down-trendline (resistance)
 * - lowerTrendline: Current value of the up-trendline (support)
 * - upperBreakout: 1 if price broke above down-trendline, 0 otherwise
 * - lowerBreakout: 1 if price broke below up-trendline, 0 otherwise
 * - slope: Current calculated slope value
 * 
 * VISUALIZATION:
 * - Lines for upper and lower trendlines
 * - Shapes/markers for breakout points
 * - Optional extended lines showing future projection
 */
@Component
public class TrendlinesWithBreaksIndicator extends AbstractIndicator {
    
    /**
     * State for the trendlines indicator
     */
    public static class TrendlineState {
        // Trendline values
        public BigDecimal upper = BigDecimal.ZERO;
        public BigDecimal lower = BigDecimal.ZERO;
        
        // Slopes for each trendline
        public BigDecimal slopePh = BigDecimal.ZERO;  // Slope from pivot high
        public BigDecimal slopePl = BigDecimal.ZERO;  // Slope from pivot low
        
        // Breakout tracking
        public int upos = 0;  // Upper position (breakout state)
        public int dnos = 0;  // Down position (breakout state)
        
        // Bar index for tracking
        public int barIndex = 0;
        
        // Candle buffer for pivot detection
        public List<CandlestickData> candleBuffer = new ArrayList<>();
        public int maxBufferSize = 200;
        
        // Track pivot points for extended lines
        public Instant lastPivotHighTime = null;
        public BigDecimal lastPivotHighPrice = null;
        public Instant lastPivotLowTime = null;
        public BigDecimal lastPivotLowPrice = null;
        
        // Previous values for breakout detection
        public int prevUpos = 0;
        public int prevDnos = 0;
        
        // Confirmation tracking for breakouts
        public boolean pendingUpperBreakout = false;  // Waiting for confirmation of upper breakout
        public boolean pendingLowerBreakout = false;  // Waiting for confirmation of lower breakout
        public BigDecimal pendingUpperBreakoutPrice = null;  // Price at which upper breakout occurred
        public BigDecimal pendingLowerBreakoutPrice = null;  // Price at which lower breakout occurred
    }
    
    public TrendlinesWithBreaksIndicator() {
        super("trendlines_breaks", "Trendlines with Breaks", 
              "Dynamic trendlines based on pivot points with breakout detection",
              Indicator.IndicatorCategory.TREND);
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("length", IndicatorParameter.builder("length")
            .displayName("Swing Detection Lookback")
            .description("Number of candles to look left and right for pivot detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(14)
            .minValue(2)
            .maxValue(50)
            .required(true)
            .build());
        
        params.put("mult", IndicatorParameter.builder("mult")
            .displayName("Slope Multiplier")
            .description("Multiplier for the slope calculation")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(1.0)
            .minValue(0.0)
            .maxValue(10.0)
            .required(true)
            .build());
        
        params.put("calcMethod", IndicatorParameter.builder("calcMethod")
            .displayName("Slope Calculation Method")
            .description("Method to calculate trendline slope (Atr, Stdev, or Linreg)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Atr")
            .required(true)
            .build());
        
        params.put("backpaint", IndicatorParameter.builder("backpaint")
            .displayName("Backpainting")
            .description("Backpainting offsets displayed elements in the past")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("showExtendedLines", IndicatorParameter.builder("showExtendedLines")
            .displayName("Show Extended Lines")
            .description("Show extended trendlines into the future")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("upTrendlineColor", IndicatorParameter.builder("upTrendlineColor")
            .displayName("Up Trendline Color")
            .description("Color for up trendline (support)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#26a69a")
            .required(false)
            .build());
        
        params.put("downTrendlineColor", IndicatorParameter.builder("downTrendlineColor")
            .displayName("Down Trendline Color")
            .description("Color for down trendline (resistance)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ef5350")
            .required(false)
            .build());
        
        params.put("requireConfirmation", IndicatorParameter.builder("requireConfirmation")
            .displayName("Require Confirmation")
            .description("Only show breakout signals after they're confirmed by the next candle close")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        TrendlineState state = new TrendlineState();
        int length = getIntParameter(params, "length", 14);
        
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            // Add all historical candles to buffer
            state.candleBuffer.addAll(historicalCandles);
            
            // Process candles to build initial state
            for (int i = 0; i < historicalCandles.size(); i++) {
                CandlestickData candle = historicalCandles.get(i);
                state.barIndex++;
                
                // Check for pivots and update trendlines
                if (i >= length * 2) {
                    processCandle(candle, i, state, params);
                }
            }
            
            trimCandleBuffer(state);
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object stateObj) {
        params = mergeWithDefaults(params);
        
        // Cast or create state
        TrendlineState state = (stateObj instanceof TrendlineState) ? (TrendlineState) stateObj : new TrendlineState();
        
        int length = getIntParameter(params, "length", 14);
        boolean backpaint = getBooleanParameter(params, "backpaint", true);
        
        // Store previous values for breakout detection
        state.prevUpos = state.upos;
        state.prevDnos = state.dnos;
        
        // Add new candle to buffer
        state.candleBuffer.add(candle);
        state.barIndex++;
        
        int bufferSize = state.candleBuffer.size();
        
        // Check for pivot at the appropriate lookback position
        if (bufferSize >= length * 2 + 1) {
            int pivotIdx = bufferSize - length - 1;
            processCandle(candle, pivotIdx, state, params);
        }
        
        // Calculate current slope
        BigDecimal slope = calculateSlope(state.candleBuffer, params);
        
        // Update trendlines based on whether we found new pivots
        // If we found a pivot high, reset upper trendline, otherwise decrease it by slope
        // If we found a pivot low, reset lower trendline, otherwise increase it by slope
        BigDecimal currentHigh = candle.getHigh();
        BigDecimal currentLow = candle.getLow();
        BigDecimal currentClose = candle.getClose();
        
        // Check if we have recent pivots to calculate from
        if (state.upper.compareTo(BigDecimal.ZERO) == 0 && state.lastPivotHighPrice != null) {
            state.upper = state.lastPivotHighPrice;
        } else if (state.upper.compareTo(BigDecimal.ZERO) > 0) {
            state.upper = state.upper.subtract(state.slopePh);
        }
        
        if (state.lower.compareTo(BigDecimal.ZERO) == 0 && state.lastPivotLowPrice != null) {
            state.lower = state.lastPivotLowPrice;
        } else if (state.lower.compareTo(BigDecimal.ZERO) > 0) {
            state.lower = state.lower.add(state.slopePl);
        }
        
        // Get confirmation setting
        boolean requireConfirmation = getBooleanParameter(params, "requireConfirmation", false);
        
        // Update breakout detection with optional confirmation
        BigDecimal upperThreshold = state.upper.subtract(state.slopePh.multiply(BigDecimal.valueOf(length)));
        BigDecimal lowerThreshold = state.lower.add(state.slopePl.multiply(BigDecimal.valueOf(length)));
        
        // Check for pending confirmations from previous candle
        boolean confirmedUpperBreakout = false;
        boolean confirmedLowerBreakout = false;
        
        if (requireConfirmation) {
            // Check if we're confirming a pending upper breakout
            if (state.pendingUpperBreakout && state.pendingUpperBreakoutPrice != null) {
                // Confirm if current close stays above breakout price
                if (currentClose.compareTo(state.pendingUpperBreakoutPrice) > 0) {
                    confirmedUpperBreakout = true;
                    state.upos = 1;
                }
                // Clear pending flag regardless
                state.pendingUpperBreakout = false;
                state.pendingUpperBreakoutPrice = null;
            }
            
            // Check if we're confirming a pending lower breakout
            if (state.pendingLowerBreakout && state.pendingLowerBreakoutPrice != null) {
                // Confirm if current close stays below breakout price
                if (currentClose.compareTo(state.pendingLowerBreakoutPrice) < 0) {
                    confirmedLowerBreakout = true;
                    state.dnos = 1;
                }
                // Clear pending flag regardless
                state.pendingLowerBreakout = false;
                state.pendingLowerBreakoutPrice = null;
            }
            
            // Check for NEW breakouts (mark as pending)
            if (state.upos == 0 && currentClose.compareTo(upperThreshold) > 0) {
                state.pendingUpperBreakout = true;
                state.pendingUpperBreakoutPrice = currentClose;
            }
            
            if (state.dnos == 0 && currentClose.compareTo(lowerThreshold) < 0) {
                state.pendingLowerBreakout = true;
                state.pendingLowerBreakoutPrice = currentClose;
            }
        } else {
            // No confirmation required - signal immediately
            if (state.upos == 0 && currentClose.compareTo(upperThreshold) > 0) {
                state.upos = 1;
            }
            
            if (state.dnos == 0 && currentClose.compareTo(lowerThreshold) < 0) {
                state.dnos = 1;
            }
        }
        
        // Trim buffer
        trimCandleBuffer(state);
        
        // Build output
        Map<String, BigDecimal> values = new HashMap<>();
        
        // Calculate display values (considering backpaint offset)
        BigDecimal displayUpper = backpaint ? state.upper : 
            state.upper.subtract(state.slopePh.multiply(BigDecimal.valueOf(length)));
        BigDecimal displayLower = backpaint ? state.lower : 
            state.lower.add(state.slopePl.multiply(BigDecimal.valueOf(length)));
        
        values.put("upperTrendline", displayUpper);
        values.put("lowerTrendline", displayLower);
        values.put("slope", slope);
        
        // Breakout signals
        // With confirmation: only signal when confirmed
        // Without confirmation: signal immediately (new breakouts only)
        boolean showUpperBreakout = requireConfirmation ? confirmedUpperBreakout : (state.upos > state.prevUpos);
        boolean showLowerBreakout = requireConfirmation ? confirmedLowerBreakout : (state.dnos > state.prevDnos);
        
        values.put("upperBreakout", showUpperBreakout ? BigDecimal.ONE : BigDecimal.ZERO);
        values.put("lowerBreakout", showLowerBreakout ? BigDecimal.ONE : BigDecimal.ZERO);
        
        // Create shapes for breakouts (only show confirmed breakouts)
        List<Map<String, Object>> shapes = new ArrayList<>();
        
        if (showUpperBreakout) {
            // Upward breakout (confirmed or immediate)
            Map<String, Object> shape = new HashMap<>();
            shape.put("type", "labelup");
            shape.put("time", candle.getCloseTime().getEpochSecond());
            shape.put("price", currentLow);
            shape.put("text", requireConfirmation ? "B✓" : "B");
            shape.put("color", getStringParameter(params, "upTrendlineColor", "#26a69a"));
            shape.put("textColor", "#ffffff");
            shapes.add(shape);
        }
        
        if (showLowerBreakout) {
            // Downward breakout (confirmed or immediate)
            Map<String, Object> shape = new HashMap<>();
            shape.put("type", "labeldown");
            shape.put("time", candle.getCloseTime().getEpochSecond());
            shape.put("price", currentHigh);
            shape.put("text", requireConfirmation ? "B✓" : "B");
            shape.put("color", getStringParameter(params, "downTrendlineColor", "#ef5350"));
            shape.put("textColor", "#ffffff");
            shapes.add(shape);
        }
        
        // Create line objects for extended trendlines
        List<Map<String, Object>> lines = createExtendedLines(state, candle, params);
        
        Map<String, Object> output = new HashMap<>();
        // output.put("values", values);
        output.put("state", state);
        output.put("shapes", shapes);
        output.put("lines", lines);
        
        return output;
    }
    
    /**
     * Process a candle and detect pivots
     */
    private void processCandle(CandlestickData candle, int pivotIdx, TrendlineState state, Map<String, Object> params) {
        int length = getIntParameter(params, "length", 14);
        
        // Calculate current slope
        BigDecimal slope = calculateSlope(state.candleBuffer, params);
        
        // Check for pivot high (resistance / down-trendline origin)
        if (isPivotHigh(state.candleBuffer, pivotIdx, length)) {
            CandlestickData pivotCandle = state.candleBuffer.get(pivotIdx);
            state.upper = pivotCandle.getHigh();
            state.slopePh = slope;
            state.lastPivotHighPrice = pivotCandle.getHigh();
            state.lastPivotHighTime = pivotCandle.getCloseTime();
            state.upos = 0;  // Reset breakout state
        }
        
        // Check for pivot low (support / up-trendline origin)
        if (isPivotLow(state.candleBuffer, pivotIdx, length)) {
            CandlestickData pivotCandle = state.candleBuffer.get(pivotIdx);
            state.lower = pivotCandle.getLow();
            state.slopePl = slope;
            state.lastPivotLowPrice = pivotCandle.getLow();
            state.lastPivotLowTime = pivotCandle.getCloseTime();
            state.dnos = 0;  // Reset breakout state
        }
    }
    
    /**
     * Calculate slope based on the selected method
     */
    private BigDecimal calculateSlope(List<CandlestickData> candles, Map<String, Object> params) {
        if (candles.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int length = getIntParameter(params, "length", 14);
        double mult = getDoubleParameter(params, "mult", 1.0);
        String calcMethod = getStringParameter(params, "calcMethod", "Atr");
        
        if (candles.size() < length + 1) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal slope;
        
        switch (calcMethod) {
            case "Atr":
                slope = calculateATRSlope(candles, length);
                break;
            case "Stdev":
                slope = calculateStdevSlope(candles, length);
                break;
            case "Linreg":
                slope = calculateLinregSlope(candles, length);
                break;
            default:
                slope = calculateATRSlope(candles, length);
        }
        
        // Apply multiplier
        slope = slope.multiply(BigDecimal.valueOf(mult));
        
        return slope;
    }
    
    /**
     * Calculate slope using ATR method
     */
    private BigDecimal calculateATRSlope(List<CandlestickData> candles, int length) {
        if (candles.size() < length + 1) {
            return BigDecimal.ZERO;
        }
        
        // Get recent candles for ATR calculation
        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>();
        
        int startIdx = Math.max(0, candles.size() - length - 1);
        for (int i = startIdx; i < candles.size(); i++) {
            CandlestickData c = candles.get(i);
            highs.add(c.getHigh());
            lows.add(c.getLow());
            closes.add(c.getClose());
        }
        
        BigDecimal atr = TechnicalIndicators.calculateATR(highs, lows, closes, length);
        
        // Slope = ATR / length
        return atr.divide(BigDecimal.valueOf(length), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate slope using Standard Deviation method
     */
    private BigDecimal calculateStdevSlope(List<CandlestickData> candles, int length) {
        if (candles.size() < length) {
            return BigDecimal.ZERO;
        }
        
        // Get recent closing prices
        List<BigDecimal> closes = new ArrayList<>();
        int startIdx = Math.max(0, candles.size() - length);
        for (int i = startIdx; i < candles.size(); i++) {
            closes.add(candles.get(i).getClose());
        }
        
        BigDecimal stdev = TechnicalIndicators.calculateStandardDeviation(closes, length);
        
        // Slope = Stdev / length
        return stdev.divide(BigDecimal.valueOf(length), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate slope using Linear Regression method
     * This approximates the Pine Script formula:
     * abs(sma(src * n, length) - sma(src, length) * sma(n, length)) / variance(n, length) / 2
     */
    private BigDecimal calculateLinregSlope(List<CandlestickData> candles, int length) {
        if (candles.size() < length) {
            return BigDecimal.ZERO;
        }
        
        // Get recent closing prices
        List<BigDecimal> closes = new ArrayList<>();
        int startIdx = Math.max(0, candles.size() - length);
        for (int i = startIdx; i < candles.size(); i++) {
            closes.add(candles.get(i).getClose());
        }
        
        // Create bar index array (n)
        List<BigDecimal> barIndices = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            barIndices.add(BigDecimal.valueOf(i));
        }
        
        // Calculate sma(src * n, length)
        BigDecimal sumSrcN = BigDecimal.ZERO;
        for (int i = 0; i < length; i++) {
            sumSrcN = sumSrcN.add(closes.get(i).multiply(barIndices.get(i)));
        }
        BigDecimal smaSrcN = sumSrcN.divide(BigDecimal.valueOf(length), 8, RoundingMode.HALF_UP);
        
        // Calculate sma(src, length)
        BigDecimal sumSrc = BigDecimal.ZERO;
        for (BigDecimal close : closes) {
            sumSrc = sumSrc.add(close);
        }
        BigDecimal smaSrc = sumSrc.divide(BigDecimal.valueOf(length), 8, RoundingMode.HALF_UP);
        
        // Calculate sma(n, length)
        BigDecimal sumN = BigDecimal.ZERO;
        for (BigDecimal n : barIndices) {
            sumN = sumN.add(n);
        }
        BigDecimal smaN = sumN.divide(BigDecimal.valueOf(length), 8, RoundingMode.HALF_UP);
        
        // Calculate variance(n, length)
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (BigDecimal n : barIndices) {
            BigDecimal diff = n.subtract(smaN);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(length), 8, RoundingMode.HALF_UP);
        
        // Avoid division by zero
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate slope
        BigDecimal numerator = smaSrcN.subtract(smaSrc.multiply(smaN));
        BigDecimal slope = numerator.divide(variance, 8, RoundingMode.HALF_UP)
                                   .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        
        return slope.abs();
    }
    
    /**
     * Check if index is a pivot high
     */
    private boolean isPivotHigh(List<CandlestickData> candles, int index, int lookback) {
        if (index < lookback || index >= candles.size() - lookback) {
            return false;
        }
        
        BigDecimal pivotHigh = candles.get(index).getHigh();
        
        // Check left side
        for (int i = index - lookback; i < index; i++) {
            if (candles.get(i).getHigh().compareTo(pivotHigh) > 0) {
                return false;
            }
        }
        
        // Check right side
        for (int i = index + 1; i <= index + lookback; i++) {
            if (candles.get(i).getHigh().compareTo(pivotHigh) > 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if index is a pivot low
     */
    private boolean isPivotLow(List<CandlestickData> candles, int index, int lookback) {
        if (index < lookback || index >= candles.size() - lookback) {
            return false;
        }
        
        BigDecimal pivotLow = candles.get(index).getLow();
        
        // Check left side
        for (int i = index - lookback; i < index; i++) {
            if (candles.get(i).getLow().compareTo(pivotLow) < 0) {
                return false;
            }
        }
        
        // Check right side
        for (int i = index + 1; i <= index + lookback; i++) {
            if (candles.get(i).getLow().compareTo(pivotLow) < 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Create extended line objects for visualization
     */
    private List<Map<String, Object>> createExtendedLines(TrendlineState state, CandlestickData currentCandle, Map<String, Object> params) {
        List<Map<String, Object>> lines = new ArrayList<>();
        
        boolean showExtended = getBooleanParameter(params, "showExtendedLines", true);
        boolean backpaint = getBooleanParameter(params, "backpaint", true);
        int length = getIntParameter(params, "length", 14);
        String upColor = getStringParameter(params, "upTrendlineColor", "#26a69a");
        String downColor = getStringParameter(params, "downTrendlineColor", "#ef5350");
        
        if (!showExtended) {
            return lines;
        }
        
        long currentTime = currentCandle.getCloseTime().getEpochSecond();
        long offset = backpaint ? length * 60 : 0;  // Assume 1-minute candles
        
        // Detect interval from candle buffer to calculate bars correctly
        long intervalSeconds = 60; // Default to 1 minute
        if (state.candleBuffer.size() >= 2) {
            CandlestickData lastCandle = state.candleBuffer.get(state.candleBuffer.size() - 1);
            CandlestickData prevCandle = state.candleBuffer.get(state.candleBuffer.size() - 2);
            intervalSeconds = lastCandle.getCloseTime().getEpochSecond() - prevCandle.getCloseTime().getEpochSecond();
        }
        
        // Upper trendline (down-trending resistance)
        if (state.lastPivotHighTime != null && state.lastPivotHighPrice != null) {
            long pivotTime = state.lastPivotHighTime.getEpochSecond();
            long timeDiff = currentTime - pivotTime;
            int barsSincePivot = (int) (timeDiff / intervalSeconds);
            
            // Calculate the price change based on number of bars since pivot
            BigDecimal totalPriceChange = state.slopePh.multiply(BigDecimal.valueOf(barsSincePivot));
            
            Map<String, Object> upperLine = new HashMap<>();
            upperLine.put("type", "trendline");
            
            if (backpaint) {
                // Start from pivot point
                upperLine.put("time1", pivotTime);
                upperLine.put("price1", state.lastPivotHighPrice);
                // End at current time with accumulated slope
                upperLine.put("time2", currentTime);
                upperLine.put("price2", state.lastPivotHighPrice.subtract(totalPriceChange));
            } else {
                // Without backpaint, adjust by offset
                upperLine.put("time1", pivotTime - offset);
                upperLine.put("price1", state.upper.subtract(state.slopePh.multiply(BigDecimal.valueOf(length))));
                upperLine.put("time2", currentTime - offset);
                BigDecimal adjustedTotalChange = state.slopePh.multiply(BigDecimal.valueOf(barsSincePivot + length));
                upperLine.put("price2", state.lastPivotHighPrice.subtract(adjustedTotalChange));
            }
            
            upperLine.put("color", downColor);
            upperLine.put("style", "dashed");
            upperLine.put("extend", "right");
            lines.add(upperLine);
        }
        
        // Lower trendline (up-trending support)
        if (state.lastPivotLowTime != null && state.lastPivotLowPrice != null) {
            long pivotTime = state.lastPivotLowTime.getEpochSecond();
            long timeDiff = currentTime - pivotTime;
            int barsSincePivot = (int) (timeDiff / intervalSeconds);
            
            // Calculate the price change based on number of bars since pivot
            BigDecimal totalPriceChange = state.slopePl.multiply(BigDecimal.valueOf(barsSincePivot));
            
            Map<String, Object> lowerLine = new HashMap<>();
            lowerLine.put("type", "trendline");
            
            if (backpaint) {
                // Start from pivot point
                lowerLine.put("time1", pivotTime);
                lowerLine.put("price1", state.lastPivotLowPrice);
                // End at current time with accumulated slope
                lowerLine.put("time2", currentTime);
                lowerLine.put("price2", state.lastPivotLowPrice.add(totalPriceChange));
            } else {
                // Without backpaint, adjust by offset
                lowerLine.put("time1", pivotTime - offset);
                lowerLine.put("price1", state.lower.add(state.slopePl.multiply(BigDecimal.valueOf(length))));
                lowerLine.put("time2", currentTime - offset);
                BigDecimal adjustedTotalChange = state.slopePl.multiply(BigDecimal.valueOf(barsSincePivot + length));
                lowerLine.put("price2", state.lastPivotLowPrice.add(adjustedTotalChange));
            }
            
            lowerLine.put("color", upColor);
            lowerLine.put("style", "dashed");
            lowerLine.put("extend", "right");
            lines.add(lowerLine);
        }
        
        return lines;
    }
    
    /**
     * Trim candle buffer to prevent unlimited growth
     */
    private void trimCandleBuffer(TrendlineState state) {
        if (state.candleBuffer.size() > state.maxBufferSize) {
            int excess = state.candleBuffer.size() - state.maxBufferSize;
            state.candleBuffer = new ArrayList<>(state.candleBuffer.subList(excess, state.candleBuffer.size()));
        }
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        String upColor = getStringParameter(params, "upTrendlineColor", "#26a69a");
        String downColor = getStringParameter(params, "downTrendlineColor", "#ef5350");
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Upper trendline (resistance)
        metadata.put("upperTrendline", IndicatorMetadata.builder("upperTrendline")
            .displayName("Down Trendline")
            .asLine(downColor, 2)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        // Lower trendline (support)
        metadata.put("lowerTrendline", IndicatorMetadata.builder("lowerTrendline")
            .displayName("Up Trendline")
            .asLine(upColor, 2)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int length = getIntParameter(params, "length", 14);
        return length * 2 + 1;
    }
}

