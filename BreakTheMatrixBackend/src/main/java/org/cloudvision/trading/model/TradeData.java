package org.cloudvision.trading.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents individual trade data for order flow analysis
 */
public class TradeData {
    private final long tradeId;
    private final String symbol;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final BigDecimal quoteQuantity;
    private final Instant timestamp;
    private final boolean isBuyerMaker; // true = sell order, false = buy order
    private final String provider;
    
    // Optional: for aggregate trades
    private final Long firstTradeId;
    private final Long lastTradeId;
    
    public TradeData(long tradeId, String symbol, BigDecimal price, BigDecimal quantity,
                     BigDecimal quoteQuantity, Instant timestamp, boolean isBuyerMaker,
                     String provider) {
        this(tradeId, symbol, price, quantity, quoteQuantity, timestamp, isBuyerMaker, provider, null, null);
    }
    
    public TradeData(long tradeId, String symbol, BigDecimal price, BigDecimal quantity,
                     BigDecimal quoteQuantity, Instant timestamp, boolean isBuyerMaker,
                     String provider, Long firstTradeId, Long lastTradeId) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.quoteQuantity = quoteQuantity;
        this.timestamp = timestamp;
        this.isBuyerMaker = isBuyerMaker;
        this.provider = provider;
        this.firstTradeId = firstTradeId;
        this.lastTradeId = lastTradeId;
    }
    
    // Getters
    public long getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getQuoteQuantity() { return quoteQuantity; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isBuyerMaker() { return isBuyerMaker; }
    public boolean isAggressiveBuy() { return !isBuyerMaker; } // Market buy (taker)
    public boolean isAggressiveSell() { return isBuyerMaker; } // Market sell (taker)
    public String getProvider() { return provider; }
    public Long getFirstTradeId() { return firstTradeId; }
    public Long getLastTradeId() { return lastTradeId; }
    public boolean isAggregateTrade() { return firstTradeId != null && lastTradeId != null; }
    
    @Override
    public String toString() {
        String side = isBuyerMaker ? "SELL" : "BUY";
        if (isAggregateTrade()) {
            return String.format("TradeData{id=%d, symbol='%s', price=%s, qty=%s, side=%s, timestamp=%s, agg=%d-%d}",
                    tradeId, symbol, price, quantity, side, timestamp, firstTradeId, lastTradeId);
        } else {
            return String.format("TradeData{id=%d, symbol='%s', price=%s, qty=%s, side=%s, timestamp=%s}",
                    tradeId, symbol, price, quantity, side, timestamp);
        }
    }
}

