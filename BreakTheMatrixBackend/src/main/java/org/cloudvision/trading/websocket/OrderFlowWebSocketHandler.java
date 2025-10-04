package org.cloudvision.trading.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time order flow data
 * Streams trades, aggregate trades, order book updates, and book ticker data
 * 
 * Supported Actions:
 * - subscribe: Subscribe to order flow for a symbol (filter by type)
 * - unsubscribe: Unsubscribe from order flow
 * - getStats: Get order flow statistics
 * 
 * Message Format:
 * {"action": "subscribe", "symbol": "BTCUSDT", "types": ["TRADE", "ORDER_BOOK"]}
 * {"action": "unsubscribe", "symbol": "BTCUSDT"}
 * {"action": "getStats"}
 */
@Component
public class OrderFlowWebSocketHandler extends TextWebSocketHandler {
    
    private final TradingBot tradingBot;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Session filters: sessionId -> Set of symbols to filter
    private final Map<String, Set<String>> sessionSymbolFilters = new ConcurrentHashMap<>();
    
    // Session type filters: sessionId -> Set of data types to filter
    private final Map<String, Set<TradingDataType>> sessionTypeFilters = new ConcurrentHashMap<>();
    
    // Statistics tracking
    private final Map<String, OrderFlowStats> symbolStats = new ConcurrentHashMap<>();
    
