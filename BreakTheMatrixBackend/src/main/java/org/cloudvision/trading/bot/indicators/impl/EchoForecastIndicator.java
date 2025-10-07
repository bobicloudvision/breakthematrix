package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.BoxShape;
import org.cloudvision.trading.bot.visualization.LineShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Echo Forecast Indicator
 * 
 * This indicator uses correlation-based pattern matching to forecast future price movements.
 * It finds similar historical price patterns and projects them forward.
 * 
 * Algorithm:
 * 1. Maintains a window of historical candles (evaluation window + forecast window * 2)
 * 2. Uses the most recent 'forecast window' candles as a reference pattern
 * 3. Slides through historical data to find the window with highest correlation to reference
 * 4. Projects the pattern of changes from the matched historical window into the future
 * 
 * Three forecast construction modes:
 * - Cumulative: Sequentially add the changes from the matched pattern
 * - Mean: Use the mean of reference window plus changes
 * - Linreg: Use linear regression trend plus changes
 * 
 * Licensed under CC BY-NC-SA 4.0
 */
@Component
public class EchoForecastIndicator extends AbstractIndicator {
    
    /**
     * Internal state for Echo Forecast calculation
     */
    public static class EchoForecastState {
        // Historical price buffer
        private final LinkedList<BigDecimal> priceBuffer;
        private final LinkedList<Long> timeBuffer;
        
        // Configuration
        private final int evaluationWindow;
        private final int forecastWindow;
        
        // Forecast data
        private List<ForecastPoint> forecastPoints;
        
        // Window positions for visualization
        private WindowInfo evalWindowInfo;
        private WindowInfo refWindowInfo;
        private WindowInfo corrWindowInfo;
        
        public EchoForecastState(int evaluationWindow, int forecastWindow) {
            this.evaluationWindow = evaluationWindow;
            this.forecastWindow = forecastWindow;
            this.priceBuffer = new LinkedList<>();
            this.timeBuffer = new LinkedList<>();
            this.forecastPoints = new ArrayList<>();
        }
        
        public synchronized void addCandle(BigDecimal price, long time) {
            priceBuffer.add(price);
            timeBuffer.add(time);
            
            int maxSize = evaluationWindow + forecastWindow * 2;
            while (priceBuffer.size() > maxSize) {
                priceBuffer.removeFirst();
                timeBuffer.removeFirst();
            }
        }
        
        public synchronized int getBufferSize() {
            return priceBuffer.size();
        }
        
        public synchronized List<BigDecimal> getPriceBuffer() {
            return new ArrayList<>(priceBuffer);
        }
        
        public synchronized List<Long> getTimeBuffer() {
            return new ArrayList<>(timeBuffer);
        }
        
        public void setForecastPoints(List<ForecastPoint> points) {
            this.forecastPoints = points;
        }
        
        public List<ForecastPoint> getForecastPoints() {
            return forecastPoints;
        }
        
        public void setEvalWindowInfo(WindowInfo info) {
            this.evalWindowInfo = info;
        }
        
        public void setRefWindowInfo(WindowInfo info) {
            this.refWindowInfo = info;
        }
        
        public void setCorrWindowInfo(WindowInfo info) {
            this.corrWindowInfo = info;
        }
        
        public WindowInfo getEvalWindowInfo() {
            return evalWindowInfo;
        }
        
        public WindowInfo getRefWindowInfo() {
            return refWindowInfo;
        }
        
        public WindowInfo getCorrWindowInfo() {
            return corrWindowInfo;
        }
    }
    
    /**
     * Represents a single forecast point
     */
    public static class ForecastPoint {
        public final long time;
        public final BigDecimal price;
        
        public ForecastPoint(long time, BigDecimal price) {
            this.time = time;
            this.price = price;
        }
    }
    
    /**
     * Information about a window for visualization
     */
    public static class WindowInfo {
        public final long startTime;
        public final long endTime;
        public final BigDecimal top;
        public final BigDecimal bottom;
        
