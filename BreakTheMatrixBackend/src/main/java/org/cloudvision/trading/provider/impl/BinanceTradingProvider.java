package org.cloudvision.trading.provider.impl;

import org.cloudvision.trading.model.*;
import org.cloudvision.trading.model.OrderBookData.OrderBookLevel;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Component
public class BinanceTradingProvider implements TradingDataProvider {
    // Use combined streams endpoint for dynamic subscriptions
    private static final String BINANCE_WS_URL = "wss://stream.binance.com/ws";
    private static final String BINANCE_REST_API_URL = "https://api.binance.com/api/v3";
    
    private Consumer<TradingData> dataHandler;
    private boolean connected = false;
    private final List<String> subscribedSymbols = new ArrayList<>();
    private final Map<String, List<TimeInterval>> subscribedKlines = new ConcurrentHashMap<>();
    private final Set<String> activeStreams = ConcurrentHashMap.newKeySet();
    
    // Order flow subscriptions
    private final Set<String> subscribedTrades = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedAggregateTrades = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> subscribedOrderBooks = new ConcurrentHashMap<>();
    private final Set<String> subscribedBookTickers = ConcurrentHashMap.newKeySet();
    
    // Number of historical candles to fetch on reconnect
    private static final int HISTORICAL_CANDLES_ON_RECONNECT = 500;
    
