package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.AbstractIndicator;
import org.cloudvision.trading.bot.indicators.IndicatorParameter;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.BoxShape;
import org.cloudvision.trading.bot.visualization.LineShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fair Value Gap (FVG) Indicator
 * 
 * Fair Value Gaps are price imbalances that occur when there's a gap between candles,
 * representing inefficient price action where institutional traders may have entered.
 * 
 * DETECTION:
 * - Bullish FVG: Current low > 2-bars-ago high (gap up)
 * - Bearish FVG: Current high < 2-bars-ago low (gap down)
 * 
 * MITIGATION:
 * - Bullish FVG is mitigated when price closes back below the gap
 * - Bearish FVG is mitigated when price closes back above the gap
 * 
 * FEATURES:
 * - Auto or manual threshold filtering (% movement required)
 * - Dynamic mode: gaps shrink as price approaches them
 * - Static mode: gaps remain fixed as boxes
 * - Mitigation tracking and statistics
 * - Unmitigated levels display
 * 
 */
@Component
public class FairValueGapIndicator extends AbstractIndicator {
    
    /**
     * Fair Value Gap representation
     */
    public static class FVG {
        public BigDecimal max;      // Top of the gap
        public BigDecimal min;      // Bottom of the gap
        public boolean isBullish;   // Bullish or bearish gap
        public Instant time;        // Time when gap was created (for tracking)
        public Instant boxStartTime; // Time where box should start (2 bars ago in original)
        public int barIndex;        // Bar index when created
        public boolean mitigated;   // Has it been filled/mitigated?
        
        public FVG(BigDecimal max, BigDecimal min, boolean isBullish, Instant time, Instant boxStartTime, int barIndex) {
            this.max = max;
            this.min = min;
            this.isBullish = isBullish;
            this.time = time;
            this.boxStartTime = boxStartTime;
            this.barIndex = barIndex;
            this.mitigated = false;
        }
    }
    
    /**
     * State container for FVG indicator
     */
    public static class FVGState {
        // Candle buffer for 3-bar pattern detection
        public List<CandlestickData> candleBuffer = new ArrayList<>();
        
        // All detected FVGs
        public List<FVG> fvgRecords = new ArrayList<>();
        
        // Dynamic FVG levels (for dynamic mode)
        public BigDecimal maxBullFvg = null;
        public BigDecimal minBullFvg = null;
        public BigDecimal maxBearFvg = null;
        public BigDecimal minBearFvg = null;
        
        // Statistics
        public int bullCount = 0;
        public int bearCount = 0;
        public int bullMitigated = 0;
        public int bearMitigated = 0;
        
        // For threshold calculation
        public BigDecimal cumulativeRange = BigDecimal.ZERO; // cum((high-low)/low)
        public long thresholdBars = 0L; // number of bars accumulated into threshold average
        public int barIndex = 0;
        
        // Track last FVG time to avoid duplicates
        public Instant lastBullFvgTime = null;
        public Instant lastBearFvgTime = null;
        
        // Track candle duration for box width calculation
        public long averageCandleDuration = 0;

        // Lines to draw when FVGs are mitigated (dashed), cleared each candle
        public List<LineShape> mitigationLines = new ArrayList<>();
    }
    
