package org.cloudvision.trading.bot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradingData;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for streaming real-time indicator updates
 * 
 * Clients can subscribe to specific indicator instances and receive
 * real-time updates when new candles arrive.
 * 
 * Supported Actions:
 * - subscribe: Subscribe to specific indicator instance(s)
 * - subscribeContext: Subscribe to all indicators for a context (provider:symbol:interval)
 * - unsubscribe: Unsubscribe from indicator updates
 * - listActive: Get list of all active indicator instances
 * 
 * Message Format:
 * {"action": "subscribe", "instanceKeys": ["Binance:BTCUSDT:5m:sma:7a8b9c"]}
 * {"action": "subscribeContext", "provider": "Binance", "symbol": "BTCUSDT", "interval": "5m"}
 * {"action": "unsubscribe"}
 * {"action": "listActive"}
 */
@Component
public class IndicatorWebSocketHandler extends TextWebSocketHandler {
    
    private final TradingBot tradingBot;
    private final IndicatorInstanceManager instanceManager;
    private final ObjectMapper objectMapper;
    
    // Active WebSocket sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Session subscriptions: sessionId -> Set of instance keys
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    
    // Session context subscriptions: sessionId -> Set of context keys (provider:symbol:interval)
    private final Map<String, Set<String>> sessionContextSubscriptions = new ConcurrentHashMap<>();
    
    public IndicatorWebSocketHandler(TradingBot tradingBot, 
                                     IndicatorInstanceManager instanceManager) {
        this.tradingBot = tradingBot;
        this.instanceManager = instanceManager;
        
        // Configure ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Register with TradingBot to receive candlestick data
        this.tradingBot.addDataHandler(this::processData);
        
        System.out.println("✅ IndicatorWebSocketHandler initialized");
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("📡 Indicator WebSocket connected: " + session.getId());
        
        // Send welcome message
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connected",
            "message", "Indicator update stream connected",
            "actions", Arrays.asList("subscribe", "subscribeContext", "unsubscribe", "listActive")
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionSubscriptions.remove(sessionId);
        sessionContextSubscriptions.remove(sessionId);
        
        System.out.println("📡 Indicator WebSocket disconnected: " + sessionId);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(
                message.getPayload(), 
                Map.class
            );
            
            String action = (String) messageData.get("action");
            
            if (action == null) {
                sendError(session, "Action is required");
                return;
            }
            
            switch (action) {
                case "subscribe" -> handleSubscribe(session, messageData);
                case "subscribeContext" -> handleSubscribeContext(session, messageData);
                case "unsubscribe" -> handleUnsubscribe(session);
                case "listActive" -> handleListActive(session);
                default -> sendError(session, "Unknown action: " + action);
            }
            
        } catch (Exception e) {
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }
    
    // ============================================================
    // Action Handlers
    // ============================================================
    
