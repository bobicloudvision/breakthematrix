package org.cloudvision.trading.bot.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.cloudvision.trading.bot.account.AccountManager;
import org.cloudvision.trading.bot.account.Position;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for real-time position updates
 * Broadcasts position data including unrealized P&L as prices change
 * 
 * Supported Actions:
 * - subscribe: Subscribe to position updates (accountId is OPTIONAL - omit for all accounts)
 * - getPositions: Get current positions (accountId is OPTIONAL - omit for all accounts)
 * - getAccounts: Get all trading accounts
 * - unsubscribe: Unsubscribe from updates
 * 
 * Examples:
 * {"action": "subscribe"}  // Subscribe to all accounts
 * {"action": "subscribe", "accountId": "paper-main"}  // Subscribe to specific account
 * {"action": "getPositions"}  // Get all positions
 * {"action": "getPositions", "accountId": "paper-main"}  // Get positions for specific account
 */
@Component
public class PositionsWebSocketHandler extends TextWebSocketHandler {
    
    private final AccountManager accountManager;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionFilters = new ConcurrentHashMap<>(); // sessionId -> accountId filter
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public PositionsWebSocketHandler(AccountManager accountManager) {
        this.accountManager = accountManager;
        
        // Configure ObjectMapper for Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Broadcast position updates every 1 second
        scheduler.scheduleAtFixedRate(this::broadcastPositionUpdates, 1, 1, TimeUnit.SECONDS);
        
        System.out.println("✅ PositionsWebSocketHandler initialized with auto-broadcast");
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("📡 Positions WebSocket connected: " + session.getId());
        
        // Send welcome message with available accounts
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (TradingAccount account : accountManager.getAllAccounts()) {
            accounts.add(Map.of(
                "accountId", account.getAccountId(),
                "accountName", account.getAccountName(),
                "accountType", account.getAccountType().toString(),
                "balance", account.getBalance(),
                "openPositions", account.getOpenPositions().size()
            ));
        }
        
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connected",
            "message", "Positions stream connected",
            "accounts", accounts
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcomeMessage)));
        
        // Send initial position data
        sendPositionUpdate(session, null);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("📥 Received message from " + session.getId() + ": " + payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String action = (String) messageData.get("action");
            
            switch (action) {
                case "subscribe":
                    // accountId is optional - null means all accounts
                    String accountId = messageData.containsKey("accountId") 
                        ? (String) messageData.get("accountId") 
                        : null;
                    handleSubscribe(session, accountId);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session);
                    break;
                case "getPositions":
                    // accountId is optional - null means all accounts
                    String getAccountId = messageData.containsKey("accountId") 
                        ? (String) messageData.get("accountId") 
                        : null;
                    sendPositionUpdate(session, getAccountId);
                    break;
                case "getAccounts":
                    handleGetAccounts(session);
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
        System.out.println("📡 Positions WebSocket disconnected: " + session.getId());
    }
    
    private void handleSubscribe(WebSocketSession session, String accountId) throws IOException {
        if (accountId != null && !accountId.isEmpty()) {
            sessionFilters.put(session.getId(), accountId);
            System.out.println("📌 Session " + session.getId() + " subscribed to account: " + accountId);
        } else {
            sessionFilters.remove(session.getId());
            System.out.println("📌 Session " + session.getId() + " subscribed to all accounts");
        }
        
        Map<String, Object> response = Map.of(
            "type", "subscribed",
            "accountId", accountId != null ? accountId : "all",
            "message", "Subscribed to position updates"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        
        // Send current positions immediately
        sendPositionUpdate(session, accountId);
    }
    
    private void handleUnsubscribe(WebSocketSession session) throws IOException {
        sessionFilters.remove(session.getId());
        
        Map<String, Object> response = Map.of(
            "type", "unsubscribed",
            "message", "Unsubscribed from position updates"
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    private void handleGetAccounts(WebSocketSession session) throws IOException {
        List<Map<String, Object>> accounts = new ArrayList<>();
        
        for (TradingAccount account : accountManager.getAllAccounts()) {
            Map<String, Object> accountData = new HashMap<>();
            accountData.put("accountId", account.getAccountId());
            accountData.put("accountName", account.getAccountName());
            accountData.put("accountType", account.getAccountType().toString());
            accountData.put("balance", account.getBalance());
            accountData.put("availableBalance", account.getAvailableBalance());
            accountData.put("openPositions", account.getOpenPositions().size());
            accountData.put("totalPnL", account.getTotalPnL());
            accountData.put("enabled", account.isEnabled());
            accounts.add(accountData);
        }
        
        Map<String, Object> response = Map.of(
            "type", "accounts",
            "data", accounts
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    /**
     * Broadcast position updates to all connected clients
     */
    private void broadcastPositionUpdates() {
        if (sessions.isEmpty()) {
            return;
        }
        
        sessions.forEach((sessionId, session) -> {
            try {
                if (session.isOpen()) {
                    String accountFilter = sessionFilters.get(sessionId);
                    sendPositionUpdate(session, accountFilter);
                }
            } catch (Exception e) {
                System.err.println("❌ Error broadcasting positions to session " + sessionId + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Send position update to a specific session
     */
    private void sendPositionUpdate(WebSocketSession session, String accountIdFilter) throws IOException {
        List<Map<String, Object>> positionDataList = new ArrayList<>();
        
        for (TradingAccount account : accountManager.getAllAccounts()) {
            // Apply account filter if specified
            if (accountIdFilter != null && !accountIdFilter.isEmpty() && 
                !account.getAccountId().equals(accountIdFilter)) {
                continue;
            }
            
            for (Position position : account.getPositionManager().getOpenPositions()) {
                Map<String, Object> positionData = new HashMap<>();
                positionData.put("positionId", position.getPositionId());
                positionData.put("accountId", account.getAccountId());
                positionData.put("accountName", account.getAccountName());
                positionData.put("symbol", position.getSymbol());
                positionData.put("side", position.getSide().toString());
                positionData.put("entryPrice", position.getEntryPrice());
                positionData.put("quantity", position.getQuantity());
                positionData.put("entryTime", position.getEntryTime());
                positionData.put("entryValue", position.getEntryValue());
                positionData.put("unrealizedPnL", position.getUnrealizedPnL());
                positionData.put("realizedPnL", position.getRealizedPnL());
                positionData.put("totalPnL", position.getTotalPnL());
                positionData.put("pnLPercentage", position.getPnLPercentage());
                positionData.put("stopLoss", position.getStopLoss());
                positionData.put("takeProfit", position.getTakeProfit());
                positionData.put("strategyId", position.getStrategyId());
                positionData.put("isOpen", position.isOpen());
                positionData.put("durationSeconds", position.getDuration().getSeconds());
                
                positionDataList.add(positionData);
            }
        }
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "positionsUpdate");
        message.put("timestamp", System.currentTimeMillis());
        message.put("positions", positionDataList);
        message.put("count", positionDataList.size());
        
        // Add summary statistics
        BigDecimal totalUnrealizedPnL = positionDataList.stream()
            .map(p -> (BigDecimal) p.get("unrealizedPnL"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        message.put("summary", Map.of(
            "totalPositions", positionDataList.size(),
            "totalUnrealizedPnL", totalUnrealizedPnL
        ));
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
    
    /**
     * Manually trigger a broadcast (can be called from outside)
     */
    public void triggerBroadcast() {
        broadcastPositionUpdates();
    }
}

