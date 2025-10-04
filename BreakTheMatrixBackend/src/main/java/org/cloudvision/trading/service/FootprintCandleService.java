package org.cloudvision.trading.service;

import org.cloudvision.trading.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for building and managing footprint candles from trade data
 */
@Service
public class FootprintCandleService {
    
    // Storage: symbol -> interval -> time -> footprint builder
    private final Map<String, Map<String, Map<Long, FootprintBuilder>>> footprintBuilders = new ConcurrentHashMap<>();
    
    // Price tick size for grouping (e.g., 0.01 for BTC)
    private final Map<String, BigDecimal> tickSizes = new ConcurrentHashMap<>();
    
    // Completed footprint candles cache
    private final Map<String, List<FootprintCandle>> completedCandles = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 500;
    
    public FootprintCandleService() {
        // Default tick sizes for common symbols
        tickSizes.put("BTCUSDT", new BigDecimal("0.01"));
        tickSizes.put("ETHUSDT", new BigDecimal("0.01"));
        tickSizes.put("BNBUSDT", new BigDecimal("0.01"));
        tickSizes.put("SOLUSDT", new BigDecimal("0.001"));
    }
    
    /**
     * Set custom tick size for a symbol
     */
    public void setTickSize(String symbol, BigDecimal tickSize) {
        tickSizes.put(symbol, tickSize);
    }
    
