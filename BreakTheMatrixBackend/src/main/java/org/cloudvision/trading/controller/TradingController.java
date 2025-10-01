package org.cloudvision.trading.controller;

import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/trading")
public class TradingController {
    private final UniversalTradingDataService tradingService;

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

    @GetMapping("/historical/{provider}/{symbol}/{interval}")
    public List<CandlestickData> getHistoricalKlines(
            @PathVariable String provider,
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            
            // If startTime and endTime are provided, use time range query
            if (startTime != null && endTime != null) {
                Instant start = Instant.ofEpochMilli(startTime);
                Instant end = Instant.ofEpochMilli(endTime);
                return tradingService.getHistoricalKlines(provider, symbol, timeInterval, start, end);
            }
            
            // Otherwise use limit-based query
            return tradingService.getHistoricalKlines(provider, symbol, timeInterval, limit);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid interval: " + interval);
            return List.of();
        }
    }
}
