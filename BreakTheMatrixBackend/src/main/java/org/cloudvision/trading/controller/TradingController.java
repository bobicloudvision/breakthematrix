package org.cloudvision.trading.controller;

import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.CandlestickHistoryService;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trading")
public class TradingController {
    private final UniversalTradingDataService tradingService;
    
    @Autowired(required = false)
    private CandlestickHistoryService candlestickHistoryService;

    public TradingController(UniversalTradingDataService tradingService) {
        this.tradingService = tradingService;
    }

    @GetMapping("/providers")
    public List<String> getProviders() {
        return tradingService.getProviders();
    }

    @GetMapping("/intervals/{provider}")
    public List<String> getIntervals(@PathVariable String provider) {
        return tradingService.getSupportedIntervals(provider).stream()
                .map(TimeInterval::getValue)
                .toList();
    }

    @PostMapping("/connect/{provider}")
    public String connectProvider(@PathVariable String provider) {
        tradingService.connectProvider(provider);
        return "Connected to " + provider;
    }

    @PostMapping("/subscribe/{provider}/{symbol}")
    public String subscribe(@PathVariable String provider, @PathVariable String symbol) {
        tradingService.subscribeToSymbol(provider, symbol);
        return "Subscribed to " + symbol + " ticker on " + provider;
    }

    @PostMapping("/subscribe/klines/{provider}/{symbol}/{interval}")
    public String subscribeKlines(@PathVariable String provider, 
                                 @PathVariable String symbol, 
                                 @PathVariable String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            tradingService.subscribeToKlines(provider, symbol, timeInterval);
            return "Subscribed to " + symbol + " klines (" + interval + ") on " + provider;
        } catch (IllegalArgumentException e) {
            return "Invalid interval: " + interval;
        }
    }

    @DeleteMapping("/subscribe/klines/{provider}/{symbol}/{interval}")
    public String unsubscribeKlines(@PathVariable String provider, 
                                   @PathVariable String symbol, 
                                   @PathVariable String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            tradingService.unsubscribeFromKlines(provider, symbol, timeInterval);
            return "Unsubscribed from " + symbol + " klines (" + interval + ") on " + provider;
        } catch (IllegalArgumentException e) {
            return "Invalid interval: " + interval;
        }
    }

    /**
     * Get candlestick data from in-memory storage
     * Returns the EXACT same data that trading strategies are using
     * Only returns data for actively subscribed symbols/intervals
     * 
     * @param limit Number of candles to return (default 1000 to match stored history)
     *              Set to 0 or negative to return ALL available candles
     */
    @GetMapping("/historical/{provider}/{symbol}/{interval}")
    public List<Map<String, Object>> getHistoricalKlines(
            @PathVariable String provider,
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "1000") int limit) {
        
        if (candlestickHistoryService == null) {
            System.err.println("⚠️ CandlestickHistoryService not available");
            return List.of();
        }
        
        try {
            // Read from in-memory storage (same data strategies use)
            List<CandlestickData> data;
            if (limit <= 0) {
                // Return ALL available candles
                data = candlestickHistoryService.getCandlesticks(provider, symbol, interval);
            } else {
                // Return last N candles
                data = candlestickHistoryService.getLastNCandlesticks(
                    provider, symbol, interval, limit
                );
            }
            
//            if (data.isEmpty()) {
//                System.out.println("⚠️ No cached data for " + provider + "_" + symbol + "_" + interval +
//                                 " (not subscribed or no data yet)");
//            } else {
//                System.out.println("✅ Retrieved " + data.size() + " candles from memory for " +
//                                 provider + "_" + symbol + "_" + interval);
//            }
            
            // Serialize candlesticks with proper precision for charting
            return data.stream()
                    .map(this::serializeCandlestick)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            System.err.println("❌ Error retrieving data: " + e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Debug endpoint to check latest candles with full details
     */
    @GetMapping("/debug/latest/{provider}/{symbol}/{interval}")
    public Map<String, Object> debugLatestCandles(
            @PathVariable String provider,
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "5") int count) {
        
        if (candlestickHistoryService == null) {
            return Map.of("error", "CandlestickHistoryService not available");
        }
        
        List<CandlestickData> candles = candlestickHistoryService.getLastNCandlesticks(
            provider, symbol, interval, count
        );
        
        List<Map<String, Object>> details = candles.stream().map(c -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("openTime", c.getOpenTime().toString());
            detail.put("closed", c.isClosed());
            detail.put("open", c.getOpen().doubleValue());
            detail.put("high", c.getHigh().doubleValue());
            detail.put("low", c.getLow().doubleValue());
            detail.put("close", c.getClose().doubleValue());
            detail.put("wickSize", c.getHigh().subtract(c.getLow()).doubleValue());
            detail.put("bodySize", c.getClose().subtract(c.getOpen()).abs().doubleValue());
            return detail;
        }).toList();
        
        return Map.of(
            "symbol", symbol,
            "interval", interval,
            "count", details.size(),
            "candles", details
        );
    }
    
    /**
     * Serialize candlestick data with full precision for frontend charting
     * Ensures OHLC values are properly formatted as numbers (not strings)
     */
    private Map<String, Object> serializeCandlestick(CandlestickData candle) {
        Map<String, Object> data = new HashMap<>();
        
        // Metadata
        data.put("symbol", candle.getSymbol());
        data.put("provider", candle.getProvider());
        data.put("interval", candle.getInterval());
        data.put("closed", candle.isClosed());
        
        // Timestamps - provide both formats for compatibility
        data.put("openTime", candle.getOpenTime().toString()); // ISO-8601 string
        data.put("closeTime", candle.getCloseTime().toString()); // ISO-8601 string
        data.put("time", candle.getOpenTime().getEpochSecond()); // Unix seconds (TradingView format)
        data.put("timestamp", candle.getOpenTime().getEpochSecond()); // Unix seconds (alias)
        data.put("timeMs", candle.getOpenTime().toEpochMilli()); // Unix milliseconds (Chart.js format)
        
        // OHLC - Convert BigDecimal to double for proper charting display
        // This ensures wicks are displayed correctly
        data.put("open", candle.getOpen().doubleValue());
        data.put("high", candle.getHigh().doubleValue());
        data.put("low", candle.getLow().doubleValue());
        data.put("close", candle.getClose().doubleValue());
        
        // Volume data
        data.put("volume", candle.getVolume().doubleValue());
        data.put("quoteAssetVolume", candle.getQuoteAssetVolume().doubleValue());
        data.put("numberOfTrades", candle.getNumberOfTrades());
        
        return data;
    }
}