    /**
     * Process a trade and update the corresponding footprint candle
     */
    public FootprintCandle processTrade(TradeData trade, TimeInterval interval) {
        String symbol = trade.getSymbol();
        long candleTime = getCandleTime(trade.getTimestamp(), interval);
        
        // Get or create builder
        FootprintBuilder builder = footprintBuilders
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(interval.getValue(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(candleTime, k -> new FootprintBuilder(symbol, interval.getValue(), candleTime));
        
        // Add trade to builder
        builder.addTrade(trade, getTickSize(symbol));
        
        // Return current state (may be incomplete)
        return builder.build(false);
    }
    
    /**
     * Close a candle period and return the completed footprint candle
     */
    public FootprintCandle closeCandle(String symbol, TimeInterval interval, long candleTime) {
        Map<String, Map<Long, FootprintBuilder>> symbolBuilders = footprintBuilders.get(symbol);
        if (symbolBuilders == null) return null;
        
        Map<Long, FootprintBuilder> intervalBuilders = symbolBuilders.get(interval.getValue());
        if (intervalBuilders == null) return null;
        
        FootprintBuilder builder = intervalBuilders.remove(candleTime);
        if (builder == null) return null;
        
        // Build final candle
        FootprintCandle candle = builder.build(true);
        
        // Cache completed candle
        String cacheKey = symbol + "_" + interval.getValue();
        completedCandles.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(candle);
        
        // Limit cache size
        List<FootprintCandle> cache = completedCandles.get(cacheKey);
        if (cache.size() > MAX_CACHE_SIZE) {
            cache.remove(0);
        }
        
        return candle;
    }
    
    /**
     * Get historical footprint candles
     */
    public List<FootprintCandle> getHistoricalCandles(String symbol, TimeInterval interval, int limit) {
        String cacheKey = symbol + "_" + interval.getValue();
        List<FootprintCandle> cache = completedCandles.get(cacheKey);
        
        if (cache == null || cache.isEmpty()) {
            return new ArrayList<>();
        }
        
        int startIndex = Math.max(0, cache.size() - limit);
        return new ArrayList<>(cache.subList(startIndex, cache.size()));
    }
    
    /**
     * Get current (incomplete) footprint candle
     */
    public FootprintCandle getCurrentCandle(String symbol, TimeInterval interval) {
        long currentCandleTime = getCandleTime(Instant.now(), interval);
        
        FootprintBuilder builder = footprintBuilders
                .getOrDefault(symbol, new ConcurrentHashMap<>())
                .getOrDefault(interval.getValue(), new ConcurrentHashMap<>())
                .get(currentCandleTime);
        
        return builder != null ? builder.build(false) : null;
    }
    
    private long getCandleTime(Instant timestamp, TimeInterval interval) {
        long intervalSeconds = getIntervalSeconds(interval);
        return (timestamp.getEpochSecond() / intervalSeconds) * intervalSeconds;
    }
    
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
    
    private BigDecimal getTickSize(String symbol) {
        return tickSizes.getOrDefault(symbol, new BigDecimal("0.01"));
    }
    
    /**
     * Builder for constructing footprint candles from trades
     */
    private static class FootprintBuilder {
        private final String symbol;
        private final String interval;
        private final long candleTime;
        
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        
        private BigDecimal totalVolume = BigDecimal.ZERO;
        private BigDecimal totalBuyVolume = BigDecimal.ZERO;
        private BigDecimal totalSellVolume = BigDecimal.ZERO;
        
        private final Map<BigDecimal, FootprintCandle.PriceLevelVolume> volumeProfile = new ConcurrentHashMap<>();
        private int tradeCount = 0;
        private Instant firstTradeTime;
        private Instant lastTradeTime;
        
        public FootprintBuilder(String symbol, String interval, long candleTime) {
            this.symbol = symbol;
            this.interval = interval;
            this.candleTime = candleTime;
        }
        
        public void addTrade(TradeData trade, BigDecimal tickSize) {
            BigDecimal price = trade.getPrice();
            BigDecimal quantity = trade.getQuantity();
            boolean isBuy = trade.isAggressiveBuy();
            
            // Round price to tick size
            price = roundToTickSize(price, tickSize);
            
            // Update OHLC
            if (open == null) {
                open = price;
                high = price;
                low = price;
                firstTradeTime = trade.getTimestamp();
            } else {
                if (price.compareTo(high) > 0) high = price;
                if (price.compareTo(low) < 0) low = price;
            }
            close = price;
            lastTradeTime = trade.getTimestamp();
            
            // Update volume
            totalVolume = totalVolume.add(quantity);
            if (isBuy) {
                totalBuyVolume = totalBuyVolume.add(quantity);
            } else {
                totalSellVolume = totalSellVolume.add(quantity);
            }
            
            // Update volume profile
            FootprintCandle.PriceLevelVolume level = volumeProfile.computeIfAbsent(
                    price, p -> new FootprintCandle.PriceLevelVolume(p)
            );
            
            if (isBuy) {
                level.addBuyVolume(quantity);
            } else {
                level.addSellVolume(quantity);
            }
            
            tradeCount++;
        }
        
        public FootprintCandle build(boolean isClosed) {
            if (open == null) {
                // No trades yet - return empty candle
                return null;
            }
            
            // Calculate delta
            BigDecimal delta = totalBuyVolume.subtract(totalSellVolume);
            
            // Calculate Point of Control (price with highest volume)
            BigDecimal poc = calculatePOC();
            
            // Calculate Value Area (70% of volume)
            BigDecimal[] valueArea = calculateValueArea();
            BigDecimal vah = valueArea[0];
            BigDecimal val = valueArea[1];
            
            // Calculate cumulative delta (would need historical data for accurate value)
            BigDecimal cumulativeDelta = delta; // Simplified - should track across candles
            
            Instant openTime = Instant.ofEpochSecond(candleTime);
            Instant closeTime = lastTradeTime != null ? lastTradeTime : openTime;
            
            return new FootprintCandle(
                    symbol, openTime, closeTime, interval,
                    open, high, low, close,
                    totalVolume, totalBuyVolume, totalSellVolume,
                    new HashMap<>(volumeProfile),
                    delta, cumulativeDelta,
                    poc, vah, val,
                    tradeCount
            );
        }
        
        private BigDecimal calculatePOC() {
            if (volumeProfile.isEmpty()) return BigDecimal.ZERO;
            
            return volumeProfile.entrySet().stream()
                    .max(Comparator.comparing(e -> e.getValue().getTotalVolume()))
                    .map(Map.Entry::getKey)
                    .orElse(BigDecimal.ZERO);
        }
        
        private BigDecimal[] calculateValueArea() {
            if (volumeProfile.isEmpty()) {
                return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
            }
            
            // Sort levels by volume (descending)
            List<Map.Entry<BigDecimal, FootprintCandle.PriceLevelVolume>> sorted = new ArrayList<>(volumeProfile.entrySet());
            sorted.sort((a, b) -> b.getValue().getTotalVolume().compareTo(a.getValue().getTotalVolume()));
            
            // Find 70% of total volume
            BigDecimal targetVolume = totalVolume.multiply(new BigDecimal("0.7"));
            BigDecimal accumulatedVolume = BigDecimal.ZERO;
            
            BigDecimal maxPrice = null;
            BigDecimal minPrice = null;
            
            for (Map.Entry<BigDecimal, FootprintCandle.PriceLevelVolume> entry : sorted) {
                accumulatedVolume = accumulatedVolume.add(entry.getValue().getTotalVolume());
                
                BigDecimal price = entry.getKey();
                if (maxPrice == null || price.compareTo(maxPrice) > 0) maxPrice = price;
                if (minPrice == null || price.compareTo(minPrice) < 0) minPrice = price;
                
                if (accumulatedVolume.compareTo(targetVolume) >= 0) {
                    break;
                }
            }
            
            return new BigDecimal[]{
                    maxPrice != null ? maxPrice : high,
                    minPrice != null ? minPrice : low
            };
        }
        
        private BigDecimal roundToTickSize(BigDecimal price, BigDecimal tickSize) {
            return price.divide(tickSize, 0, RoundingMode.HALF_UP).multiply(tickSize);
        }
    }
}

