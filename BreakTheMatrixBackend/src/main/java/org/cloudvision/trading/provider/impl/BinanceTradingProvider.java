package org.cloudvision.trading.provider.impl;

import org.cloudvision.trading.model.*;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class BinanceTradingProvider implements TradingDataProvider {
    private Consumer<TradingData> dataHandler;
    private boolean connected = false;
    private final List<String> subscribedSymbols = new ArrayList<>();
    private final Map<String, List<TimeInterval>> subscribedKlines = new ConcurrentHashMap<>();

    @Override
    public void connect() {
        // TODO: Implement Binance WebSocket connection
        connected = true;
        System.out.println("Binance provider connected");
    }

    @Override
    public void disconnect() {
        connected = false;
        subscribedSymbols.clear();
        subscribedKlines.clear();
        System.out.println("Binance provider disconnected");
    }

    @Override
    public void subscribe(String symbol) {
        if (!connected) throw new IllegalStateException("Not connected");
        subscribedSymbols.add(symbol);
        // TODO: Subscribe to Binance WebSocket for this symbol
        System.out.println("Subscribed to " + symbol + " ticker on Binance");
    }

    @Override
    public void unsubscribe(String symbol) {
        subscribedSymbols.remove(symbol);
        // TODO: Unsubscribe from Binance WebSocket
        System.out.println("Unsubscribed from " + symbol + " ticker on Binance");
    }

    @Override
    public void subscribeToKlines(String symbol, TimeInterval interval) {
        if (!connected) throw new IllegalStateException("Not connected");
        subscribedKlines.computeIfAbsent(symbol, k -> new ArrayList<>()).add(interval);
        // TODO: Subscribe to Binance Kline WebSocket for this symbol and interval
        System.out.println("Subscribed to " + symbol + " klines (" + interval.getValue() + ") on Binance");
    }

    @Override
    public void unsubscribeFromKlines(String symbol, TimeInterval interval) {
        List<TimeInterval> intervals = subscribedKlines.get(symbol);
        if (intervals != null) {
            intervals.remove(interval);
            if (intervals.isEmpty()) {
                subscribedKlines.remove(symbol);
            }
        }
        // TODO: Unsubscribe from Binance Kline WebSocket
        System.out.println("Unsubscribed from " + symbol + " klines (" + interval.getValue() + ") on Binance");
    }

    @Override
    public void setDataHandler(Consumer<TradingData> handler) {
        this.dataHandler = handler;
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

    // Simulate receiving ticker data (for testing)
    public void simulateTickerData(String symbol) {
        if (dataHandler != null && subscribedSymbols.contains(symbol)) {
            TradingData data = new TradingData(
                symbol,
                new BigDecimal("50000.00"),
                new BigDecimal("1.5"),
                Instant.now(),
                getProviderName(),
                TradingDataType.TICKER
            );
            dataHandler.accept(data);
        }
    }

    // Simulate receiving candlestick data (for testing)
    public void simulateKlineData(String symbol, TimeInterval interval) {
        List<TimeInterval> intervals = subscribedKlines.get(symbol);
        if (dataHandler != null && intervals != null && intervals.contains(interval)) {
            Instant now = Instant.now();
            
            CandlestickData candlestick = new CandlestickData(
                symbol,
                now.minusSeconds(60), // openTime
                now, // closeTime
                new BigDecimal("49800.00"), // open
                new BigDecimal("50200.00"), // high
                new BigDecimal("49700.00"), // low
                new BigDecimal("50000.00"), // close
                new BigDecimal("125.5"), // volume
                new BigDecimal("6275000.00"), // quoteAssetVolume
                1250, // numberOfTrades
                interval.getValue(),
                getProviderName(),
                true // isClosed
            );
            
            TradingData data = new TradingData(
                symbol,
                now,
                getProviderName(),
                TradingDataType.KLINE,
                candlestick
            );
            
            dataHandler.accept(data);
        }
    }
}
