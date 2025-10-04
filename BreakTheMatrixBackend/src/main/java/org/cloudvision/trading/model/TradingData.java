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
    
    // Optional data fields - null for types that don't use them
    private final CandlestickData candlestickData;
    private final TradeData tradeData;
    private final OrderBookData orderBookData;

    // Constructor for simple price data (TICKER, etc.)
    public TradingData(String symbol, BigDecimal price, BigDecimal volume, 
                      Instant timestamp, String provider, TradingDataType type) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.provider = provider;
        this.type = type;
        this.candlestickData = null;
        this.tradeData = null;
        this.orderBookData = null;
    }

    // Constructor for candlestick data (KLINE)
    public TradingData(String symbol, Instant timestamp, String provider, 
                      TradingDataType type, CandlestickData candlestickData) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.provider = provider;
        this.type = type;
        this.candlestickData = candlestickData;
        this.tradeData = null;
        this.orderBookData = null;
        // For KLINE, price and volume come from candlestick data
        this.price = candlestickData != null ? candlestickData.getClose() : null;
        this.volume = candlestickData != null ? candlestickData.getVolume() : null;
    }
    
    // Constructor for trade data (TRADE, AGGREGATE_TRADE)
    public TradingData(String symbol, Instant timestamp, String provider,
                      TradingDataType type, TradeData tradeData) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.provider = provider;
        this.type = type;
        this.tradeData = tradeData;
        this.candlestickData = null;
        this.orderBookData = null;
        // For TRADE, price and volume come from trade data
        this.price = tradeData != null ? tradeData.getPrice() : null;
        this.volume = tradeData != null ? tradeData.getQuantity() : null;
    }
    
    // Constructor for order book data (ORDER_BOOK, BOOK_TICKER)
    public TradingData(String symbol, Instant timestamp, String provider,
                      TradingDataType type, OrderBookData orderBookData) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.provider = provider;
        this.type = type;
        this.orderBookData = orderBookData;
        this.candlestickData = null;
        this.tradeData = null;
        // For ORDER_BOOK, use best bid as price
        this.price = orderBookData != null ? orderBookData.getBestBid() : null;
        this.volume = null; // No single volume for order book
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getVolume() { return volume; }
    public Instant getTimestamp() { return timestamp; }
    public String getProvider() { return provider; }
    public TradingDataType getType() { return type; }
    public CandlestickData getCandlestickData() { return candlestickData; }
    public TradeData getTradeData() { return tradeData; }
    public OrderBookData getOrderBookData() { return orderBookData; }
    
    public boolean hasCandlestickData() { return candlestickData != null; }
    public boolean hasTradeData() { return tradeData != null; }
    public boolean hasOrderBookData() { return orderBookData != null; }

    @Override
    public String toString() {
        if (hasCandlestickData()) {
            return String.format("TradingData{symbol='%s', type=%s, candlestick=%s, provider='%s'}",
                    symbol, type, candlestickData, provider);
        } else if (hasTradeData()) {
            return String.format("TradingData{symbol='%s', type=%s, trade=%s, provider='%s'}",
                    symbol, type, tradeData, provider);
        } else if (hasOrderBookData()) {
            return String.format("TradingData{symbol='%s', type=%s, orderBook=%s, provider='%s'}",
                    symbol, type, orderBookData, provider);
        } else {
            return String.format("TradingData{symbol='%s', price=%s, volume=%s, timestamp=%s, provider='%s', type=%s}",
                    symbol, price, volume, timestamp, provider, type);
        }
    }
}
