package org.cloudvision.trading.controller;

import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.CandlestickHistoryService;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

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
    public List<CandlestickData> getHistoricalKlines(
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
            
            return data;
            
        } catch (Exception e) {
            System.err.println("❌ Error retrieving data: " + e.getMessage());
            return List.of();
        }
    }
}
