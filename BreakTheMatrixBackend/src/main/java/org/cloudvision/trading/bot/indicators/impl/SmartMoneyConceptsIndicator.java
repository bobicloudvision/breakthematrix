package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.BoxShape;
import org.cloudvision.trading.bot.visualization.LineShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Smart Money Concepts (SMC) Indicator
 * 
 * A comprehensive indicator that implements institutional trading concepts including:
 * - Market Structure (Break of Structure - BOS, Change of Character - CHoCH)
 * - Internal and Swing Structure
 * - Order Blocks (bullish/bearish zones where smart money entered)
 * - Equal Highs/Lows (liquidity zones)
 * - Fair Value Gaps (imbalances in price)
 * - Premium/Discount Zones
 * - Strong/Weak Highs and Lows
 * 
 * Based on the LuxAlgo Smart Money Concepts indicator
 * 
 * ALGORITHM:
 * 1. Detect pivot points (swing highs/lows) using left/right bar lookback
 * 2. Track market structure changes (BOS vs CHoCH)
 * 3. Identify order blocks from structure breaks
 * 4. Detect equal price levels for liquidity
 * 5. Find fair value gaps (3-bar imbalance patterns)
 * 6. Calculate premium/discount zones from swing ranges
 * 
 * MODES:
 * - Historical: Shows all historical structures
 * - Present: Shows only the most recent structures
 */
@Component
public class SmartMoneyConceptsIndicator extends AbstractIndicator {
    
    // Constants
    private static final int BULLISH = 1;
    private static final int BEARISH = -1;
    private static final int NEUTRAL = 0;
    
    /**
     * Pivot point (swing point) representation
     */
    public static class Pivot {
        public BigDecimal currentLevel;
        public BigDecimal lastLevel;
        public boolean crossed;
        public Instant barTime;
        public int barIndex;
        
        public Pivot() {
            this.currentLevel = null;
            this.lastLevel = null;
            this.crossed = false;
            this.barTime = null;
            this.barIndex = 0;
        }
    }
    
    /**
     * Order Block representation
     */
    public static class OrderBlock {
        public BigDecimal high;
        public BigDecimal low;
        public Instant time;
        public int bias; // BULLISH or BEARISH
        public boolean mitigated;
        
        public OrderBlock(BigDecimal high, BigDecimal low, Instant time, int bias) {
            this.high = high;
            this.low = low;
            this.time = time;
            this.bias = bias;
            this.mitigated = false;
        }
    }
    
    /**
     * Fair Value Gap representation
     */
    public static class FairValueGap {
        public BigDecimal top;
        public BigDecimal bottom;
        public Instant time;
        public int bias; // BULLISH or BEARISH
        public boolean filled;
        
        public FairValueGap(BigDecimal top, BigDecimal bottom, Instant time, int bias) {
            this.top = top;
            this.bottom = bottom;
            this.time = time;
            this.bias = bias;
            this.filled = false;
        }
    }
    
    /**
     * Trailing extremes for premium/discount zones
     */
    public static class TrailingExtremes {
        public BigDecimal top;
        public BigDecimal bottom;
        public Instant topTime;
        public Instant bottomTime;
        public Instant barTime;
        public int barIndex;
        
        public TrailingExtremes() {
            this.top = null;
            this.bottom = null;
            this.topTime = null;
            this.bottomTime = null;
            this.barTime = null;
            this.barIndex = 0;
        }
    }
    
    /**
     * Complete state for Smart Money Concepts indicator
     */
    public static class SMCState {
        // Swing structure
        public Pivot swingHigh = new Pivot();
        public Pivot swingLow = new Pivot();
        public int swingTrend = NEUTRAL;
        
        // Internal structure
        public Pivot internalHigh = new Pivot();
        public Pivot internalLow = new Pivot();
        public int internalTrend = NEUTRAL;
        
        // Equal highs/lows
        public Pivot equalHigh = new Pivot();
        public Pivot equalLow = new Pivot();
        
        // Order blocks
        public List<OrderBlock> swingOrderBlocks = new ArrayList<>();
        public List<OrderBlock> internalOrderBlocks = new ArrayList<>();
        
        // Fair value gaps
        public List<FairValueGap> fairValueGaps = new ArrayList<>();
        
        // Candle buffer for calculations
        public List<CandlestickData> candleBuffer = new ArrayList<>();
        public List<BigDecimal> parsedHighs = new ArrayList<>();
        public List<BigDecimal> parsedLows = new ArrayList<>();
        public List<BigDecimal> highs = new ArrayList<>();
        public List<BigDecimal> lows = new ArrayList<>();
        public List<Instant> times = new ArrayList<>();
        
        // Trailing extremes for zones
        public TrailingExtremes trailing = new TrailingExtremes();
        
        // Bar tracking
        public int barIndex = 0;
        
        // Volatility measure for filtering
        public BigDecimal atrValue = BigDecimal.ZERO;
        public List<BigDecimal> trueRanges = new ArrayList<>();
    }
    