    public OrderFlowWebSocketHandler(TradingBot tradingBot) {
        this.tradingBot = tradingBot;
        
        // Configure ObjectMapper for Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Register with TradingBot to receive order flow data
        this.tradingBot.setAdditionalDataHandler(this::broadcastOrderFlowData);
        
        System.out.println("✅ OrderFlowWebSocketHandler initialized");
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("📡 Order Flow WebSocket connected: " + session.getId());
        
        // Send welcome message
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connected",
            "message", "Order flow data stream connected",
            "supportedTypes", Arrays.asList("TRADE", "AGGREGATE_TRADE", "ORDER_BOOK", "BOOK_TICKER")
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("📨 Received order flow message from " + session.getId() + ": " + payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String action = (String) messageData.get("action");
            
            switch (action) {
                case "subscribe":
                    handleSubscribe(session, messageData);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, messageData);
                    break;
                case "getStats":
                    handleGetStats(session);
                    break;
                default:
                    sendError(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            sendError(session, "Invalid message format: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        sessionSymbolFilters.remove(session.getId());
        sessionTypeFilters.remove(session.getId());
        System.out.println("🔌 Order Flow WebSocket disconnected: " + session.getId());
    }
    
    private void handleSubscribe(WebSocketSession session, Map<String, Object> messageData) throws IOException {
        String symbol = (String) messageData.get("symbol");
        @SuppressWarnings("unchecked")
        List<String> typeStrings = (List<String>) messageData.get("types");
        
        // If no symbol specified, subscribe to all
        if (symbol == null || symbol.isEmpty()) {
            sessionSymbolFilters.remove(session.getId()); // Remove filter = subscribe to all
        } else {
            sessionSymbolFilters.computeIfAbsent(session.getId(), k -> new HashSet<>()).add(symbol);
        }
        
        // Parse and set type filters
        if (typeStrings != null && !typeStrings.isEmpty()) {
            Set<TradingDataType> types = new HashSet<>();
            for (String typeStr : typeStrings) {
                try {
                    types.add(TradingDataType.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    sendError(session, "Invalid data type: " + typeStr);
                    return;
                }
            }
            sessionTypeFilters.put(session.getId(), types);
        } else {
            // Default: subscribe to all order flow types
            sessionTypeFilters.put(session.getId(), new HashSet<>(Arrays.asList(
                TradingDataType.TRADE,
                TradingDataType.AGGREGATE_TRADE,
                TradingDataType.ORDER_BOOK,
                TradingDataType.BOOK_TICKER
            )));
        }
        
        Map<String, Object> response = Map.of(
            "type", "subscribed",
            "symbol", symbol != null ? symbol : "ALL",
            "types", typeStrings != null ? typeStrings : "ALL"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        System.out.println("✅ Session " + session.getId() + " subscribed to " + symbol + " order flow");
    }
    
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> messageData) throws IOException {
        String symbol = (String) messageData.get("symbol");
        
        if (symbol == null) {
            sessionSymbolFilters.remove(session.getId());
            sessionTypeFilters.remove(session.getId());
        } else {
            Set<String> symbols = sessionSymbolFilters.get(session.getId());
            if (symbols != null) {
                symbols.remove(symbol);
            }
        }
        
        Map<String, Object> response = Map.of(
            "type", "unsubscribed",
            "symbol", symbol != null ? symbol : "ALL"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    private void handleGetStats(WebSocketSession session) throws IOException {
        Map<String, Object> stats = new HashMap<>();
        
        for (Map.Entry<String, OrderFlowStats> entry : symbolStats.entrySet()) {
            OrderFlowStats stat = entry.getValue();
            stats.put(entry.getKey(), Map.of(
                "totalTrades", stat.totalTrades,
                "buyVolume", stat.buyVolume,
                "sellVolume", stat.sellVolume,
                "lastPrice", stat.lastPrice != null ? stat.lastPrice : 0,
                "lastSpread", stat.lastSpread != null ? stat.lastSpread : 0
            ));
        }
        
        Map<String, Object> response = Map.of(
            "type", "stats",
            "data", stats
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    private void broadcastOrderFlowData(TradingData data) {
        // Only broadcast order flow data types
        if (!isOrderFlowType(data.getType())) {
            return;
        }
        
        try {
            // Update statistics
            updateStats(data);
            
            // Build message
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("type", "orderFlow");
            messageData.put("dataType", data.getType().toString());
            messageData.put("symbol", data.getSymbol());
            messageData.put("timestamp", data.getTimestamp());
            messageData.put("provider", data.getProvider());
            
            // Add specific data based on type
            if (data.hasTradeData()) {
                TradeData trade = data.getTradeData();
                messageData.put("trade", Map.of(
                    "tradeId", trade.getTradeId(),
                    "price", trade.getPrice(),
                    "quantity", trade.getQuantity(),
                    "quoteQuantity", trade.getQuoteQuantity(),
                    "isBuyerMaker", trade.isBuyerMaker(),
                    "isAggressiveBuy", trade.isAggressiveBuy(),
                    "isAggressiveSell", trade.isAggressiveSell(),
                    "isAggregate", trade.isAggregateTrade(),
                    "firstTradeId", trade.getFirstTradeId(),
                    "lastTradeId", trade.getLastTradeId()
                ));
            } else if (data.hasOrderBookData()) {
                OrderBookData orderBook = data.getOrderBookData();
                
                // Convert order book levels
                List<Map<String, Object>> bids = new ArrayList<>();
                for (OrderBookData.OrderBookLevel level : orderBook.getBids()) {
                    bids.add(Map.of("price", level.getPrice(), "quantity", level.getQuantity()));
                }
                
                List<Map<String, Object>> asks = new ArrayList<>();
                for (OrderBookData.OrderBookLevel level : orderBook.getAsks()) {
                    asks.add(Map.of("price", level.getPrice(), "quantity", level.getQuantity()));
                }
                
                messageData.put("orderBook", Map.of(
                    "lastUpdateId", orderBook.getLastUpdateId(),
                    "bids", bids,
                    "asks", asks,
                    "bestBid", orderBook.getBestBid(),
                    "bestAsk", orderBook.getBestAsk(),
                    "spread", orderBook.getSpread(),
                    "bidVolume5", orderBook.getTotalBidVolume(5),
                    "askVolume5", orderBook.getTotalAskVolume(5),
                    "bidVolume10", orderBook.getTotalBidVolume(10),
                    "askVolume10", orderBook.getTotalAskVolume(10)
                ));
            }
            
            String jsonData = objectMapper.writeValueAsString(messageData);
            TextMessage message = new TextMessage(jsonData);
            
            // Broadcast to filtered sessions
            int sentCount = 0;
            for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
                WebSocketSession session = entry.getValue();
                String sessionId = entry.getKey();
                
                try {
                    if (session.isOpen() && shouldSendToSession(sessionId, data)) {
                        session.sendMessage(message);
                        sentCount++;
                    }
                } catch (IOException e) {
                    System.err.println("❌ Failed to send order flow to session " + sessionId + ": " + e.getMessage());
                }
            }
            
            if (sentCount > 0) {
                System.out.println("📤 Broadcast order flow (" + data.getType() + ") to " + sentCount + " sessions");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast order flow data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean shouldSendToSession(String sessionId, TradingData data) {
        // Check symbol filter
        Set<String> symbolFilter = sessionSymbolFilters.get(sessionId);
        if (symbolFilter != null && !symbolFilter.isEmpty() && !symbolFilter.contains(data.getSymbol())) {
            return false;
        }
        
        // Check type filter
        Set<TradingDataType> typeFilter = sessionTypeFilters.get(sessionId);
        if (typeFilter != null && !typeFilter.isEmpty() && !typeFilter.contains(data.getType())) {
            return false;
        }
        
        return true;
    }
    
    private boolean isOrderFlowType(TradingDataType type) {
        return type == TradingDataType.TRADE ||
               type == TradingDataType.AGGREGATE_TRADE ||
               type == TradingDataType.ORDER_BOOK ||
               type == TradingDataType.BOOK_TICKER;
    }
    
    private void updateStats(TradingData data) {
        OrderFlowStats stats = symbolStats.computeIfAbsent(data.getSymbol(), k -> new OrderFlowStats());
        
        if (data.hasTradeData()) {
            TradeData trade = data.getTradeData();
            stats.totalTrades++;
            
            if (trade.isAggressiveBuy()) {
                stats.buyVolume = stats.buyVolume.add(trade.getQuantity());
            } else {
                stats.sellVolume = stats.sellVolume.add(trade.getQuantity());
            }
            
            stats.lastPrice = trade.getPrice();
        }
        
        if (data.hasOrderBookData()) {
            OrderBookData orderBook = data.getOrderBookData();
            stats.lastSpread = orderBook.getSpread();
        }
    }
    
    private void sendError(WebSocketSession session, String error) throws IOException {
        Map<String, Object> errorMessage = Map.of(
            "type", "error",
            "message", error
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
    }
    
    private static class OrderFlowStats {
        long totalTrades = 0;
        java.math.BigDecimal buyVolume = java.math.BigDecimal.ZERO;
        java.math.BigDecimal sellVolume = java.math.BigDecimal.ZERO;
        java.math.BigDecimal lastPrice;
        java.math.BigDecimal lastSpread;
    }
}

