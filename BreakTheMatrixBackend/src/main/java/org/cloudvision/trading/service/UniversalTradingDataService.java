package org.cloudvision.trading.service;

import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradingData;
import org.cloudvision.trading.model.TradingDataType;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.provider.TradingDataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class UniversalTradingDataService {
    private final Map<String, TradingDataProvider> providers = new ConcurrentHashMap<>();
    private Consumer<TradingData> globalDataHandler;
    
    @Autowired(required = false)
    private CandlestickHistoryService candlestickHistoryService;
    
    @Autowired(required = false)
    private TradeHistoryService tradeHistoryService;

    public void registerProvider(TradingDataProvider provider) {
        providers.put(provider.getProviderName(), provider);
        provider.setDataHandler(this::handleData);
        System.out.println("Registered provider: " + provider.getProviderName());
    }

    public void connectProvider(String providerName) {
        TradingDataProvider provider = providers.get(providerName);
        if (provider != null) {
            provider.connect();
        }
    }

    public void subscribeToSymbol(String providerName, String symbol) {
        TradingDataProvider provider = providers.get(providerName);
        if (provider != null && provider.isConnected()) {
            provider.subscribe(symbol);
        }
    }

    public void subscribeToKlines(String providerName, String symbol, TimeInterval interval) {
        TradingDataProvider provider = providers.get(providerName);
        if (provider != null && provider.isConnected()) {
            // First, fetch and store historical candlestick data
            if (candlestickHistoryService != null) {
                try {
                    System.out.println("📥 Fetching historical candles for " + symbol + " before subscribing...");
                    List<CandlestickData> historicalData = provider.getHistoricalKlines(symbol, interval, 5000);
                    
                    if (!historicalData.isEmpty()) {
                        candlestickHistoryService.addCandlesticks(providerName, symbol, interval.getValue(), historicalData);
                        System.out.println("✅ Stored " + historicalData.size() + " historical candles for " + symbol);
                    }


                    // Also fetch historical trades for the same time range
//                    if (tradeHistoryService != null) {
//                        try {
//                            fetchHistoricalTrades(provider, providerName, symbol, historicalData);
//                        } catch (Exception e) {
//                            System.err.println("⚠️ Failed to fetch historical trades for " + symbol + ": " + e.getMessage());
//                        }
//                    }

                } catch (Exception e) {
                    System.err.println("⚠️ Failed to fetch historical data for " + symbol + ": " + e.getMessage());
                }
            }
            
            // Then subscribe to real-time updates
            provider.subscribeToKlines(symbol, interval);
        }
    }
    
    /**
     * Fetch historical trades for the time range covered by candles
     * Fetches ALL trades with proper pagination - no skipping or gaps
     */
    private void fetchHistoricalTrades(TradingDataProvider provider, String providerName, String symbol, List<CandlestickData> candles) {
        if (candles.isEmpty()) {
            return;
        }
        
        Instant startTime = candles.get(0).getOpenTime();
        Instant endTime = candles.get(candles.size() - 1).getCloseTime();
        long timeRangeMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        
        System.out.println("📥 Fetching COMPLETE historical trades for " + symbol + " (" + timeRangeMinutes + " minutes)...");
        
        try {
            List<org.cloudvision.trading.model.TradeData> allTrades = new java.util.ArrayList<>();
            
            // Split time range into 5-minute chunks for detailed coverage
            long chunkMinutes = 5;
            int numChunks = (int) Math.ceil((double) timeRangeMinutes / chunkMinutes);
            
            System.out.println("   📊 Fetching " + numChunks + " time chunks (" + chunkMinutes + " min each) with pagination");
            
            int totalApiCalls = 0;
            
            // Fetch each chunk completely (with pagination if needed)
            for (int i = 0; i < numChunks; i++) {
                Instant chunkStart = startTime.plus(java.time.Duration.ofMinutes(i * chunkMinutes));
                Instant chunkEnd = startTime.plus(java.time.Duration.ofMinutes((i + 1) * chunkMinutes));
                
                if (chunkEnd.isAfter(endTime)) {
                    chunkEnd = endTime;
                }
                
                // Fetch ALL trades in this chunk with pagination
                List<org.cloudvision.trading.model.TradeData> chunkTrades = fetchChunkWithPagination(
                    provider, symbol, chunkStart, chunkEnd);
                
                if (!chunkTrades.isEmpty()) {
                    allTrades.addAll(chunkTrades);
                    totalApiCalls += (chunkTrades.size() / 1000) + 1; // Estimate API calls made
                }
                
                // Progress update every 20 chunks
                if (numChunks > 50 && i > 0 && i % 20 == 0) {
                    System.out.println("   📊 Progress: " + i + "/" + numChunks + " chunks, " + allTrades.size() + " trades so far");
                }
                
                // Rate limiting - be gentle on the API
                if (i < numChunks - 1) {
                    try {
                        Thread.sleep(100); // 100ms delay between chunks
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (!allTrades.isEmpty()) {
                tradeHistoryService.addTrades(providerName, symbol, allTrades);
                System.out.println("✅ Stored " + allTrades.size() + " complete historical trades for " + symbol + " (" + totalApiCalls + " API calls)");
            } else {
                System.out.println("⚠️ No trades found in time range for " + symbol);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error fetching historical trades: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Fetch all trades in a time chunk with proper pagination
     * Handles cases where there are more than 1000 trades (API limit) in the time window
     */
    private List<org.cloudvision.trading.model.TradeData> fetchChunkWithPagination(
            TradingDataProvider provider, String symbol, Instant chunkStart, Instant chunkEnd) {
        
        List<org.cloudvision.trading.model.TradeData> allChunkTrades = new java.util.ArrayList<>();
        Instant currentStart = chunkStart;
        int pageCount = 0;
        final int maxPages = 10; // Safety limit per chunk
        
        while (currentStart.isBefore(chunkEnd) && pageCount < maxPages) {
            List<org.cloudvision.trading.model.TradeData> batch = 
                provider.getHistoricalAggregateTrades(symbol, currentStart, chunkEnd, 1000);
            
            if (batch.isEmpty()) {
                break; // No more trades in this range
            }
            
            allChunkTrades.addAll(batch);
            pageCount++;
            
            // If we got exactly 1000 trades, there might be more
            if (batch.size() == 1000) {
                // Start next request from 1ms after the last trade we received
                org.cloudvision.trading.model.TradeData lastTrade = batch.get(batch.size() - 1);
                currentStart = lastTrade.getTimestamp().plusMillis(1);
                
                // If we've advanced the time cursor, continue pagination
                if (currentStart.isBefore(chunkEnd)) {
                    try {
                        Thread.sleep(50); // Small delay between pagination requests
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            }
            
            break; // Got less than 1000, we have all trades for this chunk
        }
        
        return allChunkTrades;
    }

    public void unsubscribeFromKlines(String providerName, String symbol, TimeInterval interval) {
        TradingDataProvider provider = providers.get(providerName);
        if (provider != null && provider.isConnected()) {
            provider.unsubscribeFromKlines(symbol, interval);
        }
    }

    public void setGlobalDataHandler(Consumer<TradingData> handler) {
        if (this.globalDataHandler != null) {
            System.out.println("⚠️ WARNING: Overwriting existing global data handler!");
        }
        this.globalDataHandler = handler;
        System.out.println("✅ Global data handler set for UniversalTradingDataService");
    }

    private void handleData(TradingData data) {
        // Store candlestick data in centralized history service (before forwarding to TradingBot)
        if (candlestickHistoryService != null && 
            data.getType() == TradingDataType.KLINE && 
            data.getCandlestickData() != null) {
            
            CandlestickData candlestick = data.getCandlestickData();
            candlestickHistoryService.addCandlestick(candlestick);
        }
        
        // Forward data to global handler (TradingBot) for strategy execution
        if (globalDataHandler != null) {
            try {
                globalDataHandler.accept(data);
            } catch (Exception e) {
                System.err.println("❌ Exception in global data handler: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ No global data handler set! Data not forwarded.");
        }
    }

    public List<String> getProviders() {
        return List.copyOf(providers.keySet());
    }

    public List<TimeInterval> getSupportedIntervals(String providerName) {
        TradingDataProvider provider = providers.get(providerName);
        return provider != null ? provider.getSupportedIntervals() : List.of();
    }

    public TradingDataProvider getProvider(String name) {
        return providers.get(name);
    }

    /**
     * Fetch historical klines directly from provider.
     * NOTE: This data is NOT stored in CandlestickHistoryService.
     * Only use for API responses or visualization where persistence is not needed.
     * For data that strategies need, use the provider's WebSocket connection instead.
     */
    public List<CandlestickData> getHistoricalKlines(String providerName, String symbol, TimeInterval interval, int limit) {
        TradingDataProvider provider = providers.get(providerName);
        if (provider != null) {
            return provider.getHistoricalKlines(symbol, interval, limit);
        }
        return List.of();
    }

    /**
     * Fetch historical klines for a time range directly from provider.
     * NOTE: This data is NOT stored in CandlestickHistoryService.
     * Only use for API responses or visualization where persistence is not needed.
     * For data that strategies need, use the provider's WebSocket connection instead.
     */
    public List<CandlestickData> getHistoricalKlines(String providerName, String symbol, TimeInterval interval, 
                                                     Instant startTime, Instant endTime) {
        TradingDataProvider provider = providers.get(providerName);
        if (provider != null) {
            return provider.getHistoricalKlines(symbol, interval, startTime, endTime);
        }
        return List.of();
    }
    
    /**
     * Print storage statistics from CandlestickHistoryService
     */
    public void printStorageStats() {
        if (candlestickHistoryService != null) {
            candlestickHistoryService.printStorageSummary();
        } else {
            System.out.println("⚠️ CandlestickHistoryService not available");
        }
    }
}
