package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.FillShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Market Structure Trailing Stop Indicator
 * 
 * Based on LuxAlgo's Market Structure Trailing Stop indicator
 * License: Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)
 * 
 * This indicator:
 * - Detects pivot highs and lows to identify market structure
 * - Tracks bullish and bearish market structure breaks
 * - Provides a dynamic trailing stop that adjusts based on structure changes
 * - Supports Change of Character (CHoCH) detection
 * - Shows retracement zones when price crosses to wrong side of trailing stop
 * 
 * Key Features:
 * - Pivot-based market structure detection
 * - Dynamic trailing stop line that changes color based on direction
 * - Configurable increment factor for stop adjustment
 * - Optional CHoCH-only reset mode
 * - Fill area with retracement color when price violates trailing stop
 */
@Component
public class MarketStructureTrailingStopIndicator extends AbstractIndicator {
    
    public MarketStructureTrailingStopIndicator() {
        super(
            "market_structure_trailing_stop",
            "Market Structure Trailing Stop",
            "Detects market structure breaks and provides a dynamic trailing stop based on pivot points. " +
            "Tracks bullish/bearish structures and adjusts stop levels based on price movement.",
            IndicatorCategory.TREND
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("pivotLookback", IndicatorParameter.builder("pivotLookback")
            .displayName("Pivot Lookback")
            .description("Number of candles to look back for pivot detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(14)
            .minValue(1)
            .maxValue(100)
            .required(true)
            .build());
        
        params.put("incrementFactor", IndicatorParameter.builder("incrementFactor")
            .displayName("Increment Factor %")
            .description("Percentage factor for trailing stop adjustment")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(100.0)
            .minValue(0.0)
            .maxValue(1000.0)
            .required(true)
            .build());
        
        params.put("resetOn", IndicatorParameter.builder("resetOn")
            .displayName("Reset Stop On")
            .description("When to reset the trailing stop (CHoCH = Change of Character only, ALL = all structure breaks)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("CHoCH")
            .required(true)
            .build());
        
        params.put("showStructures", IndicatorParameter.builder("showStructures")
            .displayName("Show Structures")
            .description("Display market structure break lines")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("bullColor", IndicatorParameter.builder("bullColor")
            .displayName("Bullish Color")
            .description("Color for bullish market structure and trailing stop")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#26a69a")
            .required(false)
            .build());
        
        params.put("bearColor", IndicatorParameter.builder("bearColor")
            .displayName("Bearish Color")
            .description("Color for bearish market structure and trailing stop")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ef5350")
            .required(false)
            .build());
        
        params.put("retracementColor", IndicatorParameter.builder("retracementColor")
            .displayName("Retracement Color")
            .description("Color when price crosses to wrong side of trailing stop")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#ff5d00")
            .required(false)
            .build());
        
        params.put("areaTransparency", IndicatorParameter.builder("areaTransparency")
            .displayName("Area Transparency")
            .description("Transparency for fill area (0-100)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(80)
            .minValue(0)
            .maxValue(100)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int pivotLookback = getIntParameter(params, "pivotLookback", 14);
        double incrementFactorPct = getDoubleParameter(params, "incrementFactor", 100.0);
        String resetOn = getStringParameter(params, "resetOn", "CHoCH");
        
        if (candles == null || candles.size() < pivotLookback * 2 + 1) {
            return createEmptyResult();
        }
        
        int n = candles.size();
        int currentIndex = n - 1;
        
        // Calculate full market structure
        MarketStructureResult result = calculateFullMarketStructure(candles, pivotLookback, 
                                                                     incrementFactorPct, resetOn);
        
        // Check if there's a pivot at the lookback position (currentIndex - pivotLookback)
        // This is the candle we're calculating for (the last confirmed candle)
        int pivotCheckIndex = currentIndex - pivotLookback;
        BigDecimal pivotHighValue = BigDecimal.ZERO;
        BigDecimal pivotLowValue = BigDecimal.ZERO;
        
        if (pivotCheckIndex >= 0 && pivotCheckIndex < n) {
            if (isPivotHigh(candles, pivotCheckIndex, pivotLookback)) {
                pivotHighValue = candles.get(pivotCheckIndex).getHigh();
            }
            if (isPivotLow(candles, pivotCheckIndex, pivotLookback)) {
                pivotLowValue = candles.get(pivotCheckIndex).getLow();
            }
        }
        
        // Check if price is in retracement (wrong side of trailing stop)
        // Pine Script: (close - ts) * os < 0
        BigDecimal closePrice = candles.get(currentIndex).getClose();
        boolean isRetracement = false;
        if (result.direction.intValue() != 0 && result.trailingStop.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal diff = closePrice.subtract(result.trailingStop);
            BigDecimal product = diff.multiply(result.direction);
            isRetracement = product.compareTo(BigDecimal.ZERO) < 0;
        }
        
        // Detect trend change signals
        BigDecimal trendSignal = BigDecimal.valueOf(result.marketStructureChange);
        
        return Map.of(
            "trailingStop", result.trailingStop,
            "direction", result.direction, // 1 = bullish, -1 = bearish, 0 = neutral
            "isRetracement", isRetracement ? BigDecimal.ONE : BigDecimal.ZERO,
            "pivotHigh", pivotHighValue,
            "pivotLow", pivotLowValue,
            "signal", trendSignal  // 1 = bullish signal, -1 = bearish signal, 0 = no signal
        );
    }
    
    /**
     * Progressive calculation that maintains state across candles
     * This is the recommended method for historical sequential processing
     * 
     * @return Map containing:
     *   - "values": Map<String, BigDecimal> with indicator values
     *   - "state": MarketStructureState for next iteration
     *   - "lines": List of market structure break lines (optional)
     */
    public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                    Map<String, Object> params,
                                                    Object previousState) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        int pivotLookback = getIntParameter(params, "pivotLookback", 14);
        double incrementFactorPct = getDoubleParameter(params, "incrementFactor", 100.0);
        String resetOn = getStringParameter(params, "resetOn", "CHoCH");
        boolean showStructures = getBooleanParameter(params, "showStructures", true);
        String bullColor = getStringParameter(params, "bullColor", "#26a69a");
        String bearColor = getStringParameter(params, "bearColor", "#ef5350");
        
        // Cast previousState to MarketStructureState (or create new if null)
        MarketStructureState state = (previousState instanceof MarketStructureState) 
            ? (MarketStructureState) previousState 
            : new MarketStructureState();
        
        if (candles == null || candles.size() < pivotLookback * 2 + 1) {
            return Map.of(
                "values", createEmptyResult(),
                "state", state,
                "lines", new ArrayList<>()
            );
        }
        
        int n = candles.size();
        int i = n - 1; // Current candle index
        CandlestickData currentCandle = candles.get(i);
        List<Map<String, Object>> newLines = new ArrayList<>();
        
        // Check for pivot high at (i - pivotLookback)
        if (i >= pivotLookback * 2) {
            int pivotIndex = i - pivotLookback;
            if (isPivotHigh(candles, pivotIndex, pivotLookback)) {
                state.phY = candles.get(pivotIndex).getHigh();
                state.phX = pivotIndex;
                state.phCross = false;
            }
            
            // Check for pivot low at (i - pivotLookback)
            if (isPivotLow(candles, pivotIndex, pivotLookback)) {
                state.plY = candles.get(pivotIndex).getLow();
                state.plX = pivotIndex;
                state.plCross = false;
            }
        }
        
        int ms = 0; // Market structure change signal
        int previousOs = state.os; // Store previous overall structure
        
        // Check for bullish structure (close above pivot high)
        if (state.phY != null && currentCandle.getClose().compareTo(state.phY) > 0 && !state.phCross) {
            if (resetOn.equals("CHoCH")) {
                ms = (state.os == -1) ? 1 : 0;
            } else {
                ms = 1;
            }
            
            state.phCross = true;
            state.os = 1;
            
            // Create market structure line if showing structures
            if (showStructures && ms != 0) {
                Map<String, Object> line = new HashMap<>();
                line.put("time1", candles.get(state.phX).getCloseTime().getEpochSecond());
                line.put("time2", currentCandle.getCloseTime().getEpochSecond());
                line.put("price1", state.phY);
                line.put("price2", state.phY);
                line.put("color", bullColor);
                line.put("lineWidth", 1);
                // Dashed for CHoCH, dotted for regular break
                line.put("lineStyle", (previousOs == -1) ? "dashed" : "dotted");
                line.put("label", (previousOs == -1) ? "CHoCH ↑" : "BOS ↑");
                newLines.add(line);
            }
            
            // Search for local minima
            state.btm = candles.get(state.phX).getLow();
            for (int j = state.phX; j <= i; j++) {
                BigDecimal low = candles.get(j).getLow();
                if (low.compareTo(state.btm) < 0) {
                    state.btm = low;
                }
            }
        }
        
        // Check for bearish structure (close below pivot low)
        if (state.plY != null && currentCandle.getClose().compareTo(state.plY) < 0 && !state.plCross) {
            if (resetOn.equals("CHoCH")) {
                ms = (state.os == 1) ? -1 : 0;
            } else {
                ms = -1;
            }
            
            state.plCross = true;
            state.os = -1;
            
            // Create market structure line if showing structures
            if (showStructures && ms != 0) {
                Map<String, Object> line = new HashMap<>();
                line.put("time1", candles.get(state.plX).getCloseTime().getEpochSecond());
                line.put("time2", currentCandle.getCloseTime().getEpochSecond());
                line.put("price1", state.plY);
                line.put("price2", state.plY);
                line.put("color", bearColor);
                line.put("lineWidth", 1);
                // Dashed for CHoCH, dotted for regular break
                line.put("lineStyle", (previousOs == 1) ? "dashed" : "dotted");
                line.put("label", (previousOs == 1) ? "CHoCH ↓" : "BOS ↓");
                newLines.add(line);
            }
            
            // Search for local maxima
            state.top = candles.get(state.plX).getHigh();
            for (int j = state.plX; j <= i; j++) {
                BigDecimal high = candles.get(j).getHigh();
                if (high.compareTo(state.top) > 0) {
                    state.top = high;
                }
            }
        }
        
        // Trailing max/min logic
        if (ms == 1) {
            state.max = currentCandle.getClose();
            state.min = null;
        } else if (ms == -1) {
            state.min = currentCandle.getClose();
            state.max = null;
        } else {
            if (state.max != null) {
                state.max = state.max.max(currentCandle.getClose());
            }
            if (state.min != null) {
                state.min = state.min.min(currentCandle.getClose());
            }
        }
        
        // Calculate trailing stop
        if (ms == 1) {
            state.ts = state.btm;
        } else if (ms == -1) {
            state.ts = state.top;
        } else if (state.ts != null && state.os != 0) {
            if (state.os == 1 && state.max != null && state.prevMax != null) {
                BigDecimal maxChange = state.max.subtract(state.prevMax);
                BigDecimal increment = maxChange
                    .multiply(BigDecimal.valueOf(incrementFactorPct))
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                state.ts = state.ts.add(increment);
            } else if (state.os == -1 && state.min != null && state.prevMin != null) {
                BigDecimal minChange = state.min.subtract(state.prevMin);
                BigDecimal increment = minChange
                    .multiply(BigDecimal.valueOf(incrementFactorPct))
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                state.ts = state.ts.add(increment);
            }
        }
        
        // Store previous max/min for next iteration
        state.prevMax = state.max;
        state.prevMin = state.min;
        
        // Calculate trailing stop value
        BigDecimal tsValue = state.ts != null ? state.ts : BigDecimal.ZERO;
        
        // Check if price is in retracement (wrong side of trailing stop)
        // Pine Script: (close - ts) * os < 0
        boolean isRetracement = false;
        if (state.os != 0 && tsValue.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal diff = currentCandle.getClose().subtract(tsValue);
            BigDecimal product = diff.multiply(BigDecimal.valueOf(state.os));
            isRetracement = product.compareTo(BigDecimal.ZERO) < 0;
        }
        
        // Return current values (for the current candle)
        Map<String, BigDecimal> values = Map.of(
            "trailingStop", tsValue,
            "direction", BigDecimal.valueOf(state.os),
            "isRetracement", isRetracement ? BigDecimal.ONE : BigDecimal.ZERO,
            "pivotHigh", BigDecimal.ZERO,  // Will be set via markers below
            "pivotLow", BigDecimal.ZERO,   // Will be set via markers below
            "signal", BigDecimal.valueOf(ms)  // 1 = bullish signal, -1 = bearish signal, 0 = no signal
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("values", values);
        result.put("state", state);
        
        // Add lines if any were created
        if (!newLines.isEmpty()) {
            result.put("lines", newLines);
        }
        
        // Add fill shape configuration for area between price and trailing stop
        // Get transparency parameter
        int areaTransparency = getIntParameter(params, "areaTransparency", 80);
        double opacity = (100 - areaTransparency) / 100.0;
        
        // Convert hex colors to rgba with configurable transparency
        String bullFillColor = convertHexToRgba(bullColor, opacity);
        String bearFillColor = convertHexToRgba(bearColor, opacity);
        String retracementColor = getStringParameter(params, "retracementColor", "#ff5d00");
        String retFillColor = convertHexToRgba(retracementColor, opacity);
        
        List<Map<String, Object>> fills = new ArrayList<>();
        
        // Single fill between close and trailing stop
        // Color changes based on direction and retracement status
        FillShape fill = FillShape.builder()
            .enabled(true)
            .mode("series")
            .source1("close")
            .source2("trailingStop")
            .colorMode("dynamic")
            .upFillColor(bullFillColor)        // Bullish color
            .downFillColor(bearFillColor)      // Bearish color
            .neutralFillColor(retFillColor)    // Retracement color (when isRetracement = 1)
            .fillGaps(true)
            .display(true)
            .build();
        fills.add(fill.toMap());
        
        result.put("fills", fills);
        
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        String bullColor = getStringParameter(params, "bullColor", "#26a69a");
        String bearColor = getStringParameter(params, "bearColor", "#ef5350");
        String retracementColor = getStringParameter(params, "retracementColor", "#ff5d00");
        int areaTransparency = getIntParameter(params, "areaTransparency", 80);
        double opacity = (100 - areaTransparency) / 100.0;
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Trailing stop line (changes color based on direction)
        metadata.put("trailingStop", IndicatorMetadata.builder("trailingStop")
            .displayName("Trailing Stop")
            .asLine("#9c27b0", 2) // Default purple, will be overridden by frontend based on direction
            .separatePane(false)
            .paneOrder(0)
            .addConfig("bullColor", bullColor)
            .addConfig("bearColor", bearColor)
            .addConfig("directionField", "direction") // Frontend reads this to determine color
            .build());
        
        // Fill area between price and trailing stop
        // Color logic: retracement color when isRetracement=1, otherwise direction color
        metadata.put("fill", IndicatorMetadata.builder("fill")
            .displayName("Trailing Stop Fill")
            .seriesType("fill")
            .separatePane(false)
            .paneOrder(0)
            .addConfig("mode", "series")
            .addConfig("source1", "close")
            .addConfig("source2", "trailingStop")
            .addConfig("colorMode", "dynamic")
            .addConfig("upFillColor", convertHexToRgba(bullColor, opacity))
            .addConfig("downFillColor", convertHexToRgba(bearColor, opacity))
            .addConfig("neutralFillColor", convertHexToRgba(retracementColor, opacity))
            .addConfig("fillGaps", true)
            .addConfig("display", true)
            .addConfig("directionField", "direction")
            .addConfig("retracementField", "isRetracement")
            .build());
        
        // Bullish signal marker (when signal = 1)
        metadata.put("bullishSignal", IndicatorMetadata.builder("bullishSignal")
            .displayName("Bullish Trend Change")
            .seriesType("marker")
            .separatePane(false)
            .paneOrder(0)
            .addConfig("shape", "circle")
            .addConfig("color", bullColor)
            .addConfig("position", "below")
            .addConfig("size", 4)
            .addConfig("conditionField", "signal")
            .addConfig("conditionValue", 1)
            .addConfig("priceField", "trailingStop") // Place marker at trailing stop level
            .build());
        
        // Bearish signal marker (when signal = -1)
        metadata.put("bearishSignal", IndicatorMetadata.builder("bearishSignal")
            .displayName("Bearish Trend Change")
            .seriesType("marker")
            .separatePane(false)
            .paneOrder(0)
            .addConfig("shape", "circle")
            .addConfig("color", bearColor)
            .addConfig("position", "above")
            .addConfig("size", 4)
            .addConfig("conditionField", "signal")
            .addConfig("conditionValue", -1)
            .addConfig("priceField", "trailingStop") // Place marker at trailing stop level
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int pivotLookback = getIntParameter(params, "pivotLookback", 14);
        return pivotLookback * 2 + 1;
    }
    
    /**
     * Calculate full market structure across all candles
     * 
     * Corresponds to Pine Script lines 20-93 (main calculation logic)
     */
    private MarketStructureResult calculateFullMarketStructure(List<CandlestickData> candles, 
                                                               int pivotLookback,
                                                               double incrementFactorPct,
                                                               String resetOn) {
        int n = candles.size();
        
        // State variables (Pine Script lines 20-27)
        BigDecimal phY = null; // Pivot high Y (price) - var float ph_y
        Integer phX = null;    // Pivot high X (index) - var int ph_x
        BigDecimal plY = null; // Pivot low Y (price) - var float pl_y
        Integer plX = null;    // Pivot low X (index) - var int pl_x
        
        boolean phCross = false; // var ph_cross
        boolean plCross = false; // var pl_cross
        
        BigDecimal top = null; // var float top
        BigDecimal btm = null; // var float btm
        
        BigDecimal max = null; // var float max
        BigDecimal min = null; // var float min
        BigDecimal ts = null;  // var float ts (trailing stop)
        
        int os = 0;  // Overall structure: 1 = bullish, -1 = bearish, 0 = neutral (var os)
        int ms = 0;  // Market structure change: 1 = new bullish, -1 = new bearish, 0 = no change (ms)
        
        BigDecimal prevMax = null; // For calculating max[1]
        BigDecimal prevMin = null; // For calculating min[1]
        
        int lastMs = 0; // Track the last market structure change for signal output
        
        // Iterate through candles to detect pivots and calculate trailing stop
        // Start at 2*pivotLookback to ensure we have enough candles for pivot detection
        for (int i = pivotLookback * 2; i < n; i++) {
            CandlestickData currentCandle = candles.get(i);
            ms = 0; // Reset at start of each iteration
            
            // Detect pivots and get coordinates (Pine Script lines 29-42)
            // Pine Script: ph = ta.pivothigh(length, length)
            // Pine Script: pl = ta.pivotlow(length, length)
            int pivotIndex = i - pivotLookback;
            if (isPivotHigh(candles, pivotIndex, pivotLookback)) {
                phY = candles.get(pivotIndex).getHigh();
                phX = pivotIndex;
                phCross = false;
            }
            
            if (isPivotLow(candles, pivotIndex, pivotLookback)) {
                plY = candles.get(pivotIndex).getLow();
                plX = pivotIndex;
                plCross = false;
            }
            
            // Bullish structures (Pine Script lines 44-63)
            if (phY != null && currentCandle.getClose().compareTo(phY) > 0 && !phCross) {
                if (resetOn.equals("CHoCH")) {
                    ms = (os == -1) ? 1 : 0; // Only signal on Change of Character
                } else {
                    ms = 1; // Signal on all breaks
                }
                
                phCross = true;
                os = 1;
                
                // Search for local minima between pivot and current
                btm = candles.get(phX).getLow();
                for (int j = phX; j <= i; j++) {
                    BigDecimal low = candles.get(j).getLow();
                    if (low.compareTo(btm) < 0) {
                        btm = low;
                    }
                }
            }
            
            // Bearish structures (Pine Script lines 65-82)
            if (plY != null && currentCandle.getClose().compareTo(plY) < 0 && !plCross) {
                if (resetOn.equals("CHoCH")) {
                    ms = (os == 1) ? -1 : 0; // Only signal on Change of Character
                } else {
                    ms = -1; // Signal on all breaks
                }
                
                plCross = true;
                os = -1;
                
                // Search for local maxima between pivot and current
                top = candles.get(plX).getHigh();
                for (int j = plX; j <= i; j++) {
                    BigDecimal high = candles.get(j).getHigh();
                    if (high.compareTo(top) > 0) {
                        top = high;
                    }
                }
            }
            
            // Trailing max/min logic (Pine Script lines 80-85)
            // Pine Script:
            //   if ms == 1
            //       max := close
            //   else if ms == -1
            //       min := close
            //   else
            //       max := math.max(close, max)
            //       min := math.min(close, min)
            if (ms == 1) {
                max = currentCandle.getClose();
                min = null;
            } else if (ms == -1) {
                min = currentCandle.getClose();
                max = null;
            } else {
                if (max != null) {
                    max = max.max(currentCandle.getClose());
                }
                if (min != null) {
                    min = min.min(currentCandle.getClose());
                }
            }
            
            // Calculate trailing stop (Pine Script lines 87-93)
            // Pine Script:
            //   ts := ms == 1 ? btm
            //     : ms == -1 ? top
            //     : os == 1 ? ts + (max - max[1]) * incr / 100
            //     : ts + (min - min[1]) * incr / 100
            if (ms == 1) {
                // New bullish structure - set stop to bottom
                ts = btm;
            } else if (ms == -1) {
                // New bearish structure - set stop to top
                ts = top;
            } else if (ts != null && os != 0) {
                // Update existing trailing stop
                if (os == 1 && max != null && prevMax != null) {
                    // Bullish - add increment based on max movement
                    BigDecimal maxChange = max.subtract(prevMax);
                    BigDecimal increment = maxChange
                        .multiply(BigDecimal.valueOf(incrementFactorPct))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                    ts = ts.add(increment);
                } else if (os == -1 && min != null && prevMin != null) {
                    // Bearish - add increment based on min movement
                    BigDecimal minChange = min.subtract(prevMin);
                    BigDecimal increment = minChange
                        .multiply(BigDecimal.valueOf(incrementFactorPct))
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
                    ts = ts.add(increment);
                }
            }
            
            // Store previous max/min for next iteration
            prevMax = max;
            prevMin = min;
            
            // Track the last market structure change (for signal output on the last candle)
            if (i == n - 1) {
                lastMs = ms;
            }
        }
        
        MarketStructureResult result = new MarketStructureResult();
        result.trailingStop = (ts != null) ? ts : BigDecimal.ZERO;
        result.direction = BigDecimal.valueOf(os);
        result.lastPivotHigh = phY;
        result.lastPivotLow = plY;
        result.marketStructureChange = lastMs;
        
        return result;
    }
    
    /**
     * Check if the given index is a pivot high
     */
    private boolean isPivotHigh(List<CandlestickData> candles, int index, int lookback) {
        if (index < lookback || index >= candles.size() - lookback) {
            return false;
        }
        
        BigDecimal high = candles.get(index).getHigh();
        
        // Check left side
        for (int i = index - lookback; i < index; i++) {
            if (candles.get(i).getHigh().compareTo(high) >= 0) {
                return false;
            }
        }
        
        // Check right side
        for (int i = index + 1; i <= index + lookback; i++) {
            if (candles.get(i).getHigh().compareTo(high) >= 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if the given index is a pivot low
     */
    private boolean isPivotLow(List<CandlestickData> candles, int index, int lookback) {
        if (index < lookback || index >= candles.size() - lookback) {
            return false;
        }
        
        BigDecimal low = candles.get(index).getLow();
        
        // Check left side
        for (int i = index - lookback; i < index; i++) {
            if (candles.get(i).getLow().compareTo(low) <= 0) {
                return false;
            }
        }
        
        // Check right side
        for (int i = index + 1; i <= index + lookback; i++) {
            if (candles.get(i).getLow().compareTo(low) <= 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get maximum close price in range
     */
    private BigDecimal getMaxClose(List<CandlestickData> candles, int start, int end) {
        BigDecimal max = candles.get(start).getClose();
        for (int i = start + 1; i <= end && i < candles.size(); i++) {
            max = max.max(candles.get(i).getClose());
        }
        return max;
    }
    
    /**
     * Get minimum close price in range
     */
    private BigDecimal getMinClose(List<CandlestickData> candles, int start, int end) {
        BigDecimal min = candles.get(start).getClose();
        for (int i = start + 1; i <= end && i < candles.size(); i++) {
            min = min.min(candles.get(i).getClose());
        }
        return min;
    }
    
    /**
     * Get double parameter with default value
     */
    protected double getDoubleParameter(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
    
    /**
     * Create empty result when insufficient data
     */
    private Map<String, BigDecimal> createEmptyResult() {
        return Map.of(
            "trailingStop", BigDecimal.ZERO,
            "direction", BigDecimal.ZERO,
            "isRetracement", BigDecimal.ZERO,
            "pivotHigh", BigDecimal.ZERO,
            "pivotLow", BigDecimal.ZERO,
            "signal", BigDecimal.ZERO
        );
    }
    
    /**
     * Convert hex color to rgba with specified opacity
     * Example: "#26a69a" with opacity 0.15 -> "rgba(38, 166, 154, 0.15)"
     */
    private String convertHexToRgba(String hex, double opacity) {
        // Remove # if present
        hex = hex.replace("#", "");
        
        // Parse RGB values
        int r, g, b;
        try {
            if (hex.length() == 6) {
                r = Integer.parseInt(hex.substring(0, 2), 16);
                g = Integer.parseInt(hex.substring(2, 4), 16);
                b = Integer.parseInt(hex.substring(4, 6), 16);
            } else if (hex.length() == 3) {
                // Short form like #abc -> #aabbcc
                r = Integer.parseInt(String.valueOf(hex.charAt(0)) + hex.charAt(0), 16);
                g = Integer.parseInt(String.valueOf(hex.charAt(1)) + hex.charAt(1), 16);
                b = Integer.parseInt(String.valueOf(hex.charAt(2)) + hex.charAt(2), 16);
            } else {
                // Invalid format, return default
                return "rgba(158, 158, 158, 0.1)";
            }
            
            return String.format("rgba(%d, %d, %d, %.2f)", r, g, b, opacity);
        } catch (NumberFormatException e) {
            // If parsing fails, return default gray
            return "rgba(158, 158, 158, 0.1)";
        }
    }
    
    /**
     * Result container for market structure calculation
     */
    private static class MarketStructureResult {
        BigDecimal trailingStop;
        BigDecimal direction;
        BigDecimal lastPivotHigh;
        BigDecimal lastPivotLow;
        int marketStructureChange; // 1 = bullish signal, -1 = bearish signal, 0 = no signal
    }
    
    /**
     * State container for progressive calculation
     */
    public static class MarketStructureState {
        // Pivot state
        BigDecimal phY = null;  // Pivot high price
        Integer phX = null;     // Pivot high index
        BigDecimal plY = null;  // Pivot low price
        Integer plX = null;     // Pivot low index
        
        boolean phCross = false;
        boolean plCross = false;
        
        // Structure state
        BigDecimal top = null;
        BigDecimal btm = null;
        
        // Trailing stop state
        BigDecimal max = null;
        BigDecimal min = null;
        BigDecimal ts = null;
        
        // Previous values for increment calculation
        BigDecimal prevMax = null;
        BigDecimal prevMin = null;
        
        // Overall structure: 1 = bullish, -1 = bearish, 0 = neutral
        int os = 0;
    }
}

