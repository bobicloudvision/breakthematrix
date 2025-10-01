package org.cloudvision.trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public class TradingData {
    private final String symbol;
    private final BigDecimal price;
    private final BigDecimal volume;
    private final Instant timestamp;
    private final String provider;
    private final TradingDataType type;
    
    // Optional candlestick data - null for non-KLINE types
    private final CandlestickData candlestickData;

    // Constructor for simple price data (TICKER, TRADE, etc.)
    public TradingData(String symbol, BigDecimal price, BigDecimal volume, 
                      Instant timestamp, String provider, TradingDataType type) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.provider = provider;
        this.type = type;
        this.candlestickData = null;
    }

    // Constructor for candlestick data (KLINE)
    public TradingData(String symbol, Instant timestamp, String provider, 
                      TradingDataType type, CandlestickData candlestickData) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.provider = provider;
        this.type = type;
        this.candlestickData = candlestickData;
        // For KLINE, price and volume come from candlestick data
        this.price = candlestickData != null ? candlestickData.getClose() : null;
        this.volume = candlestickData != null ? candlestickData.getVolume() : null;
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getVolume() { return volume; }
    public Instant getTimestamp() { return timestamp; }
    public String getProvider() { return provider; }
    public TradingDataType getType() { return type; }
    public CandlestickData getCandlestickData() { return candlestickData; }
    
    public boolean hasCandlestickData() { return candlestickData != null; }

    @Override
    public String toString() {
        if (hasCandlestickData()) {
            return String.format("TradingData{symbol='%s', type=%s, candlestick=%s, provider='%s'}",
                    symbol, type, candlestickData, provider);
        } else {
            return String.format("TradingData{symbol='%s', price=%s, volume=%s, timestamp=%s, provider='%s', type=%s}",
                    symbol, price, volume, timestamp, provider, type);
        }
    }
}
