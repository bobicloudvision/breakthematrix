package org.cloudvision.trading.service;

import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.model.*;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to trading data and builds footprint candles from trades
 */
@Component
public class FootprintDataListener {
    
    private final TradingBot tradingBot;
    private final FootprintCandleService footprintService;
    
    // Default intervals to build footprint candles for
    private final List<TimeInterval> activeIntervals = List.of(
            TimeInterval.ONE_MINUTE,
            TimeInterval.FIVE_MINUTES,
            TimeInterval.FIFTEEN_MINUTES
    );
    
    // Track last candle time for each symbol/interval to detect period changes
    private final Map<String, Map<String, Long>> lastCandleTimes = new ConcurrentHashMap<>();
    
    public FootprintDataListener(TradingBot tradingBot, FootprintCandleService footprintService) {
        this.tradingBot = tradingBot;
        this.footprintService = footprintService;
    }
    
//    @PostConstruct
    public void initialize() {
        // Register to receive all trading data
        tradingBot.addDataHandler(this::processData);
        System.out.println("✅ FootprintDataListener initialized - building footprint candles from trades");
        System.out.println("📊 Active intervals: " + activeIntervals);
    }
    
    /**
     * Process incoming trading data
     */
    private void processData(TradingData data) {
        // Only process trade data (individual or aggregate trades)
        if (!data.hasTradeData()) {
            return;
        }
        
        TradeData trade = data.getTradeData();
        String symbol = trade.getSymbol();
        
        // Log first few trades for debugging
        if (Math.random() < 0.01) { // Log ~1% of trades to avoid spam
            System.out.println("🦶 FootprintDataListener: Processing trade " + symbol + 
                    " @ " + trade.getPrice() + " (qty: " + trade.getQuantity() + ")");
        }
        
        // Build footprint candles for all active intervals
        for (TimeInterval interval : activeIntervals) {
            try {
                long currentCandleTime = getCandleTime(trade.getTimestamp(), interval);
                
                // Check if we've moved to a new candle period
                Map<String, Long> symbolTimes = lastCandleTimes.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
                Long previousCandleTime = symbolTimes.get(interval.getValue());
                
                if (previousCandleTime != null && previousCandleTime != currentCandleTime) {
                    // New candle period - close the previous candle
                    FootprintCandle closedCandle = footprintService.closeCandle(symbol, interval, previousCandleTime);
                    if (closedCandle != null) {
                        System.out.println("✅ Closed footprint candle: " + symbol + " " + interval.getValue() + 
                                " (Delta: " + closedCandle.getDelta() + ", Volume: " + closedCandle.getTotalVolume() + ")");
                    }
                }
                
                // Update last candle time
                symbolTimes.put(interval.getValue(), currentCandleTime);
                
                // Process the trade
                footprintService.processTrade(trade, interval);
                
            } catch (Exception e) {
                System.err.println("❌ Error processing trade for footprint: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Get the candle time bucket for a given timestamp and interval
     */
    private long getCandleTime(Instant timestamp, TimeInterval interval) {
        long intervalSeconds = getIntervalSeconds(interval);
        return (timestamp.getEpochSecond() / intervalSeconds) * intervalSeconds;
    }
    
    /**
     * Get interval duration in seconds
     */
    private long getIntervalSeconds(TimeInterval interval) {
        switch (interval) {
            case ONE_MINUTE: return 60;
            case THREE_MINUTES: return 180;
            case FIVE_MINUTES: return 300;
            case FIFTEEN_MINUTES: return 900;
            case THIRTY_MINUTES: return 1800;
            case ONE_HOUR: return 3600;
            case FOUR_HOURS: return 14400;
            case ONE_DAY: return 86400;
            default: return 60;
        }
    }
    
    /**
     * Scheduled task to close old candles periodically
     * Runs every 10 seconds to ensure candles get closed even if trades are sparse
     */
    @Scheduled(fixedRate = 10000)
    public void closeOldCandles() {
        Instant now = Instant.now();
        
        // For each symbol and interval we're tracking
        lastCandleTimes.forEach((symbol, intervalMap) -> {
            intervalMap.forEach((intervalStr, lastCandleTime) -> {
                try {
                    TimeInterval interval = TimeInterval.fromString(intervalStr);
                    long currentCandleTime = getCandleTime(now, interval);
                    
                    // If the current time is in a new candle period, close the old one
                    if (currentCandleTime > lastCandleTime) {
                        FootprintCandle closedCandle = footprintService.closeCandle(symbol, interval, lastCandleTime);
                        if (closedCandle != null) {
                            System.out.println("⏰ Auto-closed footprint candle: " + symbol + " " + intervalStr + 
                                    " (Delta: " + closedCandle.getDelta() + ", Volume: " + closedCandle.getTotalVolume() + ")");
                        }
                        // Update to current time
                        intervalMap.put(intervalStr, currentCandleTime);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error auto-closing candle: " + e.getMessage());
                }
            });
        });
    }
}

