package org.cloudvision.trading.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.model.OrderBookData;
import org.cloudvision.trading.model.TradeData;
import org.cloudvision.trading.service.OrderBookHistoryService;
import org.cloudvision.trading.service.TradeHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Historical Order Flow Data
 * 
 * Provides endpoints to:
 * - Query historical trade data
 * - Query historical order book snapshots
 * - Get statistics about stored data
 * - Configure storage parameters
 */
@RestController
@RequestMapping("/api/orderflow/historical")
@CrossOrigin(origins = "*")
@Tag(name = "Order Flow History", description = "Historical trade and order book data API")
public class OrderFlowHistoryController {
    
    @Autowired
    private TradeHistoryService tradeHistoryService;
    
    @Autowired
    private OrderBookHistoryService orderBookHistoryService;
    
    @Autowired
    private org.cloudvision.trading.service.BookTickerHistoryService bookTickerHistoryService;
    
    // ============================================================
    // TRADE DATA ENDPOINTS
    // ============================================================
    
    /**
     * Get historical trade data
     * GET /api/orderflow/historical/trades/{provider}/{symbol}
     */
    @Operation(
        summary = "Get historical trade data",
        description = "Retrieves historical trade data for a specific symbol. Returns last N trades (default 1000)"
    )
    @GetMapping("/trades/{provider}/{symbol}")
    public ResponseEntity<Map<String, Object>> getTrades(
            @Parameter(description = "Provider name (e.g., Binance)") 
            @PathVariable String provider,
            @Parameter(description = "Trading symbol (e.g., BTCUSDT)") 
            @PathVariable String symbol,
            @Parameter(description = "Number of trades to retrieve (default 1000)")
            @RequestParam(defaultValue = "1000") int count,
            @Parameter(description = "Start time (ISO-8601, optional)")
            @RequestParam(required = false) String startTime,
            @Parameter(description = "End time (ISO-8601, optional)")
            @RequestParam(required = false) String endTime) {
        
        try {
            List<TradeData> trades;
            
            // Time range query
            if (startTime != null && endTime != null) {
                Instant start = Instant.parse(startTime);
                Instant end = Instant.parse(endTime);
                trades = tradeHistoryService.getTradesInTimeRange(provider, symbol, start, end);
            } 
            // Last N trades
            else {
                trades = tradeHistoryService.getLastNTrades(provider, symbol, count);
            }
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("provider", provider);
            response.put("symbol", symbol);
            response.put("tradeCount", trades.size());
            response.put("trades", trades);
            
            if (!trades.isEmpty()) {
                response.put("firstTradeTime", trades.get(0).getTimestamp());
                response.put("lastTradeTime", trades.get(trades.size() - 1).getTimestamp());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get the latest trade for a symbol
     */
    @Operation(
        summary = "Get latest trade",
        description = "Retrieves the most recent trade for a symbol"
    )
    @GetMapping("/trades/{provider}/{symbol}/latest")
    public ResponseEntity<?> getLatestTrade(
            @PathVariable String provider,
            @PathVariable String symbol) {
        
        TradeData latest = tradeHistoryService.getLatestTrade(provider, symbol);
        
        if (latest == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "No trades found for " + provider + ":" + symbol
            ));
        }
        
        return ResponseEntity.ok(latest);
    }
    
    // ============================================================
    // ORDER BOOK DATA ENDPOINTS
    // ============================================================
    
    /**
     * Get historical order book snapshots
     * GET /api/orderflow/historical/orderbook/{provider}/{symbol}
     */
    @Operation(
        summary = "Get historical order book snapshots",
        description = "Retrieves historical order book snapshots for a specific symbol. " +
                     "Snapshots are automatically throttled to 1 per 10 seconds (configurable). " +
                     "Returns last N snapshots (default 100)"
    )
    @GetMapping("/orderbook/{provider}/{symbol}")
    public ResponseEntity<Map<String, Object>> getOrderBooks(
            @Parameter(description = "Provider name (e.g., Binance)") 
            @PathVariable String provider,
            @Parameter(description = "Trading symbol (e.g., BTCUSDT)") 
            @PathVariable String symbol,
            @Parameter(description = "Number of snapshots to retrieve (default 100)")
            @RequestParam(defaultValue = "100") int count,
            @Parameter(description = "Start time (ISO-8601, optional)")
            @RequestParam(required = false) String startTime,
            @Parameter(description = "End time (ISO-8601, optional)")
            @RequestParam(required = false) String endTime) {
        
        try {
            List<OrderBookData> snapshots;
            
            // Time range query
            if (startTime != null && endTime != null) {
                Instant start = Instant.parse(startTime);
                Instant end = Instant.parse(endTime);
                snapshots = orderBookHistoryService.getOrderBooksInTimeRange(provider, symbol, start, end);
            } 
            // Last N snapshots
            else {
                snapshots = orderBookHistoryService.getLastNOrderBooks(provider, symbol, count);
            }
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("provider", provider);
            response.put("symbol", symbol);
            response.put("snapshotCount", snapshots.size());
            response.put("snapshots", snapshots);
            
            if (!snapshots.isEmpty()) {
                response.put("firstSnapshotTime", snapshots.get(0).getTimestamp());
                response.put("lastSnapshotTime", snapshots.get(snapshots.size() - 1).getTimestamp());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get order book at a specific time (or closest before)
     */
    @Operation(
        summary = "Get order book at specific time",
        description = "Retrieves the order book snapshot at or before a specific time"
    )
    @GetMapping("/orderbook/{provider}/{symbol}/at/{time}")
    public ResponseEntity<?> getOrderBookAtTime(
            @PathVariable String provider,
            @PathVariable String symbol,
            @Parameter(description = "Target time (ISO-8601)")
            @PathVariable String time) {
        
        try {
            Instant targetTime = Instant.parse(time);
            OrderBookData orderBook = orderBookHistoryService.getOrderBookAtTime(provider, symbol, targetTime);
            
            if (orderBook == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "No order book snapshot found at or before " + time
                ));
            }
            
            return ResponseEntity.ok(orderBook);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get the latest order book for a symbol
     */
    @Operation(
        summary = "Get latest order book",
        description = "Retrieves the most recent order book snapshot for a symbol"
    )
    @GetMapping("/orderbook/{provider}/{symbol}/latest")
    public ResponseEntity<?> getLatestOrderBook(
            @PathVariable String provider,
            @PathVariable String symbol) {
        
        OrderBookData latest = orderBookHistoryService.getLatestOrderBook(provider, symbol);
        
        if (latest == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "No order book snapshots found for " + provider + ":" + symbol
            ));
        }
        
        return ResponseEntity.ok(latest);
    }
    
    // ============================================================
    // BOOK TICKER DATA ENDPOINTS
    // ============================================================
    
    /**
     * Get historical book ticker snapshots (best bid/ask over time)
     */
    @Operation(
        summary = "Get historical book ticker snapshots",
        description = "Retrieves historical best bid/ask snapshots for spread and scalping analysis. " +
                     "Snapshots are throttled to 1 per second by default (configurable)."
    )
    @GetMapping("/bookticker/{provider}/{symbol}")
    public ResponseEntity<Map<String, Object>> getBookTickers(
            @PathVariable String provider,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3600") int count) {
        
        try {
            List<org.cloudvision.trading.service.BookTickerHistoryService.BookTickerSnapshot> snapshots = 
                bookTickerHistoryService.getLastNBookTickers(provider, symbol, count);
            
            Map<String, Object> response = new HashMap<>();
            response.put("provider", provider);
            response.put("symbol", symbol);
            response.put("snapshotCount", snapshots.size());
            response.put("snapshots", snapshots);
            
            if (!snapshots.isEmpty()) {
                response.put("firstSnapshotTime", snapshots.get(0).getTimestamp());
                response.put("lastSnapshotTime", snapshots.get(snapshots.size() - 1).getTimestamp());
                
                // Calculate spread statistics
                double avgSpread = bookTickerHistoryService.getAverageSpread(provider, symbol, count);
                double avgSpreadBps = bookTickerHistoryService.getAverageSpreadBps(provider, symbol, count);
                double avgImbalance = bookTickerHistoryService.getAverageImbalance(provider, symbol, count);
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("averageSpread", avgSpread);
                stats.put("averageSpreadBps", avgSpreadBps);
                stats.put("averageImbalance", avgImbalance);
                response.put("statistics", stats);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get the latest book ticker
     */
    @Operation(
        summary = "Get latest book ticker",
        description = "Retrieves the most recent best bid/ask snapshot"
    )
    @GetMapping("/bookticker/{provider}/{symbol}/latest")
    public ResponseEntity<?> getLatestBookTicker(
            @PathVariable String provider,
            @PathVariable String symbol) {
        
        org.cloudvision.trading.service.BookTickerHistoryService.BookTickerSnapshot latest = 
            bookTickerHistoryService.getLatestBookTicker(provider, symbol);
        
        if (latest == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "No book ticker snapshots found for " + provider + ":" + symbol
            ));
        }
        
        return ResponseEntity.ok(latest);
    }
    
    /**
     * Detect spread anomalies
     */
    @Operation(
        summary = "Detect spread anomalies",
        description = "Finds periods where spread was unusually wide (> threshold * average)"
    )
    @GetMapping("/bookticker/{provider}/{symbol}/anomalies")
    public ResponseEntity<Map<String, Object>> getSpreadAnomalies(
            @PathVariable String provider,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1000") int lookback,
            @RequestParam(defaultValue = "2.0") double threshold) {
        
        try {
            List<org.cloudvision.trading.service.BookTickerHistoryService.BookTickerSnapshot> anomalies = 
                bookTickerHistoryService.detectSpreadAnomalies(provider, symbol, lookback, threshold);
            
            Map<String, Object> response = new HashMap<>();
            response.put("provider", provider);
            response.put("symbol", symbol);
            response.put("lookback", lookback);
            response.put("threshold", threshold);
            response.put("anomalyCount", anomalies.size());
            response.put("anomalies", anomalies);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // ============================================================
    // STATISTICS AND CONFIGURATION
    // ============================================================
    
    /**
     * Get statistics about stored trade data
     */
    @Operation(
        summary = "Get trade history statistics",
        description = "Returns statistics about stored trade data (counts, memory usage, etc.)"
    )
    @GetMapping("/trades/stats")
    public ResponseEntity<Map<String, Object>> getTradeStats() {
        return ResponseEntity.ok(tradeHistoryService.getStatistics());
    }
    
    /**
     * Get statistics about stored order book data
     */
    @Operation(
        summary = "Get order book history statistics",
        description = "Returns statistics about stored order book snapshots (counts, memory usage, etc.)"
    )
    @GetMapping("/orderbook/stats")
    public ResponseEntity<Map<String, Object>> getOrderBookStats() {
        return ResponseEntity.ok(orderBookHistoryService.getStatistics());
    }
    
    /**
     * Get statistics about stored book ticker data
     */
    @Operation(
        summary = "Get book ticker history statistics",
        description = "Returns statistics about stored book ticker snapshots (counts, memory usage, etc.)"
    )
    @GetMapping("/bookticker/stats")
    public ResponseEntity<Map<String, Object>> getBookTickerStats() {
        return ResponseEntity.ok(bookTickerHistoryService.getStatistics());
    }
    
    /**
     * Configure order book snapshot interval
     */
    @Operation(
        summary = "Configure order book snapshot interval",
        description = "Set how often to store order book snapshots (in seconds). " +
                     "Lower values = more data but more memory usage."
    )
    @PostMapping("/orderbook/{provider}/{symbol}/config/interval")
    public ResponseEntity<Map<String, Object>> setSnapshotInterval(
            @PathVariable String provider,
            @PathVariable String symbol,
            @Parameter(description = "Interval in seconds between snapshots")
            @RequestParam int intervalSeconds) {
        
        orderBookHistoryService.setSnapshotInterval(provider, symbol, intervalSeconds);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "provider", provider,
            "symbol", symbol,
            "snapshotIntervalSeconds", intervalSeconds,
            "message", "Snapshot interval updated successfully"
        ));
    }
    
    /**
     * Configure maximum snapshots to keep
     */
    @Operation(
        summary = "Configure maximum order book snapshots",
        description = "Set maximum number of order book snapshots to keep in memory"
    )
    @PostMapping("/orderbook/{provider}/{symbol}/config/max")
    public ResponseEntity<Map<String, Object>> setMaxSnapshots(
            @PathVariable String provider,
            @PathVariable String symbol,
            @Parameter(description = "Maximum number of snapshots to keep")
            @RequestParam int maxSnapshots) {
        
        orderBookHistoryService.setMaxSnapshots(provider, symbol, maxSnapshots);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "provider", provider,
            "symbol", symbol,
            "maxSnapshots", maxSnapshots,
            "message", "Maximum snapshots limit updated successfully"
        ));
    }
    
    /**
     * Clear history for a specific symbol
     */
    @Operation(
        summary = "Clear history for symbol",
        description = "Clears all stored trade, order book, and book ticker data for a specific symbol"
    )
    @DeleteMapping("/{provider}/{symbol}")
    public ResponseEntity<Map<String, Object>> clearHistory(
            @PathVariable String provider,
            @PathVariable String symbol) {
        
        tradeHistoryService.clear(provider, symbol);
        orderBookHistoryService.clear(provider, symbol);
        bookTickerHistoryService.clear(provider, symbol);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "provider", provider,
            "symbol", symbol,
            "message", "All history cleared successfully (trades, order books, book tickers)"
        ));
    }
}

