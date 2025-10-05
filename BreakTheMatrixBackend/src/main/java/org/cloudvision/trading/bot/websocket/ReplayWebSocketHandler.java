package org.cloudvision.trading.bot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.bot.model.IndicatorResponse;
import org.cloudvision.trading.bot.replay.ReplayEvent;
import org.cloudvision.trading.bot.replay.ReplayService;
import org.cloudvision.trading.bot.replay.ReplaySession;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for streaming replay updates
 * Clients subscribe to replay sessions and receive real-time updates
 */
@Component
public class ReplayWebSocketHandler extends TextWebSocketHandler {
    
    private final ReplayService replayService;
    private final ObjectMapper objectMapper;
    
    // Active WebSocket sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Session subscriptions: wsSessionId -> replay sessionId
    private final Map<String, String> sessionSubscriptions = new ConcurrentHashMap<>();
    
    public ReplayWebSocketHandler(ReplayService replayService) {
        this.replayService = replayService;
        
        // Configure ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        System.out.println("✅ ReplayWebSocketHandler initialized");
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("📡 Replay WebSocket connected: " + session.getId());
        
        // Send welcome message
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connected",
            "message", "Replay stream connected",
            "actions", Arrays.asList("subscribe", "unsubscribe")
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String wsSessionId = session.getId();
        sessions.remove(wsSessionId);
        
        // Unsubscribe from replay session
        String replaySessionId = sessionSubscriptions.remove(wsSessionId);
        if (replaySessionId != null) {
            unregisterListener(wsSessionId, replaySessionId);
        }
        
        System.out.println("📡 Replay WebSocket disconnected: " + wsSessionId);
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
                case "unsubscribe" -> handleUnsubscribe(session);
                default -> sendError(session, "Unknown action: " + action);
            }
            
        } catch (Exception e) {
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }
    
    /**
     * Subscribe to a replay session
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> messageData) 
            throws IOException {
        String replaySessionId = (String) messageData.get("sessionId");
        
        if (replaySessionId == null || replaySessionId.isEmpty()) {
            sendError(session, "sessionId is required");
            return;
        }
        
        // Check if replay session exists
        if (!replayService.hasSession(replaySessionId)) {
            sendError(session, "Replay session not found: " + replaySessionId);
            return;
        }
        
        // Unsubscribe from previous session (if any)
        String previousSessionId = sessionSubscriptions.get(session.getId());
        if (previousSessionId != null) {
            unregisterListener(session.getId(), previousSessionId);
        }
        
        // Subscribe to new session
        sessionSubscriptions.put(session.getId(), replaySessionId);
        
        // Register event listener
        ReplaySession replaySession = replayService.getSession(replaySessionId);
        replaySession.addEventListener((rs, event) -> {
            broadcastReplayEvent(session.getId(), event);
        });
        
        // Send confirmation
        Map<String, Object> response = new HashMap<>();
        response.put("type", "subscribed");
        response.put("sessionId", replaySessionId);
        response.put("status", replaySession.getStatus());
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        
        System.out.println("✅ WebSocket session " + session.getId() + 
                         " subscribed to replay: " + replaySessionId);
    }
    
    /**
     * Unsubscribe from replay session
     */
    private void handleUnsubscribe(WebSocketSession session) throws IOException {
        String replaySessionId = sessionSubscriptions.remove(session.getId());
        
        if (replaySessionId != null) {
            unregisterListener(session.getId(), replaySessionId);
        }
        
        Map<String, Object> response = Map.of(
            "type", "unsubscribed",
            "message", "Unsubscribed from replay updates"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        
        System.out.println("✅ WebSocket session " + session.getId() + " unsubscribed from replay");
    }
    
    /**
     * Broadcast replay event to subscribed WebSocket session
     */
    private void broadcastReplayEvent(String wsSessionId, ReplayEvent event) {
        WebSocketSession session = sessions.get(wsSessionId);
        
        if (session == null || !session.isOpen()) {
            return;
        }
        
        try {
            // Build message
            Map<String, Object> message = new HashMap<>();
            message.put("type", "replayUpdate");
            message.put("sessionId", event.getSessionId());
            message.put("currentIndex", event.getCurrentIndex());
            message.put("totalCandles", event.getTotalCandles());
            message.put("progress", event.getProgress());
            message.put("state", event.getState());
            message.put("speed", event.getSpeed());
            
            // Add candle data
            if (event.getCandle() != null) {
                message.put("candle", serializeCandle(event.getCandle()));
            }
            
            // Add indicator results
            if (!event.getIndicatorResults().isEmpty()) {
                Map<String, Object> indicators = new HashMap<>();
                
                for (Map.Entry<String, IndicatorInstanceManager.IndicatorResult> entry : 
                     event.getIndicatorResults().entrySet()) {
                    String instanceKey = entry.getKey();
                    IndicatorInstanceManager.IndicatorResult result = entry.getValue();
                    
                    // Get instance info
                    IndicatorInstanceManager.IndicatorInstance instance = 
                        getInstanceFromKey(instanceKey);
                    
                    if (instance != null) {
                        IndicatorResponse indicatorResponse = IndicatorResponse.forWebSocketUpdate(
                            instance,
                            result,
                            event.getCandle()
                        );
                        
                        indicators.put(instanceKey, indicatorResponse);
                    }
                }
                
                message.put("indicators", indicators);
            }
            
            // Send message
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
            
        } catch (Exception e) {
            System.err.println("❌ Error broadcasting replay event: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Serialize candlestick data for WebSocket
     */
    private Map<String, Object> serializeCandle(CandlestickData candle) {
        Map<String, Object> candleData = new HashMap<>();
        candleData.put("openTime", candle.getOpenTime().toString());
        candleData.put("closeTime", candle.getCloseTime().toString());
        candleData.put("time", candle.getOpenTime().getEpochSecond());
        candleData.put("timestamp", candle.getOpenTime().getEpochSecond());
        candleData.put("timeMs", candle.getOpenTime().toEpochMilli());
        candleData.put("open", candle.getOpen().doubleValue());
        candleData.put("high", candle.getHigh().doubleValue());
        candleData.put("low", candle.getLow().doubleValue());
        candleData.put("close", candle.getClose().doubleValue());
        candleData.put("volume", candle.getVolume().doubleValue());
        candleData.put("interval", candle.getInterval());
        candleData.put("provider", candle.getProvider());
        candleData.put("symbol", candle.getSymbol());
        candleData.put("closed", candle.isClosed());
        return candleData;
    }
    
    /**
     * Get indicator instance from instance key
     * (This is a workaround - ideally IndicatorInstanceManager would expose this)
     */
    private IndicatorInstanceManager.IndicatorInstance getInstanceFromKey(String instanceKey) {
        try {
            // Try to get from replay session's indicator instances
            for (ReplaySession session : replayService.getAllSessions()) {
                if (session.getIndicatorInstanceKeys().contains(instanceKey)) {
                    // Instance exists, but we need IndicatorInstanceManager to expose getInstance
                    // For now, we'll return null and handle gracefully
                    return null;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error getting instance: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Unregister listener from replay session
     */
    private void unregisterListener(String wsSessionId, String replaySessionId) {
        // Note: ReplaySession doesn't currently support removing specific listeners
        // This is a limitation we can address later if needed
        System.out.println("ℹ️ Listener cleanup for " + wsSessionId + " on replay " + replaySessionId);
    }
    
    /**
     * Send error message to WebSocket client
     */
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