        public WindowInfo(long startTime, long endTime, BigDecimal top, BigDecimal bottom) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.top = top;
            this.bottom = bottom;
        }
    }
    
    public EchoForecastIndicator() {
        super(
            "echo_forecast",
            "Echo Forecast (LuxAlgo)",
            "Pattern-matching forecast indicator that finds similar historical patterns and projects them forward using correlation analysis.",
            IndicatorCategory.OVERLAY
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("evaluationWindow", IndicatorParameter.builder("evaluationWindow")
            .displayName("Evaluation Window")
            .description("Number of candles to search for similar patterns")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .minValue(10)
            .maxValue(200)
            .required(true)
            .build());
        
        params.put("forecastWindow", IndicatorParameter.builder("forecastWindow")
            .displayName("Forecast Window")
            .description("Number of candles to forecast into the future")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .minValue(1)
            .maxValue(200)
            .required(true)
            .build());
        
        params.put("forecastMode", IndicatorParameter.builder("forecastMode")
            .displayName("Forecast Mode")
            .description("Similarity finds similar patterns, Dissimilarity finds opposite patterns")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Similarity")
            .required(false)
            .build());
        
        params.put("constructionMode", IndicatorParameter.builder("constructionMode")
            .displayName("Forecast Construction")
            .description("Method for constructing the forecast")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("Cumulative")
            .required(false)
            .build());
        
        params.put("source", IndicatorParameter.builder("source")
            .displayName("Price Source")
            .description("Price type to use for calculation")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("close")
            .required(false)
            .build());
        
        params.put("forecastColor", IndicatorParameter.builder("forecastColor")
            .displayName("Forecast Color")
            .description("Color for the forecast lines")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("#2157f3")
            .required(false)
            .build());
        
        params.put("forecastStyle", IndicatorParameter.builder("forecastStyle")
            .displayName("Forecast Style")
            .description("Line style for forecast")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("dotted")
            .required(false)
            .build());
        
        params.put("showAreas", IndicatorParameter.builder("showAreas")
            .displayName("Show Areas")
            .description("Show evaluation, reference, and correlation windows as shaded areas (advanced)")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build());
        
        params.put("refAreaColor", IndicatorParameter.builder("refAreaColor")
            .displayName("Reference Area Color")
            .description("Color for reference window area")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(255, 93, 0, 0.2)")
            .required(false)
            .build());
        
        params.put("corrAreaColor", IndicatorParameter.builder("corrAreaColor")
            .displayName("Correlation Area Color")
            .description("Color for correlation window area")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(8, 153, 129, 0.2)")
            .required(false)
            .build());
        
        params.put("evalAreaColor", IndicatorParameter.builder("evalAreaColor")
            .displayName("Evaluation Area Color")
            .description("Color for evaluation window area")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(128, 128, 128, 0.2)")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        System.out.println("EchoForecastIndicator.onInit - historicalCandles: " + 
            (historicalCandles != null ? historicalCandles.size() : 0) + " candles");
        
        validateParameters(params);
        params = mergeWithDefaults(params);
        
        int evaluationWindow = getIntParameter(params, "evaluationWindow", 50);
        int forecastWindow = getIntParameter(params, "forecastWindow", 50);
        String source = getStringParameter(params, "source", "close");
        
        EchoForecastState state = new EchoForecastState(evaluationWindow, forecastWindow);
        
        // Process historical candles
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            for (CandlestickData candle : historicalCandles) {
                BigDecimal price = extractPrice(candle, source);
                state.addCandle(price, candle.getCloseTime().getEpochSecond());
            }
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        System.out.println("EchoForecastIndicator.onNewCandle");
        
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        params = mergeWithDefaults(params);
        
        int evaluationWindow = getIntParameter(params, "evaluationWindow", 50);
        int forecastWindow = getIntParameter(params, "forecastWindow", 50);
        String source = getStringParameter(params, "source", "close");
        String forecastMode = getStringParameter(params, "forecastMode", "Similarity");
        String constructionMode = getStringParameter(params, "constructionMode", "Cumulative");
        
        // Cast or create state
        EchoForecastState echoState = (state instanceof EchoForecastState) 
            ? (EchoForecastState) state 
            : new EchoForecastState(evaluationWindow, forecastWindow);
        
        // Add new candle to buffer
        BigDecimal price = extractPrice(candle, source);
        long time = candle.getCloseTime().getEpochSecond();
        echoState.addCandle(price, time);
        
        // Calculate forecast if we have enough data
        int minRequired = evaluationWindow + forecastWindow * 2;
        if (echoState.getBufferSize() >= minRequired) {
            calculateForecast(echoState, forecastMode, constructionMode, time);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("state", echoState);
        result.put("values", new HashMap<>());
        
        // Add forecast lines
        if (!echoState.getForecastPoints().isEmpty()) {
            List<LineShape> forecastLines = createForecastLines(echoState, params);
            List<Map<String, Object>> lineMaps = forecastLines.stream()
                .map(LineShape::toMap)
                .collect(Collectors.toList());
            result.put("lines", lineMaps);
        }
        
        // Add window areas if enabled (disabled by default - only show forecast)
        boolean showAreas = getBooleanParameter(params, "showAreas", false);
        if (showAreas && echoState.getEvalWindowInfo() != null) {
            List<BoxShape> areas = createWindowAreas(echoState, params);
            List<Map<String, Object>> boxMaps = areas.stream()
                .map(BoxShape::toMap)
                .collect(Collectors.toList());
            result.put("boxes", boxMaps);
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> onNewTick(BigDecimal price, Map<String, Object> params, Object state) {
        // Return empty result for ticks - forecast only updates on new candles
        Map<String, Object> result = new HashMap<>();
        result.put("state", state);
        result.put("values", new HashMap<>());
        return result;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        // This indicator uses custom shapes (lines and boxes) for visualization
        return new HashMap<>();
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        int evaluationWindow = getIntParameter(params, "evaluationWindow", 50);
        int forecastWindow = getIntParameter(params, "forecastWindow", 50);
        return evaluationWindow + forecastWindow * 2;
    }
    
    /**
     * Calculate the forecast based on pattern matching
     */
    private void calculateForecast(EchoForecastState state, String forecastMode, 
                                   String constructionMode, long currentTime) {
        List<BigDecimal> priceBuffer = state.getPriceBuffer();
        List<Long> timeBuffer = state.getTimeBuffer();
        int fcast = state.forecastWindow;
        int length = state.evaluationWindow;
        
        // Calculate changes (differences between consecutive prices)
        List<BigDecimal> changes = new ArrayList<>();
        for (int i = 1; i < priceBuffer.size(); i++) {
            changes.add(priceBuffer.get(i).subtract(priceBuffer.get(i - 1)));
        }
        
        // Reference window (most recent fcast candles)
        List<BigDecimal> refWindow = priceBuffer.subList(
            priceBuffer.size() - fcast, priceBuffer.size());
        
        // Find best matching window
        double bestCorrelation = forecastMode.equals("Similarity") 
            ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        int bestOffset = 0;
        
        for (int i = 0; i < length; i++) {
            int start = priceBuffer.size() - fcast * 2 - i;
            int end = start + fcast;
            
            if (start < 0 || end > priceBuffer.size()) {
                continue;
            }
            
            List<BigDecimal> evalWindow = priceBuffer.subList(start, end);
            double correlation = calculateCorrelation(refWindow, evalWindow);
            
            boolean isBetter = forecastMode.equals("Similarity") 
                ? correlation > bestCorrelation 
                : correlation < bestCorrelation;
            
            if (isBetter) {
                bestCorrelation = correlation;
                bestOffset = i;
            }
        }
        
        // Generate forecast points
        List<ForecastPoint> forecastPoints = new ArrayList<>();
        BigDecimal prevPrice = priceBuffer.get(priceBuffer.size() - 1);
        long timeStep = timeBuffer.size() > 1 
            ? timeBuffer.get(timeBuffer.size() - 1) - timeBuffer.get(timeBuffer.size() - 2) 
            : 60; // Default 1 minute
        
        // For linreg mode, calculate linear regression on reference window
        BigDecimal alpha = BigDecimal.ZERO;
        BigDecimal beta = BigDecimal.ZERO;
        if (constructionMode.equals("Linreg")) {
            LinregResult lr = calculateLinearRegression(refWindow);
            alpha = lr.alpha;
            beta = lr.beta;
        }
        
        // For mean mode, calculate mean of reference window
        BigDecimal refMean = BigDecimal.ZERO;
        if (constructionMode.equals("Mean")) {
            refMean = calculateMean(refWindow);
        }
        
        // Generate forecast using matched pattern changes
        for (int i = 0; i < fcast; i++) {
            int changeIndex = priceBuffer.size() - fcast * 2 - bestOffset + i;
            
            if (changeIndex >= 0 && changeIndex < changes.size()) {
                BigDecimal change = changes.get(changeIndex);
                BigDecimal forecastPrice;
                
                if (constructionMode.equals("Cumulative")) {
                    forecastPrice = prevPrice.add(change);
                } else if (constructionMode.equals("Mean")) {
                    forecastPrice = refMean.add(change);
                } else { // Linreg
                    // Linear regression projection at time i
                    BigDecimal linregValue = alpha.multiply(BigDecimal.valueOf(refWindow.size() + i))
                        .add(beta);
                    forecastPrice = linregValue.add(change);
                }
                
                long forecastTime = currentTime + (i + 1) * timeStep;
                forecastPoints.add(new ForecastPoint(forecastTime, forecastPrice));
                
                prevPrice = forecastPrice;
            }
        }
        
        state.setForecastPoints(forecastPoints);
        
        // Calculate window positions for visualization
        calculateWindowPositions(state, bestOffset, timeBuffer, priceBuffer);
    }
    
    /**
     * Calculate correlation coefficient between two price series
     * Correlation = Covariance(A, B) / (StdDev(A) * StdDev(B))
     */
    private double calculateCorrelation(List<BigDecimal> a, List<BigDecimal> b) {
        if (a.size() != b.size() || a.isEmpty()) {
            return 0.0;
        }
        
        double meanA = a.stream()
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(0.0);
        
        double meanB = b.stream()
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(0.0);
        
        double covariance = 0.0;
        double varianceA = 0.0;
        double varianceB = 0.0;
        
        for (int i = 0; i < a.size(); i++) {
            double devA = a.get(i).doubleValue() - meanA;
            double devB = b.get(i).doubleValue() - meanB;
            covariance += devA * devB;
            varianceA += devA * devA;
            varianceB += devB * devB;
        }
        
        double stdDevA = Math.sqrt(varianceA / a.size());
        double stdDevB = Math.sqrt(varianceB / b.size());
        
        if (stdDevA == 0.0 || stdDevB == 0.0) {
            return 0.0;
        }
        
        return (covariance / a.size()) / (stdDevA * stdDevB);
    }
    
    /**
     * Calculate linear regression parameters
     */
    private static class LinregResult {
        BigDecimal alpha;  // Slope
        BigDecimal beta;   // Intercept
    }
    
    private LinregResult calculateLinearRegression(List<BigDecimal> prices) {
        LinregResult result = new LinregResult();
        
        int n = prices.size();
        BigDecimal sumX = BigDecimal.ZERO;
        BigDecimal sumY = BigDecimal.ZERO;
        BigDecimal sumXY = BigDecimal.ZERO;
        BigDecimal sumX2 = BigDecimal.ZERO;
        
        for (int i = 0; i < n; i++) {
            BigDecimal x = BigDecimal.valueOf(i);
            BigDecimal y = prices.get(i);
            
            sumX = sumX.add(x);
            sumY = sumY.add(y);
            sumXY = sumXY.add(x.multiply(y));
            sumX2 = sumX2.add(x.multiply(x));
        }
        
        BigDecimal nBig = BigDecimal.valueOf(n);
        BigDecimal meanX = sumX.divide(nBig, 8, RoundingMode.HALF_UP);
        BigDecimal meanY = sumY.divide(nBig, 8, RoundingMode.HALF_UP);
        
        // Variance of X
        BigDecimal varianceX = sumX2.divide(nBig, 8, RoundingMode.HALF_UP)
            .subtract(meanX.multiply(meanX));
        
        // Covariance of X and Y
        BigDecimal covariance = sumXY.divide(nBig, 8, RoundingMode.HALF_UP)
            .subtract(meanX.multiply(meanY));
        
        // Alpha (slope) = Covariance(X,Y) / Variance(X)
        if (varianceX.compareTo(BigDecimal.ZERO) != 0) {
            result.alpha = covariance.divide(varianceX, 8, RoundingMode.HALF_UP);
        } else {
            result.alpha = BigDecimal.ZERO;
        }
        
        // Beta (intercept) = meanY - alpha * meanX
        result.beta = meanY.subtract(result.alpha.multiply(meanX));
        
        return result;
    }
    
    /**
     * Calculate mean of prices
     */
    private BigDecimal calculateMean(List<BigDecimal> prices) {
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = prices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(prices.size()), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate window positions for visualization
     */
    private void calculateWindowPositions(EchoForecastState state, int bestOffset, 
                                          List<Long> timeBuffer, List<BigDecimal> priceBuffer) {
        int fcast = state.forecastWindow;
        int length = state.evaluationWindow;
        
        // Find top and bottom prices across all windows
        int windowStart = priceBuffer.size() - length - fcast * 2;
        if (windowStart < 0) windowStart = 0;
        
        BigDecimal top = priceBuffer.subList(windowStart, priceBuffer.size()).stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal bottom = priceBuffer.subList(windowStart, priceBuffer.size()).stream()
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        // Evaluation window (entire search space)
        int evalStart = priceBuffer.size() - length - fcast * 2;
        int evalEnd = priceBuffer.size() - fcast;
        if (evalStart >= 0 && evalEnd < timeBuffer.size()) {
            state.setEvalWindowInfo(new WindowInfo(
                timeBuffer.get(evalStart),
                timeBuffer.get(evalEnd),
                top, bottom
            ));
        }
        
        // Reference window (most recent fcast candles)
        int refStart = priceBuffer.size() - fcast;
        int refEnd = priceBuffer.size() - 1;
        if (refStart >= 0 && refEnd < timeBuffer.size()) {
            state.setRefWindowInfo(new WindowInfo(
                timeBuffer.get(refStart),
                timeBuffer.get(refEnd),
                top, bottom
            ));
        }
        
        // Correlation window (best matching historical window)
        int corrStart = priceBuffer.size() - fcast * 2 - bestOffset;
        int corrEnd = corrStart + fcast - 1;
        if (corrStart >= 0 && corrEnd < timeBuffer.size()) {
            state.setCorrWindowInfo(new WindowInfo(
                timeBuffer.get(corrStart),
                timeBuffer.get(corrEnd),
                top, bottom
            ));
        }
    }
    
    /**
     * Create forecast line shapes
     */
    private List<LineShape> createForecastLines(EchoForecastState state, Map<String, Object> params) {
        List<LineShape> lines = new ArrayList<>();
        List<ForecastPoint> points = state.getForecastPoints();
        
        if (points.isEmpty()) {
            return lines;
        }
        
        String forecastColor = getStringParameter(params, "forecastColor", "#2157f3");
        String forecastStyle = getStringParameter(params, "forecastStyle", "dotted");
        
        // Add line from last actual price to first forecast point
        List<Long> timeBuffer = state.getTimeBuffer();
        List<BigDecimal> priceBuffer = state.getPriceBuffer();
        
        if (!timeBuffer.isEmpty() && !priceBuffer.isEmpty()) {
            long lastTime = timeBuffer.get(timeBuffer.size() - 1);
            BigDecimal lastPrice = priceBuffer.get(priceBuffer.size() - 1);
            
            lines.add(LineShape.builder()
                .time1(lastTime)
                .price1(lastPrice)
                .time2(points.get(0).time)
                .price2(points.get(0).price)
                .color(forecastColor)
                .lineWidth(2)
                .lineStyle(forecastStyle)
                .build());
        }
        
        // Add lines between forecast points
        for (int i = 0; i < points.size() - 1; i++) {
            ForecastPoint p1 = points.get(i);
            ForecastPoint p2 = points.get(i + 1);
            
            lines.add(LineShape.builder()
                .time1(p1.time)
                .price1(p1.price)
                .time2(p2.time)
                .price2(p2.price)
                .color(forecastColor)
                .lineWidth(2)
                .lineStyle(forecastStyle)
                .build());
        }
        
        return lines;
    }
    
    /**
     * Create window area boxes
     */
    private List<BoxShape> createWindowAreas(EchoForecastState state, Map<String, Object> params) {
        List<BoxShape> boxes = new ArrayList<>();
        
        String evalColor = getStringParameter(params, "evalAreaColor", "rgba(128, 128, 128, 0.2)");
        String refColor = getStringParameter(params, "refAreaColor", "rgba(255, 93, 0, 0.2)");
        String corrColor = getStringParameter(params, "corrAreaColor", "rgba(8, 153, 129, 0.2)");
        
        // Evaluation window
        if (state.getEvalWindowInfo() != null) {
            WindowInfo info = state.getEvalWindowInfo();
            boxes.add(BoxShape.builder()
                .time1(info.startTime)
                .time2(info.endTime)
                .price1(info.top)
                .price2(info.bottom)
                .color(evalColor)
                .borderColor("transparent")
                .label("Evaluation Window")
                .build());
        }
        
        // Reference window
        if (state.getRefWindowInfo() != null) {
            WindowInfo info = state.getRefWindowInfo();
            boxes.add(BoxShape.builder()
                .time1(info.startTime)
                .time2(info.endTime)
                .price1(info.top)
                .price2(info.bottom)
                .color(refColor)
                .borderColor("transparent")
                .label("Reference Window")
                .build());
        }
        
        // Correlation window (best match)
        if (state.getCorrWindowInfo() != null) {
            WindowInfo info = state.getCorrWindowInfo();
            boxes.add(BoxShape.builder()
                .time1(info.startTime)
                .time2(info.endTime)
                .price1(info.top)
                .price2(info.bottom)
                .color(corrColor)
                .borderColor("transparent")
                .label("Best Match Window")
                .build());
        }
        
        return boxes;
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

