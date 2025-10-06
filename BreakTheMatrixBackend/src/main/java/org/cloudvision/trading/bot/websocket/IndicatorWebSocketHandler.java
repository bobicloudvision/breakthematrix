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
    
    // ============================================================
    // DATA PROCESSING - ENTRY POINT FOR ALL TRADING DATA
    // ============================================================
    
    /**
     * Process incoming trading data and update indicators
     * 
     * This is the MAIN ENTRY POINT for all trading data from exchanges.
     * The TradingBot calls this method whenever new data arrives.
     * 
     * DATA FLOW:
     * Exchange -> TradingDataProvider -> TradingBot -> this.processData() -> Indicators
     * 
     * SUPPORTED DATA TYPES:
     * 1. Candlestick Data (KLINE) - Most common, used by most indicators
     * 2. Trade Data (TRADE/AGGREGATE_TRADE) - For order flow indicators
     * 3. Order Book Data (ORDER_BOOK) - For Bookmap-style indicators
     * 
     * WHAT HAPPENS:
     * - Checks data type using TradingData.hasCandlestickData(), hasTradeData(), etc.
     * - Routes to appropriate processing method
     * - Updates all indicators that need this data type
     * - Broadcasts results to WebSocket clients
     */
    private void processData(TradingData data) {
        // ============================================================
        // 1. PROCESS CANDLESTICK DATA (MOST COMMON)
        // ============================================================
        // Candlestick/OHLC data is used by most indicators (SMA, RSI, etc.)
        // Can be closed candle or real-time tick (forming candle)
        if (data.hasCandlestickData()) {
            CandlestickData candle = data.getCandlestickData();
            
            // Broadcast raw candle update to all sessions (for chart updates)
            // This allows charts to display live price movement
            broadcastCandleUpdate(candle);
            
            if (candle.isClosed()) {
                // CLOSED CANDLE - Full indicator calculation with state update
                // This is when the candle period completes (e.g., 1 minute passed for 1m candle)
                // All indicators recalculate and store the result in history
                processCandleClose(candle);
            } else {
                // REAL-TIME TICK - Preview of indicator values with current price
                // This shows what indicators would be if candle closed now
                // State is NOT updated, just a preview calculation
                processTick(candle.getProvider(), candle.getSymbol(), 
                           candle.getInterval(), candle.getClose());
            }
            return;
        }
        
        // ============================================================
        // 2. PROCESS TRADE DATA (FOR ORDER FLOW INDICATORS)
        // ============================================================
        // Individual or aggregate trades from the exchange
        // Used for: CVD, order flow imbalance, footprint candles, volume profile
        if (data.hasTradeData()) {
            org.cloudvision.trading.model.TradeData trade = data.getTradeData();
            
            // Process trade for indicators that need trade-level data
            // Examples: CVD indicator, order flow imbalance, volume profile
            processTrade(trade);
            
            // Also update tick-based indicators with current price
            // This gives real-time price updates for trailing stops, etc.
            processTick(data.getProvider(), data.getSymbol(), 
                       guessIntervalFromSubscriptions(data.getSymbol()), 
                       trade.getPrice());
            return;
        }
        
        // ============================================================
        // 3. PROCESS ORDER BOOK DATA (FOR BOOKMAP-STYLE INDICATORS)
        // ============================================================
        // Order book snapshots/updates from the exchange
        // Used for: Bookmap heatmap, bid/ask imbalance, liquidity detection
        if (data.hasOrderBookData()) {
            org.cloudvision.trading.model.OrderBookData orderBook = data.getOrderBookData();
            
            // Process order book for indicators that visualize market depth
            // Examples: Bookmap heatmap, liquidity walls, bid/ask ratio
            processOrderBook(orderBook);
            return;
        }
        
        // If we get here, the data type is not handled
        // This is normal - we only process data types that indicators need
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
    
    // ============================================================
    // NEW: TRADE DATA PROCESSING FOR ORDER FLOW INDICATORS
    // ============================================================
    
    /**
     * Process trade data for order flow indicators
     * 
     * WHAT THIS DOES:
     * 1. Receives individual or aggregate trade from exchange
     * 2. Finds all indicators that need trade data (declared via getRequiredDataTypes())
     * 3. Calls indicator.onTradeUpdate() for each matching indicator
     * 4. Broadcasts results to WebSocket clients ONLY if indicator returns data
     * 
     * TYPICAL INDICATORS THAT USE THIS:
     * - Cumulative Volume Delta (CVD)
     * - Order Flow Imbalance
     * - Volume Profile
     * - Footprint Charts
     * - Bookmap Heatmap
     * 
     * HOW TRADE DATA FLOWS:
     * Exchange (Binance, etc.) 
     *   -> TradingDataProvider 
     *   -> TradingBot 
     *   -> this.processTrade() 
     *   -> IndicatorInstanceManager.updateAllWithTrade()
     *   -> Indicator.onTradeUpdate()
     *   -> WebSocket broadcast (only if values not empty)
     * 
     * PERFORMANCE NOTE:
     * This method is called VERY frequently (1000+ times per second on active pairs)
     * Most indicators accumulate trades in their state and only output on candle close
     * We only broadcast if the indicator returns non-empty values or additional data
     * This prevents flooding WebSocket with unnecessary messages
     * 
     * @param trade Individual or aggregate trade data
     */
    private void processTrade(org.cloudvision.trading.model.TradeData trade) {
        // Update all indicators that need trade data for this symbol
        // The IndicatorInstanceManager filters to only indicators that declared
        // TRADE or AGGREGATE_TRADE in their getRequiredDataTypes()
        Map<String, IndicatorInstanceManager.IndicatorResult> results = 
            instanceManager.updateAllWithTrade(
                trade.getProvider(), 
                trade.getSymbol(), 
                trade
            );
        
        if (results.isEmpty()) {
            return; // No active indicators need trade data for this symbol
        }
        
        // Broadcast trade-based indicator updates to subscribed sessions
        // ONLY if the indicator returns non-empty values or additional data
        // Most indicators (like Bookmap) accumulate trades and only output on candle close
        for (Map.Entry<String, IndicatorInstanceManager.IndicatorResult> entry : results.entrySet()) {
            String instanceKey = entry.getKey();
            IndicatorInstanceManager.IndicatorResult result = entry.getValue();
            
            // Only broadcast if indicator has values OR additional data
            // If both are empty, the indicator is accumulating silently
            boolean hasValues = result.getValues() != null && !result.getValues().isEmpty();
            boolean hasAdditionalData = result.getAdditionalData() != null && !result.getAdditionalData().isEmpty();
            
            if (hasValues || hasAdditionalData) {
                broadcastTradeIndicatorUpdate(instanceKey, result, trade);
            }
            // Otherwise skip - indicator is accumulating silently
        }
    }
    
    // ============================================================
    // NEW: ORDER BOOK DATA PROCESSING FOR BOOKMAP-STYLE INDICATORS
    // ============================================================
    
    /**
     * Process order book data for Bookmap-style indicators
     * 
     * WHAT THIS DOES:
     * 1. Receives order book snapshot/update from exchange
     * 2. Finds all indicators that need order book data (declared via getRequiredDataTypes())
     * 3. Calls indicator.onOrderBookUpdate() for each matching indicator
     * 4. Broadcasts results to WebSocket clients ONLY if indicator returns data
     * 
     * TYPICAL INDICATORS THAT USE THIS:
     * - Bookmap Heatmap (volume at each price level)
     * - Bid/Ask Imbalance
     * - Liquidity Wall Detection
     * - Order Book Depth Analysis
     * 
     * HOW ORDER BOOK DATA FLOWS:
     * Exchange (Binance, etc.) 
     *   -> TradingDataProvider 
     *   -> TradingBot 
     *   -> this.processOrderBook() 
     *   -> IndicatorInstanceManager.updateAllWithOrderBook()
     *   -> Indicator.onOrderBookUpdate()
     *   -> WebSocket broadcast (only if values not empty)
     * 
     * PERFORMANCE NOTE:
     * Order book updates are EXTREMELY frequent (100+ per second)
     * Many indicators (like Bookmap) accumulate silently and only output on candle close
     * We only broadcast if the indicator returns non-empty values or additional data
     * 
     * WHAT ORDER BOOK CONTAINS:
     * - List of bid levels (price + quantity)
     * - List of ask levels (price + quantity)
     * - Best bid/ask prices
     * - Spread
     * - Total volume at each level
     * 
     * @param orderBook Order book snapshot with bid/ask levels
     */
    private void processOrderBook(org.cloudvision.trading.model.OrderBookData orderBook) {
        // Update all indicators that need order book data for this symbol
        // The IndicatorInstanceManager filters to only indicators that declared
        // ORDER_BOOK in their getRequiredDataTypes()
        Map<String, IndicatorInstanceManager.IndicatorResult> results = 
            instanceManager.updateAllWithOrderBook(
                orderBook.getProvider(), 
                orderBook.getSymbol(), 
                orderBook
            );
        
        if (results.isEmpty()) {
            return; // No active indicators need order book data for this symbol
        }
        
        // Broadcast order book indicator updates to subscribed sessions
        // ONLY if the indicator returns non-empty values or additional data
        // This allows indicators to accumulate silently and only broadcast on candle close
        for (Map.Entry<String, IndicatorInstanceManager.IndicatorResult> entry : results.entrySet()) {
            String instanceKey = entry.getKey();
            IndicatorInstanceManager.IndicatorResult result = entry.getValue();
            
            // Only broadcast if indicator has values OR additional data
            // If both are empty, the indicator is accumulating silently
            boolean hasValues = result.getValues() != null && !result.getValues().isEmpty();
            boolean hasAdditionalData = result.getAdditionalData() != null && !result.getAdditionalData().isEmpty();
            
            if (hasValues || hasAdditionalData) {
                broadcastOrderBookIndicatorUpdate(instanceKey, result, orderBook);
            }
            // Otherwise skip - indicator is accumulating silently
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
    // NEW: BROADCAST METHODS FOR TRADE AND ORDER BOOK INDICATORS
    // ============================================================
    
    /**
     * Broadcast trade-based indicator update to subscribed sessions
     * 
     * WHAT THIS DOES:
     * Sends indicator updates from trade data processing to WebSocket clients
     * Uses the STANDARD indicatorTick format for consistency with other indicators
     * 
     * MESSAGE FORMAT (Same as SMA, EMA, etc.):
     * {
     *   "type": "indicatorTick",
     *   "price": 4597.3,
     *   "data": { ... indicator response ... }
     * }
     * 
     * TYPICAL USE:
     * Order flow indicators like Bookmap, CVD, imbalance, volume profile
     * Most accumulate trades and only output meaningful data periodically
     * 
     * @param instanceKey Indicator instance key
     * @param result Indicator calculation result
     * @param trade The trade that triggered this update
     */
    private void broadcastTradeIndicatorUpdate(String instanceKey, 
                                              IndicatorInstanceManager.IndicatorResult result,
                                              org.cloudvision.trading.model.TradeData trade) {
        try {
            // Get the indicator instance for context information
            IndicatorInstanceManager.IndicatorInstance instance = 
                instanceManager.getInstance(instanceKey);
            
            if (instance == null) {
                return;
            }
            
            // Generate context key for routing
            String contextKey = String.format("%s:%s:%s", 
                trade.getProvider(), trade.getSymbol(), instance.getInterval());
            
            // Create unified response for WebSocket update
            IndicatorResponse indicatorResponse = IndicatorResponse.builder()
                .fromInstance(instance)
                .fromResult(result)
                .build();
            
            // Build update message wrapper - using STANDARD indicatorTick format
            // This matches the format used by other indicators (SMA, EMA, etc.)
            Map<String, Object> updateMessage = new HashMap<>();
            updateMessage.put("type", "indicatorTick");  // Standard format
            updateMessage.put("data", indicatorResponse);
            updateMessage.put("price", trade.getPrice());  // Current price for context
            
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
                    System.err.println("❌ Failed to send trade indicator update to session " + 
                                     sessionId + ": " + e.getMessage());
                }
            }
            
            // Log only occasionally to avoid spam (trade updates are very frequent)
            if (sentCount > 0 && Math.random() < 0.001) { // Log ~0.1% of updates
                System.out.println("📤 Trade indicator update (" + instance.getIndicatorId() + 
                                 ") sent to " + sentCount + " session(s)");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast trade indicator update: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast order book indicator update to subscribed sessions
     * 
     * WHAT THIS DOES:
     * Sends indicator updates from order book processing to WebSocket clients
     * Typically includes heatmap data for Bookmap-style visualization
     * 
     * MESSAGE FORMAT:
     * {
     *   "type": "indicatorOrderBook",
     *   "orderBook": { ... order book snapshot ... },
     *   "data": { 
     *     ... indicator response ...,
     *     "heatmap": [ ... price level data ... ],
     *     "levels": [ ... significant levels ... ]
     *   }
     * }
     * 
     * TYPICAL USE:
     * Bookmap heatmap, bid/ask imbalance, liquidity walls
     * Updates are very frequent (100+ per second)
     * Frontend should throttle rendering for performance
     * 
     * @param instanceKey Indicator instance key
     * @param result Indicator calculation result (includes heatmap data)
     * @param orderBook The order book that triggered this update
     */
    private void broadcastOrderBookIndicatorUpdate(String instanceKey, 
                                                   IndicatorInstanceManager.IndicatorResult result,
                                                   org.cloudvision.trading.model.OrderBookData orderBook) {
        try {
            // Get the indicator instance for context information
            IndicatorInstanceManager.IndicatorInstance instance = 
                instanceManager.getInstance(instanceKey);
            
            if (instance == null) {
                return;
            }
            
            // Generate context key for routing
            // Note: Order book doesn't have interval, so we use the instance's interval
            String contextKey = String.format("%s:%s:%s", 
                orderBook.getProvider(), orderBook.getSymbol(), instance.getInterval());
            
            // Create unified response for WebSocket update
            IndicatorResponse indicatorResponse = IndicatorResponse.builder()
                .fromInstance(instance)
                .fromResult(result)
                .build();
            
            // Build update message wrapper
            Map<String, Object> updateMessage = new HashMap<>();
            updateMessage.put("type", "indicatorOrderBook");
            updateMessage.put("data", indicatorResponse);
            
            // Include order book summary for context (don't send full book every time)
            Map<String, Object> orderBookInfo = new HashMap<>();
            orderBookInfo.put("bestBid", orderBook.getBestBid());
            orderBookInfo.put("bestAsk", orderBook.getBestAsk());
            orderBookInfo.put("spread", orderBook.getSpread());
            orderBookInfo.put("timestamp", orderBook.getTimestamp().toString());
            orderBookInfo.put("bidLevels", orderBook.getBids().size());
            orderBookInfo.put("askLevels", orderBook.getAsks().size());
            updateMessage.put("orderBookSummary", orderBookInfo);
            
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
                    System.err.println("❌ Failed to send order book indicator update to session " + 
                                     sessionId + ": " + e.getMessage());
                }
            }
            
            // Log only very occasionally to avoid spam (order book updates are extremely frequent)
            if (sentCount > 0 && Math.random() < 0.0001) { // Log ~0.01% of updates
                System.out.println("📤 Order book indicator update (" + instance.getIndicatorId() + 
                                 ") sent to " + sentCount + " session(s)");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast order book indicator update: " + e.getMessage());
        }
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

