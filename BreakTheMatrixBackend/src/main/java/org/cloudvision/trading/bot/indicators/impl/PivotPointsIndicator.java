package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pivot Points Indicator
 * 
 * Calculates support and resistance levels based on previous period's high, low, and close.
 * Used by floor traders to identify key price levels for potential reversals.
 * 
 * Calculations (Standard method):
 * P = (High + Low + Close) / 3
 * R1 = (2 * P) - Low
 * S1 = (2 * P) - High
 * R2 = P + (High - Low)
 * S2 = P - (High - Low)
 * R3 = High + 2 * (P - Low)
 * S3 = Low - 2 * (High - P)
 */
@Component
public class PivotPointsIndicator extends AbstractIndicator {
    
    public PivotPointsIndicator() {
        super(
            "pivotpoints",
            "Pivot Points",
            "Support and resistance levels based on previous period's price action. Shows Pivot Point (P), three resistance levels (R1-R3), and three support levels (S1-S3).",
            IndicatorCategory.OVERLAY
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("type", IndicatorParameter.builder("type")
            .displayName("Calculation Type")
            .description("Method to calculate pivot points")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("standard")
            .required(false)
            .build());
        
        params.put("showR3S3", IndicatorParameter.builder("showR3S3")
            .displayName("Show R3/S3")
            .description("Show third resistance and support levels")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        params.put("pivotColor", IndicatorParameter.builder("pivotColor")
            .displayName("Pivot Color")
            .description("Color for the pivot point line")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#808080")
            .required(false)
            .build());
        
        params.put("resistanceColor", IndicatorParameter.builder("resistanceColor")
            .displayName("Resistance Color")
            .description("Color for resistance lines")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#FF5252")
            .required(false)
            .build());
        
        params.put("supportColor", IndicatorParameter.builder("supportColor")
            .displayName("Support Color")
            .description("Color for support lines")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#4CAF50")
            .required(false)
            .build());
        
        params.put("lineWidth", IndicatorParameter.builder("lineWidth")
            .displayName("Line Width")
            .description("Width of the pivot lines")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(1)
            .minValue(1)
            .maxValue(5)
            .required(false)
            .build());
        
        params.put("lineStyle", IndicatorParameter.builder("lineStyle")
            .displayName("Line Style")
            .description("Style of the pivot lines (solid, dashed, dotted)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("dashed")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Map<String, BigDecimal> calculate(List<CandlestickData> candles, Map<String, Object> params) {
        params = mergeWithDefaults(params);
        validateParameters(params);
        
        if (candles == null || candles.isEmpty()) {
            return createEmptyResult();
        }
        
        String type = getStringParameter(params, "type", "standard");
        boolean showR3S3 = getBooleanParameter(params, "showR3S3", true);
        
        // Use the most recent candle for calculation
        CandlestickData lastCandle = candles.get(candles.size() - 1);
        
        return calculatePivotPoints(lastCandle, type, showR3S3);
    }
    
    /**
     * Calculate pivot points based on the given method
     */
    private Map<String, BigDecimal> calculatePivotPoints(CandlestickData candle, String type, boolean showR3S3) {
        BigDecimal high = candle.getHigh();
        BigDecimal low = candle.getLow();
        BigDecimal close = candle.getClose();
        BigDecimal open = candle.getOpen();
        
        Map<String, BigDecimal> result = new HashMap<>();
        
        switch (type.toLowerCase()) {
            case "standard":
                calculateStandardPivots(result, high, low, close, showR3S3);
                break;
            case "fibonacci":
                calculateFibonacciPivots(result, high, low, close, showR3S3);
                break;
            case "woodie":
                calculateWoodiePivots(result, high, low, close, open, showR3S3);
                break;
            case "camarilla":
                calculateCamarillaPivots(result, high, low, close);
                break;
            case "demark":
                calculateDeMarkPivots(result, high, low, close, open);
                break;
            default:
                calculateStandardPivots(result, high, low, close, showR3S3);
        }
        
        return result;
    }
    
    /**
     * Standard pivot point calculation
     */
    private void calculateStandardPivots(Map<String, BigDecimal> result, 
                                         BigDecimal high, BigDecimal low, BigDecimal close,
                                         boolean showR3S3) {
        // P = (High + Low + Close) / 3
        BigDecimal pivot = high.add(low).add(close)
            .divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
        
        // Range = High - Low
        BigDecimal range = high.subtract(low);
        
        // R1 = (2 * P) - Low
        BigDecimal r1 = pivot.multiply(BigDecimal.valueOf(2)).subtract(low);
        
        // S1 = (2 * P) - High
        BigDecimal s1 = pivot.multiply(BigDecimal.valueOf(2)).subtract(high);
        
        // R2 = P + Range
        BigDecimal r2 = pivot.add(range);
        
        // S2 = P - Range
        BigDecimal s2 = pivot.subtract(range);
        
        result.put("pivot", pivot);
        result.put("r1", r1);
        result.put("r2", r2);
        result.put("s1", s1);
        result.put("s2", s2);
        
        if (showR3S3) {
            // R3 = High + 2 * (P - Low)
            BigDecimal r3 = high.add(pivot.subtract(low).multiply(BigDecimal.valueOf(2)));
            
            // S3 = Low - 2 * (High - P)
            BigDecimal s3 = low.subtract(high.subtract(pivot).multiply(BigDecimal.valueOf(2)));
            
            result.put("r3", r3);
            result.put("s3", s3);
        }
    }
    
    /**
     * Fibonacci pivot point calculation
     */
    private void calculateFibonacciPivots(Map<String, BigDecimal> result,
                                          BigDecimal high, BigDecimal low, BigDecimal close,
                                          boolean showR3S3) {
        // P = (High + Low + Close) / 3
        BigDecimal pivot = high.add(low).add(close)
            .divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
        
        BigDecimal range = high.subtract(low);
        
        // Fibonacci ratios
        BigDecimal fib382 = range.multiply(new BigDecimal("0.382"));
        BigDecimal fib618 = range.multiply(new BigDecimal("0.618"));
        BigDecimal fib1000 = range;
        
        result.put("pivot", pivot);
        result.put("r1", pivot.add(fib382));
        result.put("r2", pivot.add(fib618));
        result.put("s1", pivot.subtract(fib382));
        result.put("s2", pivot.subtract(fib618));
        
        if (showR3S3) {
            result.put("r3", pivot.add(fib1000));
            result.put("s3", pivot.subtract(fib1000));
        }
    }
    
    /**
     * Woodie's pivot point calculation
     * Gives more weight to the closing price
     */
    private void calculateWoodiePivots(Map<String, BigDecimal> result,
                                       BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal open,
                                       boolean showR3S3) {
        // P = (High + Low + 2*Close) / 4
        BigDecimal pivot = high.add(low).add(close.multiply(BigDecimal.valueOf(2)))
            .divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP);
        
        BigDecimal range = high.subtract(low);
        
        // R1 = (2 * P) - Low
        BigDecimal r1 = pivot.multiply(BigDecimal.valueOf(2)).subtract(low);
        
        // S1 = (2 * P) - High
        BigDecimal s1 = pivot.multiply(BigDecimal.valueOf(2)).subtract(high);
        
        // R2 = P + Range
        BigDecimal r2 = pivot.add(range);
        
        // S2 = P - Range
        BigDecimal s2 = pivot.subtract(range);
        
        result.put("pivot", pivot);
        result.put("r1", r1);
        result.put("r2", r2);
        result.put("s1", s1);
        result.put("s2", s2);
        
        if (showR3S3) {
            BigDecimal r3 = high.add(pivot.subtract(low).multiply(BigDecimal.valueOf(2)));
            BigDecimal s3 = low.subtract(high.subtract(pivot).multiply(BigDecimal.valueOf(2)));
            
            result.put("r3", r3);
            result.put("s3", s3);
        }
    }
    
    /**
     * Camarilla pivot point calculation
     * Focuses on price returning to mean
     */
    private void calculateCamarillaPivots(Map<String, BigDecimal> result,
                                          BigDecimal high, BigDecimal low, BigDecimal close) {
        // P = (High + Low + Close) / 3
        BigDecimal pivot = high.add(low).add(close)
            .divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
        
        BigDecimal range = high.subtract(low);
        
        // Camarilla levels use specific multipliers
        BigDecimal mult1 = new BigDecimal("1.1");
        BigDecimal mult2 = new BigDecimal("1.2");
        BigDecimal mult3 = new BigDecimal("1.1");
        BigDecimal mult4 = new BigDecimal("1.5");
        
        // R4 = (High - Low) * 1.1/2 + Close
        BigDecimal r4 = range.multiply(mult3).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP).add(close);
        
        // R3 = (High - Low) * 1.1/4 + Close
        BigDecimal r3 = range.multiply(mult1).divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP).add(close);
        
        // R2 = (High - Low) * 1.1/6 + Close
        BigDecimal r2 = range.multiply(mult1).divide(BigDecimal.valueOf(6), 8, RoundingMode.HALF_UP).add(close);
        
        // R1 = (High - Low) * 1.1/12 + Close
        BigDecimal r1 = range.multiply(mult1).divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP).add(close);
        
        // S1 = Close - (High - Low) * 1.1/12
        BigDecimal s1 = close.subtract(range.multiply(mult1).divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP));
        