    public SmartMoneyConceptsIndicator() {
        super("smart_money_concepts", "Smart Money Concepts", 
              "Comprehensive institutional trading concepts: market structure, order blocks, FVG, and more",
              Indicator.IndicatorCategory.CUSTOM);
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        // General settings
        params.put("mode", IndicatorParameter.builder("mode")
            .displayName("Mode")
            .description("Historical shows all structures, Present shows only recent ones")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Historical")
            .required(true)
            .build());
        
        params.put("style", IndicatorParameter.builder("style")
            .displayName("Style")
            .description("Color theme for the indicator")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Colored")
            .required(false)
            .build());
        
        // Swing structure
        params.put("showSwingStructure", IndicatorParameter.builder("showSwingStructure")
            .displayName("Show Swing Structure")
            .description("Display swing market structure (BOS/CHoCH)")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("swingLength", IndicatorParameter.builder("swingLength")
            .displayName("Swing Length")
            .description("Lookback period for swing point detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .minValue(10)
            .maxValue(200)
            .required(true)
            .build());
        
        params.put("showSwingBull", IndicatorParameter.builder("showSwingBull")
            .displayName("Show Bullish Swing Structure")
            .description("Which bullish structures to show: All, BOS, or CHoCH")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("All")
            .required(false)
            .build());
        
        params.put("showSwingBear", IndicatorParameter.builder("showSwingBear")
            .displayName("Show Bearish Swing Structure")
            .description("Which bearish structures to show: All, BOS, or CHoCH")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("All")
            .required(false)
            .build());
        
        params.put("swingBullColor", IndicatorParameter.builder("swingBullColor")
            .displayName("Bullish Structure Color")
            .description("Color for bullish swing structures")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#089981")
            .required(false)
            .build());
        
        params.put("swingBearColor", IndicatorParameter.builder("swingBearColor")
            .displayName("Bearish Structure Color")
            .description("Color for bearish swing structures")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#F23645")
            .required(false)
            .build());
        
        // Internal structure
        params.put("showInternalStructure", IndicatorParameter.builder("showInternalStructure")
            .displayName("Show Internal Structure")
            .description("Display internal market structure (faster timeframe)")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("internalFilterConfluence", IndicatorParameter.builder("internalFilterConfluence")
            .displayName("Internal Confluence Filter")
            .description("Filter non-significant internal structure breakouts")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("showInternalBull", IndicatorParameter.builder("showInternalBull")
            .displayName("Show Bullish Internal Structure")
            .description("Which bullish internal structures to show")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("All")
            .required(false)
            .build());
        
        params.put("showInternalBear", IndicatorParameter.builder("showInternalBear")
            .displayName("Show Bearish Internal Structure")
            .description("Which bearish internal structures to show")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("All")
            .required(false)
            .build());
        
        params.put("internalBullColor", IndicatorParameter.builder("internalBullColor")
            .displayName("Internal Bullish Color")
            .description("Color for bullish internal structures")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#089981")
            .required(false)
            .build());
        
        params.put("internalBearColor", IndicatorParameter.builder("internalBearColor")
            .displayName("Internal Bearish Color")
            .description("Color for bearish internal structures")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#F23645")
            .required(false)
            .build());
        
        // Order Blocks
        params.put("showSwingOrderBlocks", IndicatorParameter.builder("showSwingOrderBlocks")
            .displayName("Show Swing Order Blocks")
            .description("Display swing order blocks")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("swingOrderBlocksSize", IndicatorParameter.builder("swingOrderBlocksSize")
            .displayName("Swing Order Blocks Count")
            .description("Number of swing order blocks to display")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(1)
            .maxValue(20)
            .required(false)
            .build());
        
        params.put("showInternalOrderBlocks", IndicatorParameter.builder("showInternalOrderBlocks")
            .displayName("Show Internal Order Blocks")
            .description("Display internal order blocks")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("internalOrderBlocksSize", IndicatorParameter.builder("internalOrderBlocksSize")
            .displayName("Internal Order Blocks Count")
            .description("Number of internal order blocks to display")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)
            .minValue(1)
            .maxValue(20)
            .required(false)
            .build());
        
        params.put("orderBlockFilter", IndicatorParameter.builder("orderBlockFilter")
            .displayName("Order Block Filter")
            .description("Method to filter volatile order blocks: Atr or Range")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Atr")
            .required(false)
            .build());
        
        params.put("orderBlockMitigation", IndicatorParameter.builder("orderBlockMitigation")
            .displayName("Order Block Mitigation")
            .description("What triggers mitigation: Close or High/Low")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("High/Low")
            .required(false)
            .build());
        
        params.put("bullishOrderBlockColor", IndicatorParameter.builder("bullishOrderBlockColor")
            .displayName("Bullish Order Block Color")
            .description("Color for bullish order blocks")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#1848cc80")
            .required(false)
            .build());
        
        params.put("bearishOrderBlockColor", IndicatorParameter.builder("bearishOrderBlockColor")
            .displayName("Bearish Order Block Color")
            .description("Color for bearish order blocks")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#b2283380")
            .required(false)
            .build());
        
        params.put("internalBullishOrderBlockColor", IndicatorParameter.builder("internalBullishOrderBlockColor")
            .displayName("Internal Bullish OB Color")
            .description("Color for internal bullish order blocks")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#3179f580")
            .required(false)
            .build());
        
        params.put("internalBearishOrderBlockColor", IndicatorParameter.builder("internalBearishOrderBlockColor")
            .displayName("Internal Bearish OB Color")
            .description("Color for internal bearish order blocks")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#f77c8080")
            .required(false)
            .build());
        
        // Equal Highs/Lows
        params.put("showEqualHighsLows", IndicatorParameter.builder("showEqualHighsLows")
            .displayName("Show Equal Highs/Lows")
            .description("Display equal highs and lows (liquidity zones)")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("equalHighsLowsLength", IndicatorParameter.builder("equalHighsLowsLength")
            .displayName("Equal H/L Confirmation Bars")
            .description("Number of bars to confirm equal highs/lows")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(1)
            .maxValue(10)
            .required(false)
            .build());
        
        params.put("equalHighsLowsThreshold", IndicatorParameter.builder("equalHighsLowsThreshold")
            .displayName("Equal H/L Threshold")
            .description("Sensitivity threshold (0-0.5) for equal highs/lows detection")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.1)
            .minValue(0.0)
            .maxValue(0.5)
            .required(false)
            .build());
        
        // Fair Value Gaps
        params.put("showFairValueGaps", IndicatorParameter.builder("showFairValueGaps")
            .displayName("Show Fair Value Gaps")
            .description("Display fair value gaps (price imbalances)")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("fairValueGapsThreshold", IndicatorParameter.builder("fairValueGapsThreshold")
            .displayName("FVG Auto Threshold")
            .description("Automatically filter insignificant fair value gaps")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("fairValueGapsExtend", IndicatorParameter.builder("fairValueGapsExtend")
            .displayName("FVG Extension Bars")
            .description("How many bars to extend FVG boxes")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(1)
            .minValue(0)
            .maxValue(50)
            .required(false)
            .build());
        
        params.put("bullishFVGColor", IndicatorParameter.builder("bullishFVGColor")
            .displayName("Bullish FVG Color")
            .description("Color for bullish fair value gaps")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#00ff6870")
            .required(false)
            .build());
        
        params.put("bearishFVGColor", IndicatorParameter.builder("bearishFVGColor")
            .displayName("Bearish FVG Color")
            .description("Color for bearish fair value gaps")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ff000870")
            .required(false)
            .build());
        
        // Premium/Discount Zones
        params.put("showPremiumDiscountZones", IndicatorParameter.builder("showPremiumDiscountZones")
            .displayName("Show Premium/Discount Zones")
            .description("Display premium, equilibrium, and discount zones")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("premiumZoneColor", IndicatorParameter.builder("premiumZoneColor")
            .displayName("Premium Zone Color")
            .description("Color for premium zone")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#F23645")
            .required(false)
            .build());
        
        params.put("discountZoneColor", IndicatorParameter.builder("discountZoneColor")
            .displayName("Discount Zone Color")
            .description("Color for discount zone")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#089981")
            .required(false)
            .build());
        
        params.put("equilibriumZoneColor", IndicatorParameter.builder("equilibriumZoneColor")
            .displayName("Equilibrium Zone Color")
            .description("Color for equilibrium zone")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#878b94")
            .required(false)
            .build());
        
        // High/Low Swings
        params.put("showHighLowSwings", IndicatorParameter.builder("showHighLowSwings")
            .displayName("Show Strong/Weak High/Low")
            .description("Display strong and weak highs/lows based on trend")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        SMCState state = new SMCState();
        
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            int swingLength = getIntParameter(params, "swingLength", 50);
            
            // Process each historical candle
            for (CandlestickData candle : historicalCandles) {
                // Add to buffer
                state.candleBuffer.add(candle);
                state.times.add(candle.getCloseTime());
                state.highs.add(candle.getHigh());
                state.lows.add(candle.getLow());
                
                // Calculate volatility measures
                updateVolatilityMeasures(state, candle, params);
                
                // Parse highs/lows (filter out high volatility bars)
                boolean highVolatilityBar = isHighVolatilityBar(candle, state);
                state.parsedHighs.add(highVolatilityBar ? candle.getLow() : candle.getHigh());
                state.parsedLows.add(highVolatilityBar ? candle.getHigh() : candle.getLow());
                
                state.barIndex++;
                
                // Detect structures once we have enough data
                if (state.candleBuffer.size() >= swingLength * 2 + 1) {
                    processStructures(state, candle, params);
                }
                
                // Trim buffer to prevent unlimited growth
                trimBuffers(state, swingLength * 3);
            }
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object stateObj) {
        params = mergeWithDefaults(params);
        
        SMCState state = (stateObj instanceof SMCState) ? (SMCState) stateObj : new SMCState();
        
        // Add candle to buffers
        state.candleBuffer.add(candle);
        state.times.add(candle.getCloseTime());
        state.highs.add(candle.getHigh());
        state.lows.add(candle.getLow());
        
        // Update volatility
        updateVolatilityMeasures(state, candle, params);
        
        // Parse highs/lows
        boolean highVolatilityBar = isHighVolatilityBar(candle, state);
        state.parsedHighs.add(highVolatilityBar ? candle.getLow() : candle.getHigh());
        state.parsedLows.add(highVolatilityBar ? candle.getHigh() : candle.getLow());
        
        state.barIndex++;
        
        // Process structures
        processStructures(state, candle, params);
        
        // Check order block mitigation
        checkOrderBlockMitigation(state, candle, params);
        
        // Trim buffers
        int swingLength = getIntParameter(params, "swingLength", 50);
        trimBuffers(state, swingLength * 3);
        
        // Build output
        Map<String, Object> output = new HashMap<>();
        output.put("state", state);
        
        // Generate visualization elements
        List<Map<String, Object>> lines = generateLines(state, candle, params);
        List<Map<String, Object>> markers = generateMarkers(state, candle, params);
        List<Map<String, Object>> boxes = generateBoxes(state, candle, params);
        
        if (!lines.isEmpty()) {
            output.put("lines", lines);
        }
        if (!markers.isEmpty()) {
            output.put("markers", markers);
        }
        if (!boxes.isEmpty()) {
            output.put("boxes", boxes);
        }
        
        return output;
    }
    
    /**
     * Update volatility measures (ATR, True Range)
     */
    private void updateVolatilityMeasures(SMCState state, CandlestickData candle, Map<String, Object> params) {
        // Calculate true range
        BigDecimal trueRange;
        if (state.candleBuffer.isEmpty()) {
            trueRange = candle.getHigh().subtract(candle.getLow());
        } else {
            CandlestickData prev = state.candleBuffer.get(state.candleBuffer.size() - 1);
            BigDecimal hl = candle.getHigh().subtract(candle.getLow());
            BigDecimal hc = candle.getHigh().subtract(prev.getClose()).abs();
            BigDecimal lc = candle.getLow().subtract(prev.getClose()).abs();
            trueRange = hl.max(hc).max(lc);
        }
        
        state.trueRanges.add(trueRange);
        if (state.trueRanges.size() > 200) {
            state.trueRanges.remove(0);
        }
        
        // Calculate ATR (simple moving average of TR)
        if (state.trueRanges.size() >= 14) {
            BigDecimal sum = BigDecimal.ZERO;
            int period = Math.min(200, state.trueRanges.size());
            for (int i = state.trueRanges.size() - period; i < state.trueRanges.size(); i++) {
                sum = sum.add(state.trueRanges.get(i));
            }
            state.atrValue = sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Check if current bar is high volatility (used for filtering)
     */
    private boolean isHighVolatilityBar(CandlestickData candle, SMCState state) {
        if (state.atrValue.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal range = candle.getHigh().subtract(candle.getLow());
        return range.compareTo(state.atrValue.multiply(new BigDecimal("2"))) >= 0;
    }
    
    /**
     * Process market structures (pivots, BOS/CHoCH, order blocks)
     */
    private void processStructures(SMCState state, CandlestickData candle, Map<String, Object> params) {
        int swingLength = getIntParameter(params, "swingLength", 50);
        int equalLength = getIntParameter(params, "equalHighsLowsLength", 3);
        
        // Detect swing structure
        detectPivots(state, swingLength, false);
        
        // Detect internal structure (5-bar pivots)
        detectPivots(state, 5, true);
        
        // Detect equal highs/lows
        if (getBooleanParameter(params, "showEqualHighsLows", true)) {
            detectEqualHighsLows(state, equalLength, params);
        }
        
        // Detect structure breaks (BOS/CHoCH)
        detectStructureBreaks(state, candle, params, false); // Swing
        detectStructureBreaks(state, candle, params, true);  // Internal
        
        // Detect Fair Value Gaps
        if (getBooleanParameter(params, "showFairValueGaps", false)) {
            detectFairValueGaps(state, candle, params);
        }
        
        // Update trailing extremes for zones
        updateTrailingExtremes(state, candle);
    }
    
    /**
     * Detect pivot points for market structure
     */
    private void detectPivots(SMCState state, int length, boolean internal) {
        int bufferSize = state.candleBuffer.size();
        if (bufferSize < length * 2 + 1) {
            return;
        }
        
        // Check pivot at length bars ago
        int pivotIdx = bufferSize - length - 1;
        CandlestickData pivotCandle = state.candleBuffer.get(pivotIdx);
        
        Pivot high = internal ? state.internalHigh : state.swingHigh;
        Pivot low = internal ? state.internalLow : state.swingLow;
        
        // Check for pivot high
        if (isPivotHigh(state.candleBuffer, pivotIdx, length)) {
            high.lastLevel = high.currentLevel;
            high.currentLevel = pivotCandle.getHigh();
            high.crossed = false;
            high.barTime = pivotCandle.getCloseTime();
            high.barIndex = state.barIndex - length;
            
            // Update trailing for swing structures
            if (!internal) {
                state.trailing.top = high.currentLevel;
                state.trailing.topTime = high.barTime;
                state.trailing.barTime = high.barTime;
                state.trailing.barIndex = high.barIndex;
            }
        }
        
        // Check for pivot low
        if (isPivotLow(state.candleBuffer, pivotIdx, length)) {
            low.lastLevel = low.currentLevel;
            low.currentLevel = pivotCandle.getLow();
            low.crossed = false;
            low.barTime = pivotCandle.getCloseTime();
            low.barIndex = state.barIndex - length;
            
            // Update trailing for swing structures
            if (!internal) {
                state.trailing.bottom = low.currentLevel;
                state.trailing.bottomTime = low.barTime;
                state.trailing.barTime = low.barTime;
                state.trailing.barIndex = low.barIndex;
            }
        }
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
     * Detect equal highs and lows (liquidity zones)
     */
    private void detectEqualHighsLows(SMCState state, int length, Map<String, Object> params) {
        int bufferSize = state.candleBuffer.size();
        if (bufferSize < length * 2 + 1) {
            return;
        }
        
        double threshold = getDoubleParameter(params, "equalHighsLowsThreshold", 0.1);
        int pivotIdx = bufferSize - length - 1;
        CandlestickData pivotCandle = state.candleBuffer.get(pivotIdx);
        
        // Check for equal high
        if (state.equalHigh.currentLevel != null) {
            BigDecimal diff = state.equalHigh.currentLevel.subtract(pivotCandle.getHigh()).abs();
            BigDecimal thresholdValue = state.atrValue.multiply(BigDecimal.valueOf(threshold));
            
            if (diff.compareTo(thresholdValue) < 0 && isPivotHigh(state.candleBuffer, pivotIdx, length)) {
                // Found equal high - will be visualized later
            }
        }
        
        if (isPivotHigh(state.candleBuffer, pivotIdx, length)) {
            state.equalHigh.lastLevel = state.equalHigh.currentLevel;
            state.equalHigh.currentLevel = pivotCandle.getHigh();
            state.equalHigh.barTime = pivotCandle.getCloseTime();
            state.equalHigh.barIndex = state.barIndex - length;
        }
        
        // Check for equal low
        if (state.equalLow.currentLevel != null) {
            BigDecimal diff = state.equalLow.currentLevel.subtract(pivotCandle.getLow()).abs();
            BigDecimal thresholdValue = state.atrValue.multiply(BigDecimal.valueOf(threshold));
            
            if (diff.compareTo(thresholdValue) < 0 && isPivotLow(state.candleBuffer, pivotIdx, length)) {
                // Found equal low - will be visualized later
            }
        }
        
        if (isPivotLow(state.candleBuffer, pivotIdx, length)) {
            state.equalLow.lastLevel = state.equalLow.currentLevel;
            state.equalLow.currentLevel = pivotCandle.getLow();
            state.equalLow.barTime = pivotCandle.getCloseTime();
            state.equalLow.barIndex = state.barIndex - length;
        }
    }
    
    /**
     * Detect structure breaks (BOS and CHoCH)
     */
    private void detectStructureBreaks(SMCState state, CandlestickData candle, Map<String, Object> params, boolean internal) {
        Pivot high = internal ? state.internalHigh : state.swingHigh;
        Pivot low = internal ? state.internalLow : state.swingLow;
        
        BigDecimal close = candle.getClose();
        
        // Check for bullish break (close crosses above pivot high)
        if (high.currentLevel != null && !high.crossed && close.compareTo(high.currentLevel) > 0) {
            high.crossed = true;
            
            // Update trend (BOS if trend continues, CHoCH if trend changes)
            if (internal) {
                state.internalTrend = BULLISH;
            } else {
                state.swingTrend = BULLISH;
            }
            
            // Store order block if enabled
            if ((internal && getBooleanParameter(params, "showInternalOrderBlocks", true)) ||
                (!internal && getBooleanParameter(params, "showSwingOrderBlocks", false))) {
                storeOrderBlock(state, high, BULLISH, internal, params);
            }
        }
        
        // Check for bearish break (close crosses below pivot low)
        if (low.currentLevel != null && !low.crossed && close.compareTo(low.currentLevel) < 0) {
            low.crossed = true;
            
            // Update trend (BOS if trend continues, CHoCH if trend changes)
            if (internal) {
                state.internalTrend = BEARISH;
            } else {
                state.swingTrend = BEARISH;
            }
            
            // Store order block if enabled
            if ((internal && getBooleanParameter(params, "showInternalOrderBlocks", true)) ||
                (!internal && getBooleanParameter(params, "showSwingOrderBlocks", false))) {
                storeOrderBlock(state, low, BEARISH, internal, params);
            }
        }
    }
    
    /**
     * Store order block when structure breaks
     */
    private void storeOrderBlock(SMCState state, Pivot pivot, int bias, boolean internal, Map<String, Object> params) {
        // Need to find the extreme candle between the pivot and current position
        // The pivot.barIndex is relative to when pivot was detected (length bars ago)
        
        // Calculate actual indices in the buffer
        int currentBufferIdx = state.candleBuffer.size() - 1;
        
        // Get swing length to calculate pivot position
        int swingLength = internal ? 5 : getIntParameter(params, "swingLength", 50);
        
        // The pivot candle is at buffer index: current - swingLength (since pivot is detected length bars ago)
        int pivotBufferIdx = Math.max(0, currentBufferIdx - swingLength);
        
        // Search range: from pivot to current (exclusive)
        int startIdx = pivotBufferIdx;
        int endIdx = currentBufferIdx;
        
        if (startIdx >= endIdx || startIdx < 0 || endIdx >= state.candleBuffer.size()) {
            return;
        }
        
        // Find the extreme candle in the range
        int extremeIdx = startIdx;
        if (bias == BEARISH) {
            // For bearish OB, find the highest high in the range
            BigDecimal maxHigh = state.candleBuffer.get(startIdx).getHigh();
            for (int i = startIdx + 1; i < endIdx; i++) {
                BigDecimal currentHigh = state.candleBuffer.get(i).getHigh();
                if (currentHigh.compareTo(maxHigh) > 0) {
                    maxHigh = currentHigh;
                    extremeIdx = i;
                }
            }
        } else {
            // For bullish OB, find the lowest low in the range
            BigDecimal minLow = state.candleBuffer.get(startIdx).getLow();
            for (int i = startIdx + 1; i < endIdx; i++) {
                BigDecimal currentLow = state.candleBuffer.get(i).getLow();
                if (currentLow.compareTo(minLow) < 0) {
                    minLow = currentLow;
                    extremeIdx = i;
                }
            }
        }
        
        // Get the candle at extreme index
        CandlestickData extremeCandle = state.candleBuffer.get(extremeIdx);
        
        // Use parsed highs/lows if available (filters out high volatility bars)
        BigDecimal obHigh = extremeCandle.getHigh();
        BigDecimal obLow = extremeCandle.getLow();
        
        // Apply parsed values if we have them and they're different
        int parsedIdx = extremeIdx - (state.candleBuffer.size() - state.parsedHighs.size());
        if (parsedIdx >= 0 && parsedIdx < state.parsedHighs.size()) {
            obHigh = state.parsedHighs.get(parsedIdx);
            obLow = state.parsedLows.get(parsedIdx);
        }
        
        OrderBlock ob = new OrderBlock(
            obHigh,
            obLow,
            extremeCandle.getCloseTime(),
            bias
        );
        
        List<OrderBlock> blocks = internal ? state.internalOrderBlocks : state.swingOrderBlocks;
        blocks.add(0, ob); // Add to front
        
        // Limit size to prevent unlimited growth
        int maxSize = internal ? 
            getIntParameter(params, "internalOrderBlocksSize", 5) * 3 :
            getIntParameter(params, "swingOrderBlocksSize", 5) * 3;
        while (blocks.size() > maxSize) {
            blocks.remove(blocks.size() - 1);
        }
    }
    
    /**
     * Check order block mitigation and remove mitigated blocks
     */
    private void checkOrderBlockMitigation(SMCState state, CandlestickData candle, Map<String, Object> params) {
        String mitigationType = getStringParameter(params, "orderBlockMitigation", "High/Low");
        
        BigDecimal mitigationHigh = mitigationType.equals("Close") ? candle.getClose() : candle.getHigh();
        BigDecimal mitigationLow = mitigationType.equals("Close") ? candle.getClose() : candle.getLow();
        
        // Check internal order blocks
        state.internalOrderBlocks.removeIf(ob -> {
            if (ob.bias == BEARISH && mitigationHigh.compareTo(ob.high) > 0) {
                return true;
            }
            if (ob.bias == BULLISH && mitigationLow.compareTo(ob.low) < 0) {
                return true;
            }
            return false;
        });
        
        // Check swing order blocks
        state.swingOrderBlocks.removeIf(ob -> {
            if (ob.bias == BEARISH && mitigationHigh.compareTo(ob.high) > 0) {
                return true;
            }
            if (ob.bias == BULLISH && mitigationLow.compareTo(ob.low) < 0) {
                return true;
            }
            return false;
        });
    }
    
    /**
     * Detect Fair Value Gaps (3-bar imbalance pattern)
     */
    private void detectFairValueGaps(SMCState state, CandlestickData candle, Map<String, Object> params) {
        if (state.candleBuffer.size() < 3) {
            return;
        }
        
        int size = state.candleBuffer.size();
        CandlestickData c0 = state.candleBuffer.get(size - 1); // Current
        CandlestickData c1 = state.candleBuffer.get(size - 2); // Previous
        CandlestickData c2 = state.candleBuffer.get(size - 3); // Two bars ago
        
        // Bullish FVG: current low > 2-bars-ago high, and previous close > 2-bars-ago high
        if (c0.getLow().compareTo(c2.getHigh()) > 0 && c1.getClose().compareTo(c2.getHigh()) > 0) {
            FairValueGap fvg = new FairValueGap(c0.getLow(), c2.getHigh(), c1.getCloseTime(), BULLISH);
            state.fairValueGaps.add(0, fvg);
        }
        
        // Bearish FVG: current high < 2-bars-ago low, and previous close < 2-bars-ago low
        if (c0.getHigh().compareTo(c2.getLow()) < 0 && c1.getClose().compareTo(c2.getLow()) < 0) {
            FairValueGap fvg = new FairValueGap(c2.getLow(), c0.getHigh(), c1.getCloseTime(), BEARISH);
            state.fairValueGaps.add(0, fvg);
        }
        
        // Check for filled gaps and remove them
        state.fairValueGaps.removeIf(fvg -> {
            if (fvg.bias == BULLISH && candle.getLow().compareTo(fvg.bottom) < 0) {
                return true;
            }
            if (fvg.bias == BEARISH && candle.getHigh().compareTo(fvg.top) > 0) {
                return true;
            }
            return false;
        });
        
        // Limit FVG count
        while (state.fairValueGaps.size() > 20) {
            state.fairValueGaps.remove(state.fairValueGaps.size() - 1);
        }
    }
    
    /**
     * Update trailing extremes for premium/discount zones
     */
    private void updateTrailingExtremes(SMCState state, CandlestickData candle) {
        if (state.trailing.top == null) {
            state.trailing.top = candle.getHigh();
            state.trailing.topTime = candle.getCloseTime();
        } else if (candle.getHigh().compareTo(state.trailing.top) > 0) {
            state.trailing.top = candle.getHigh();
            state.trailing.topTime = candle.getCloseTime();
        }
        
        if (state.trailing.bottom == null) {
            state.trailing.bottom = candle.getLow();
            state.trailing.bottomTime = candle.getCloseTime();
        } else if (candle.getLow().compareTo(state.trailing.bottom) < 0) {
            state.trailing.bottom = candle.getLow();
            state.trailing.bottomTime = candle.getCloseTime();
        }
    }
    
    /**
     * Trim buffers to prevent unlimited growth
     */
    private void trimBuffers(SMCState state, int maxSize) {
        while (state.candleBuffer.size() > maxSize) {
            state.candleBuffer.remove(0);
            state.parsedHighs.remove(0);
            state.parsedLows.remove(0);
            state.highs.remove(0);
            state.lows.remove(0);
            state.times.remove(0);
        }
    }
    
    /**
     * Generate line visualizations (structure breaks, equal highs/lows)
     */
    private List<Map<String, Object>> generateLines(SMCState state, CandlestickData candle, Map<String, Object> params) {
        List<Map<String, Object>> lines = new ArrayList<>();
        
        // Structure break lines are generated dynamically on breaks
        // For now, we'll generate strong/weak high/low lines
        if (getBooleanParameter(params, "showHighLowSwings", true)) {
            if (state.trailing.top != null && state.trailing.topTime != null) {
                LineShape line = LineShape.builder()
                    .time1(state.trailing.topTime.getEpochSecond())
                    .time2(candle.getCloseTime().getEpochSecond())
                    .price1(state.trailing.top)
                    .price2(state.trailing.top)
                    .color(state.swingTrend == BEARISH ? "#F23645" : "#78909C")
                    .lineWidth(2)
                    .lineStyle("solid")
                    .label(state.swingTrend == BEARISH ? "Strong High" : "Weak High")
                    .build();
                lines.add(line.toMap());
            }
            
            if (state.trailing.bottom != null && state.trailing.bottomTime != null) {
                LineShape line = LineShape.builder()
                    .time1(state.trailing.bottomTime.getEpochSecond())
                    .time2(candle.getCloseTime().getEpochSecond())
                    .price1(state.trailing.bottom)
                    .price2(state.trailing.bottom)
                    .color(state.swingTrend == BULLISH ? "#089981" : "#78909C")
                    .lineWidth(2)
                    .lineStyle("solid")
                    .label(state.swingTrend == BULLISH ? "Strong Low" : "Weak Low")
                    .build();
                lines.add(line.toMap());
            }
        }
        
        return lines;
    }
    
    /**
     * Generate marker visualizations (BOS/CHoCH labels)
     */
    private List<Map<String, Object>> generateMarkers(SMCState state, CandlestickData candle, Map<String, Object> params) {
        List<Map<String, Object>> markers = new ArrayList<>();
        
        // Markers are typically generated at the time of structure break
        // This method would be called during the structure break detection
        
        return markers;
    }
    
    /**
     * Generate box visualizations (order blocks, FVG, zones)
     */
    private List<Map<String, Object>> generateBoxes(SMCState state, CandlestickData candle, Map<String, Object> params) {
        List<Map<String, Object>> boxes = new ArrayList<>();
        
        long currentTime = candle.getCloseTime().getEpochSecond();
        long extendTime = currentTime + (20 * 60); // Extend 20 bars (assume 1-min candles)
        
        // Order Blocks - Internal
        if (getBooleanParameter(params, "showInternalOrderBlocks", true)) {
            int maxBlocks = getIntParameter(params, "internalOrderBlocksSize", 5);
            String bullColor = getStringParameter(params, "internalBullishOrderBlockColor", "#3179f580");
            String bearColor = getStringParameter(params, "internalBearishOrderBlockColor", "#f77c8080");
            
            int count = 0;
            for (OrderBlock ob : state.internalOrderBlocks) {
                if (count >= maxBlocks) break;
                if (ob.mitigated) continue; // Skip mitigated blocks
                
                BoxShape box = BoxShape.builder()
                    .time1(ob.time.getEpochSecond())
                    .time2(extendTime)
                    .price1(ob.high)
                    .price2(ob.low)
                    .color(ob.bias == BULLISH ? bullColor : bearColor)
                    .borderColor(ob.bias == BULLISH ? bullColor : bearColor)
                    .label("Internal OB " + (ob.bias == BULLISH ? "Bull" : "Bear"))
                    .build();
                boxes.add(box.toMap());
                count++;
            }
        }
        
        // Order Blocks - Swing
        if (getBooleanParameter(params, "showSwingOrderBlocks", false)) {
            int maxBlocks = getIntParameter(params, "swingOrderBlocksSize", 5);
            String bullColor = getStringParameter(params, "bullishOrderBlockColor", "#1848cc80");
            String bearColor = getStringParameter(params, "bearishOrderBlockColor", "#b2283380");
            
            int count = 0;
            for (OrderBlock ob : state.swingOrderBlocks) {
                if (count >= maxBlocks) break;
                if (ob.mitigated) continue; // Skip mitigated blocks
                
                BoxShape box = BoxShape.builder()
                    .time1(ob.time.getEpochSecond())
                    .time2(extendTime)
                    .price1(ob.high)
                    .price2(ob.low)
                    .color(ob.bias == BULLISH ? bullColor : bearColor)
                    .borderColor(ob.bias == BULLISH ? bullColor : bearColor)
                    .label("Swing OB " + (ob.bias == BULLISH ? "Bull" : "Bear"))
                    .build();
                boxes.add(box.toMap());
                count++;
            }
        }
        
        // Fair Value Gaps
        if (getBooleanParameter(params, "showFairValueGaps", false)) {
            int extend = getIntParameter(params, "fairValueGapsExtend", 1);
            String bullColor = getStringParameter(params, "bullishFVGColor", "#00ff6870");
            String bearColor = getStringParameter(params, "bearishFVGColor", "#ff000870");
            
            for (FairValueGap fvg : state.fairValueGaps) {
                if (fvg.filled) continue;
                
                long fvgEndTime = fvg.time.getEpochSecond() + (extend * 60);
                
                BoxShape box = BoxShape.builder()
                    .time1(fvg.time.getEpochSecond())
                    .time2(Math.max(fvgEndTime, currentTime))
                    .price1(fvg.top)
                    .price2(fvg.bottom)
                    .color(fvg.bias == BULLISH ? bullColor : bearColor)
                    .borderColor(fvg.bias == BULLISH ? bullColor : bearColor)
                    .label("FVG")
                    .build();
                boxes.add(box.toMap());
            }
        }
        
        // Premium/Discount Zones
        if (getBooleanParameter(params, "showPremiumDiscountZones", false) && 
            state.trailing.top != null && state.trailing.bottom != null) {
            
            String premiumColor = getStringParameter(params, "premiumZoneColor", "#F2364580");
            String discountColor = getStringParameter(params, "discountZoneColor", "#08998180");
            String equilibriumColor = getStringParameter(params, "equilibriumZoneColor", "#878b9480");
            
            long zoneStartTime = state.trailing.barTime != null ? state.trailing.barTime.getEpochSecond() : currentTime - (100 * 60);
            
            // Premium zone (top 5%)
            BigDecimal premiumTop = state.trailing.top;
            BigDecimal premiumBottom = state.trailing.top.multiply(new BigDecimal("0.95"))
                .add(state.trailing.bottom.multiply(new BigDecimal("0.05")));
            
            BoxShape premiumBox = BoxShape.builder()
                .time1(zoneStartTime)
                .time2(currentTime)
                .price1(premiumTop)
                .price2(premiumBottom)
                .color(premiumColor)
                .borderColor("transparent")
                .label("Premium")
                .build();
            boxes.add(premiumBox.toMap());
            
            // Equilibrium zone (45-55%)
            BigDecimal eqTop = state.trailing.top.multiply(new BigDecimal("0.525"))
                .add(state.trailing.bottom.multiply(new BigDecimal("0.475")));
            BigDecimal eqBottom = state.trailing.top.multiply(new BigDecimal("0.475"))
                .add(state.trailing.bottom.multiply(new BigDecimal("0.525")));
            
            BoxShape eqBox = BoxShape.builder()
                .time1(zoneStartTime)
                .time2(currentTime)
                .price1(eqTop)
                .price2(eqBottom)
                .color(equilibriumColor)
                .borderColor("transparent")
                .label("Equilibrium")
                .build();
            boxes.add(eqBox.toMap());
            
            // Discount zone (bottom 5%)
            BigDecimal discountTop = state.trailing.bottom.multiply(new BigDecimal("0.95"))
                .add(state.trailing.top.multiply(new BigDecimal("0.05")));
            BigDecimal discountBottom = state.trailing.bottom;
            
            BoxShape discountBox = BoxShape.builder()
                .time1(zoneStartTime)
                .time2(currentTime)
                .price1(discountTop)
                .price2(discountBottom)
                .color(discountColor)
                .borderColor("transparent")
                .label("Discount")
                .build();
            boxes.add(discountBox.toMap());
        }
        
        return boxes;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        return new HashMap<>(); // Visual elements are provided via lines/markers/boxes
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int swingLength = getIntParameter(params, "swingLength", 50);
        return swingLength * 2 + 100; // Need enough for structure + ATR calculation
    }
}

