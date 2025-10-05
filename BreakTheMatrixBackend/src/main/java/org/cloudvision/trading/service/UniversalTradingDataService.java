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
            // First, fetch and store historical data so strategies have immediate data
            if (candlestickHistoryService != null) {
                try {
                    System.out.println("📥 Fetching historical data for " + symbol + " before subscribing...");
                    List<CandlestickData> historicalData = provider.getHistoricalKlines(symbol, interval, 5000);
                    
                    if (!historicalData.isEmpty()) {
                        candlestickHistoryService.addCandlesticks(providerName, symbol, interval.getValue(), historicalData);
                        System.out.println("✅ Stored " + historicalData.size() + " historical candles for " + symbol);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to fetch historical data for " + symbol + ": " + e.getMessage());
                }
            }
            
            // Then subscribe to real-time updates
            provider.subscribeToKlines(symbol, interval);
        }
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
