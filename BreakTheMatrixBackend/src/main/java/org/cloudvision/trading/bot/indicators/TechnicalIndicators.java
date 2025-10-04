package org.cloudvision.trading.bot.indicators;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Technical Indicators Calculator
 * Provides common technical analysis indicators for trading strategies
 * 
 * This class is stateless and thread-safe - all methods are static
 */
public class TechnicalIndicators {
    
    private TechnicalIndicators() {
        // Utility class - prevent instantiation
    }
    
    // ==================== Moving Averages ====================
    
    /**
     * Calculate Simple Moving Average (SMA)
     * @param prices List of prices
     * @param period Number of periods
     * @return SMA value, or zero if insufficient data
     */
    public static BigDecimal calculateSMA(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        
        return sum.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate Exponential Moving Average (EMA)
     * @param prices List of prices
     * @param period Number of periods
     * @return EMA value, or zero if insufficient data
     */
    public static BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal multiplier = new BigDecimal("2").divide(
            new BigDecimal(period + 1), 8, RoundingMode.HALF_UP
        );
        
        // Start with SMA for the first EMA value
        BigDecimal ema = calculateSMA(prices.subList(0, period), period);
        
        // Calculate EMA for remaining prices
        for (int i = period; i < prices.size(); i++) {
            BigDecimal price = prices.get(i);
            ema = price.subtract(ema).multiply(multiplier).add(ema);
        }
        
        return ema;
    }
    
    /**
     * Calculate Weighted Moving Average (WMA)
     * More recent prices have higher weight
     */
    public static BigDecimal calculateWMA(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        
        for (int i = 0; i < period; i++) {
            int weight = i + 1;
            int priceIndex = prices.size() - period + i;
            weightedSum = weightedSum.add(prices.get(priceIndex).multiply(new BigDecimal(weight)));
            weightSum = weightSum.add(new BigDecimal(weight));
        }
        
        return weightedSum.divide(weightSum, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate Running Moving Average (RMA) / Modified Moving Average
     * Also known as SMMA (Smoothed Moving Average) or Wilder's Moving Average
     * Used in RSI and other indicators
     * 
     * @param prices List of prices
     * @param period Number of periods
     * @return RMA value, or zero if insufficient data
     */
    public static BigDecimal calculateRMA(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return BigDecimal.ZERO;
        }
        
        // First RMA is SMA
        BigDecimal rma = calculateSMA(prices.subList(0, period), period);
        
        // Subsequent values: RMA = (prevRMA * (period - 1) + currentPrice) / period
        BigDecimal alpha = BigDecimal.ONE.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        
        for (int i = period; i < prices.size(); i++) {
            BigDecimal price = prices.get(i);
            // RMA = alpha * price + (1 - alpha) * prevRMA
            rma = price.multiply(alpha).add(
                rma.multiply(BigDecimal.ONE.subtract(alpha))
            );
        }
        
        return rma;
    }
    
    /**
     * Calculate Hull Moving Average (HMA)
     * Responsive moving average that reduces lag
     * HMA = WMA(2 * WMA(n/2) - WMA(n), sqrt(n))
     * 
     * @param prices List of prices
     * @param period Number of periods
     * @return HMA value, or zero if insufficient data
     */
    public static BigDecimal calculateHMA(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return BigDecimal.ZERO;
        }
        
        int halfPeriod = period / 2;
        int sqrtPeriod = (int) Math.sqrt(period);
        
        if (prices.size() < period) {
            return BigDecimal.ZERO;
        }
        
        // Calculate WMA(n/2)
        BigDecimal wmaHalf = calculateWMA(prices, halfPeriod);
        
        // Calculate WMA(n)
        BigDecimal wmaFull = calculateWMA(prices, period);
        
        // Calculate 2 * WMA(n/2) - WMA(n)
        BigDecimal diff = wmaHalf.multiply(new BigDecimal("2")).subtract(wmaFull);
        
        // For the final WMA of sqrt(n), we need to build a list
        // In practice, for a single point calculation, we approximate
        // For full implementation, you'd need to maintain state
        
        // Simplified: use the diff directly (in full implementation, you'd apply WMA(sqrt(n)) to a series of diff values)
        return diff;
    }
    