    private void handleSubscribe(WebSocketSession session, Map<String, Object> messageData) 
            throws IOException {
        @SuppressWarnings("unchecked")
        List<String> instanceKeys = (List<String>) messageData.get("instanceKeys");
        
        if (instanceKeys == null || instanceKeys.isEmpty()) {
            sendError(session, "instanceKeys is required and cannot be empty");
            return;
        }
        
        // Add to subscriptions
        Set<String> subscriptions = sessionSubscriptions.computeIfAbsent(
            session.getId(), 
            k -> ConcurrentHashMap.newKeySet()
        );
        
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        
        for (String instanceKey : instanceKeys) {
            if (instanceManager.isActive(instanceKey)) {
                subscriptions.add(instanceKey);
                valid.add(instanceKey);
            } else {
                invalid.add(instanceKey);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "subscribed");
        response.put("validSubscriptions", valid);
        
        if (!invalid.isEmpty()) {
            response.put("invalidInstances", invalid);
        }
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        System.out.println("✅ Session " + session.getId() + " subscribed to " + 
                         valid.size() + " indicator(s)");
    }
    
    private void handleSubscribeContext(WebSocketSession session, Map<String, Object> messageData) 
            throws IOException {
        String provider = (String) messageData.get("provider");
        String symbol = (String) messageData.get("symbol");
        String interval = (String) messageData.get("interval");
        
        if (provider == null || symbol == null || interval == null) {
            sendError(session, "provider, symbol, and interval are required");
            return;
        }
        
        String contextKey = String.format("%s:%s:%s", provider, symbol, interval);
        
        // Add to context subscriptions
        Set<String> contextSubscriptions = sessionContextSubscriptions.computeIfAbsent(
            session.getId(), 
            k -> ConcurrentHashMap.newKeySet()
        );
        contextSubscriptions.add(contextKey);
        
        // Get current active instances for this context
        List<IndicatorInstanceManager.IndicatorInstance> instances = 
            instanceManager.getInstancesForContext(provider, symbol, interval);
        
        Map<String, Object> response = Map.of(
            "type", "contextSubscribed",
            "context", Map.of(
                "provider", provider,
                "symbol", symbol,
                "interval", interval
            ),
            "activeInstances", instances.size()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        System.out.println("✅ Session " + session.getId() + " subscribed to context " + contextKey);
    }
    
    private void handleUnsubscribe(WebSocketSession session) throws IOException {
        sessionSubscriptions.remove(session.getId());
        sessionContextSubscriptions.remove(session.getId());
        
        Map<String, Object> response = Map.of(
            "type", "unsubscribed",
            "message", "Unsubscribed from all indicator updates"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        System.out.println("✅ Session " + session.getId() + " unsubscribed from all indicators");
    }
    
    private void handleListActive(WebSocketSession session) throws IOException {
        List<IndicatorInstanceManager.IndicatorInstance> instances = 
            instanceManager.getAllInstances();
        
        List<Map<String, Object>> instanceData = new ArrayList<>();
        for (IndicatorInstanceManager.IndicatorInstance instance : instances) {
            Map<String, Object> data = new HashMap<>();
            data.put("instanceKey", instance.getInstanceKey());
            data.put("indicatorId", instance.getIndicatorId());
            data.put("provider", instance.getProvider());
            data.put("symbol", instance.getSymbol());
            data.put("interval", instance.getInterval());
            data.put("params", instance.getParams());
            instanceData.add(data);
        }
        
        Map<String, Object> response = Map.of(
            "type", "activeInstances",
            "totalActive", instances.size(),
            "instances", instanceData
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    // ============================================================
    // Data Processing
    // ============================================================
    
    /**
     * Process incoming trading data and update indicators
     */
    private void processData(TradingData data) {
        // Only process completed candlestick data
        if (!data.hasCandlestickData()) {
            return;
        }
        
        CandlestickData candle = data.getCandlestickData();
        
        // Only process closed candles
        if (!candle.isClosed()) {
            return;
        }
        
        // Update all indicators for this context
        Map<String, IndicatorInstanceManager.IndicatorResult> results = 
            instanceManager.updateAllForContext(candle);
        
        if (results.isEmpty()) {
            return; // No active indicators for this context
        }
        
        // Broadcast updates to subscribed sessions
        for (Map.Entry<String, IndicatorInstanceManager.IndicatorResult> entry : results.entrySet()) {
            String instanceKey = entry.getKey();
            IndicatorInstanceManager.IndicatorResult result = entry.getValue();
            
            broadcastIndicatorUpdate(instanceKey, result, candle);
        }
    }
    
    /**
     * Broadcast indicator update to subscribed sessions
     */
    private void broadcastIndicatorUpdate(String instanceKey, 
                                         IndicatorInstanceManager.IndicatorResult result,
                                         CandlestickData candle) {
        try {
            IndicatorInstanceManager.IndicatorInstance instance = 
                instanceManager.getInstance(instanceKey);
            
            if (instance == null) {
                return;
            }
            
            String contextKey = String.format("%s:%s:%s", 
                instance.getProvider(), instance.getSymbol(), instance.getInterval());
            
            // Build update message
            Map<String, Object> updateMessage = new HashMap<>();
            updateMessage.put("type", "indicatorUpdate");
            updateMessage.put("instanceKey", instanceKey);
            updateMessage.put("indicatorId", instance.getIndicatorId());
            updateMessage.put("provider", instance.getProvider());
            updateMessage.put("symbol", instance.getSymbol());
            updateMessage.put("interval", instance.getInterval());
            updateMessage.put("timestamp", result.getTimestamp());
            updateMessage.put("values", result.getValues());
            
            if (!result.getAdditionalData().isEmpty()) {
                updateMessage.put("additionalData", result.getAdditionalData());
            }
            
            // Add candle data
            Map<String, Object> candleData = new HashMap<>();
            candleData.put("openTime", candle.getOpenTime());
            candleData.put("closeTime", candle.getCloseTime());
            candleData.put("open", candle.getOpen());
            candleData.put("high", candle.getHigh());
            candleData.put("low", candle.getLow());
            candleData.put("close", candle.getClose());
            candleData.put("volume", candle.getVolume());
            updateMessage.put("candle", candleData);
            
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(updateMessage));
            
            // Send to subscribed sessions
            int sentCount = 0;
            for (Map.Entry<String, WebSocketSession> sessionEntry : sessions.entrySet()) {
                String sessionId = sessionEntry.getKey();
                WebSocketSession session = sessionEntry.getValue();
                
                try {
                    if (session.isOpen() && shouldSendToSession(sessionId, instanceKey, contextKey)) {
                        session.sendMessage(message);
                        sentCount++;
                    }
                } catch (IOException e) {
                    System.err.println("❌ Failed to send indicator update to session " + 
                                     sessionId + ": " + e.getMessage());
                }
            }
            
            if (sentCount > 0) {
                System.out.println("📤 Broadcast indicator update (" + instance.getIndicatorId() + 
                                 ") to " + sentCount + " session(s)");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast indicator update: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if indicator update should be sent to a session
     */
    private boolean shouldSendToSession(String sessionId, String instanceKey, String contextKey) {
        // Check direct instance subscriptions
        Set<String> instanceSubscriptions = sessionSubscriptions.get(sessionId);
        if (instanceSubscriptions != null && instanceSubscriptions.contains(instanceKey)) {
            return true;
        }
        
        // Check context subscriptions
        Set<String> contextSubscriptions = sessionContextSubscriptions.get(sessionId);
        if (contextSubscriptions != null && contextSubscriptions.contains(contextKey)) {
            return true;
        }
        
        return false;
    }
    
    // ============================================================
    // Helper Methods
    // ============================================================
    
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> error = Map.of(
                "type", "error",
                "error", errorMessage
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            System.err.println("Failed to send error message: " + e.getMessage());
        }
    }
}