    public FairValueGapIndicator() {
        super(
            "fvg",
            "Fair Value Gap",
            "Detects price imbalances (Fair Value Gaps) and tracks their mitigation. FVGs represent areas where price moved inefficiently and may return to fill the gap.",
            IndicatorCategory.OVERLAY
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new LinkedHashMap<>();
        
        params.put("thresholdPercent", IndicatorParameter.builder("thresholdPercent")
            .displayName("Threshold %")
            .description("Minimum price movement % to qualify as FVG (0 = all gaps)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.0)
            .minValue(0.0)
            .maxValue(100.0)
            .required(false)
            .build());
        
        params.put("autoThreshold", IndicatorParameter.builder("autoThreshold")
            .displayName("Auto Threshold")
            .description("Automatically calculate threshold based on average volatility")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("showLast", IndicatorParameter.builder("showLast")
            .displayName("Unmitigated Levels")
            .description("Number of unmitigated FVG levels to display (0 = all)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(0)
            .minValue(0)
            .maxValue(50)
            .required(false)
            .build());
        
        params.put("mitigationLevels", IndicatorParameter.builder("mitigationLevels")
            .displayName("Mitigation Levels")
            .description("Show dashed lines when FVGs are mitigated")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("extend", IndicatorParameter.builder("extend")
            .displayName("Box Width (Bars)")
            .description("Width of FVG boxes in bars (controls visual size, not how long they remain active)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(20)
            .minValue(5)
            .maxValue(100)
            .required(false)
            .build());
        
        params.put("dynamic", IndicatorParameter.builder("dynamic")
            .displayName("Dynamic Mode")
            .description("FVG levels shrink as price approaches them")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("bullishColor", IndicatorParameter.builder("bullishColor")
            .displayName("Bullish FVG Color")
            .description("Color for bullish fair value gaps")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#089981")
            .required(false)
            .build());
        
        params.put("bearishColor", IndicatorParameter.builder("bearishColor")
            .displayName("Bearish FVG Color")
            .description("Color for bearish fair value gaps")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#f23645")
            .required(false)
            .build());
        
        params.put("showDashboard", IndicatorParameter.builder("showDashboard")
            .displayName("Show Dashboard")
            .description("Display statistics dashboard with FVG counts and mitigation rates")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());

        // Optional timeframe parameter for future multi-timeframe support
        params.put("timeframe", IndicatorParameter.builder("timeframe")
            .displayName("Timeframe")
            .description("Optional alternate timeframe for detection (not yet implemented)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        FVGState state = new FVGState();
        
        // Process historical candles
        for (CandlestickData candle : historicalCandles) {
            onNewCandle(candle, params, state);
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object stateObj) {
        FVGState state = (FVGState) stateObj;
        if (state == null) {
            state = new FVGState();
        }
        
        // Clear mitigation lines from previous candle
        state.mitigationLines.clear();

        // Calculate average candle duration for box width
        if (state.candleBuffer.size() > 0) {
            CandlestickData lastCandle = state.candleBuffer.get(state.candleBuffer.size() - 1);
            long duration = candle.getCloseTime().getEpochSecond() - lastCandle.getCloseTime().getEpochSecond();
            if (duration > 0) {
                if (state.averageCandleDuration == 0) {
                    state.averageCandleDuration = duration;
                } else {
                    // Smooth the average
                    state.averageCandleDuration = (state.averageCandleDuration * 9 + duration) / 10;
                }
            }
        }
        
        // Add candle to buffer
        state.candleBuffer.add(candle);
        if (state.candleBuffer.size() > 3) {
            state.candleBuffer.remove(0); // Keep only last 3 candles
        }
        
        state.barIndex++;
        
        // Need at least 3 candles to detect FVG
        if (state.candleBuffer.size() < 3) {
            return buildResult(state, candle, params);
        }
        
        // Detect new FVGs
        detectFVG(state, candle, params);
        
        // Check for mitigation of existing FVGs
        checkMitigation(state, candle, params);
        
        return buildResult(state, candle, params);
    }
    
    /**
     * Detect Fair Value Gaps using the 3-bar pattern
     */
    private void detectFVG(FVGState state, CandlestickData currentCandle, Map<String, Object> params) {
        int size = state.candleBuffer.size();
        CandlestickData current = state.candleBuffer.get(size - 1);    // [0]
        CandlestickData middle = state.candleBuffer.get(size - 2);     // [1]
        CandlestickData old = state.candleBuffer.get(size - 3);        // [2]
        
        BigDecimal currentHigh = current.getHigh();
        BigDecimal currentLow = current.getLow();
        BigDecimal middleClose = middle.getClose();
        BigDecimal oldHigh = old.getHigh();
        BigDecimal oldLow = old.getLow();
        
        // Calculate threshold using CURRENT candle (as in original PineScript: threshold uses current bar's high/low)
        BigDecimal threshold = calculateThreshold(state, params, current);
        
        // Bullish FVG: low > high[2] AND close[1] > high[2] AND gap size > threshold
        boolean bullFvg = currentLow.compareTo(oldHigh) > 0 && 
                         middleClose.compareTo(oldHigh) > 0;
        
        if (bullFvg) {
            // Calculate gap size as percentage: (low - high[2]) / high[2]
            BigDecimal gapSize = currentLow.subtract(oldHigh)
                .divide(oldHigh, 8, RoundingMode.HALF_UP);
            
            if (gapSize.compareTo(threshold) > 0) {
                // Don't create duplicate if same timestamp
                if (state.lastBullFvgTime == null || !current.getCloseTime().equals(state.lastBullFvgTime)) {
                    // In original PineScript: box starts at n-2 (2 bars ago where gap begins)
                    FVG fvg = new FVG(currentLow, oldHigh, true, current.getCloseTime(), old.getCloseTime(), state.barIndex);
                    state.fvgRecords.add(0, fvg); // Add to beginning
                    state.bullCount++;
                    state.lastBullFvgTime = current.getCloseTime();
                    
                    // Update dynamic levels
                    boolean dynamic = getBooleanParameter(params, "dynamic", false);
                    if (dynamic) {
                        state.maxBullFvg = currentLow;
                        state.minBullFvg = oldHigh;
                    }
                }
            }
        }
        
        // Bearish FVG: high < low[2] AND close[1] < low[2] AND gap size > threshold
        boolean bearFvg = currentHigh.compareTo(oldLow) < 0 && 
                         middleClose.compareTo(oldLow) < 0;
        
        if (bearFvg) {
            // Calculate gap size as percentage: (low[2] - high) / high
            BigDecimal gapSize = oldLow.subtract(currentHigh)
                .divide(currentHigh, 8, RoundingMode.HALF_UP);
            
            if (gapSize.compareTo(threshold) > 0) {
                // Don't create duplicate if same timestamp
                if (state.lastBearFvgTime == null || !current.getCloseTime().equals(state.lastBearFvgTime)) {
                    // In original PineScript: box starts at n-2 (2 bars ago where gap begins)
                    FVG fvg = new FVG(oldLow, currentHigh, false, current.getCloseTime(), old.getCloseTime(), state.barIndex);
                    state.fvgRecords.add(0, fvg); // Add to beginning
                    state.bearCount++;
                    state.lastBearFvgTime = current.getCloseTime();
                    
                    // Update dynamic levels
                    boolean dynamic = getBooleanParameter(params, "dynamic", false);
                    if (dynamic) {
                        state.maxBearFvg = oldLow;
                        state.minBearFvg = currentHigh;
                    }
                }
            }
        }
        
        // Update dynamic levels if not new FVG
        boolean dynamic = getBooleanParameter(params, "dynamic", false);
        if (dynamic) {
            // Dynamic Bull FVG: shrinks as price approaches from above
            if (state.maxBullFvg != null && state.minBullFvg != null && !bullFvg) {
                BigDecimal close = currentCandle.getClose();
                // max_bull_fvg := math.max(math.min(close, max_bull_fvg), min_bull_fvg)
                BigDecimal newMax = close.min(state.maxBullFvg).max(state.minBullFvg);
                state.maxBullFvg = newMax;
            }
            
            // Dynamic Bear FVG: shrinks as price approaches from below
            if (state.maxBearFvg != null && state.minBearFvg != null && !bearFvg) {
                BigDecimal close = currentCandle.getClose();
                // min_bear_fvg := math.min(math.max(close, min_bear_fvg), max_bear_fvg)
                BigDecimal newMin = close.max(state.minBearFvg).min(state.maxBearFvg);
                state.minBearFvg = newMin;
            }
        }
    }
    
    /**
     * Calculate threshold for FVG detection
     */
    private BigDecimal calculateThreshold(FVGState state, Map<String, Object> params, CandlestickData candle) {
        boolean auto = getBooleanParameter(params, "autoThreshold", false);
        
        if (auto) {
            // Auto threshold per Pine:
            // threshold = ta.cum((high - low) / low) / bar_index
            BigDecimal high = candle.getHigh();
            BigDecimal low = candle.getLow();

            // Only accumulate when low > 0 to avoid division by zero
            if (low.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal barRange = high.subtract(low).divide(low, 8, RoundingMode.HALF_UP);
                state.cumulativeRange = state.cumulativeRange.add(barRange);
                state.thresholdBars++;
            }

            if (state.thresholdBars > 0) {
                return state.cumulativeRange.divide(new BigDecimal(state.thresholdBars), 8, RoundingMode.HALF_UP);
            } else {
                return BigDecimal.ZERO;
            }
        } else {
            // Manual threshold: convert percentage to decimal
            double thresholdPercent = getDoubleParameter(params, "thresholdPercent", 0.0);
            return new BigDecimal(thresholdPercent / 100.0);
        }
    }
    
    /**
     * Check if existing FVGs have been mitigated
     */
    private void checkMitigation(FVGState state, CandlestickData candle, Map<String, Object> params) {
        BigDecimal close = candle.getClose();
        
        // Iterate through FVGs and remove mitigated ones
        Iterator<FVG> iterator = state.fvgRecords.iterator();
        while (iterator.hasNext()) {
            FVG fvg = iterator.next();
            
            if (fvg.mitigated) {
                continue; // Already processed
            }
            
            // Bullish FVG is mitigated when close < min (price comes back down into gap)
            if (fvg.isBullish && close.compareTo(fvg.min) < 0) {
                fvg.mitigated = true;
                state.bullMitigated++;

                // Display dashed mitigation line if enabled
                boolean mitigationLevels = getBooleanParameter(params, "mitigationLevels", false);
                if (mitigationLevels) {
                    String bullishColor = getStringParameter(params, "bullishColor", "#089981");
                    LineShape line = LineShape.builder()
                        .time1(fvg.boxStartTime.getEpochSecond())
                        .price1(fvg.min)
                        .time2(candle.getCloseTime().getEpochSecond())
                        .price2(fvg.min)
                        .color(bullishColor)
                        .lineWidth(2)
                        .lineStyle("dashed")
                        .label("Bull FVG Mitigated")
                        .build();
                    state.mitigationLines.add(line);
                }

                iterator.remove();
            }
            // Bearish FVG is mitigated when close > max (price comes back up into gap)
            else if (!fvg.isBullish && close.compareTo(fvg.max) > 0) {
                fvg.mitigated = true;
                state.bearMitigated++;

                // Display dashed mitigation line if enabled
                boolean mitigationLevels = getBooleanParameter(params, "mitigationLevels", false);
                if (mitigationLevels) {
                    String bearishColor = getStringParameter(params, "bearishColor", "#f23645");
                    LineShape line = LineShape.builder()
                        .time1(fvg.boxStartTime.getEpochSecond())
                        .price1(fvg.max)
                        .time2(candle.getCloseTime().getEpochSecond())
                        .price2(fvg.max)
                        .color(bearishColor)
                        .lineWidth(2)
                        .lineStyle("dashed")
                        .label("Bear FVG Mitigated")
                        .build();
                    state.mitigationLines.add(line);
                }

                iterator.remove();
            }
        }
        
        // Limit total FVG records to prevent memory issues
        while (state.fvgRecords.size() > 500) {
            state.fvgRecords.remove(state.fvgRecords.size() - 1);
        }
    }
    
    /**
     * Build result with all FVG data, boxes, and lines
     */
    private Map<String, Object> buildResult(FVGState state, CandlestickData candle, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        Map<String, BigDecimal> values = new HashMap<>();
        
        // Dynamic mode values (for plotting)
        boolean dynamic = getBooleanParameter(params, "dynamic", false);
        if (dynamic) {
            if (state.maxBullFvg != null) {
                values.put("maxBullFvg", state.maxBullFvg);
            }
            if (state.minBullFvg != null) {
                values.put("minBullFvg", state.minBullFvg);
            }
            if (state.maxBearFvg != null) {
                values.put("maxBearFvg", state.maxBearFvg);
            }
            if (state.minBearFvg != null) {
                values.put("minBearFvg", state.minBearFvg);
            }
        }
        
        // Statistics
        values.put("bullCount", new BigDecimal(state.bullCount));
        values.put("bearCount", new BigDecimal(state.bearCount));
        values.put("bullMitigated", new BigDecimal(state.bullMitigated));
        values.put("bearMitigated", new BigDecimal(state.bearMitigated));
        
        // Calculate mitigation percentages
        if (state.bullCount > 0) {
            double bullMitigationRate = (double) state.bullMitigated / state.bullCount * 100.0;
            values.put("bullMitigationRate", new BigDecimal(bullMitigationRate).setScale(2, RoundingMode.HALF_UP));
        }
        if (state.bearCount > 0) {
            double bearMitigationRate = (double) state.bearMitigated / state.bearCount * 100.0;
            values.put("bearMitigationRate", new BigDecimal(bearMitigationRate).setScale(2, RoundingMode.HALF_UP));
        }
        
//        result.put("values", values);  
        result.put("state", state);
        
        // Add boxes for unmitigated FVGs (static mode only)
        if (!dynamic) {
            List<BoxShape> boxes = new ArrayList<>();
            String bullishColor = getStringParameter(params, "bullishColor", "#089981");
            String bearishColor = getStringParameter(params, "bearishColor", "#f23645");
            int extendBars = getIntParameter(params, "extend", 20);
            
            for (FVG fvg : state.fvgRecords) {
                if (!fvg.mitigated) {
                    String color = fvg.isBullish ? bullishColor : bearishColor;
                    // Convert hex to rgba with opacity
                    String rgbaColor = hexToRgba(color, 0.3);
                    
                    // Calculate box end time based on extend parameter
                    // Box width = extend bars * average candle duration
                    // In original: box.new(n-2, max, n+extend, min) - starts at n-2, ends at n+extend
                    long boxEndTime;
                    if (state.averageCandleDuration > 0) {
                        // Box starts at boxStartTime (2 bars ago) and extends forward
                        boxEndTime = fvg.boxStartTime.getEpochSecond() + (extendBars * state.averageCandleDuration);
                        // Don't extend beyond current time
                        boxEndTime = Math.min(boxEndTime, candle.getCloseTime().getEpochSecond());
                    } else {
                        // Fallback if we don't have duration yet
                        boxEndTime = candle.getCloseTime().getEpochSecond();
                    }
                    
                    BoxShape box = BoxShape.builder()
                        .time1(fvg.boxStartTime.getEpochSecond())
                        .time2(boxEndTime)
                        .price1(fvg.max)
                        .price2(fvg.min)
                        .color(rgbaColor)
                        .borderColor("transparent")
                        .label(fvg.isBullish ? "Bull FVG" : "Bear FVG")
                        .build();
                    boxes.add(box);
                }
            }
            
            // Convert BoxShape objects to Map for API serialization
            List<Map<String, Object>> boxMaps = boxes.stream()
                .map(BoxShape::toMap)
                .collect(Collectors.toList());
            result.put("boxes", boxMaps);
        }
        
        // Add lines for unmitigated levels
        int showLast = getIntParameter(params, "showLast", 0);
        if (showLast > 0) {
            List<LineShape> lines = new ArrayList<>();
            String bullishColor = getStringParameter(params, "bullishColor", "#089981");
            String bearishColor = getStringParameter(params, "bearishColor", "#f23645");
            
            int count = 0;
            for (FVG fvg : state.fvgRecords) {
                if (!fvg.mitigated && count < showLast) {
                    BigDecimal price = fvg.isBullish ? fvg.min : fvg.max;
                    String color = fvg.isBullish ? bullishColor : bearishColor;
                    
                    LineShape line = LineShape.builder()
                        .time1(fvg.boxStartTime.getEpochSecond())  // Start from where gap begins (2 bars ago)
                        .price1(price)
                        .time2(candle.getCloseTime().getEpochSecond())
                        .price2(price)
                        .color(color)
                        .lineWidth(2)
                        .lineStyle("solid")
                        .label(fvg.isBullish ? "Bull FVG Level" : "Bear FVG Level")
                        .build();
                    lines.add(line);
                    count++;
                }
            }
            
            // Convert LineShape objects to Map for API serialization
            List<Map<String, Object>> lineMaps = lines.stream()
                .map(LineShape::toMap)
                .collect(Collectors.toList());
            result.put("lines", lineMaps);
        }
        
        // Add mitigation lines if any
        if (!state.mitigationLines.isEmpty()) {
            List<Map<String, Object>> mitigationLineMaps = state.mitigationLines.stream()
                .map(LineShape::toMap)
                .collect(Collectors.toList());
            result.put("lines", mitigationLineMaps);
        }
        
        // Add dashboard data
        boolean showDashboard = getBooleanParameter(params, "showDashboard", false);
        if (showDashboard) {
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("bullCount", state.bullCount);
            dashboard.put("bearCount", state.bearCount);
            dashboard.put("bullMitigated", state.bullMitigated);
            dashboard.put("bearMitigated", state.bearMitigated);
            
            if (state.bullCount > 0) {
                dashboard.put("bullMitigationRate", 
                    String.format("%.1f%%", (double) state.bullMitigated / state.bullCount * 100));
            }
            if (state.bearCount > 0) {
                dashboard.put("bearMitigationRate", 
                    String.format("%.1f%%", (double) state.bearMitigated / state.bearCount * 100));
            }
            
            result.put("dashboard", dashboard);
        }
        
        return result;
    }
    
    /**
     * Convert hex color to rgba string with opacity
     */
    private String hexToRgba(String hex, double opacity) {
        // Remove # if present
        hex = hex.replace("#", "");
        
        // Parse RGB components
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        
        return String.format("rgba(%d, %d, %d, %.2f)", r, g, b, opacity);
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        boolean dynamic = getBooleanParameter(params, "dynamic", false);
        String bullishColor = getStringParameter(params, "bullishColor", "#089981");
        String bearishColor = getStringParameter(params, "bearishColor", "#f23645");
        
        if (dynamic) {
            // Dynamic mode: show as filled areas between lines
            metadata.put("maxBullFvg", IndicatorMetadata.builder("maxBullFvg")
                .displayName("Bull FVG Top")
                .asLine(bullishColor, 0)
                .build());
            
            metadata.put("minBullFvg", IndicatorMetadata.builder("minBullFvg")
                .displayName("Bull FVG Bottom")
                .asLine(bullishColor, 0)
                .build());
            
            metadata.put("maxBearFvg", IndicatorMetadata.builder("maxBearFvg")
                .displayName("Bear FVG Top")
                .asLine(bearishColor, 0)
                .build());
            
            metadata.put("minBearFvg", IndicatorMetadata.builder("minBearFvg")
                .displayName("Bear FVG Bottom")
                .asLine(bearishColor, 0)
                .build());
        } else {
            // Static mode: show as boxes - handled via shapes
            // No metadata needed as boxes are rendered via result.put("boxes", ...)
        }
        
        int showLast = getIntParameter(params, "showLast", 0);
        if (showLast > 0) {
            // Lines are handled via shapes - no metadata needed
            // Lines are rendered via result.put("lines", ...)
        }
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 3; // Need 3 candles for the detection pattern
    }
}

