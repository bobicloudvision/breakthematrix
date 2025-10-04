package org.cloudvision.trading.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.model.*;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for Order Flow operations
 * Allows subscribing/unsubscribing to order flow streams and querying order flow configuration
 */
@RestController
@RequestMapping("/api/orderflow")
@Tag(name = "Order Flow", description = "Order flow data management and subscriptions")
@CrossOrigin(origins = "*")
public class OrderFlowController {
    
    private final UniversalTradingDataService tradingService;
    
    public OrderFlowController(UniversalTradingDataService tradingService) {
        this.tradingService = tradingService;
    }
    
    @PostMapping("/subscribe/trades")
    @Operation(summary = "Subscribe to individual trades", description = "Start receiving individual trade data for a symbol (high frequency)")
    public ResponseEntity<?> subscribeToTrades(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.subscribeToTrades(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscribed to " + symbol + " trades on " + provider,
                "provider", provider,
                "symbol", symbol,
                "type", "TRADE"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/subscribe/trades")
    @Operation(summary = "Unsubscribe from individual trades", description = "Stop receiving individual trade data for a symbol")
    public ResponseEntity<?> unsubscribeFromTrades(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.unsubscribeFromTrades(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Unsubscribed from " + symbol + " trades on " + provider
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/subscribe/aggregate-trades")
    @Operation(summary = "Subscribe to aggregate trades", description = "Start receiving aggregate trade data for a symbol (recommended)")
    public ResponseEntity<?> subscribeToAggregateTrades(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.subscribeToAggregateTrades(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscribed to " + symbol + " aggregate trades on " + provider,
                "provider", provider,
                "symbol", symbol,
                "type", "AGGREGATE_TRADE"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/subscribe/aggregate-trades")
    @Operation(summary = "Unsubscribe from aggregate trades", description = "Stop receiving aggregate trade data for a symbol")
    public ResponseEntity<?> unsubscribeFromAggregateTrades(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.unsubscribeFromAggregateTrades(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Unsubscribed from " + symbol + " aggregate trades on " + provider
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/subscribe/orderbook")
    @Operation(summary = "Subscribe to order book", description = "Start receiving order book depth updates for a symbol")
    public ResponseEntity<?> subscribeToOrderBook(
            @RequestParam String provider,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int depth) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.subscribeToOrderBook(symbol, depth);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscribed to " + symbol + " order book (depth: " + depth + ") on " + provider,
                "provider", provider,
                "symbol", symbol,
                "type", "ORDER_BOOK",
                "depth", depth
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/subscribe/orderbook")
    @Operation(summary = "Unsubscribe from order book", description = "Stop receiving order book depth updates for a symbol")
    public ResponseEntity<?> unsubscribeFromOrderBook(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.unsubscribeFromOrderBook(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Unsubscribed from " + symbol + " order book on " + provider
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/subscribe/book-ticker")
    @Operation(summary = "Subscribe to book ticker", description = "Start receiving best bid/ask updates for a symbol (lightweight)")
    public ResponseEntity<?> subscribeToBookTicker(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.subscribeToBookTicker(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscribed to " + symbol + " book ticker on " + provider,
                "provider", provider,
                "symbol", symbol,
                "type", "BOOK_TICKER"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/subscribe/book-ticker")
    @Operation(summary = "Unsubscribe from book ticker", description = "Stop receiving best bid/ask updates for a symbol")
    public ResponseEntity<?> unsubscribeFromBookTicker(
            @RequestParam String provider,
            @RequestParam String symbol) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            dataProvider.unsubscribeFromBookTicker(symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Unsubscribed from " + symbol + " book ticker on " + provider
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/subscribe/all")
    @Operation(summary = "Subscribe to all order flow types", description = "Subscribe to all order flow streams for a symbol")
    public ResponseEntity<?> subscribeToAllOrderFlow(
            @RequestParam String provider,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") int orderBookDepth,
            @RequestParam(defaultValue = "true") boolean includeAggregateTrades,
            @RequestParam(defaultValue = "false") boolean includeIndividualTrades,
            @RequestParam(defaultValue = "true") boolean includeOrderBook,
            @RequestParam(defaultValue = "true") boolean includeBookTicker) {
        try {
            TradingDataProvider dataProvider = tradingService.getProvider(provider);
            List<String> subscribed = new ArrayList<>();
            
            if (includeIndividualTrades) {
                dataProvider.subscribeToTrades(symbol);
                subscribed.add("TRADE");
            }
            
            if (includeAggregateTrades) {
                dataProvider.subscribeToAggregateTrades(symbol);
                subscribed.add("AGGREGATE_TRADE");
            }
            
            if (includeOrderBook) {
                dataProvider.subscribeToOrderBook(symbol, orderBookDepth);
                subscribed.add("ORDER_BOOK");
            }
            
            if (includeBookTicker) {
                dataProvider.subscribeToBookTicker(symbol);
                subscribed.add("BOOK_TICKER");
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscribed to order flow streams for " + symbol,
                "provider", provider,
                "symbol", symbol,
                "subscribed", subscribed
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @GetMapping("/supported-types")
    @Operation(summary = "Get supported order flow types", description = "List all supported order flow data types")
    public ResponseEntity<?> getSupportedTypes() {
        return ResponseEntity.ok(Map.of(
            "types", List.of(
                Map.of(
                    "name", "TRADE",
                    "description", "Individual trades (high frequency)",
                    "recommended", false
                ),
                Map.of(
                    "name", "AGGREGATE_TRADE",
                    "description", "Aggregate trades (compressed, recommended)",
                    "recommended", true
                ),
                Map.of(
                    "name", "ORDER_BOOK",
                    "description", "Order book depth (5, 10, or 20 levels)",
                    "recommended", true
                ),
                Map.of(
                    "name", "BOOK_TICKER",
                    "description", "Best bid/ask updates (lightweight)",
                    "recommended", true
                )
            )
        ));
    }
    
    @GetMapping("/info")
    @Operation(summary = "Get order flow information", description = "Get information about order flow capabilities")
    public ResponseEntity<?> getOrderFlowInfo() {
        return ResponseEntity.ok(Map.of(
            "websocket", Map.of(
                "endpoint", "/orderflow-ws",
                "protocol", "ws://",
                "actions", List.of("subscribe", "unsubscribe", "getStats")
            ),
            "rest", Map.of(
                "baseUrl", "/api/orderflow",
                "endpoints", List.of(
                    "/subscribe/trades",
                    "/subscribe/aggregate-trades",
                    "/subscribe/orderbook",
                    "/subscribe/book-ticker",
                    "/subscribe/all"
                )
            ),
            "dataTypes", List.of("TRADE", "AGGREGATE_TRADE", "ORDER_BOOK", "BOOK_TICKER")
        ));
    }
}

