package org.cloudvision.trading.provider;

import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.model.TimeInterval;
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
    
    void setDataHandler(Consumer<TradingData> handler);
    List<String> getSupportedSymbols();
    List<TimeInterval> getSupportedIntervals();
    String getProviderName();
    boolean isConnected();
}
