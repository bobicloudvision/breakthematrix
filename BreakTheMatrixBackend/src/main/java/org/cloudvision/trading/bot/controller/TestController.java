package org.cloudvision.trading.bot.controller;

import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.strategy.impl.MovingAverageStrategy;
import org.cloudvision.trading.model.TimeInterval;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    private final MovingAverageStrategy movingAverageStrategy;
    private final TradingBot tradingBot;

    public TestController(MovingAverageStrategy movingAverageStrategy, TradingBot tradingBot) {
        this.movingAverageStrategy = movingAverageStrategy;
        this.tradingBot = tradingBot;
    }

    @PostMapping("/simulate-data/{symbol}")
    public String simulateMarketData(@PathVariable String symbol) {
        // Simulate some price data to generate visualization
        for (int i = 0; i < 25; i++) {
            double basePrice = 50000 + (Math.random() - 0.5) * 1000;
            movingAverageStrategy.simulateTickerData(symbol);
            
            try {
                Thread.sleep(100); // Small delay between data points
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return "Simulated 25 data points for " + symbol;
    }

    @PostMapping("/simulate-kline-data/{symbol}/{interval}")
    public String simulateKlineData(@PathVariable String symbol, @PathVariable String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            
            // Simulate some kline data
            for (int i = 0; i < 10; i++) {
                movingAverageStrategy.simulateKlineData(symbol, timeInterval);
                
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            return "Simulated 10 kline data points for " + symbol + " with interval " + interval;
        } catch (IllegalArgumentException e) {
            return "Invalid interval: " + interval;
        }
    }
}
