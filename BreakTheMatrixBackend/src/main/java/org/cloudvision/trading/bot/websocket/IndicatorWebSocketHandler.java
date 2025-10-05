package org.cloudvision.trading.bot.websocket;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.bot.model.IndicatorResponse;
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
    
    /**
     * Custom BigDecimal serializer to limit precision and avoid excessive decimal places
     */
    private static class BigDecimalSerializer extends JsonSerializer<java.math.BigDecimal> {
        @Override
        public void serialize(java.math.BigDecimal value, JsonGenerator gen, SerializerProvider serializers) 
                throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                // Round to 8 decimal places for reasonable precision
                java.math.BigDecimal rounded = value.setScale(8, java.math.RoundingMode.HALF_UP);
                // Strip trailing zeros for cleaner output
                gen.writeNumber(rounded.stripTrailingZeros().toPlainString());
            }
        }
    }
    
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
        
        // Register custom BigDecimal serializer to avoid excessive precision
        SimpleModule bigDecimalModule = new SimpleModule();
        bigDecimalModule.addSerializer(java.math.BigDecimal.class, new BigDecimalSerializer());
        this.objectMapper.registerModule(bigDecimalModule);
        
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
        // Process candlestick data (closed candles and real-time ticks)
        if (data.hasCandlestickData()) {
            CandlestickData candle = data.getCandlestickData();
            
            // Broadcast raw candle update to all sessions (for chart updates)
            broadcastCandleUpdate(candle);
            
            if (candle.isClosed()) {
                // Closed candle - full indicator update
                processCandleClose(candle);
            } else {
                // Real-time tick - update indicators with current price
                processTick(candle.getProvider(), candle.getSymbol(), 
                           candle.getInterval(), candle.getClose());
            }
            return;
        }
        
        // Process trade data for real-time price updates
        if (data.hasTradeData()) {
            org.cloudvision.trading.model.TradeData trade = data.getTradeData();
            processTick(data.getProvider(), data.getSymbol(), 
                       guessIntervalFromSubscriptions(data.getSymbol()), 
                       trade.getPrice());
            return;
        }
    }
    
    /**
     * Process closed candle - full indicator calculation and store in history
     */
    private void processCandleClose(CandlestickData candle) {
        // Update all indicators for this context with closed candle
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
     * Process real-time tick - quick indicator update (not stored in history)
     */
    private void processTick(String provider, String symbol, String interval, java.math.BigDecimal price) {
        // Update all indicators for this context with current price
        Map<String, IndicatorInstanceManager.IndicatorResult> results = 
            instanceManager.updateAllForTick(provider, symbol, interval, price);
        
        if (results.isEmpty()) {
            return; // No active indicators for this context
        }
        
        // Broadcast tick updates to subscribed sessions
        for (Map.Entry<String, IndicatorInstanceManager.IndicatorResult> entry : results.entrySet()) {
            String instanceKey = entry.getKey();
            IndicatorInstanceManager.IndicatorResult result = entry.getValue();
            
            // Only broadcast if the indicator has tick values (some indicators don't support ticks)
            if (result.getValues() != null && !result.getValues().isEmpty()) {
                broadcastTickUpdate(instanceKey, result, provider, symbol, interval, price);
            }
        }
    }
    
    /**
     * Guess interval from active subscriptions for trade data
     * (Trade data doesn't include interval, so we need to infer it)
     */
    private String guessIntervalFromSubscriptions(String symbol) {
        // Look through active instances to find matching symbol
        for (IndicatorInstanceManager.IndicatorInstance instance : 
             instanceManager.getAllInstances()) {
            if (instance.getSymbol().equals(symbol)) {
                return instance.getInterval();
            }
        }
        return "1m"; // Default fallback
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
            
            // Create unified response for WebSocket update
            IndicatorResponse indicatorResponse = IndicatorResponse.forWebSocketUpdate(
                instance, 
                result, 
                candle
            );
            
            // Build update message wrapper
            Map<String, Object> updateMessage = new HashMap<>();
            updateMessage.put("type", "indicatorUpdate");
            updateMessage.put("data", indicatorResponse);
            
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
     * Broadcast raw candlestick update to all sessions subscribed to this context
     */
    private void broadcastCandleUpdate(CandlestickData candle) {
        try {
            String contextKey = String.format("%s:%s:%s", 
                candle.getProvider(), candle.getSymbol(), candle.getInterval());
            
            // Build candle update message
            Map<String, Object> updateMessage = new HashMap<>();
            updateMessage.put("type", "candleUpdate");
            updateMessage.put("provider", candle.getProvider());
            updateMessage.put("symbol", candle.getSymbol());
            updateMessage.put("interval", candle.getInterval());
            
            // Add candle data with proper format for frontend
            Map<String, Object> candleData = new HashMap<>();
            candleData.put("openTime", candle.getOpenTime().toString());
            candleData.put("closeTime", candle.getCloseTime().toString());
            candleData.put("time", candle.getOpenTime().getEpochSecond()); // Unix seconds (TradingView)
            candleData.put("timestamp", candle.getOpenTime().getEpochSecond()); // Unix seconds (alias)
            candleData.put("timeMs", candle.getOpenTime().toEpochMilli()); // Unix milliseconds (Chart.js)
            candleData.put("open", candle.getOpen().doubleValue());
            candleData.put("high", candle.getHigh().doubleValue());
            candleData.put("low", candle.getLow().doubleValue());
            candleData.put("close", candle.getClose().doubleValue());
            candleData.put("volume", candle.getVolume().doubleValue());
            candleData.put("closed", candle.isClosed());
            updateMessage.put("candle", candleData);
            
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(updateMessage));
            
            // Send to all sessions subscribed to this context
            int sentCount = 0;
            for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
                String sessionId = entry.getKey();
                WebSocketSession session = entry.getValue();
                
                try {
                    if (session.isOpen() && shouldSendToContext(sessionId, contextKey)) {
                        session.sendMessage(message);
                        sentCount++;
                    }
                } catch (IOException e) {
                    System.err.println("❌ Failed to send candle update to session " + 
                                     sessionId + ": " + e.getMessage());
                }
            }
            
            // Log only when sessions are actually receiving updates
            if (sentCount > 0 && Math.random() < 0.01) {
                System.out.println("📤 Candle update sent to " + sentCount + " session(s)");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast candle update: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if a session should receive updates for this context
     */
    private boolean shouldSendToContext(String sessionId, String contextKey) {
        Set<String> sessionContexts = sessionContextSubscriptions.get(sessionId);
        return sessionContexts != null && sessionContexts.contains(contextKey);
    }
    
    /**
     * Broadcast real-time tick update to subscribed sessions
     */
    private void broadcastTickUpdate(String instanceKey, 
                                     IndicatorInstanceManager.IndicatorResult result,
                                     String provider, String symbol, String interval,
                                     java.math.BigDecimal price) {
        try {
            IndicatorInstanceManager.IndicatorInstance instance = 
                instanceManager.getInstance(instanceKey);
            
            if (instance == null) {
                return;
            }
            
            String contextKey = String.format("%s:%s:%s", provider, symbol, interval);
            
            // Create unified response for tick update
            IndicatorResponse indicatorResponse = IndicatorResponse.builder()
                .fromInstance(instance)
                .fromResult(result)
                .build();
            
            // Build tick update message wrapper
            Map<String, Object> updateMessage = new HashMap<>();
            updateMessage.put("type", "indicatorTick");
            updateMessage.put("price", price);
            updateMessage.put("data", indicatorResponse);
            
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
                    System.err.println("❌ Failed to send tick update to session " + 
                                     sessionId + ": " + e.getMessage());
                }
            }
            
            // Don't log every tick to avoid spam
            // System.out.println("📡 Sent tick update for " + instanceKey + " to " + sentCount + " sessions");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast tick update: " + e.getMessage());
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

