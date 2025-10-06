package org.cloudvision.trading.service;

import org.cloudvision.trading.model.OrderBookData;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized in-memory storage for historical order book snapshots
 * 
 * PURPOSE:
 * - Store order book snapshots at regular intervals
 * - Enable historical reconstruction of market depth over time
 * - Support Bookmap and liquidity analysis indicators
 * 
 * DESIGN:
 * - Time-based snapshots (configurable interval, default 10 seconds)
 * - Circular buffer to limit memory usage
 * - Automatic throttling to avoid storing every update
 * 
 * MEMORY CONSIDERATIONS:
 * - Order books are large (~2-5 KB per snapshot)
 * - Default: 1000 snapshots = ~2.5 MB per symbol
 * - Covers ~2.7 hours at 10-second intervals
 * 
 * USAGE:
 * - Called by trading data providers when order books arrive
 * - Automatically throttled to store only 1 snapshot per interval
 * - Queried by REST endpoints for historical order book data
 */
@Service
public class OrderBookHistoryService {
    
    // Key: "PROVIDER_SYMBOL" (e.g., "Binance_BTCUSDT")
    // Value: Sorted list of order book snapshots
    private final Map<String, List<OrderBookData>> orderBookHistory = new ConcurrentHashMap<>();
    
    // Track last snapshot time for throttling
    private final Map<String, Instant> lastSnapshotTime = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int DEFAULT_MAX_SNAPSHOTS = 1000; // Last 1000 snapshots
    private static final int DEFAULT_SNAPSHOT_INTERVAL_SECONDS = 10; // 1 snapshot per 10 seconds
    
    private final Map<String, Integer> maxSnapshotsConfig = new ConcurrentHashMap<>();
    private final Map<String, Integer> snapshotIntervalConfig = new ConcurrentHashMap<>();
    
    /**
     * Add an order book snapshot to history
     * 
     * NOTE: Automatically throttled to only store 1 snapshot per configured interval
     * This prevents memory overflow from high-frequency order book updates
     * 
     * @param orderBook Order book data to store
     * @return true if stored, false if throttled
     */
    public boolean addOrderBook(OrderBookData orderBook) {
        String key = createKey(orderBook.getProvider(), orderBook.getSymbol());
        
        // Check if enough time has passed since last snapshot
        Instant lastSnapshot = lastSnapshotTime.get(key);
        Instant now = orderBook.getTimestamp();
        
        int intervalSeconds = snapshotIntervalConfig.getOrDefault(key, DEFAULT_SNAPSHOT_INTERVAL_SECONDS);
        
        if (lastSnapshot != null) {
            long elapsedSeconds = Duration.between(lastSnapshot, now).getSeconds();
            if (elapsedSeconds < intervalSeconds) {
                // Too soon - throttle this update
                return false;
            }
        }
        
        // Store this snapshot
        List<OrderBookData> snapshots = orderBookHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (snapshots) {
            snapshots.add(orderBook);
            
            // Keep sorted by timestamp
            snapshots.sort(Comparator.comparing(OrderBookData::getTimestamp));
            
            // Trim old data if exceeds max
            int maxSnapshots = maxSnapshotsConfig.getOrDefault(key, DEFAULT_MAX_SNAPSHOTS);
            if (snapshots.size() > maxSnapshots) {
                int toRemove = snapshots.size() - maxSnapshots;
                snapshots.subList(0, toRemove).clear();
            }
        }
        
        // Update last snapshot time
        lastSnapshotTime.put(key, now);
        
        return true;
    }
    
    /**
     * Add multiple order book snapshots in bulk (e.g., from historical data load)
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param orderBooks List of order book snapshots to add
     */
    public void addOrderBooks(String provider, String symbol, List<OrderBookData> orderBooks) {
        if (orderBooks == null || orderBooks.isEmpty()) {
            return;
        }
        
        String key = createKey(provider, symbol);
        List<OrderBookData> existingSnapshots = orderBookHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (existingSnapshots) {
            // Add all snapshots
            existingSnapshots.addAll(orderBooks);
            
            // Remove duplicates (by timestamp)
            Set<Instant> seen = new HashSet<>();
            existingSnapshots.removeIf(ob -> !seen.add(ob.getTimestamp()));
            
            // Keep sorted by timestamp
            existingSnapshots.sort(Comparator.comparing(OrderBookData::getTimestamp));
            
            // Trim old data if exceeds max
            int maxSnapshots = maxSnapshotsConfig.getOrDefault(key, DEFAULT_MAX_SNAPSHOTS);
            if (existingSnapshots.size() > maxSnapshots) {
                int toRemove = existingSnapshots.size() - maxSnapshots;
                existingSnapshots.subList(0, toRemove).clear();
            }
            
            System.out.println("📦 Stored " + orderBooks.size() + " order book snapshots for " + key + 
                             " (total: " + existingSnapshots.size() + ")");
        }
        
        // Update last snapshot time
        if (!orderBooks.isEmpty()) {
            Instant lastTime = orderBooks.get(orderBooks.size() - 1).getTimestamp();
            lastSnapshotTime.put(key, lastTime);
        }
    }
    