    /**
     * Calculate Volume Weighted Average Price (VWAP)
     * VWAP = Sum(Price * Volume) / Sum(Volume)
     * 
     * @param prices List of prices (typically hl2 or close)
     * @param volumes List of volumes
     * @return VWAP value, or zero if insufficient data
     */
    public static BigDecimal calculateVWAP(List<BigDecimal> prices, List<BigDecimal> volumes) {
        if (prices == null || volumes == null || prices.size() != volumes.size() || prices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sumPriceVolume = BigDecimal.ZERO;
        BigDecimal sumVolume = BigDecimal.ZERO;
        
        for (int i = 0; i < prices.size(); i++) {
            BigDecimal priceVolume = prices.get(i).multiply(volumes.get(i));
            sumPriceVolume = sumPriceVolume.add(priceVolume);
            sumVolume = sumVolume.add(volumes.get(i));
        }
        
        if (sumVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return sumPriceVolume.divide(sumVolume, 8, RoundingMode.HALF_UP);
    }
    
    // ==================== Momentum Indicators ====================
    
    /**
     * Calculate Relative Strength Index (RSI)
     * @param prices List of prices
     * @param period Number of periods (typically 14)
     * @return RSI value (0-100), or null if insufficient data
     */
    public static BigDecimal calculateRSI(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period + 1 || period <= 0) {
            return null;
        }
        
        // Calculate price changes
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        
        for (int i = 1; i <= period; i++) {
            BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }
        
        avgGain = avgGain.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        
        // Smooth the averages for remaining prices
        for (int i = period + 1; i < prices.size(); i++) {
            BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
            BigDecimal gain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;
            
            avgGain = avgGain.multiply(new BigDecimal(period - 1))
                             .add(gain)
                             .divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(new BigDecimal(period - 1))
                             .add(loss)
                             .divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        }
        
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100");
        }
        
        // RSI = 100 - (100 / (1 + RS))
        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return new BigDecimal("100").subtract(
            new BigDecimal("100").divide(
                BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP
            )
        );
    }
    
    /**
     * Calculate Moving Average Convergence Divergence (MACD)
     * Returns [MACD Line, Signal Line, Histogram]
     */
    public static BigDecimal[] calculateMACD(List<BigDecimal> prices, int fastPeriod, 
                                            int slowPeriod, int signalPeriod) {
        if (prices == null || prices.size() < slowPeriod) {
            return null;
        }
        
        BigDecimal fastEMA = calculateEMA(prices, fastPeriod);
        BigDecimal slowEMA = calculateEMA(prices, slowPeriod);
        BigDecimal macdLine = fastEMA.subtract(slowEMA);
        
        // For signal line, we'd need to calculate EMA of MACD line
        // Simplified version - in production, you'd maintain MACD history
        BigDecimal signalLine = macdLine; // Placeholder
        BigDecimal histogram = macdLine.subtract(signalLine);
        
        return new BigDecimal[] { macdLine, signalLine, histogram };
    }
    
    // ==================== Volatility Indicators ====================
    
