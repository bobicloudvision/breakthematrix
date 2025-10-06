package org.cloudvision.trading.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized storage for high-frequency book ticker data (best bid/ask)
 * 
 * PURPOSE:
 * - Track best bid/ask prices and quantities over time
 * - Calculate spread statistics and anomalies
 * - Detect liquidity changes at top of book
 * - Support scalping and micro-structure analysis
 * 
 * DESIGN:
 * - Time-based snapshots (configurable interval, default 1 second)
 * - Circular buffer to limit memory usage
 * - Automatic throttling (don't store every update)
 * - Spread and imbalance calculations
 * 
 * MEMORY USAGE:
 * - Each snapshot: ~100 bytes
 * - Default: 3600 snapshots = ~350 KB per symbol (1 hour at 1s intervals)
 * 
 * USE CASES:
 * - Scalping strategies
 * - Spread analysis
 * - Top-of-book liquidity tracking
 * - Entry/exit timing optimization
 */
@Service
public class BookTickerHistoryService {
    
    // Key: "PROVIDER_SYMBOL" (e.g., "Binance_BTCUSDT")
    // Value: Sorted list of book ticker snapshots
    private final Map<String, List<BookTickerSnapshot>> tickerHistory = new ConcurrentHashMap<>();
    
    // Track last snapshot time for throttling
    private final Map<String, Instant> lastSnapshotTime = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int DEFAULT_MAX_SNAPSHOTS = 3600; // Last 3600 snapshots (1 hour at 1s)
    private static final int DEFAULT_SNAPSHOT_INTERVAL_MS = 1000; // 1 snapshot per second
    
    private final Map<String, Integer> maxSnapshotsConfig = new ConcurrentHashMap<>();
    private final Map<String, Integer> snapshotIntervalConfig = new ConcurrentHashMap<>();
    
    /**
     * Book ticker snapshot - lightweight representation of best bid/ask
     */
    public static class BookTickerSnapshot {
        private final Instant timestamp;
        private final BigDecimal bestBid;
        private final BigDecimal bestBidQty;
        private final BigDecimal bestAsk;
        private final BigDecimal bestAskQty;
        private final BigDecimal spread;
        private final double imbalance; // bid qty / ask qty ratio
        
        public BookTickerSnapshot(Instant timestamp, BigDecimal bestBid, BigDecimal bestBidQty,
                                 BigDecimal bestAsk, BigDecimal bestAskQty) {
            this.timestamp = timestamp;
            this.bestBid = bestBid;
            this.bestBidQty = bestBidQty;
            this.bestAsk = bestAsk;
            this.bestAskQty = bestAskQty;
            this.spread = bestAsk.subtract(bestBid);
            
            // Calculate imbalance (bid pressure vs ask pressure)
            if (bestAskQty.compareTo(BigDecimal.ZERO) > 0) {
                this.imbalance = bestBidQty.divide(bestAskQty, 4, java.math.RoundingMode.HALF_UP).doubleValue();
            } else {
                this.imbalance = 999.0; // Infinite imbalance (no asks)
            }
        }
        
        // Getters
        public Instant getTimestamp() { return timestamp; }
        public BigDecimal getBestBid() { return bestBid; }
        public BigDecimal getBestBidQty() { return bestBidQty; }
        public BigDecimal getBestAsk() { return bestAsk; }
        public BigDecimal getBestAskQty() { return bestAskQty; }
        public BigDecimal getSpread() { return spread; }
        public double getImbalance() { return imbalance; }
        
        public double getSpreadBps() {
            // Spread in basis points (0.01% = 1bp)
            if (bestBid.compareTo(BigDecimal.ZERO) > 0) {
                return spread.divide(bestBid, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("10000")).doubleValue();
            }
            return 0.0;
        }
    }
    
    /**
     * Add a book ticker snapshot to history
     * 
     * Automatically throttled to avoid storing every update
     * Book ticker updates can be 100ms or faster, we throttle to ~1s by default
     * 
     * @return true if stored, false if throttled
     */
    public boolean addBookTicker(String provider, String symbol, Instant timestamp,
                                 BigDecimal bestBid, BigDecimal bestBidQty,
                                 BigDecimal bestAsk, BigDecimal bestAskQty) {
        String key = createKey(provider, symbol);
        
        // Check if enough time has passed since last snapshot
        Instant lastSnapshot = lastSnapshotTime.get(key);
        
        int intervalMs = snapshotIntervalConfig.getOrDefault(key, DEFAULT_SNAPSHOT_INTERVAL_MS);
        
        if (lastSnapshot != null) {
            long elapsedMs = Duration.between(lastSnapshot, timestamp).toMillis();
            if (elapsedMs < intervalMs) {
                // Too soon - throttle this update
                return false;
            }
        }
        
        // Create and store snapshot
        BookTickerSnapshot snapshot = new BookTickerSnapshot(
            timestamp, bestBid, bestBidQty, bestAsk, bestAskQty
        );
        
        List<BookTickerSnapshot> snapshots = tickerHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (snapshots) {
            snapshots.add(snapshot);
            
            // Keep sorted by timestamp
            snapshots.sort(Comparator.comparing(BookTickerSnapshot::getTimestamp));
            
            // Trim old data if exceeds max
            int maxSnapshots = maxSnapshotsConfig.getOrDefault(key, DEFAULT_MAX_SNAPSHOTS);
            if (snapshots.size() > maxSnapshots) {
                int toRemove = snapshots.size() - maxSnapshots;
                snapshots.subList(0, toRemove).clear();
            }
        }
        
        // Update last snapshot time
        lastSnapshotTime.put(key, timestamp);
        
        return true;
    }
    
    /**
     * Get all book ticker snapshots for a symbol
     */
    public List<BookTickerSnapshot> getBookTickers(String provider, String symbol) {
        String key = createKey(provider, symbol);
        List<BookTickerSnapshot> snapshots = tickerHistory.get(key);
        return snapshots != null ? new ArrayList<>(snapshots) : Collections.emptyList();
    }
    
    /**
     * Get last N book ticker snapshots
     */
    public List<BookTickerSnapshot> getLastNBookTickers(String provider, String symbol, int count) {
        List<BookTickerSnapshot> all = getBookTickers(provider, symbol);
        
        if (all.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        
        int size = all.size();
        int startIndex = Math.max(0, size - count);
        return all.subList(startIndex, size);
    }
    
    /**
     * Get book ticker snapshots within a time range
     */
    public List<BookTickerSnapshot> getBookTickersInTimeRange(String provider, String symbol,
                                                              Instant startTime, Instant endTime) {
        List<BookTickerSnapshot> all = getBookTickers(provider, symbol);
        
        return all.stream()
            .filter(snap -> !snap.getTimestamp().isBefore(startTime) && 
                          !snap.getTimestamp().isAfter(endTime))
            .collect(Collectors.toList());
    }
    
    /**
     * Get the latest book ticker snapshot
     */
    public BookTickerSnapshot getLatestBookTicker(String provider, String symbol) {
        List<BookTickerSnapshot> snapshots = getBookTickers(provider, symbol);
        return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    }
    
    /**
     * Get average spread over last N snapshots
     */
    public double getAverageSpread(String provider, String symbol, int count) {
        List<BookTickerSnapshot> snapshots = getLastNBookTickers(provider, symbol, count);
        
        if (snapshots.isEmpty()) {
            return 0.0;
        }
        
        return snapshots.stream()
            .mapToDouble(s -> s.getSpread().doubleValue())
            .average()
            .orElse(0.0);
    }
    
    /**
     * Get average spread in basis points
     */
    public double getAverageSpreadBps(String provider, String symbol, int count) {
        List<BookTickerSnapshot> snapshots = getLastNBookTickers(provider, symbol, count);
        
        if (snapshots.isEmpty()) {
            return 0.0;
        }
        
        return snapshots.stream()
            .mapToDouble(BookTickerSnapshot::getSpreadBps)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Get average imbalance (bid/ask ratio) over last N snapshots
     */
    public double getAverageImbalance(String provider, String symbol, int count) {
        List<BookTickerSnapshot> snapshots = getLastNBookTickers(provider, symbol, count);
        
        if (snapshots.isEmpty()) {
            return 1.0; // Neutral
        }
        
        return snapshots.stream()
            .mapToDouble(BookTickerSnapshot::getImbalance)
            .filter(imb -> imb < 100.0) // Filter out extreme values
            .average()
            .orElse(1.0);
    }
    
    /**
     * Detect spread anomalies (unusually wide spreads)
     * Returns snapshots where spread > threshold * average
     */
    public List<BookTickerSnapshot> detectSpreadAnomalies(String provider, String symbol, 
                                                          int lookback, double threshold) {
        List<BookTickerSnapshot> snapshots = getLastNBookTickers(provider, symbol, lookback);
        
        if (snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Calculate average spread
        double avgSpread = snapshots.stream()
            .mapToDouble(s -> s.getSpread().doubleValue())
            .average()
            .orElse(0.0);
        
        double anomalyThreshold = avgSpread * threshold;
        
        // Find snapshots with unusually wide spreads
        return snapshots.stream()
            .filter(s -> s.getSpread().doubleValue() > anomalyThreshold)
            .collect(Collectors.toList());
    }
    
    /**
     * Get snapshot count for a symbol
     */
    public int getSnapshotCount(String provider, String symbol) {
        String key = createKey(provider, symbol);
        List<BookTickerSnapshot> snapshots = tickerHistory.get(key);
        return snapshots != null ? snapshots.size() : 0;
    }
    
    /**
     * Configure snapshot interval (milliseconds between snapshots)
     */
    public void setSnapshotInterval(String provider, String symbol, int intervalMs) {
        String key = createKey(provider, symbol);
        if (intervalMs > 0) {
            snapshotIntervalConfig.put(key, intervalMs);
        } else {
            snapshotIntervalConfig.remove(key);
        }
    }
    
    /**
     * Configure maximum snapshots to keep
     */
    public void setMaxSnapshots(String provider, String symbol, int maxSnapshots) {
        String key = createKey(provider, symbol);
        if (maxSnapshots > 0) {
            maxSnapshotsConfig.put(key, maxSnapshots);
        } else {
            maxSnapshotsConfig.remove(key);
        }
    }
    
    /**
     * Clear all snapshots for a symbol
     */
    public void clear(String provider, String symbol) {
        String key = createKey(provider, symbol);
        tickerHistory.remove(key);
        lastSnapshotTime.remove(key);
        System.out.println("🗑️ Cleared book ticker history for " + key);
    }
    
    /**
     * Clear all stored data
     */
    public void clearAll() {
        int totalSnapshots = tickerHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        tickerHistory.clear();
        lastSnapshotTime.clear();
        maxSnapshotsConfig.clear();
        snapshotIntervalConfig.clear();
        System.out.println("🗑️ Cleared all book ticker history (" + totalSnapshots + " snapshots)");
    }
    
    /**
     * Get statistics about stored data
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalSnapshots = tickerHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        
        stats.put("totalSnapshots", totalSnapshots);
        stats.put("symbolCount", tickerHistory.size());
        stats.put("symbols", new ArrayList<>(tickerHistory.keySet()));
        
        // Count by symbol
        Map<String, Integer> snapshotsBySymbol = new HashMap<>();
        for (Map.Entry<String, List<BookTickerSnapshot>> entry : tickerHistory.entrySet()) {
            snapshotsBySymbol.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("snapshotsBySymbol", snapshotsBySymbol);
        
        // Estimated memory usage (rough: 100 bytes per snapshot)
        long estimatedMemoryKB = totalSnapshots * 100 / 1024;
        stats.put("estimatedMemoryKB", estimatedMemoryKB);
        stats.put("estimatedMemoryMB", estimatedMemoryKB / 1024);
        
        return stats;
    }
    
    private String createKey(String provider, String symbol) {
        return provider + "_" + symbol;
    }
}

