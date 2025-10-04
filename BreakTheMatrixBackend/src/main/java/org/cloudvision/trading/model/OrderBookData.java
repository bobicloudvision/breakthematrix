package org.cloudvision.trading.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Represents order book depth data for order flow analysis
 */
public class OrderBookData {
    private final String symbol;
    private final long lastUpdateId;
    private final Instant timestamp;
    private final List<OrderBookLevel> bids;
    private final List<OrderBookLevel> asks;
    private final String provider;
    
    public OrderBookData(String symbol, long lastUpdateId, Instant timestamp,
                        List<OrderBookLevel> bids, List<OrderBookLevel> asks, String provider) {
        this.symbol = symbol;
        this.lastUpdateId = lastUpdateId;
        this.timestamp = timestamp;
        this.bids = bids;
        this.asks = asks;
        this.provider = provider;
    }
    
    // Getters
    public String getSymbol() { return symbol; }
    public long getLastUpdateId() { return lastUpdateId; }
    public Instant getTimestamp() { return timestamp; }
    public List<OrderBookLevel> getBids() { return bids; }
    public List<OrderBookLevel> getAsks() { return asks; }
    public String getProvider() { return provider; }
    
    /**
     * Get best bid price
     */
    public BigDecimal getBestBid() {
        return bids.isEmpty() ? BigDecimal.ZERO : bids.get(0).getPrice();
    }
    
    /**
     * Get best ask price
     */
    public BigDecimal getBestAsk() {
        return asks.isEmpty() ? BigDecimal.ZERO : asks.get(0).getPrice();
    }
    
    /**
     * Get bid-ask spread
     */
    public BigDecimal getSpread() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return getBestAsk().subtract(getBestBid());
    }
    
    /**
     * Get total bid volume up to N levels
     */
    public BigDecimal getTotalBidVolume(int levels) {
        return bids.stream()
                .limit(levels)
                .map(OrderBookLevel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get total ask volume up to N levels
     */
    public BigDecimal getTotalAskVolume(int levels) {
        return asks.stream()
                .limit(levels)
                .map(OrderBookLevel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    @Override
    public String toString() {
        return String.format("OrderBookData{symbol='%s', bids=%d, asks=%d, bestBid=%s, bestAsk=%s, spread=%s, updateId=%d}",
                symbol, bids.size(), asks.size(), getBestBid(), getBestAsk(), getSpread(), lastUpdateId);
    }
    
    /**
     * Represents a single level in the order book
     */
    public static class OrderBookLevel {
        private final BigDecimal price;
        private final BigDecimal quantity;
        
        public OrderBookLevel(BigDecimal price, BigDecimal quantity) {
            this.price = price;
            this.quantity = quantity;
        }
        
        public BigDecimal getPrice() { return price; }
        public BigDecimal getQuantity() { return quantity; }
        
        @Override
        public String toString() {
            return String.format("[%s @ %s]", quantity, price);
        }
    }
}

