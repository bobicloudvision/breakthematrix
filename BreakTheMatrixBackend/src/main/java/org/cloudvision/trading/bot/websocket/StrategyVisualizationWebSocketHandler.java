package org.cloudvision.trading.bot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StrategyVisualizationWebSocketHandler extends TextWebSocketHandler {
    
    private final VisualizationManager visualizationManager;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionFilters = new ConcurrentHashMap<>(); // sessionId -> strategyId filter

    public StrategyVisualizationWebSocketHandler(VisualizationManager visualizationManager) {
        this.visualizationManager = visualizationManager;
        this.objectMapper = new ObjectMapper();
        
        // Set up global data handler to broadcast to all connected clients
        this.visualizationManager.registerGlobalDataHandler(this::broadcastVisualizationData);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("Strategy Visualization WebSocket connected: " + session.getId());
        
        // Send welcome message with available strategies
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connected",
            "message", "Strategy visualization stream connected",
            "availableStrategies", visualizationManager.getAvailableStrategies()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received message from " + session.getId() + ": " + payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String action = (String) messageData.get("action");
            
            switch (action) {
                case "subscribe":
                    String strategyId = (String) messageData.get("strategyId");
                    handleSubscribe(session, strategyId);
                    break;
                case "getHistoricalData":
                    String historyStrategyId = (String) messageData.get("strategyId");
                    String symbol = (String) messageData.get("symbol");
                    Integer limit = (Integer) messageData.get("limit");
                    handleGetHistoricalData(session, historyStrategyId, symbol, limit);
                    break;
                case "getStrategies":
                    handleGetStrategies(session);
                    break;
                case "getStrategySymbols":
                    String symbolsStrategyId = (String) messageData.get("strategyId");
                    handleGetStrategySymbols(session, symbolsStrategyId);
                    break;
                default:
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "error", "message", "Unknown action: " + action)
                    )));
            }
        } catch (Exception e) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", "error", "message", "Invalid message format: " + e.getMessage())
            )));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        sessionFilters.remove(session.getId());
        System.out.println("Strategy Visualization WebSocket disconnected: " + session.getId());
    }

    private void handleSubscribe(WebSocketSession session, String strategyId) throws IOException {
        sessionFilters.put(session.getId(), strategyId);
        
        Map<String, Object> response = Map.of(
            "type", "subscribed",
            "strategyId", strategyId,
            "symbols", visualizationManager.getStrategySymbols(strategyId)
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleGetHistoricalData(WebSocketSession session, String strategyId, String symbol, Integer limit) throws IOException {
        var data = visualizationManager.getVisualizationData(strategyId, symbol);
        
        // Limit data if requested
        if (limit != null && limit > 0 && data.size() > limit) {
            data = data.subList(data.size() - limit, data.size());
        }
        
        Map<String, Object> response = Map.of(
            "type", "historicalData",
            "strategyId", strategyId,
            "symbol", symbol,
            "data", data
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleGetStrategies(WebSocketSession session) throws IOException {
        Map<String, Object> response = Map.of(
            "type", "strategies",
            "data", visualizationManager.getAvailableStrategies()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleGetStrategySymbols(WebSocketSession session, String strategyId) throws IOException {
        Map<String, Object> response = Map.of(
            "type", "strategySymbols",
            "strategyId", strategyId,
            "symbols", visualizationManager.getStrategySymbols(strategyId)
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void broadcastVisualizationData(StrategyVisualizationData data) {
        try {
            Map<String, Object> messageData = Map.of(
                "type", "visualizationData",
                "strategyId", data.getStrategyId(),
                "symbol", data.getSymbol(),
                "timestamp", data.getTimestamp(),
                "price", data.getPrice(),
                "indicators", data.getIndicators(),
                "signals", data.getSignals(),
                "performance", data.getPerformance(),
                "action", data.getAction()
            );
            
            String jsonData = objectMapper.writeValueAsString(messageData);
            TextMessage message = new TextMessage(jsonData);
            
            // Broadcast to filtered sessions
            sessions.entrySet().forEach(entry -> {
                String sessionId = entry.getKey();
                WebSocketSession session = entry.getValue();
                String filter = sessionFilters.get(sessionId);
                
                try {
                    if (session.isOpen() && (filter == null || filter.equals(data.getStrategyId()))) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to send visualization data to session " + sessionId + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to broadcast visualization data: " + e.getMessage());
        }
    }
}
