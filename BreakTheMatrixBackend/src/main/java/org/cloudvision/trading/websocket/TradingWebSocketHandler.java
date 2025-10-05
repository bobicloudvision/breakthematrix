package org.cloudvision.trading.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TradingWebSocketHandler extends TextWebSocketHandler {
    
    private final UniversalTradingDataService tradingService;
    private final TradingBot tradingBot;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public TradingWebSocketHandler(UniversalTradingDataService tradingService, TradingBot tradingBot) {
        this.tradingService = tradingService;
        this.tradingBot = tradingBot;
        
        // Configure ObjectMapper for Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Register with TradingBot to receive forwarded data
        System.out.println("🔧 Registering WebSocket handler with TradingBot");
        this.tradingBot.addDataHandler(this::broadcastTradingData);
        System.out.println("✅ WebSocket handler registered with TradingBot successfully");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("Trading WebSocket connected: " + session.getId());
        
        // Send welcome message
        session.sendMessage(new TextMessage("{\"type\":\"connected\",\"message\":\"Trading data stream connected\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received message from " + session.getId() + ": " + payload);
        
        try {
            // Parse incoming message as JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String action = (String) messageData.get("action");
            
            switch (action) {
                case "subscribe":
                    String provider = (String) messageData.get("provider");
                    String symbol = (String) messageData.get("symbol");
                    handleSubscribe(session, provider, symbol);
                    break;
                case "subscribeKlines":
                    String klineProvider = (String) messageData.get("provider");
                    String klineSymbol = (String) messageData.get("symbol");
                    String interval = (String) messageData.get("interval");
                    handleSubscribeKlines(session, klineProvider, klineSymbol, interval);
                    break;
                case "getProviders":
                    handleGetProviders(session);
                    break;
                case "getIntervals":
                    String intervalProvider = (String) messageData.get("provider");
                    handleGetIntervals(session, intervalProvider);
                    break;
                default:
                    session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Unknown action: " + action + "\"}"));
            }
        } catch (Exception e) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Invalid message format\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("Trading WebSocket disconnected: " + session.getId());
    }

    private void handleSubscribe(WebSocketSession session, String provider, String symbol) throws IOException {
        try {
            tradingService.connectProvider(provider);
            tradingService.subscribeToSymbol(provider, symbol);
            
            String response = String.format(
                "{\"type\":\"subscribed\",\"provider\":\"%s\",\"symbol\":\"%s\"}", 
                provider, symbol
            );
            session.sendMessage(new TextMessage(response));
        } catch (Exception e) {
            String errorResponse = String.format(
                "{\"type\":\"error\",\"message\":\"Failed to subscribe to %s on %s: %s\"}", 
                symbol, provider, e.getMessage()
            );
            session.sendMessage(new TextMessage(errorResponse));
        }
    }

    private void handleSubscribeKlines(WebSocketSession session, String provider, String symbol, String interval) throws IOException {
        try {
            System.out.println("🔄 Processing klines subscription: " + provider + "/" + symbol + "/" + interval);
            
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            
            // Connect provider (this should not throw exceptions now)
            tradingService.connectProvider(provider);
            
            // Check if provider is actually connected
            var tradingProvider = tradingService.getProvider(provider);
            if (tradingProvider == null || !tradingProvider.isConnected()) {
                String errorResponse = String.format(
                    "{\"type\":\"error\",\"message\":\"Provider %s is not available or failed to connect\"}", 
                    provider
                );
                session.sendMessage(new TextMessage(errorResponse));
                return;
            }
            
            // Subscribe to klines
            tradingService.subscribeToKlines(provider, symbol, timeInterval);
            
            String response = String.format(
                "{\"type\":\"subscribedKlines\",\"provider\":\"%s\",\"symbol\":\"%s\",\"interval\":\"%s\"}", 
                provider, symbol, interval
            );
            session.sendMessage(new TextMessage(response));
            
            System.out.println("✅ Klines subscription processed successfully");
            
        } catch (Exception e) {
            System.err.println("❌ Error in handleSubscribeKlines: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            
            String errorResponse = String.format(
                "{\"type\":\"error\",\"message\":\"Failed to subscribe to %s klines (%s) on %s: %s\"}", 
                symbol, interval, provider, e.getMessage()
            );
            session.sendMessage(new TextMessage(errorResponse));
        }
    }

    private void handleGetProviders(WebSocketSession session) throws IOException {
        try {
            String providers = objectMapper.writeValueAsString(tradingService.getProviders());
            String response = String.format("{\"type\":\"providers\",\"data\":%s}", providers);
            session.sendMessage(new TextMessage(response));
        } catch (Exception e) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Failed to get providers\"}"));
        }
    }

    private void handleGetIntervals(WebSocketSession session, String provider) throws IOException {
        try {
            String intervals = objectMapper.writeValueAsString(
                tradingService.getSupportedIntervals(provider).stream()
                    .map(TimeInterval::getValue)
                    .toList()
            );
            String response = String.format("{\"type\":\"intervals\",\"provider\":\"%s\",\"data\":%s}", provider, intervals);
            session.sendMessage(new TextMessage(response));
        } catch (Exception e) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Failed to get intervals\"}"));
        }
    }

    private void broadcastTradingData(TradingData data) {
//        System.out.println("🚀 broadcastTradingData() method called with: " + data.getSymbol());
        try {
//            System.out.println("📡 Broadcasting data to " + sessions.size() + " WebSocket sessions: " + data.getSymbol());
            
            Map<String, Object> messageData = Map.of(
                "type", "candleUpdate", // Frontend expects "candleUpdate" for real-time candle updates
                "symbol", data.getSymbol(),
                "timestamp", data.getTimestamp(),
                "provider", data.getProvider(),
                "dataType", data.getType().toString()
            );

            // Add candlestick data if available
            if (data.hasCandlestickData()) {
                var candlestick = data.getCandlestickData();
                messageData = new java.util.HashMap<>(messageData);
                
                Map<String, Object> candlestickMap = new java.util.HashMap<>();
                // Convert BigDecimal to double for proper charting display
                candlestickMap.put("open", candlestick.getOpen().doubleValue());
                candlestickMap.put("high", candlestick.getHigh().doubleValue());
                candlestickMap.put("low", candlestick.getLow().doubleValue());
                candlestickMap.put("close", candlestick.getClose().doubleValue());
                candlestickMap.put("volume", candlestick.getVolume().doubleValue());
                candlestickMap.put("quoteAssetVolume", candlestick.getQuoteAssetVolume().doubleValue());
                candlestickMap.put("numberOfTrades", candlestick.getNumberOfTrades());
                candlestickMap.put("interval", candlestick.getInterval());
                // Timestamps - provide both formats for compatibility
                candlestickMap.put("openTime", candlestick.getOpenTime().toString());
                candlestickMap.put("closeTime", candlestick.getCloseTime().toString());
                candlestickMap.put("time", candlestick.getOpenTime().getEpochSecond()); // Unix seconds (TradingView)
                candlestickMap.put("timestamp", candlestick.getOpenTime().getEpochSecond()); // Unix seconds (alias)
                candlestickMap.put("timeMs", candlestick.getOpenTime().toEpochMilli()); // Unix milliseconds (Chart.js)
                candlestickMap.put("isClosed", candlestick.isClosed());
                
                // Frontend expects "candle" key, not "candlestick"
                messageData.put("candle", candlestickMap);
            } 
            // Add trade data if available (order flow)
            else if (data.hasTradeData()) {
                messageData = new java.util.HashMap<>(messageData);
                var trade = data.getTradeData();
                
                Map<String, Object> tradeMap = new java.util.HashMap<>();
                tradeMap.put("tradeId", trade.getTradeId());
                tradeMap.put("price", trade.getPrice());
                tradeMap.put("quantity", trade.getQuantity());
                tradeMap.put("quoteQuantity", trade.getQuoteQuantity());
                tradeMap.put("isBuyerMaker", trade.isBuyerMaker());
                tradeMap.put("isAggressiveBuy", trade.isAggressiveBuy());
                tradeMap.put("isAggressiveSell", trade.isAggressiveSell());
                tradeMap.put("isAggregate", trade.isAggregateTrade());
                tradeMap.put("timestamp", trade.getTimestamp());
                
                if (trade.isAggregateTrade()) {
                    tradeMap.put("firstTradeId", trade.getFirstTradeId());
                    tradeMap.put("lastTradeId", trade.getLastTradeId());
                }
                
                messageData.put("trade", tradeMap);
            }
            // Add order book data if available (order flow)
            else if (data.hasOrderBookData()) {
                messageData = new java.util.HashMap<>(messageData);
                var orderBook = data.getOrderBookData();
                
                Map<String, Object> orderBookMap = new java.util.HashMap<>();
                orderBookMap.put("lastUpdateId", orderBook.getLastUpdateId());
                orderBookMap.put("bestBid", orderBook.getBestBid());
                orderBookMap.put("bestAsk", orderBook.getBestAsk());
                orderBookMap.put("spread", orderBook.getSpread());
                
                // Convert bid levels
                java.util.List<Map<String, Object>> bids = new java.util.ArrayList<>();
                for (var level : orderBook.getBids()) {
                    bids.add(Map.of("price", level.getPrice(), "quantity", level.getQuantity()));
                }
                orderBookMap.put("bids", bids);
                
                // Convert ask levels
                java.util.List<Map<String, Object>> asks = new java.util.ArrayList<>();
                for (var level : orderBook.getAsks()) {
                    asks.add(Map.of("price", level.getPrice(), "quantity", level.getQuantity()));
                }
                orderBookMap.put("asks", asks);
                
                // Add volume aggregates
                orderBookMap.put("bidVolume5", orderBook.getTotalBidVolume(5));
                orderBookMap.put("askVolume5", orderBook.getTotalAskVolume(5));
                orderBookMap.put("bidVolume10", orderBook.getTotalBidVolume(10));
                orderBookMap.put("askVolume10", orderBook.getTotalAskVolume(10));
                
                messageData.put("orderBook", orderBookMap);
            } 
            else {
                // Add simple price/volume data
                messageData = new java.util.HashMap<>(messageData);
                messageData.put("price", data.getPrice());
                messageData.put("volume", data.getVolume());
            }

            String jsonData = objectMapper.writeValueAsString(messageData);
            
            TextMessage message = new TextMessage(jsonData);
            
            // Broadcast to all connected sessions
            int sentCount = 0;
            for (WebSocketSession session : sessions.values()) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                        sentCount++;
                    } else {
                        System.out.println("⚠️ Session " + session.getId() + " is closed, skipping");
                    }
                } catch (IOException e) {
                    System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
                }
            }
//            System.out.println("✅ Successfully broadcast to " + sentCount + " sessions");
        } catch (Exception e) {
            System.err.println("Failed to broadcast trading data: " + e.getMessage());
        }
    }
}
