package org.cloudvision.trading.service;

import org.cloudvision.trading.model.TradeData;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized in-memory storage for historical trade data
 * 
 * PURPOSE:
 * - Store individual or aggregate trades for historical analysis
 * - Enable Bookmap and other order flow indicators to access past trade data
 * - Provide efficient querying by time range, price range, etc.
 * 
 * DESIGN:
 * - Similar to CandlestickHistoryService but for trades
 * - Circular buffer to limit memory usage
 * - Fast lookups by symbol/provider
 * 
 * USAGE:
 * - Called by trading data providers when trades arrive
 * - Queried by REST endpoints for historical trade data
 * - Used by Bookmap indicator for historical reconstruction
 */
@Service
public class TradeHistoryService {
    
    // Key: "PROVIDER_SYMBOL" (e.g., "Binance_BTCUSDT")
    // Value: Sorted list of trades
    private final Map<String, List<TradeData>> tradeHistory = new ConcurrentHashMap<>();
    
    // Configuration: max trades to keep per symbol
    // Increased to support historical order flow indicators (BigTrades, CVD, Bookmap)
    // For 5000 candles of 1m data (~3.5 days), busy markets can have 500k+ trades
    private static final int DEFAULT_MAX_TRADES = 1000000; // Last 1M trades (sufficient for full history)
    private final Map<String, Integer> maxTradesConfig = new ConcurrentHashMap<>();
    
    /**
     * Add a single trade to history
     * 
     * @param trade Trade data to store
     */
    public void addTrade(TradeData trade) {
        String key = createKey(trade.getProvider(), trade.getSymbol());
        
        List<TradeData> trades = tradeHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (trades) {
            trades.add(trade);
            
            // Keep sorted by timestamp
            trades.sort(Comparator.comparing(TradeData::getTimestamp));
            
            // Trim old data if exceeds max
            int maxTrades = maxTradesConfig.getOrDefault(key, DEFAULT_MAX_TRADES);
            if (trades.size() > maxTrades) {
                int toRemove = trades.size() - maxTrades;
                trades.subList(0, toRemove).clear();
            }
        }
    }
    
    /**
     * Add multiple trades in bulk (e.g., from historical data load)
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param trades List of trades to add
     */
    public void addTrades(String provider, String symbol, List<TradeData> trades) {
        if (trades == null || trades.isEmpty()) {
            return;
        }
        
        String key = createKey(provider, symbol);
        List<TradeData> existingTrades = tradeHistory.computeIfAbsent(key, k -> new ArrayList<>());
        
        synchronized (existingTrades) {
            // Add all new trades
            existingTrades.addAll(trades);
            
            // Remove duplicates (by timestamp + price + quantity)
            Set<String> seen = new HashSet<>();
            existingTrades.removeIf(trade -> {
                String signature = trade.getTimestamp() + "_" + trade.getPrice() + "_" + trade.getQuantity();
                return !seen.add(signature);
            });
            
            // Keep sorted by timestamp
            existingTrades.sort(Comparator.comparing(TradeData::getTimestamp));
            
            // Trim old data if exceeds max
            int maxTrades = maxTradesConfig.getOrDefault(key, DEFAULT_MAX_TRADES);
            if (existingTrades.size() > maxTrades) {
                int toRemove = existingTrades.size() - maxTrades;
                existingTrades.subList(0, toRemove).clear();
            }
            
            System.out.println("📦 Stored " + trades.size() + " trades for " + key + 
                             " (total: " + existingTrades.size() + ")");
        }
    }
    
    /**
     * Get all trades for a provider and symbol
     * 
     * @return List of trades (copy, thread-safe)
     */
    public List<TradeData> getTrades(String provider, String symbol) {
        String key = createKey(provider, symbol);
        List<TradeData> trades = tradeHistory.get(key);
        return trades != null ? new ArrayList<>(trades) : Collections.emptyList();
    }
    
    /**
     * Get last N trades for a provider and symbol
     * 
     * @param count Number of trades to retrieve
     * @return List of last N trades
     */
    public List<TradeData> getLastNTrades(String provider, String symbol, int count) {
        List<TradeData> allTrades = getTrades(provider, symbol);
        
        if (allTrades.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        
        int size = allTrades.size();
        int startIndex = Math.max(0, size - count);
        return allTrades.subList(startIndex, size);
    }
    
    /**
     * Get trades within a time range
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param startTime Start time (inclusive)
     * @param endTime End time (inclusive)
     * @return List of trades within the time range
     */
    public List<TradeData> getTradesInTimeRange(String provider, String symbol, 
                                                Instant startTime, Instant endTime) {
        List<TradeData> allTrades = getTrades(provider, symbol);
        
        return allTrades.stream()
            .filter(trade -> !trade.getTimestamp().isBefore(startTime) && 
                           !trade.getTimestamp().isAfter(endTime))
            .collect(Collectors.toList());
    }
    
    /**
     * Get the latest trade for a symbol
     * 
     * @return Latest trade, or null if no trades exist
     */
    public TradeData getLatestTrade(String provider, String symbol) {
        List<TradeData> trades = getTrades(provider, symbol);
        return trades.isEmpty() ? null : trades.get(trades.size() - 1);
    }
    
    /**
     * Get trade count for a symbol
     * 
     * @return Number of trades stored
     */
    public int getTradeCount(String provider, String symbol) {
        String key = createKey(provider, symbol);
        List<TradeData> trades = tradeHistory.get(key);
        return trades != null ? trades.size() : 0;
    }
    
    /**
     * Configure maximum trades to keep for a specific symbol
     * 
     * @param provider Provider name
     * @param symbol Trading symbol
     * @param maxTrades Maximum number of trades to keep (0 = use default)
     */
    public void setMaxTrades(String provider, String symbol, int maxTrades) {
        String key = createKey(provider, symbol);
        if (maxTrades > 0) {
            maxTradesConfig.put(key, maxTrades);
        } else {
            maxTradesConfig.remove(key);
        }
    }
    
    /**
     * Clear all trades for a specific symbol
     */
    public void clear(String provider, String symbol) {
        String key = createKey(provider, symbol);
        tradeHistory.remove(key);
        System.out.println("🗑️ Cleared trade history for " + key);
    }
    
    /**
     * Clear all stored trade data
     */
    public void clearAll() {
        int totalTrades = tradeHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        tradeHistory.clear();
        maxTradesConfig.clear();
        System.out.println("🗑️ Cleared all trade history (" + totalTrades + " trades)");
    }
    
    /**
     * Get statistics about stored trade data
     * 
     * @return Map containing statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalTrades = tradeHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        
        stats.put("totalTrades", totalTrades);
        stats.put("symbolCount", tradeHistory.size());
        stats.put("symbols", new ArrayList<>(tradeHistory.keySet()));
        
        // Count by symbol
        Map<String, Integer> tradesBySymbol = new HashMap<>();
        for (Map.Entry<String, List<TradeData>> entry : tradeHistory.entrySet()) {
            tradesBySymbol.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("tradesBySymbol", tradesBySymbol);
        
        return stats;
    }
    
    /**
     * Create storage key from provider and symbol
     */
    private String createKey(String provider, String symbol) {
        return provider + "_" + symbol;
    }
}

