package org.cloudvision.trading.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public TradingWebSocketHandler(UniversalTradingDataService tradingService) {
        this.tradingService = tradingService;
        this.objectMapper = new ObjectMapper();
        
        // Set up global data handler to broadcast to all connected clients
        this.tradingService.setGlobalDataHandler(this::broadcastTradingData);
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
        try {
            Map<String, Object> messageData = Map.of(
                "type", "tradingData",
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
                candlestickMap.put("open", candlestick.getOpen());
                candlestickMap.put("high", candlestick.getHigh());
                candlestickMap.put("low", candlestick.getLow());
                candlestickMap.put("close", candlestick.getClose());
                candlestickMap.put("volume", candlestick.getVolume());
                candlestickMap.put("quoteAssetVolume", candlestick.getQuoteAssetVolume());
                candlestickMap.put("numberOfTrades", candlestick.getNumberOfTrades());
                candlestickMap.put("interval", candlestick.getInterval());
                candlestickMap.put("openTime", candlestick.getOpenTime());
                candlestickMap.put("closeTime", candlestick.getCloseTime());
                candlestickMap.put("isClosed", candlestick.isClosed());
                
                messageData.put("candlestick", candlestickMap);
            } else {
                // Add simple price/volume data
                messageData = new java.util.HashMap<>(messageData);
                messageData.put("price", data.getPrice());
                messageData.put("volume", data.getVolume());
            }

            String jsonData = objectMapper.writeValueAsString(messageData);
            
            TextMessage message = new TextMessage(jsonData);
            
            // Broadcast to all connected sessions
            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to broadcast trading data: " + e.getMessage());
        }
    }
}
