package org.cloudvision.trading.provider.impl;

import org.cloudvision.trading.model.*;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.net.URI;
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
    private static final String BINANCE_WS_URL = "wss://stream.binance.com/ws/btcusdt@kline_1m";
    
    private Consumer<TradingData> dataHandler;
    private boolean connected = false;
    private final List<String> subscribedSymbols = new ArrayList<>();
    private final Map<String, List<TimeInterval>> subscribedKlines = new ConcurrentHashMap<>();
    private final Set<String> activeStreams = ConcurrentHashMap.newKeySet();
    
    private WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);

    @Override
    public void connect() {
        if (connected) {
            System.out.println("🔗 Binance provider already connected");
            return;
        }
        
        try {
            System.out.println("🔗 Connecting directly to Binance WebSocket: " + BINANCE_WS_URL);
            
            URI serverUri = URI.create(BINANCE_WS_URL);
            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    System.out.println("✅ Connected to Binance WebSocket successfully!");
                    System.out.println("🤝 Handshake status: " + handshake.getHttpStatus());
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
                    
                    // Auto-reconnect after 5 seconds if not manually disconnected
                    if (remote && code != 1000) {
                        System.out.println("🔄 Attempting to reconnect in 5 seconds...");
                        executorService.schedule(() -> reconnect(), 5, java.util.concurrent.TimeUnit.SECONDS);
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
        connect();
        
        // For direct stream connection, no need to re-subscribe
        if (connected) {
            System.out.println("✅ Reconnected to BTCUSDT kline stream");
        }
    }

    @Override
    public void disconnect() {
        System.out.println("🔌 Disconnecting from Binance WebSocket...");
        
        connected = false;
        activeStreams.clear();
        subscribedSymbols.clear();
        subscribedKlines.clear();
        
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
        
        // For direct stream connection, we're already connected to btcusdt@kline_1m
        if (symbol.equalsIgnoreCase("BTCUSDT") && interval.getValue().equals("1m")) {
            System.out.println("✅ Already connected to " + symbol + " klines (" + interval.getValue() + ")");
            subscribedKlines.computeIfAbsent(symbol, k -> new ArrayList<>()).add(interval);
        } else {
            System.err.println("❌ This demo only supports BTCUSDT 1m klines (currently connected stream)");
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
            
            // For direct stream connection, we get kline data directly
            if (json.has("k")) {
                // This is kline data from btcusdt@kline_1m
                handleKlineDataDirect(json);
            } else if (json.has("e") && "24hrTicker".equals(json.get("e").asText())) {
                // Ignore 24hr ticker data (we only want klines)
                // System.out.println("📊 24hr ticker: " + json.get("c").asText());
            } else {
                System.out.println("📈 Received other data: " + message);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing Binance message: " + e.getMessage());
            System.err.println("📜 Raw message: " + message);
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
            
            System.out.println("🕯️ " + symbol + " (" + intervalStr + ") OHLC: " + 
                             open + "/" + high + "/" + low + "/" + close + " [" + (isClosed ? "CLOSED" : "LIVE") + "]");
            
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
            
            System.out.println("🕯️ " + symbol + " (" + intervalStr + ") OHLC: " + 
                             open + "/" + high + "/" + low + "/" + close + " [" + (isClosed ? "CLOSED" : "LIVE") + "]");
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing kline data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    
}
