package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.model.CandlestickData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for TrendlinesWithBreaksIndicator
 */
class TrendlinesWithBreaksIndicatorTest {
    
    private TrendlinesWithBreaksIndicator indicator;
    private Map<String, Object> defaultParams;
    
    @BeforeEach
    void setUp() {
        indicator = new TrendlinesWithBreaksIndicator();
        
        defaultParams = new HashMap<>();
        defaultParams.put("length", 14);
        defaultParams.put("mult", 1.0);
        defaultParams.put("calcMethod", "Atr");
        defaultParams.put("backpaint", true);
        defaultParams.put("showExtendedLines", true);
    }
    
    @Test
    void testIndicatorMetadata() {
        assertEquals("trendlines_breaks", indicator.getId());
        assertEquals("Trendlines with Breaks", indicator.getName());
        assertNotNull(indicator.getDescription());
    }
    
    @Test
    void testParameterDefinitions() {
        Map<String, org.cloudvision.trading.bot.indicators.IndicatorParameter> params = indicator.getParameters();
        
        assertTrue(params.containsKey("length"));
        assertTrue(params.containsKey("mult"));
        assertTrue(params.containsKey("calcMethod"));
        assertTrue(params.containsKey("backpaint"));
        assertTrue(params.containsKey("showExtendedLines"));
        
        // Check default values
        assertEquals(14, params.get("length").getDefaultValue());
        assertEquals(1.0, params.get("mult").getDefaultValue());
        assertEquals("Atr", params.get("calcMethod").getDefaultValue());
        assertEquals(true, params.get("backpaint").getDefaultValue());
    }
    
    @Test
    void testMinRequiredCandles() {
        int minCandles = indicator.getMinRequiredCandles(defaultParams);
        assertEquals(29, minCandles); // length * 2 + 1 = 14 * 2 + 1 = 29
    }
    
    @Test
    void testInitializationWithEmptyData() {
        List<CandlestickData> emptyList = new ArrayList<>();
        Object state = indicator.onInit(emptyList, defaultParams);
        
        assertNotNull(state);
        assertTrue(state instanceof TrendlinesWithBreaksIndicator.TrendlineState);
        
        TrendlinesWithBreaksIndicator.TrendlineState trendlineState = 
            (TrendlinesWithBreaksIndicator.TrendlineState) state;
        
        assertEquals(0, trendlineState.barIndex);
        assertTrue(trendlineState.candleBuffer.isEmpty());
    }
    
    @Test
    void testInitializationWithHistoricalData() {
        List<CandlestickData> candles = generateTestCandles(50, 50000.0, 100.0);
        Object state = indicator.onInit(candles, defaultParams);
        
        assertNotNull(state);
        assertTrue(state instanceof TrendlinesWithBreaksIndicator.TrendlineState);
        
        TrendlinesWithBreaksIndicator.TrendlineState trendlineState = 
            (TrendlinesWithBreaksIndicator.TrendlineState) state;
        
        assertEquals(50, trendlineState.barIndex);
        assertEquals(50, trendlineState.candleBuffer.size());
    }
    
    @Test
    void testOnNewCandleWithoutState() {
        CandlestickData candle = createCandle("BTCUSDT", 50000.0, 50100.0, 49900.0, 50050.0, 1000.0, 0);
        
        Map<String, Object> result = indicator.onNewCandle(candle, defaultParams, null);
        
        assertNotNull(result);
        assertTrue(result.containsKey("values"));
        assertTrue(result.containsKey("state"));
        
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        
        assertTrue(values.containsKey("upperTrendline"));
        assertTrue(values.containsKey("lowerTrendline"));
        assertTrue(values.containsKey("slope"));
        assertTrue(values.containsKey("upperBreakout"));
        assertTrue(values.containsKey("lowerBreakout"));
    }
    
    @Test
    void testPivotDetectionWithTrendingData() {
        // Create uptrending data
        List<CandlestickData> candles = generateTrendingCandles(50, 50000.0, 200.0, true);
        Object state = indicator.onInit(candles, defaultParams);
        
        assertNotNull(state);
        TrendlinesWithBreaksIndicator.TrendlineState trendlineState = 
            (TrendlinesWithBreaksIndicator.TrendlineState) state;
        
        // Should have detected some pivots
        assertTrue(trendlineState.lastPivotLowPrice != null || trendlineState.lastPivotHighPrice != null);
    }
    
