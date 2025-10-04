package org.cloudvision.trading.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Footprint Candle - Shows volume traded at each price level with buy/sell breakdown
 * Essential for order flow and tape reading analysis
 */
public class FootprintCandle {
    private final String symbol;
    private final Instant openTime;
    private final Instant closeTime;
    private final String interval;
    
    // Standard OHLC
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    
    // Total volume
    private final BigDecimal totalVolume;
    private final BigDecimal totalBuyVolume;
    private final BigDecimal totalSellVolume;
    
    // Volume profile: price level -> volume data
    private final Map<BigDecimal, PriceLevelVolume> volumeProfile;
    
    // Key metrics
    private final BigDecimal delta; // Buy volume - Sell volume
    private final BigDecimal cumulativeDelta;
    private final BigDecimal pointOfControl; // Price with highest volume
    private final BigDecimal valueAreaHigh;
    private final BigDecimal valueAreaLow;
    private final int numberOfTrades;
    
    public FootprintCandle(String symbol, Instant openTime, Instant closeTime, String interval,
                          BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                          BigDecimal totalVolume, BigDecimal totalBuyVolume, BigDecimal totalSellVolume,
                          Map<BigDecimal, PriceLevelVolume> volumeProfile,
                          BigDecimal delta, BigDecimal cumulativeDelta,
                          BigDecimal pointOfControl, BigDecimal valueAreaHigh, BigDecimal valueAreaLow,
                          int numberOfTrades) {
        this.symbol = symbol;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.interval = interval;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.totalVolume = totalVolume;
        this.totalBuyVolume = totalBuyVolume;
        this.totalSellVolume = totalSellVolume;
        this.volumeProfile = volumeProfile;
        this.delta = delta;
        this.cumulativeDelta = cumulativeDelta;
        this.pointOfControl = pointOfControl;
        this.valueAreaHigh = valueAreaHigh;
        this.valueAreaLow = valueAreaLow;
        this.numberOfTrades = numberOfTrades;
    }
    
    // Getters
    public String getSymbol() { return symbol; }
    public Instant getOpenTime() { return openTime; }
    public Instant getCloseTime() { return closeTime; }
    public String getInterval() { return interval; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public BigDecimal getTotalVolume() { return totalVolume; }
    public BigDecimal getTotalBuyVolume() { return totalBuyVolume; }
    public BigDecimal getTotalSellVolume() { return totalSellVolume; }
    public Map<BigDecimal, PriceLevelVolume> getVolumeProfile() { return volumeProfile; }
    public BigDecimal getDelta() { return delta; }
    public BigDecimal getCumulativeDelta() { return cumulativeDelta; }
    public BigDecimal getPointOfControl() { return pointOfControl; }
    public BigDecimal getValueAreaHigh() { return valueAreaHigh; }
    public BigDecimal getValueAreaLow() { return valueAreaLow; }
    public int getNumberOfTrades() { return numberOfTrades; }
    
    /**
     * Get volume profile sorted by price (descending)
     */
    public List<Map.Entry<BigDecimal, PriceLevelVolume>> getSortedVolumeProfile() {
        List<Map.Entry<BigDecimal, PriceLevelVolume>> sorted = new ArrayList<>(volumeProfile.entrySet());
        sorted.sort((a, b) -> b.getKey().compareTo(a.getKey())); // Descending price
        return sorted;
    }
    
    /**
     * Get imbalance at price level (buy % vs sell %)
     */
    public BigDecimal getImbalanceAtPrice(BigDecimal price) {
        PriceLevelVolume level = volumeProfile.get(price);
        if (level == null || level.getTotalVolume().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Imbalance = (buyVolume - sellVolume) / totalVolume
        return level.getBuyVolume()
                .subtract(level.getSellVolume())
                .divide(level.getTotalVolume(), 4, java.math.RoundingMode.HALF_UP);
    }
    
    @Override
    public String toString() {
        return String.format("FootprintCandle{symbol='%s', interval='%s', OHLC=[%s/%s/%s/%s], " +
                "volume=%s, delta=%s, POC=%s, trades=%d, levels=%d}",
                symbol, interval, open, high, low, close, totalVolume, delta, 
                pointOfControl, numberOfTrades, volumeProfile.size());
    }
    
    /**
     * Volume data at a specific price level
     */
    public static class PriceLevelVolume {
        private final BigDecimal price;
        private BigDecimal buyVolume;
        private BigDecimal sellVolume;
        private int tradeCount;
        
        public PriceLevelVolume(BigDecimal price) {
            this.price = price;
            this.buyVolume = BigDecimal.ZERO;
            this.sellVolume = BigDecimal.ZERO;
            this.tradeCount = 0;
        }
        
        public void addBuyVolume(BigDecimal volume) {
            this.buyVolume = this.buyVolume.add(volume);
            this.tradeCount++;
        }
        
        public void addSellVolume(BigDecimal volume) {
            this.sellVolume = this.sellVolume.add(volume);
            this.tradeCount++;
        }
        
        public BigDecimal getPrice() { return price; }
        public BigDecimal getBuyVolume() { return buyVolume; }
        public BigDecimal getSellVolume() { return sellVolume; }
        public BigDecimal getTotalVolume() { return buyVolume.add(sellVolume); }
        public BigDecimal getDelta() { return buyVolume.subtract(sellVolume); }
        public int getTradeCount() { return tradeCount; }
        
        /**
         * Get buy/sell ratio (0-1, where >0.5 means more buying)
         */
        public BigDecimal getBuyRatio() {
            BigDecimal total = getTotalVolume();
            if (total.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.valueOf(0.5);
            }
            return buyVolume.divide(total, 4, java.math.RoundingMode.HALF_UP);
        }
        
        @Override
        public String toString() {
            return String.format("[%s: Buy=%s Sell=%s Delta=%s]", 
                    price, buyVolume, sellVolume, getDelta());
        }
    }
}