    private WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);
    private final HttpClient httpClient;
    
    // Flag to prevent manual disconnect from triggering reconnect
    private volatile boolean manualDisconnect = false;
    
    // Reconnection attempt tracking for exponential backoff
    private volatile int reconnectionAttempts = 0;
    private static final int MAX_RECONNECTION_DELAY_SECONDS = 60;
    
    public BinanceTradingProvider() {
        // Configure ObjectMapper for Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Configure HttpClient with timeout
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    }

    @Override
    public void connect() {
        if (connected) {
            System.out.println("🔗 Binance provider already connected");
            return;
        }
        
        try {
            System.out.println("🔗 Connecting to Binance Combined Streams WebSocket: " + BINANCE_WS_URL);
            
            URI serverUri = URI.create(BINANCE_WS_URL);
            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    reconnectionAttempts = 0; // Reset reconnection counter on successful connection
                    System.out.println("✅ Connected to Binance WebSocket successfully!");
                    System.out.println("🤝 Handshake status: " + handshake.getHttpStatus());
                    System.out.println("💡 You can now subscribe to any symbol/interval dynamically");
                }

                @Override
                public void onMessage(String message) {
                    try {
                        handleBinanceMessage(message);
                    } catch (Exception e) {
                        System.err.println("❌ Error processing message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    System.out.println("🔌 Binance WebSocket closed. Code: " + code + ", Reason: " + reason + ", Remote: " + remote);
                    
                    // Auto-reconnect for any abnormal closure (not 1000 = normal close)
                    // This includes code 1006 (abnormal closure, pong timeout, etc.)
                    if (!manualDisconnect && code != 1000) {
                        reconnectionAttempts++;
                        
                        // Exponential backoff: 5s, 10s, 20s, 40s, 60s (max)
                        int delaySeconds = Math.min(5 * (1 << (reconnectionAttempts - 1)), MAX_RECONNECTION_DELAY_SECONDS);
                        
                        System.out.println("🔄 Connection lost abnormally. Reconnection attempt #" + reconnectionAttempts + 
                                         " in " + delaySeconds + " seconds...");
                        executorService.schedule(() -> reconnect(), delaySeconds, java.util.concurrent.TimeUnit.SECONDS);
                    } else if (code == 1000) {
                        System.out.println("✅ WebSocket closed normally");
                        reconnectionAttempts = 0;
                    }
                }

                @Override
                public void onError(Exception e) {
                    System.err.println("❌ Binance WebSocket error: " + e.getMessage());
                    connected = false;
                }
            };
            
            // Connect with timeout
            System.out.println("⏱️ Connecting to Binance WebSocket...");
            boolean connectSuccess = webSocketClient.connectBlocking(10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!connectSuccess) {
                System.err.println("❌ Failed to connect to Binance WebSocket within 10 seconds");
                connected = false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error connecting to Binance WebSocket: " + e.getMessage());
            e.printStackTrace();
            connected = false;
            webSocketClient = null;
        }
    }
    
    private void reconnect() {
        System.out.println("🔄 Attempting to reconnect to Binance...");
        manualDisconnect = false; // Reset flag for reconnection
        connect();
        
        // Re-subscribe to all previously subscribed streams
        if (connected) {
            System.out.println("✅ Reconnected to Binance WebSocket");
            System.out.println("🔄 Re-subscribing to " + activeStreams.size() + " streams...");
            
            // Re-subscribe to all active streams
            List<String> streamsToResubscribe = new ArrayList<>(activeStreams);
            activeStreams.clear();
            
            for (String stream : streamsToResubscribe) {
                sendSubscriptionMessage(stream, true);
            }
            
            // Load historical data for all subscribed klines
            System.out.println("📊 Loading historical data on reconnect...");
            loadHistoricalDataOnReconnect();
        }
    }
    
    /**
     * Load historical candlestick data after reconnection
     * Simple approach: fetch last N candles for each subscribed symbol+interval
     */
    private void loadHistoricalDataOnReconnect() {
        System.out.println("🔄 Fetching " + HISTORICAL_CANDLES_ON_RECONNECT + " historical candles for each subscription...");
        
        int successCount = 0;
        int failureCount = 0;
        
        // For each subscribed symbol+interval, fetch historical data
        for (Map.Entry<String, List<TimeInterval>> entry : subscribedKlines.entrySet()) {
            String symbol = entry.getKey();
            
            for (TimeInterval interval : entry.getValue()) {
                if (loadHistoricalDataForSymbol(symbol, interval)) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
        }
        
        System.out.println("✅ Historical data loading completed: " + successCount + " succeeded, " + failureCount + " failed");
    }
    
    /**
     * Load historical data for a specific symbol and interval
     * @return true if successful, false otherwise
     */
    private boolean loadHistoricalDataForSymbol(String symbol, TimeInterval interval) {
        try {
            System.out.println("📊 Fetching historical data for " + symbol + " (" + interval.getValue() + ")");
            
            // Fetch historical data with retry on failure
            List<CandlestickData> historicalData = fetchHistoricalDataWithRetry(symbol, interval, HISTORICAL_CANDLES_ON_RECONNECT, 2);
            
            if (!historicalData.isEmpty()) {
                System.out.println("✅ Retrieved " + historicalData.size() + " historical candles for " + 
                                 symbol + " (" + interval.getValue() + ")");
                
                // Send historical data through the data handler
                for (CandlestickData candlestick : historicalData) {
                    TradingData tradingData = new TradingData(
                        symbol,
                        candlestick.getCloseTime(),
                        getProviderName(),
                        TradingDataType.KLINE,
                        candlestick
                    );
                    
                    if (dataHandler != null) {
                        dataHandler.accept(tradingData);
                    }
                }
                
                System.out.println("✅ Historical data loaded for " + symbol + " (" + interval.getValue() + ")");
                return true;
            } else {
                System.out.println("⚠️ No historical data retrieved for " + symbol + " (" + interval.getValue() + ")");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error loading historical data for " + symbol + " (" + interval.getValue() + "): " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Fetch historical data with retry logic
     */
    private List<CandlestickData> fetchHistoricalDataWithRetry(String symbol, TimeInterval interval, int limit, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                List<CandlestickData> data = getHistoricalKlines(symbol, interval, limit);
                if (!data.isEmpty()) {
                    return data;
                }
                
                if (attempt < maxRetries) {
                    System.out.println("⚠️ Empty result, retrying... (attempt " + attempt + "/" + maxRetries + ")");
                    Thread.sleep(2000); // Wait 2 seconds before retry
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    System.out.println("⚠️ Failed, retrying... (attempt " + attempt + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(2000); // Wait 2 seconds before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return new ArrayList<>();
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 Disconnecting from Binance WebSocket...");
        
        manualDisconnect = true; // Prevent auto-reconnect
        connected = false;
        reconnectionAttempts = 0;
        activeStreams.clear();
        subscribedSymbols.clear();
        subscribedKlines.clear();
        subscribedTrades.clear();
        subscribedAggregateTrades.clear();
        subscribedOrderBooks.clear();
        subscribedBookTickers.clear();
        
        if (webSocketClient != null && !webSocketClient.isClosed()) {
            webSocketClient.close();
        }
        
        System.out.println("🔌 Binance provider disconnected");
    }

    @Override
    public void subscribe(String symbol) {
        if (!connected) {
            System.err.println("❌ Cannot subscribe to " + symbol + " ticker: Not connected to Binance");
            return;
        }
        
        try {
            System.out.println("📈 Subscribing to " + symbol + " ticker stream...");
            
            String streamName = symbol.toLowerCase() + "@ticker";
            sendSubscriptionMessage(streamName, true);
            
            subscribedSymbols.add(symbol);
            activeStreams.add(streamName);
            
            System.out.println("✅ Successfully subscribed to " + symbol + " ticker");
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe to " + symbol + " ticker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void unsubscribe(String symbol) {
        try {
            String streamName = symbol.toLowerCase() + "@ticker";
            sendSubscriptionMessage(streamName, false);
            
            subscribedSymbols.remove(symbol);
            activeStreams.remove(streamName);
            
            System.out.println("📉 Unsubscribed from " + symbol + " ticker");
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe from " + symbol + " ticker: " + e.getMessage());
        }
    }

    @Override
    public void subscribeToKlines(String symbol, TimeInterval interval) {
        if (!connected) {
            System.err.println("❌ Cannot subscribe to " + symbol + " klines: Not connected to Binance");
            return;
        }
        
        try {
            System.out.println("🕯️ Subscribing to " + symbol + " klines (" + interval.getValue() + ")...");
            
            String streamName = symbol.toLowerCase() + "@kline_" + interval.getValue();
            sendSubscriptionMessage(streamName, true);
            
            subscribedKlines.computeIfAbsent(symbol, k -> new ArrayList<>()).add(interval);
            activeStreams.add(streamName);
            
            System.out.println("✅ Successfully subscribed to " + symbol + " klines (" + interval.getValue() + ")");
            
            // Load historical data immediately after subscribing
            System.out.println("📊 Loading historical data for " + symbol + " (" + interval.getValue() + ")...");
            loadHistoricalDataForSymbol(symbol, interval);
            
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe to " + symbol + " klines: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void unsubscribeFromKlines(String symbol, TimeInterval interval) {
        try {
            String streamName = symbol.toLowerCase() + "@kline_" + interval.getValue();
            sendSubscriptionMessage(streamName, false);
            
            List<TimeInterval> intervals = subscribedKlines.get(symbol);
            if (intervals != null) {
                intervals.remove(interval);
                if (intervals.isEmpty()) {
                    subscribedKlines.remove(symbol);
                }
            }
            activeStreams.remove(streamName);
            
            System.out.println("🕯️ Unsubscribed from " + symbol + " klines (" + interval.getValue() + ")");
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe from " + symbol + " klines: " + e.getMessage());
        }
    }

    @Override
    public void setDataHandler(Consumer<TradingData> handler) {
        this.dataHandler = handler;
    }
    
    /**
     * Send subscription message to Binance WebSocket
     */
    private void sendSubscriptionMessage(String streamName, boolean subscribe) {
        try {
            String method = subscribe ? "SUBSCRIBE" : "UNSUBSCRIBE";
            int id = requestId.getAndIncrement();
            
            String message = String.format(
                "{\"method\":\"%s\",\"params\":[\"%s\"],\"id\":%d}",
                method, streamName, id
            );
            
            System.out.println("📤 Sending to Binance: " + message);
            webSocketClient.send(message);
            
        } catch (Exception e) {
            System.err.println("❌ Failed to send subscription message: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Handle incoming message from Binance WebSocket
     */
    private void handleBinanceMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            
            // Handle subscription responses
            if (json.has("result") && json.get("result").isNull()) {
                int id = json.has("id") ? json.get("id").asInt() : -1;
                System.out.println("✅ Subscription response received (id: " + id + ")");
                return;
            }
            
            // Handle error responses
            if (json.has("error")) {
                System.err.println("❌ Binance error: " + json.get("error"));
                return;
            }
            
            // Handle kline stream data
            if (json.has("e") && "kline".equals(json.get("e").asText())) {
                handleKlineDataDirect(json);
            } 
            // Handle ticker stream data
            else if (json.has("e") && "24hrTicker".equals(json.get("e").asText())) {
                String streamName = json.has("stream") ? json.get("stream").asText() : "unknown";
                handleTickerData(streamName, json);
            } 
            // Handle trade stream data
            else if (json.has("e") && "trade".equals(json.get("e").asText())) {
                handleTradeDataDirect(json);
            }
            // Handle aggregate trade stream data
            else if (json.has("e") && "aggTrade".equals(json.get("e").asText())) {
                handleAggregateTradeDataDirect(json);
            }
            // Handle depth/order book update (differential depth stream)
            else if (json.has("e") && "depthUpdate".equals(json.get("e").asText())) {
                handleDepthUpdateDirect(json);
            }
            // Handle partial book depth (no "e" field, has "lastUpdateId")
            else if (json.has("lastUpdateId") && json.has("bids") && json.has("asks")) {
                handlePartialBookDepth(json);
            }
            // Handle book ticker
            else if (json.has("e") && "bookTicker".equals(json.get("e").asText())) {
                handleBookTickerDirect(json);
            }
            // Unknown message type
            else if (!json.has("result")) {
//                System.out.println("📈 Received other data: " + message.substring(0, Math.min(200, message.length())));
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing Binance message: " + e.getMessage());
            System.err.println("📜 Raw message: " + message.substring(0, Math.min(200, message.length())));
            e.printStackTrace();
        }
    }
    
    /**
     * Handle kline data directly from stream (not wrapped in stream/data)
     */
    private void handleKlineDataDirect(JsonNode json) {
        try {
            JsonNode kline = json.get("k");
            
            String symbol = kline.get("s").asText().toUpperCase();
            Instant openTime = Instant.ofEpochMilli(kline.get("t").asLong());
            Instant closeTime = Instant.ofEpochMilli(kline.get("T").asLong());
            BigDecimal open = new BigDecimal(kline.get("o").asText());
            BigDecimal high = new BigDecimal(kline.get("h").asText());
            BigDecimal low = new BigDecimal(kline.get("l").asText());
            BigDecimal close = new BigDecimal(kline.get("c").asText());
            BigDecimal volume = new BigDecimal(kline.get("v").asText());
            BigDecimal quoteVolume = new BigDecimal(kline.get("q").asText());
            int numberOfTrades = kline.get("n").asInt();
            String intervalStr = kline.get("i").asText();
            boolean isClosed = kline.get("x").asBoolean();
            
            CandlestickData candlestick = new CandlestickData(
                symbol,
                openTime,
                closeTime,
                open,
                high,
                low,
                close,
                volume,
                quoteVolume,
                numberOfTrades,
                intervalStr,
                getProviderName(),
                isClosed
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                closeTime,
                getProviderName(),
                TradingDataType.KLINE,
                candlestick
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }


        } catch (Exception e) {
            System.err.println("❌ Error parsing direct kline data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getSupportedSymbols() {
        return List.of("BTCUSDT", "ETHUSDT", "ADAUSDT", "DOTUSDT", "BNBUSDT", "SOLUSDT");
    }

    @Override
    public List<TimeInterval> getSupportedIntervals() {
        return List.of(
            TimeInterval.ONE_MINUTE,
            TimeInterval.THREE_MINUTES,
            TimeInterval.FIVE_MINUTES,
            TimeInterval.FIFTEEN_MINUTES,
            TimeInterval.THIRTY_MINUTES,
            TimeInterval.ONE_HOUR,
            TimeInterval.FOUR_HOURS,
            TimeInterval.ONE_DAY
        );
    }

    @Override
    public String getProviderName() {
        return "Binance";
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public List<CandlestickData> getHistoricalKlines(String symbol, TimeInterval interval, int limit) {
        try {
            System.out.println("📊 Fetching " + limit + " historical klines for " + symbol + " (" + interval.getValue() + ")");
            
            String url = String.format("%s/klines?symbol=%s&interval=%s&limit=%d",
                BINANCE_REST_API_URL,
                symbol.toUpperCase(),
                interval.getValue(),
                Math.min(limit, 1000) // Binance max is 1000
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15)) // Request timeout
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("❌ Failed to fetch historical data. Status: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return new ArrayList<>();
            }
            
            return parseKlineResponse(response.body(), symbol, interval.getValue());
            
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("❌ Timeout fetching historical klines for " + symbol + ": Request took longer than 15 seconds");
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("❌ Error fetching historical klines: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public List<CandlestickData> getHistoricalKlines(String symbol, TimeInterval interval, Instant startTime, Instant endTime) {
        try {
            System.out.println("📊 Fetching historical klines for " + symbol + " (" + interval.getValue() + ") from " + 
                             startTime + " to " + endTime);
            
            String url = String.format("%s/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=1000",
                BINANCE_REST_API_URL,
                symbol.toUpperCase(),
                interval.getValue(),
                startTime.toEpochMilli(),
                endTime.toEpochMilli()
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15)) // Request timeout
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("❌ Failed to fetch historical data. Status: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return new ArrayList<>();
            }
            
            return parseKlineResponse(response.body(), symbol, interval.getValue());
            
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("❌ Timeout fetching historical klines for " + symbol + ": Request took longer than 15 seconds");
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("❌ Error fetching historical klines: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Parse Binance kline response array
     * Format: [[openTime, open, high, low, close, volume, closeTime, quoteVolume, trades, ...], ...]
     */
    private List<CandlestickData> parseKlineResponse(String responseBody, String symbol, String interval) {
        List<CandlestickData> klines = new ArrayList<>();
        
        try {
            JsonNode array = objectMapper.readTree(responseBody);
            
            if (!array.isArray()) {
                System.err.println("❌ Expected array response from Binance klines API");
                return klines;
            }
            
            for (JsonNode klineArray : array) {
                try {
                    Instant openTime = Instant.ofEpochMilli(klineArray.get(0).asLong());
                    BigDecimal open = new BigDecimal(klineArray.get(1).asText());
                    BigDecimal high = new BigDecimal(klineArray.get(2).asText());
                    BigDecimal low = new BigDecimal(klineArray.get(3).asText());
                    BigDecimal close = new BigDecimal(klineArray.get(4).asText());
                    BigDecimal volume = new BigDecimal(klineArray.get(5).asText());
                    Instant closeTime = Instant.ofEpochMilli(klineArray.get(6).asLong());
                    BigDecimal quoteVolume = new BigDecimal(klineArray.get(7).asText());
                    int numberOfTrades = klineArray.get(8).asInt();
                    
                    CandlestickData candlestick = new CandlestickData(
                        symbol,
                        openTime,
                        closeTime,
                        open,
                        high,
                        low,
                        close,
                        volume,
                        quoteVolume,
                        numberOfTrades,
                        interval,
                        getProviderName(),
                        true // Historical data is always closed
                    );
                    
                    klines.add(candlestick);
                    
                } catch (Exception e) {
                    System.err.println("❌ Error parsing individual kline: " + e.getMessage());
                }
            }
            
            System.out.println("✅ Successfully parsed " + klines.size() + " historical klines for " + symbol);
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing kline response: " + e.getMessage());
            e.printStackTrace();
        }
        
        return klines;
    }

    /**
     * Handle ticker data from Binance WebSocket
     */
    private void handleTickerData(String streamName, JsonNode data) {
        try {
            String symbol = data.get("s").asText().toUpperCase();
            BigDecimal price = new BigDecimal(data.get("c").asText()); // Close price
            BigDecimal volume = new BigDecimal(data.get("v").asText()); // Volume
            
            TradingData tradingData = new TradingData(
                symbol,
                price,
                volume,
                Instant.now(),
                getProviderName(),
                TradingDataType.TICKER
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }
            
            System.out.println("📈 " + symbol + " ticker: $" + price + " (Vol: " + volume + ")");
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing ticker data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle kline data from Binance WebSocket
     */
    private void handleKlineData(String streamName, JsonNode data) {
        try {
            JsonNode kline = data.get("k");
            
            String symbol = kline.get("s").asText().toUpperCase();
            Instant openTime = Instant.ofEpochMilli(kline.get("t").asLong());
            Instant closeTime = Instant.ofEpochMilli(kline.get("T").asLong());
            BigDecimal open = new BigDecimal(kline.get("o").asText());
            BigDecimal high = new BigDecimal(kline.get("h").asText());
            BigDecimal low = new BigDecimal(kline.get("l").asText());
            BigDecimal close = new BigDecimal(kline.get("c").asText());
            BigDecimal volume = new BigDecimal(kline.get("v").asText());
            BigDecimal quoteVolume = new BigDecimal(kline.get("q").asText());
            int numberOfTrades = kline.get("n").asInt();
            String intervalStr = kline.get("i").asText();
            boolean isClosed = kline.get("x").asBoolean();
            
            CandlestickData candlestick = new CandlestickData(
                symbol,
                openTime,
                closeTime,
                open,
                high,
                low,
                close,
                volume,
                quoteVolume,
                numberOfTrades,
                intervalStr,
                getProviderName(),
                isClosed
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                closeTime,
                getProviderName(),
                TradingDataType.KLINE,
                candlestick
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }
            
//            System.out.println("🕯️ " + symbol + " (" + intervalStr + ") OHLC: " +
//                             open + "/" + high + "/" + low + "/" + close + " [" + (isClosed ? "CLOSED" : "LIVE") + "]");
//
        } catch (Exception e) {
            System.err.println("❌ Error parsing kline data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ========== ORDER FLOW SUBSCRIPTIONS ==========
    
    @Override
    public void subscribeToTrades(String symbol) {
        if (!connected) {
            System.err.println("❌ Cannot subscribe to " + symbol + " trades: Not connected to Binance");
            return;
        }
        
        try {
            System.out.println("💹 Subscribing to " + symbol + " trade stream...");
            
            String streamName = symbol.toLowerCase() + "@trade";
            sendSubscriptionMessage(streamName, true);
            
            subscribedTrades.add(symbol);
            activeStreams.add(streamName);
            
            System.out.println("✅ Successfully subscribed to " + symbol + " trades");
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe to " + symbol + " trades: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void unsubscribeFromTrades(String symbol) {
        try {
            String streamName = symbol.toLowerCase() + "@trade";
            sendSubscriptionMessage(streamName, false);
            
            subscribedTrades.remove(symbol);
            activeStreams.remove(streamName);
            
            System.out.println("💹 Unsubscribed from " + symbol + " trades");
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe from " + symbol + " trades: " + e.getMessage());
        }
    }
    
    @Override
    public void subscribeToAggregateTrades(String symbol) {
        if (!connected) {
            System.err.println("❌ Cannot subscribe to " + symbol + " aggregate trades: Not connected to Binance");
            return;
        }
        
        try {
            System.out.println("📊 Subscribing to " + symbol + " aggregate trade stream...");
            
            String streamName = symbol.toLowerCase() + "@aggTrade";
            sendSubscriptionMessage(streamName, true);
            
            subscribedAggregateTrades.add(symbol);
            activeStreams.add(streamName);
            
            System.out.println("✅ Successfully subscribed to " + symbol + " aggregate trades");
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe to " + symbol + " aggregate trades: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void unsubscribeFromAggregateTrades(String symbol) {
        try {
            String streamName = symbol.toLowerCase() + "@aggTrade";
            sendSubscriptionMessage(streamName, false);
            
            subscribedAggregateTrades.remove(symbol);
            activeStreams.remove(streamName);
            
            System.out.println("📊 Unsubscribed from " + symbol + " aggregate trades");
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe from " + symbol + " aggregate trades: " + e.getMessage());
        }
    }
    
    @Override
    public void subscribeToOrderBook(String symbol, int depth) {
        if (!connected) {
            System.err.println("❌ Cannot subscribe to " + symbol + " order book: Not connected to Binance");
            return;
        }
        
        try {
            System.out.println("📚 Subscribing to " + symbol + " order book (depth: " + depth + ")...");
            
            // Use differential depth stream (@depth) which includes symbol in messages
            // Note: This sends updates, not full snapshots like @depth5/10/20
            // The depth parameter is noted but we use the differential stream for better integration
            String streamName = symbol.toLowerCase() + "@depth";
            sendSubscriptionMessage(streamName, true);
            
            subscribedOrderBooks.put(symbol, depth);
            activeStreams.add(streamName);
            
            System.out.println("✅ Successfully subscribed to " + symbol + " order book (differential depth stream)");
            System.out.println("💡 Tip: Differential depth stream sends updates. First message may have many levels.");
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe to " + symbol + " order book: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void unsubscribeFromOrderBook(String symbol) {
        try {
            Integer depth = subscribedOrderBooks.get(symbol);
            if (depth != null) {
                String streamName = symbol.toLowerCase() + "@depth";
                sendSubscriptionMessage(streamName, false);
                
                subscribedOrderBooks.remove(symbol);
                activeStreams.remove(streamName);
                
                System.out.println("📚 Unsubscribed from " + symbol + " order book");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe from " + symbol + " order book: " + e.getMessage());
        }
    }
    
    @Override
    public void subscribeToBookTicker(String symbol) {
        if (!connected) {
            System.err.println("❌ Cannot subscribe to " + symbol + " book ticker: Not connected to Binance");
            return;
        }
        
        try {
            System.out.println("📖 Subscribing to " + symbol + " book ticker stream...");
            
            String streamName = symbol.toLowerCase() + "@bookTicker";
            sendSubscriptionMessage(streamName, true);
            
            subscribedBookTickers.add(symbol);
            activeStreams.add(streamName);
            
            System.out.println("✅ Successfully subscribed to " + symbol + " book ticker");
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe to " + symbol + " book ticker: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void unsubscribeFromBookTicker(String symbol) {
        try {
            String streamName = symbol.toLowerCase() + "@bookTicker";
            sendSubscriptionMessage(streamName, false);
            
            subscribedBookTickers.remove(symbol);
            activeStreams.remove(streamName);
            
            System.out.println("📖 Unsubscribed from " + symbol + " book ticker");
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe from " + symbol + " book ticker: " + e.getMessage());
        }
    }
    
    // ========== ORDER FLOW HANDLERS ==========
    
    /**
     * Handle individual trade data from Binance WebSocket
     */
    private void handleTradeDataDirect(JsonNode json) {
        try {
            String symbol = json.get("s").asText().toUpperCase();
            long tradeId = json.get("t").asLong();
            BigDecimal price = new BigDecimal(json.get("p").asText());
            BigDecimal quantity = new BigDecimal(json.get("q").asText());
            Instant timestamp = Instant.ofEpochMilli(json.get("T").asLong());
            boolean isBuyerMaker = json.get("m").asBoolean();
            
            // Calculate quote quantity
            BigDecimal quoteQuantity = price.multiply(quantity);
            
            TradeData tradeData = new TradeData(
                tradeId,
                symbol,
                price,
                quantity,
                quoteQuantity,
                timestamp,
                isBuyerMaker,
                getProviderName()
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                timestamp,
                getProviderName(),
                TradingDataType.TRADE,
                tradeData
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }
            
            String side = isBuyerMaker ? "SELL" : "BUY";
            System.out.println("💹 " + symbol + " TRADE: " + side + " " + quantity + " @ " + price);
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing trade data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle aggregate trade data from Binance WebSocket
     */
    private void handleAggregateTradeDataDirect(JsonNode json) {
        try {
            String symbol = json.get("s").asText().toUpperCase();
            long aggTradeId = json.get("a").asLong();
            BigDecimal price = new BigDecimal(json.get("p").asText());
            BigDecimal quantity = new BigDecimal(json.get("q").asText());
            Instant timestamp = Instant.ofEpochMilli(json.get("T").asLong());
            boolean isBuyerMaker = json.get("m").asBoolean();
            long firstTradeId = json.get("f").asLong();
            long lastTradeId = json.get("l").asLong();
            
            // Calculate quote quantity
            BigDecimal quoteQuantity = price.multiply(quantity);
            
            TradeData tradeData = new TradeData(
                aggTradeId,
                symbol,
                price,
                quantity,
                quoteQuantity,
                timestamp,
                isBuyerMaker,
                getProviderName(),
                firstTradeId,
                lastTradeId
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                timestamp,
                getProviderName(),
                TradingDataType.AGGREGATE_TRADE,
                tradeData
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }
            
            String side = isBuyerMaker ? "SELL" : "BUY";
            int numTrades = (int)(lastTradeId - firstTradeId + 1);
//            System.out.println("📊 " + symbol + " AGG_TRADE: " + side + " " + quantity + " @ " + price +
//                             " (" + numTrades + " trades)");
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing aggregate trade data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle order book depth update from Binance WebSocket (differential depth stream)
     */
    private void handleDepthUpdateDirect(JsonNode json) {
        try {
            String symbol = json.get("s").asText().toUpperCase();
            long updateId = json.get("u").asLong();
            Instant timestamp = Instant.now(); // Binance doesn't provide timestamp in depth updates
            
            // Parse bids
            List<OrderBookLevel> bids = new ArrayList<>();
            JsonNode bidsArray = json.get("b");
            if (bidsArray != null && bidsArray.isArray()) {
                for (JsonNode bid : bidsArray) {
                    BigDecimal price = new BigDecimal(bid.get(0).asText());
                    BigDecimal quantity = new BigDecimal(bid.get(1).asText());
                    bids.add(new OrderBookLevel(price, quantity));
                }
            }
            
            // Parse asks
            List<OrderBookLevel> asks = new ArrayList<>();
            JsonNode asksArray = json.get("a");
            if (asksArray != null && asksArray.isArray()) {
                for (JsonNode ask : asksArray) {
                    BigDecimal price = new BigDecimal(ask.get(0).asText());
                    BigDecimal quantity = new BigDecimal(ask.get(1).asText());
                    asks.add(new OrderBookLevel(price, quantity));
                }
            }
            
            OrderBookData orderBookData = new OrderBookData(
                symbol,
                updateId,
                timestamp,
                bids,
                asks,
                getProviderName()
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                timestamp,
                getProviderName(),
                TradingDataType.ORDER_BOOK,
                orderBookData
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }

//            System.out.println("📚 " + symbol + " ORDER_BOOK (diff): " + bids.size() + " bids, " +
//                             asks.size() + " asks, spread=" + orderBookData.getSpread());

        } catch (Exception e) {
            System.err.println("❌ Error parsing depth update: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle partial book depth from Binance WebSocket (@depth5, @depth10, @depth20)
     * Format: {"lastUpdateId": xxx, "bids": [[price, qty], ...], "asks": [[price, qty], ...]}
     */
    private void handlePartialBookDepth(JsonNode json) {
        try {
            long updateId = json.get("lastUpdateId").asLong();
            Instant timestamp = Instant.now();
            
            // The symbol is not in the message, we need to track it from the stream name
            // For now, we'll try to extract it from context or use a default
            // This is a limitation of partial book depth streams
            
            // Parse bids
            List<OrderBookLevel> bids = new ArrayList<>();
            JsonNode bidsArray = json.get("bids");
            if (bidsArray != null && bidsArray.isArray()) {
                for (JsonNode bid : bidsArray) {
                    BigDecimal price = new BigDecimal(bid.get(0).asText());
                    BigDecimal quantity = new BigDecimal(bid.get(1).asText());
                    bids.add(new OrderBookLevel(price, quantity));
                }
            }
            
            // Parse asks
            List<OrderBookLevel> asks = new ArrayList<>();
            JsonNode asksArray = json.get("asks");
            if (asksArray != null && asksArray.isArray()) {
                for (JsonNode ask : asksArray) {
                    BigDecimal price = new BigDecimal(ask.get(0).asText());
                    BigDecimal quantity = new BigDecimal(ask.get(1).asText());
                    asks.add(new OrderBookLevel(price, quantity));
                }
            }
            
            // Try to determine symbol from subscribed order books
            // This is a workaround since partial depth doesn't include symbol in the message
            String symbol = "UNKNOWN";
            if (!subscribedOrderBooks.isEmpty()) {
                // Use the first subscribed symbol (limitation of the current approach)
                symbol = subscribedOrderBooks.keySet().iterator().next();
            }
            
            OrderBookData orderBookData = new OrderBookData(
                symbol,
                updateId,
                timestamp,
                bids,
                asks,
                getProviderName()
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                timestamp,
                getProviderName(),
                TradingDataType.ORDER_BOOK,
                orderBookData
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }
            
//            System.out.println("📚 " + symbol + " ORDER_BOOK (partial): " + bids.size() + " bids, " +
//                             asks.size() + " asks, spread=" + orderBookData.getSpread());
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing partial book depth: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle book ticker data from Binance WebSocket
     */
    private void handleBookTickerDirect(JsonNode json) {
        try {
            String symbol = json.get("s").asText().toUpperCase();
            long updateId = json.get("u").asLong();
            Instant timestamp = Instant.now();
            
            BigDecimal bestBidPrice = new BigDecimal(json.get("b").asText());
            BigDecimal bestBidQty = new BigDecimal(json.get("B").asText());
            BigDecimal bestAskPrice = new BigDecimal(json.get("a").asText());
            BigDecimal bestAskQty = new BigDecimal(json.get("A").asText());
            
            // Create minimal order book with just best bid/ask
            List<OrderBookLevel> bids = List.of(new OrderBookLevel(bestBidPrice, bestBidQty));
            List<OrderBookLevel> asks = List.of(new OrderBookLevel(bestAskPrice, bestAskQty));
            
            OrderBookData orderBookData = new OrderBookData(
                symbol,
                updateId,
                timestamp,
                bids,
                asks,
                getProviderName()
            );
            
            TradingData tradingData = new TradingData(
                symbol,
                timestamp,
                getProviderName(),
                TradingDataType.BOOK_TICKER,
                orderBookData
            );
            
            if (dataHandler != null) {
                dataHandler.accept(tradingData);
            }
            
            System.out.println("📖 " + symbol + " BOOK_TICKER: Bid=" + bestBidPrice + " Ask=" + bestAskPrice + 
                             " Spread=" + orderBookData.getSpread());
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing book ticker: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
