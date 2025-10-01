package org.cloudvision.trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public class CandlestickData {
    private final String symbol;
    private final Instant openTime;
    private final Instant closeTime;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final BigDecimal volume;
    private final BigDecimal quoteAssetVolume;
    private final int numberOfTrades;
    private final String interval;
    private final String provider;
    private final boolean isClosed;

    public CandlestickData(String symbol, Instant openTime, Instant closeTime,
                          BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                          BigDecimal volume, BigDecimal quoteAssetVolume, int numberOfTrades,
                          String interval, String provider, boolean isClosed) {
        this.symbol = symbol;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.quoteAssetVolume = quoteAssetVolume;
        this.numberOfTrades = numberOfTrades;
        this.interval = interval;
        this.provider = provider;
        this.isClosed = isClosed;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public Instant getOpenTime() { return openTime; }
    public Instant getCloseTime() { return closeTime; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public BigDecimal getVolume() { return volume; }
    public BigDecimal getQuoteAssetVolume() { return quoteAssetVolume; }
    public int getNumberOfTrades() { return numberOfTrades; }
    public String getInterval() { return interval; }
    public String getProvider() { return provider; }
    public boolean isClosed() { return isClosed; }

    @Override
    public String toString() {
        return String.format("CandlestickData{symbol='%s', interval='%s', open=%s, high=%s, low=%s, close=%s, volume=%s, openTime=%s, provider='%s', closed=%s}",
                symbol, interval, open, high, low, close, volume, openTime, provider, isClosed);
    }
}