    /**
     * Get all order book snapshots for a provider and symbol
     * 
     * @return List of order book snapshots (copy, thread-safe)
     */
    public List<OrderBookData> getOrderBooks(String provider, String symbol) {
        String key = createKey(provider, symbol);
        List<OrderBookData> snapshots = orderBookHistory.get(key);
        return snapshots != null ? new ArrayList<>(snapshots) : Collections.emptyList();
    }
    
    /**
     * Get last N order book snapshots for a provider and symbol
     * 
     * @param count Number of snapshots to retrieve
     * @return List of last N order book snapshots
     */
    public List<OrderBookData> getLastNOrderBooks(String provider, String symbol, int count) {
        List<OrderBookData> allSnapshots = getOrderBooks(provider, symbol);
        
        if (allSnapshots.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        
        int size = allSnapshots.size();
        int startIndex = Math.max(0, size - count);
        return allSnapshots.subList(startIndex, size);
    }
    
    /**
     * Get order book snapshots within a time range
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param startTime Start time (inclusive)
     * @param endTime End time (inclusive)
     * @return List of order book snapshots within the time range
     */
    public List<OrderBookData> getOrderBooksInTimeRange(String provider, String symbol,
                                                        Instant startTime, Instant endTime) {
        List<OrderBookData> allSnapshots = getOrderBooks(provider, symbol);
        
        return allSnapshots.stream()
            .filter(ob -> !ob.getTimestamp().isBefore(startTime) && 
                         !ob.getTimestamp().isAfter(endTime))
            .collect(Collectors.toList());
    }
    
    /**
     * Get the latest order book snapshot for a symbol
     * 
     * @return Latest order book snapshot, or null if none exist
     */
    public OrderBookData getLatestOrderBook(String provider, String symbol) {
        List<OrderBookData> snapshots = getOrderBooks(provider, symbol);
        return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    }
    
    /**
     * Get order book at a specific time (or closest before that time)
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param time Target time
     * @return Order book at or before the specified time, or null if none exist
     */
    public OrderBookData getOrderBookAtTime(String provider, String symbol, Instant time) {
        List<OrderBookData> snapshots = getOrderBooks(provider, symbol);
        
        // Find the latest snapshot that's not after the target time
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            OrderBookData ob = snapshots.get(i);
            if (!ob.getTimestamp().isAfter(time)) {
                return ob;
            }
        }
        
        return null; // No snapshot before the target time
    }
    
    /**
     * Get order book snapshot count for a symbol
     * 
     * @return Number of order book snapshots stored
     */
    public int getSnapshotCount(String provider, String symbol) {
        String key = createKey(provider, symbol);
        List<OrderBookData> snapshots = orderBookHistory.get(key);
        return snapshots != null ? snapshots.size() : 0;
    }
    
    /**
     * Configure maximum snapshots to keep for a specific symbol
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param maxSnapshots Maximum number of snapshots to keep (0 = use default)
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
     * Configure snapshot interval for a specific symbol
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param intervalSeconds Minimum seconds between snapshots (0 = use default)
     */
    public void setSnapshotInterval(String provider, String symbol, int intervalSeconds) {
        String key = createKey(provider, symbol);
        if (intervalSeconds > 0) {
            snapshotIntervalConfig.put(key, intervalSeconds);
        } else {
            snapshotIntervalConfig.remove(key);
        }
    }
    
    /**
     * Clear all order book snapshots for a specific symbol
     */
    public void clear(String provider, String symbol) {
        String key = createKey(provider, symbol);
        orderBookHistory.remove(key);
        lastSnapshotTime.remove(key);
        System.out.println("🗑️ Cleared order book history for " + key);
    }
    
    /**
     * Clear all stored order book data
     */
    public void clearAll() {
        int totalSnapshots = orderBookHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        orderBookHistory.clear();
        lastSnapshotTime.clear();
        maxSnapshotsConfig.clear();
        snapshotIntervalConfig.clear();
        System.out.println("🗑️ Cleared all order book history (" + totalSnapshots + " snapshots)");
    }
    
    /**
     * Get statistics about stored order book data
     * 
     * @return Map containing statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalSnapshots = orderBookHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        
        stats.put("totalSnapshots", totalSnapshots);
        stats.put("symbolCount", orderBookHistory.size());
        stats.put("symbols", new ArrayList<>(orderBookHistory.keySet()));
        
        // Count by symbol
        Map<String, Integer> snapshotsBySymbol = new HashMap<>();
        for (Map.Entry<String, List<OrderBookData>> entry : orderBookHistory.entrySet()) {
            snapshotsBySymbol.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("snapshotsBySymbol", snapshotsBySymbol);
        
        // Estimated memory usage (rough estimate: 3KB per snapshot)
        long estimatedMemoryKB = totalSnapshots * 3;
        stats.put("estimatedMemoryKB", estimatedMemoryKB);
        stats.put("estimatedMemoryMB", estimatedMemoryKB / 1024);
        
        return stats;
    }
    
    /**
     * Create storage key from provider and symbol
     */
    private String createKey(String provider, String symbol) {
        return provider + "_" + symbol;
    }
}