    @Test
    void testBreakoutDetection() {
        // Initialize with historical data
        List<CandlestickData> candles = generateTestCandles(50, 50000.0, 100.0);
        Object state = indicator.onInit(candles, defaultParams);
        
        // Add candles that should trigger breakouts
        CandlestickData breakoutCandle = createCandle("BTCUSDT", 52000.0, 52500.0, 51900.0, 52400.0, 2000.0, 51);
        Map<String, Object> result = indicator.onNewCandle(breakoutCandle, defaultParams, state);
        
        assertNotNull(result);
        assertTrue(result.containsKey("values"));
        
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
        
        // Check breakout values
        assertNotNull(values.get("upperBreakout"));
        assertNotNull(values.get("lowerBreakout"));
    }
    
    @Test
    void testSlopeCalculationMethods() {
        List<CandlestickData> candles = generateTestCandles(30, 50000.0, 100.0);
        
        // Test ATR method
        defaultParams.put("calcMethod", "Atr");
        Object stateATR = indicator.onInit(candles, defaultParams);
        CandlestickData newCandle = createCandle("BTCUSDT", 50000.0, 50100.0, 49900.0, 50050.0, 1000.0, 31);
        Map<String, Object> resultATR = indicator.onNewCandle(newCandle, defaultParams, stateATR);
        
        // Test Stdev method
        defaultParams.put("calcMethod", "Stdev");
        Object stateStdev = indicator.onInit(candles, defaultParams);
        Map<String, Object> resultStdev = indicator.onNewCandle(newCandle, defaultParams, stateStdev);
        
        // Test Linreg method
        defaultParams.put("calcMethod", "Linreg");
        Object stateLinreg = indicator.onInit(candles, defaultParams);
        Map<String, Object> resultLinreg = indicator.onNewCandle(newCandle, defaultParams, stateLinreg);
        
        // All methods should return valid results
        assertNotNull(resultATR);
        assertNotNull(resultStdev);
        assertNotNull(resultLinreg);
        
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesATR = (Map<String, BigDecimal>) resultATR.get("values");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesStdev = (Map<String, BigDecimal>) resultStdev.get("values");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesLinreg = (Map<String, BigDecimal>) resultLinreg.get("values");
        
        // Slopes may differ but should all be non-negative
        assertTrue(valuesATR.get("slope").compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(valuesStdev.get("slope").compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(valuesLinreg.get("slope").compareTo(BigDecimal.ZERO) >= 0);
    }
    
    @Test
    void testBackpaintingEffect() {
        List<CandlestickData> candles = generateTestCandles(30, 50000.0, 100.0);
        CandlestickData newCandle = createCandle("BTCUSDT", 50000.0, 50100.0, 49900.0, 50050.0, 1000.0, 31);
        
        // Test with backpaint enabled
        defaultParams.put("backpaint", true);
        Object stateBackpaint = indicator.onInit(candles, defaultParams);
        Map<String, Object> resultBackpaint = indicator.onNewCandle(newCandle, defaultParams, stateBackpaint);
        
        // Test with backpaint disabled
        defaultParams.put("backpaint", false);
        Object stateNoBackpaint = indicator.onInit(candles, defaultParams);
        Map<String, Object> resultNoBackpaint = indicator.onNewCandle(newCandle, defaultParams, stateNoBackpaint);
        
        // Both should return valid results
        assertNotNull(resultBackpaint);
        assertNotNull(resultNoBackpaint);
        
        // Values might differ due to offset
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesBackpaint = (Map<String, BigDecimal>) resultBackpaint.get("values");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesNoBackpaint = (Map<String, BigDecimal>) resultNoBackpaint.get("values");
        
        assertNotNull(valuesBackpaint.get("upperTrendline"));
        assertNotNull(valuesNoBackpaint.get("upperTrendline"));
    }
    
    @Test
    void testExtendedLinesGeneration() {
        List<CandlestickData> candles = generateTestCandles(30, 50000.0, 100.0);
        Object state = indicator.onInit(candles, defaultParams);
        
        CandlestickData newCandle = createCandle("BTCUSDT", 50000.0, 50100.0, 49900.0, 50050.0, 1000.0, 31);
        
        // With extended lines
        defaultParams.put("showExtendedLines", true);
        Map<String, Object> resultWithLines = indicator.onNewCandle(newCandle, defaultParams, state);
        
        // Without extended lines
        defaultParams.put("showExtendedLines", false);
        Map<String, Object> resultWithoutLines = indicator.onNewCandle(newCandle, defaultParams, state);
        
        assertTrue(resultWithLines.containsKey("lines"));
        assertTrue(resultWithoutLines.containsKey("lines"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> linesEnabled = (List<Map<String, Object>>) resultWithLines.get("lines");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> linesDisabled = (List<Map<String, Object>>) resultWithoutLines.get("lines");
        
        // Disabled should have no lines
        assertTrue(linesDisabled.isEmpty());
    }
    
    @Test
    void testMultiplierEffect() {
        List<CandlestickData> candles = generateTestCandles(30, 50000.0, 100.0);
        CandlestickData newCandle = createCandle("BTCUSDT", 50000.0, 50100.0, 49900.0, 50050.0, 1000.0, 31);
        
        // Low multiplier
        defaultParams.put("mult", 0.5);
        Object stateLowMult = indicator.onInit(candles, defaultParams);
        Map<String, Object> resultLowMult = indicator.onNewCandle(newCandle, defaultParams, stateLowMult);
        
        // High multiplier
        defaultParams.put("mult", 2.0);
        Object stateHighMult = indicator.onInit(candles, defaultParams);
        Map<String, Object> resultHighMult = indicator.onNewCandle(newCandle, defaultParams, stateHighMult);
        
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesLow = (Map<String, BigDecimal>) resultLowMult.get("values");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> valuesHigh = (Map<String, BigDecimal>) resultHighMult.get("values");
        
        // Higher multiplier should produce larger slope
        assertTrue(valuesHigh.get("slope").compareTo(valuesLow.get("slope")) > 0);
    }
    
    @Test
    void testShapeGeneration() {
        List<CandlestickData> candles = generateTestCandles(30, 50000.0, 100.0);
        Object state = indicator.onInit(candles, defaultParams);
        
        // Add multiple candles to trigger potential breakouts
        for (int i = 0; i < 10; i++) {
            double price = 50000.0 + (i * 100.0);
            CandlestickData candle = createCandle("BTCUSDT", price, price + 100, price - 50, price + 50, 1000.0, 31 + i);
            Map<String, Object> result = indicator.onNewCandle(candle, defaultParams, state);
            state = result.get("state");
            
            assertTrue(result.containsKey("shapes"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shapes = (List<Map<String, Object>>) result.get("shapes");
            assertNotNull(shapes);
        }
    }
    
    // ==================== Helper Methods ====================
    
    private List<CandlestickData> generateTestCandles(int count, double basePrice, double volatility) {
        List<CandlestickData> candles = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for reproducibility
        
        for (int i = 0; i < count; i++) {
            double open = basePrice + (random.nextDouble() - 0.5) * volatility;
            double close = open + (random.nextDouble() - 0.5) * volatility;
            double high = Math.max(open, close) + random.nextDouble() * volatility / 2;
            double low = Math.min(open, close) - random.nextDouble() * volatility / 2;
            
            candles.add(createCandle("BTCUSDT", open, high, low, close, 1000.0, i));
        }
        
        return candles;
    }
    
    private List<CandlestickData> generateTrendingCandles(int count, double startPrice, double increment, boolean uptrend) {
        List<CandlestickData> candles = new ArrayList<>();
        Random random = new Random(42);
        
        for (int i = 0; i < count; i++) {
            double basePrice = uptrend ? startPrice + (i * increment) : startPrice - (i * increment);
            double open = basePrice + (random.nextDouble() - 0.5) * 50;
            double close = basePrice + (random.nextDouble() - 0.5) * 50;
            double high = Math.max(open, close) + random.nextDouble() * 25;
            double low = Math.min(open, close) - random.nextDouble() * 25;
            
            candles.add(createCandle("BTCUSDT", open, high, low, close, 1000.0, i));
        }
        
        return candles;
    }
    
    private CandlestickData createCandle(String symbol, double open, double high, double low, 
                                        double close, double volume, int index) {
        Instant openTime = Instant.now().plusSeconds(index * 60);
        Instant closeTime = openTime.plusSeconds(60);
        
        return new CandlestickData(
            symbol,
            openTime,
            closeTime,
            BigDecimal.valueOf(open),
            BigDecimal.valueOf(high),
            BigDecimal.valueOf(low),
            BigDecimal.valueOf(close),
            BigDecimal.valueOf(volume),
            BigDecimal.valueOf(volume * close),
            100,
            "1m",
            "test",
            true
        );
    }
}