    /**
     * Calculate Standard Deviation
     */
    public static BigDecimal calculateStandardDeviation(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period || period <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal mean = calculateSMA(prices, period);
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        
        for (int i = prices.size() - period; i < prices.size(); i++) {
            BigDecimal diff = prices.get(i).subtract(mean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        
        BigDecimal variance = sumSquaredDiff.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        
        // Square root using Newton's method
        return sqrt(variance);
    }
    
    /**
     * Calculate Bollinger Bands
     * Returns [Upper Band, Middle Band (SMA), Lower Band]
     */
    public static BigDecimal[] calculateBollingerBands(List<BigDecimal> prices, int period, 
                                                      BigDecimal standardDeviations) {
        BigDecimal sma = calculateSMA(prices, period);
        BigDecimal stdDev = calculateStandardDeviation(prices, period);
        
        BigDecimal upperBand = sma.add(stdDev.multiply(standardDeviations));
        BigDecimal lowerBand = sma.subtract(stdDev.multiply(standardDeviations));
        
        return new BigDecimal[] { upperBand, sma, lowerBand };
    }
    
    /**
     * Calculate Average True Range (ATR) - measures volatility
     */
    public static BigDecimal calculateATR(List<BigDecimal> highs, List<BigDecimal> lows, 
                                         List<BigDecimal> closes, int period) {
        if (highs == null || lows == null || closes == null || 
            highs.size() < period + 1 || highs.size() != lows.size() || 
            highs.size() != closes.size()) {
            return BigDecimal.ZERO;
        }
        
        // Calculate True Range for each period
        BigDecimal sumTR = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal tr = calculateTrueRange(highs.get(i), lows.get(i), closes.get(i - 1));
            sumTR = sumTR.add(tr);
        }
        
        return sumTR.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate SuperTrend indicator
     * Returns [SuperTrend value, Direction (-1=uptrend, +1=downtrend)]
     * 
     * NOTE: Direction convention matches TradingView Pine Script:
     *   direction < 0 (negative) = UPTREND (bullish, green)
     *   direction > 0 (positive) = DOWNTREND (bearish, red)
     * 
     * @param highs List of high prices
     * @param lows List of low prices
     * @param closes List of close prices
     * @param period ATR period (typically 10)
     * @param multiplier ATR multiplier (typically 3)
     * @param previousSuperTrend Previous SuperTrend value (null for first calculation)
     * @param previousDirection Previous direction (null for first calculation)
     * @return Array [SuperTrend value, Direction]
     */
    public static BigDecimal[] calculateSuperTrend(List<BigDecimal> highs, List<BigDecimal> lows,
                                                   List<BigDecimal> closes, int period, 
                                                   BigDecimal multiplier,
                                                   BigDecimal previousSuperTrend,
                                                   BigDecimal previousDirection) {
        if (highs == null || lows == null || closes == null || 
            highs.size() < period + 1 || highs.size() != lows.size() || 
            highs.size() != closes.size()) {
            return null;
        }
        
        int lastIndex = closes.size() - 1;
        BigDecimal currentClose = closes.get(lastIndex);
        BigDecimal currentHigh = highs.get(lastIndex);
        BigDecimal currentLow = lows.get(lastIndex);
        
        // Calculate ATR
        BigDecimal atr = calculateATR(highs, lows, closes, period);
        
        // Calculate basic upper and lower bands
        BigDecimal hl2 = currentHigh.add(currentLow).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        BigDecimal basicUpperBand = hl2.add(multiplier.multiply(atr));
        BigDecimal basicLowerBand = hl2.subtract(multiplier.multiply(atr));
        
        // Calculate final upper and lower bands
        BigDecimal finalUpperBand;
        BigDecimal finalLowerBand;
        
        if (previousSuperTrend != null && lastIndex > 0) {
            BigDecimal previousClose = closes.get(lastIndex - 1);
            
            // Final upper band logic
            if (basicUpperBand.compareTo(previousSuperTrend) < 0 || 
                previousClose.compareTo(previousSuperTrend) > 0) {
                finalUpperBand = basicUpperBand;
            } else {
                finalUpperBand = previousSuperTrend;
            }
            
            // Final lower band logic
            if (basicLowerBand.compareTo(previousSuperTrend) > 0 || 
                previousClose.compareTo(previousSuperTrend) < 0) {
                finalLowerBand = basicLowerBand;
            } else {
                finalLowerBand = previousSuperTrend;
            }
        } else {
            finalUpperBand = basicUpperBand;
            finalLowerBand = basicLowerBand;
        }
        
        // Determine SuperTrend value and direction
        // IMPORTANT: Using TradingView convention: -1 = uptrend, +1 = downtrend
        BigDecimal superTrend;
        BigDecimal direction;
        
        if (previousDirection == null) {
            // First calculation - determine initial direction
            if (currentClose.compareTo(finalUpperBand) <= 0) {
                superTrend = finalUpperBand;
                direction = BigDecimal.ONE; // Downtrend (Pine Script convention)
            } else {
                superTrend = finalLowerBand;
                direction = BigDecimal.ONE.negate(); // Uptrend (Pine Script convention)
            }
        } else if (previousDirection.compareTo(BigDecimal.ZERO) < 0) {
            // Previous direction was uptrend (negative)
            if (currentClose.compareTo(finalLowerBand) <= 0) {
                superTrend = finalUpperBand;
                direction = BigDecimal.ONE; // Switch to downtrend
            } else {
                superTrend = finalLowerBand;
                direction = BigDecimal.ONE.negate(); // Stay uptrend
            }
        } else {
            // Previous direction was downtrend (positive)
            if (currentClose.compareTo(finalUpperBand) >= 0) {
                superTrend = finalLowerBand;
                direction = BigDecimal.ONE.negate(); // Switch to uptrend
            } else {
                superTrend = finalUpperBand;
                direction = BigDecimal.ONE; // Stay downtrend
            }
        }
        
        return new BigDecimal[] { superTrend, direction };
    }
    
    private static BigDecimal calculateTrueRange(BigDecimal high, BigDecimal low, 
                                                BigDecimal previousClose) {
        BigDecimal hl = high.subtract(low);
        BigDecimal hc = high.subtract(previousClose).abs();
        BigDecimal lc = low.subtract(previousClose).abs();
        
        return hl.max(hc).max(lc);
    }
    
    // ==================== Trend Indicators ====================
    
    /**
     * Calculate Average Directional Index (ADX) - measures trend strength
     * Returns value 0-100, where > 25 indicates strong trend
     */
    public static BigDecimal calculateADX(List<BigDecimal> highs, List<BigDecimal> lows, 
                                         List<BigDecimal> closes, int period) {
        // Simplified implementation - full ADX requires +DI and -DI calculation
        // This is a placeholder for the full implementation
        return new BigDecimal("50"); // Placeholder
    }
    
    // ==================== Volume Indicators ====================
    
    /**
     * Calculate On-Balance Volume (OBV)
     */
    public static BigDecimal calculateOBV(List<BigDecimal> closes, List<BigDecimal> volumes) {
        if (closes == null || volumes == null || closes.size() != volumes.size() || 
            closes.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal obv = BigDecimal.ZERO;
        for (int i = 1; i < closes.size(); i++) {
            if (closes.get(i).compareTo(closes.get(i - 1)) > 0) {
                obv = obv.add(volumes.get(i));
            } else if (closes.get(i).compareTo(closes.get(i - 1)) < 0) {
                obv = obv.subtract(volumes.get(i));
            }
        }
        
        return obv;
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Calculate square root using Newton's method
     */
    private static BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x = value;
        BigDecimal two = new BigDecimal("2");
        
        // Newton's method: x_new = (x + value/x) / 2
        for (int i = 0; i < 10; i++) {
            x = x.add(value.divide(x, 8, RoundingMode.HALF_UP))
                 .divide(two, 8, RoundingMode.HALF_UP);
        }
        
        return x;
    }
    
    /**
     * Check if a crossover occurred (fast crossed above slow)
     */
    public static boolean isCrossover(BigDecimal currentFast, BigDecimal currentSlow,
                                     BigDecimal previousFast, BigDecimal previousSlow) {
        return previousFast.compareTo(previousSlow) <= 0 && 
               currentFast.compareTo(currentSlow) > 0;
    }
    
    /**
     * Check if a crossunder occurred (fast crossed below slow)
     */
    public static boolean isCrossunder(BigDecimal currentFast, BigDecimal currentSlow,
                                      BigDecimal previousFast, BigDecimal previousSlow) {
        return previousFast.compareTo(previousSlow) >= 0 && 
               currentFast.compareTo(currentSlow) < 0;
    }
}

