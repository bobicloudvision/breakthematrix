package org.cloudvision.trading.provider;

import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.model.TimeInterval;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public interface TradingDataProvider {
    void connect();
    void disconnect();
    void subscribe(String symbol);
    void unsubscribe(String symbol);
    
    // Candlestick/Kline subscriptions
    void subscribeToKlines(String symbol, TimeInterval interval);
    void unsubscribeFromKlines(String symbol, TimeInterval interval);
    
    // Order flow subscriptions
    void subscribeToTrades(String symbol);
    void unsubscribeFromTrades(String symbol);
    void subscribeToAggregateTrades(String symbol);
    void unsubscribeFromAggregateTrades(String symbol);
    void subscribeToOrderBook(String symbol, int depth);
    void unsubscribeFromOrderBook(String symbol);
    void subscribeToBookTicker(String symbol);
    void unsubscribeFromBookTicker(String symbol);
    
    // Historical data
    List<CandlestickData> getHistoricalKlines(String symbol, TimeInterval interval, int limit);
    List<CandlestickData> getHistoricalKlines(String symbol, TimeInterval interval, Instant startTime, Instant endTime);
    
    // Historical order flow data
    List<org.cloudvision.trading.model.TradeData> getHistoricalTrades(String symbol, int limit);
    List<org.cloudvision.trading.model.TradeData> getHistoricalAggregateTrades(String symbol, int limit);
    List<org.cloudvision.trading.model.TradeData> getHistoricalAggregateTrades(String symbol, Instant startTime, Instant endTime, int limit);
    org.cloudvision.trading.model.OrderBookData getOrderBookSnapshot(String symbol, int limit);
    
    void setDataHandler(Consumer<TradingData> handler);
    List<String> getSupportedSymbols();
    List<TimeInterval> getSupportedIntervals();
    String getProviderName();
    boolean isConnected();
}