        // S2 = Close - (High - Low) * 1.1/6
        BigDecimal s2 = close.subtract(range.multiply(mult1).divide(BigDecimal.valueOf(6), 8, RoundingMode.HALF_UP));
        
        // S3 = Close - (High - Low) * 1.1/4
        BigDecimal s3 = close.subtract(range.multiply(mult1).divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP));
        
        // S4 = Close - (High - Low) * 1.1/2
        BigDecimal s4 = close.subtract(range.multiply(mult3).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        
        result.put("pivot", pivot);
        result.put("r1", r1);
        result.put("r2", r2);
        result.put("r3", r3);
        result.put("s1", s1);
        result.put("s2", s2);
        result.put("s3", s3);
    }
    
    /**
     * DeMark pivot point calculation
     * Considers the relationship between open and close
     */
    private void calculateDeMarkPivots(Map<String, BigDecimal> result,
                                       BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal open) {
        // X varies based on close vs open
        BigDecimal x;
        if (close.compareTo(open) < 0) {
            // Close < Open: X = High + 2*Low + Close
            x = high.add(low.multiply(BigDecimal.valueOf(2))).add(close);
        } else if (close.compareTo(open) > 0) {
            // Close > Open: X = 2*High + Low + Close
            x = high.multiply(BigDecimal.valueOf(2)).add(low).add(close);
        } else {
            // Close = Open: X = High + Low + 2*Close
            x = high.add(low).add(close.multiply(BigDecimal.valueOf(2)));
        }
        
        // P = X / 4
        BigDecimal pivot = x.divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP);
        
        // R1 = X/2 - Low
        BigDecimal r1 = x.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP).subtract(low);
        
        // S1 = X/2 - High
        BigDecimal s1 = x.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP).subtract(high);
        
        result.put("pivot", pivot);
        result.put("r1", r1);
        result.put("s1", s1);
        
        // DeMark only calculates one R and S level
        // Set R2/S2 to null or omit them
    }
    
    /**
     * Create empty result map
     */
    private Map<String, BigDecimal> createEmptyResult() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("pivot", BigDecimal.ZERO);
        result.put("r1", BigDecimal.ZERO);
        result.put("r2", BigDecimal.ZERO);
        result.put("r3", BigDecimal.ZERO);
        result.put("s1", BigDecimal.ZERO);
        result.put("s2", BigDecimal.ZERO);
        result.put("s3", BigDecimal.ZERO);
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        boolean showR3S3 = getBooleanParameter(params, "showR3S3", true);
        String pivotColor = getStringParameter(params, "pivotColor", "#808080");
        String resistanceColor = getStringParameter(params, "resistanceColor", "#FF5252");
        String supportColor = getStringParameter(params, "supportColor", "#4CAF50");
        int lineWidth = getIntParameter(params, "lineWidth", 1);
        String lineStyle = getStringParameter(params, "lineStyle", "dashed");
        String type = getStringParameter(params, "type", "standard");
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Pivot Point
        metadata.put("pivot", IndicatorMetadata.builder("pivot")
            .displayName("Pivot")
            .asLine(pivotColor, lineWidth)
            .addConfig("lineStyle", lineStyle == null ? "dashed" : lineStyle)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        // Resistance levels
        metadata.put("r1", IndicatorMetadata.builder("r1")
            .displayName("R1")
            .asLine(resistanceColor, lineWidth)
            .addConfig("lineStyle", lineStyle == null ? "dashed" : lineStyle)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        metadata.put("r2", IndicatorMetadata.builder("r2")
            .displayName("R2")
            .asLine(resistanceColor, lineWidth)
            .addConfig("lineStyle", lineStyle == null ? "dashed" : lineStyle)
            .addConfig("lineWidth", Math.max(1, lineWidth - 1)) // Slightly thinner
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        if (showR3S3 && !type.equals("demark")) {
            metadata.put("r3", IndicatorMetadata.builder("r3")
                .displayName("R3")
                .asLine(resistanceColor, lineWidth)
                .addConfig("lineStyle", lineStyle == null ? "dotted" : lineStyle)
                .addConfig("lineWidth", Math.max(1, lineWidth - 1))
                .separatePane(false)
                .paneOrder(0)
                .build());
        }
        
        // Support levels
        metadata.put("s1", IndicatorMetadata.builder("s1")
            .displayName("S1")
            .asLine(supportColor, lineWidth)
            .addConfig("lineStyle", lineStyle == null ? "dashed" : lineStyle)
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        metadata.put("s2", IndicatorMetadata.builder("s2")
            .displayName("S2")
            .asLine(supportColor, lineWidth)
            .addConfig("lineStyle", lineStyle == null ? "dashed" : lineStyle)
            .addConfig("lineWidth", Math.max(1, lineWidth - 1))
            .separatePane(false)
            .paneOrder(0)
            .build());
        
        if (showR3S3 && !type.equals("demark")) {
            metadata.put("s3", IndicatorMetadata.builder("s3")
                .displayName("S3")
                .asLine(supportColor, lineWidth)
                .addConfig("lineStyle", lineStyle == null ? "dotted" : lineStyle)
                .addConfig("lineWidth", Math.max(1, lineWidth - 1))
                .separatePane(false)
                .paneOrder(0)
                .build());
        }
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1; // Only needs one candle to calculate pivot points
    }
}

