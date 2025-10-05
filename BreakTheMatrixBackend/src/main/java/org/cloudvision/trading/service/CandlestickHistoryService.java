package org.cloudvision.trading.service;

import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized in-memory storage for candlestick historical data.
 * Single source of truth for all strategies.
 */
@Service
public class CandlestickHistoryService {
    
    // Key: "PROVIDER_SYMBOL_INTERVAL" (e.g., "Binance_BTCUSDT_1m")
    // Value: Sorted list of candlesticks
    private final Map<String, List<CandlestickData>> candlestickHistory = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int DEFAULT_MAX_CANDLES = 1000; // Keep last 1000 candles per symbol/interval
    private final Map<String, Integer> maxCandlesConfig = new ConcurrentHashMap<>();
    
    /**
     * Add a new candlestick to history
     */
    public void addCandlestick(CandlestickData candlestick) {
        String key = createKey(candlestick.getProvider(), candlestick.getSymbol(), candlestick.getInterval());
        
        List<CandlestickData> candles = candlestickHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (candles) {
            // Check if this candlestick already exists (by timestamp)
            boolean exists = candles.stream()
                .anyMatch(c -> c.getOpenTime().equals(candlestick.getOpenTime()));
            
            if (!exists) {
                candles.add(candlestick);
                
                // Keep sorted by time
                candles.sort(Comparator.comparing(CandlestickData::getOpenTime));
                
                // Trim old data if exceeds max
                int maxCandles = maxCandlesConfig.getOrDefault(key, DEFAULT_MAX_CANDLES);
                if (candles.size() > maxCandles) {
                    int toRemove = candles.size() - maxCandles;
                    candles.subList(0, toRemove).clear();
                }
            } else {
                // Update existing candlestick (for live updates)
                for (int i = 0; i < candles.size(); i++) {
                    if (candles.get(i).getOpenTime().equals(candlestick.getOpenTime())) {
                        candles.set(i, candlestick);
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Add multiple candlesticks (bulk insert for historical data)
     */
    public void addCandlesticks(String provider, String symbol, String interval, List<CandlestickData> candlesticks) {
        String key = createKey(provider, symbol, interval);
        
        List<CandlestickData> candles = candlestickHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (candles) {
            // Add all candlesticks that don't exist yet
            Set<Instant> existingTimes = candles.stream()
                .map(CandlestickData::getOpenTime)
                .collect(Collectors.toSet());
            
            List<CandlestickData> newCandles = candlesticks.stream()
                .filter(c -> !existingTimes.contains(c.getOpenTime()))
                .collect(Collectors.toList());
            
            candles.addAll(newCandles);
            
            // Keep sorted by time
            candles.sort(Comparator.comparing(CandlestickData::getOpenTime));
            
            // Trim old data if exceeds max
            int maxCandles = maxCandlesConfig.getOrDefault(key, DEFAULT_MAX_CANDLES);
            if (candles.size() > maxCandles) {
                int toRemove = candles.size() - maxCandles;
                candles.subList(0, toRemove).clear();
            }
            
            System.out.println("📦 Stored " + newCandles.size() + " new candles for " + key + 
                             " (total: " + candles.size() + ")");
        }
    }
    
    /**
     * Get all candlesticks for a provider, symbol and interval
     */
    public List<CandlestickData> getCandlesticks(String provider, String symbol, String interval) {
        String key = createKey(provider, symbol, interval);
        List<CandlestickData> candles = candlestickHistory.get(key);
        return candles != null ? new ArrayList<>(candles) : Collections.emptyList();
    }
    
    /**
     * Get last N candlesticks for a provider, symbol and interval
     */
    public List<CandlestickData> getLastNCandlesticks(String provider, String symbol, String interval, int count) {
        List<CandlestickData> allCandles = getCandlesticks(provider, symbol, interval);
        
        if (allCandles.size() <= count) {
            return allCandles;
        }
        
        return allCandles.subList(allCandles.size() - count, allCandles.size());
    }
    
    /**
     * Get candlesticks within a time range
     */
    public List<CandlestickData> getCandlesticks(String provider, String symbol, String interval, Instant startTime, Instant endTime) {
        return getCandlesticks(provider, symbol, interval).stream()
            .filter(c -> !c.getOpenTime().isBefore(startTime) && !c.getOpenTime().isAfter(endTime))
            .collect(Collectors.toList());
    }
    
    /**
     * Get the latest (most recent) candlestick
     */
    public CandlestickData getLatestCandlestick(String provider, String symbol, String interval) {
        List<CandlestickData> candles = getCandlesticks(provider, symbol, interval);
        return candles.isEmpty() ? null : candles.get(candles.size() - 1);
    }
    
    /**
     * Get the number of available candlesticks
     */
    public int getCandlestickCount(String provider, String symbol, String interval) {
        String key = createKey(provider, symbol, interval);
        List<CandlestickData> candles = candlestickHistory.get(key);
        return candles != null ? candles.size() : 0;
    }
    
    /**
     * Check if we have enough data for a strategy
     */
    public boolean hasEnoughData(String provider, String symbol, String interval, int requiredCount) {
        return getCandlestickCount(provider, symbol, interval) >= requiredCount;
    }
    
    /**
     * Configure max candles to keep for a specific provider/symbol/interval
     */
    public void setMaxCandles(String provider, String symbol, String interval, int maxCandles) {
        String key = createKey(provider, symbol, interval);
        maxCandlesConfig.put(key, maxCandles);
        System.out.println("⚙️ Set max candles for " + key + " to " + maxCandles);
    }
    
    /**
     * Clear history for a specific provider/symbol/interval
     */
    public void clearHistory(String provider, String symbol, String interval) {
        String key = createKey(provider, symbol, interval);
        candlestickHistory.remove(key);
        System.out.println("🗑️ Cleared history for " + key);
    }
    
    /**
     * Clear all history
     */
    public void clearAllHistory() {
        candlestickHistory.clear();
        System.out.println("🗑️ Cleared all candlestick history");
    }
    
    /**
     * Get statistics about stored data
     */
    public Map<String, Integer> getStorageStats() {
        Map<String, Integer> stats = new HashMap<>();
        candlestickHistory.forEach((key, candles) -> stats.put(key, candles.size()));
        return stats;
    }
    
    /**
     * Get total memory usage estimate (approximate)
     */
    public String getMemoryUsageEstimate() {
        long totalCandles = candlestickHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        
        // Rough estimate: ~200 bytes per candlestick
        long estimatedBytes = totalCandles * 200;
        
        if (estimatedBytes < 1024) {
            return estimatedBytes + " B";
        } else if (estimatedBytes < 1024 * 1024) {
            return String.format("%.2f KB", estimatedBytes / 1024.0);
        } else {
            return String.format("%.2f MB", estimatedBytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Create a unique key for provider + symbol + interval
     */
    private String createKey(String provider, String symbol, String interval) {
        return provider + "_" + symbol.toUpperCase() + "_" + interval;
    }
    
    /**
     * Print storage summary
     */
    public void printStorageSummary() {
        System.out.println("\n📊 Candlestick History Storage Summary:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (candlestickHistory.isEmpty()) {
            System.out.println("  No data stored");
        } else {
            candlestickHistory.forEach((key, candles) -> {
                CandlestickData oldest = candles.isEmpty() ? null : candles.get(0);
                CandlestickData newest = candles.isEmpty() ? null : candles.get(candles.size() - 1);
                
                System.out.printf("  %-20s : %4d candles", key, candles.size());
                if (oldest != null && newest != null) {
                    System.out.printf(" (%s to %s)", oldest.getOpenTime(), newest.getOpenTime());
                }
                System.out.println();
            });
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("  Total memory usage: " + getMemoryUsageEstimate());
        }
        System.out.println();
    }
}

