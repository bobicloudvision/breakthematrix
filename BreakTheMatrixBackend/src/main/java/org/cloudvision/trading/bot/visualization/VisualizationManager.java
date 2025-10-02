package org.cloudvision.trading.bot.visualization;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class VisualizationManager {
    
    private final Map<String, List<StrategyVisualizationData>> strategyData = new ConcurrentHashMap<>();
    private final Map<String, Consumer<StrategyVisualizationData>> dataHandlers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registeredStrategies = new ConcurrentHashMap<>(); // strategyId -> symbols
    private final int maxDataPoints = 5000; // Keep last 5000 data points per strategy (supports ~3.5 days of 1m data)

    /**
     * Add visualization data for a strategy
     */
    public void addVisualizationData(StrategyVisualizationData data) {
        String key = data.getStrategyId() + "_" + data.getSymbol();
        
        // Store data
        strategyData.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(data);
        
        // Limit data points to prevent memory issues
        List<StrategyVisualizationData> dataList = strategyData.get(key);
        if (dataList.size() > maxDataPoints) {
            dataList.subList(0, dataList.size() - maxDataPoints).clear();
        }
        
        // Notify handlers (for real-time updates)
        Consumer<StrategyVisualizationData> handler = dataHandlers.get(key);
        if (handler != null) {
            handler.accept(data);
        }
        
        // Global handler for all strategies
        Consumer<StrategyVisualizationData> globalHandler = dataHandlers.get("global");
        if (globalHandler != null) {
            globalHandler.accept(data);
        }
    }

    /**
     * Get visualization data for a specific strategy and symbol
     */
    public List<StrategyVisualizationData> getVisualizationData(String strategyId, String symbol) {
        String key = strategyId + "_" + symbol;
        return List.copyOf(strategyData.getOrDefault(key, List.of()));
    }

    /**
     * Get latest visualization data for a strategy and symbol
     */
    public StrategyVisualizationData getLatestVisualizationData(String strategyId, String symbol) {
        List<StrategyVisualizationData> data = getVisualizationData(strategyId, symbol);
        return data.isEmpty() ? null : data.get(data.size() - 1);
    }

    /**
     * Get visualization data for all symbols of a strategy
     */
    public Map<String, List<StrategyVisualizationData>> getStrategyVisualizationData(String strategyId) {
        Map<String, List<StrategyVisualizationData>> result = new ConcurrentHashMap<>();
        
        strategyData.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(strategyId + "_"))
                .forEach(entry -> {
                    String symbol = entry.getKey().substring(strategyId.length() + 1);
                    result.put(symbol, List.copyOf(entry.getValue()));
                });
        
        return result;
    }

    /**
     * Register a handler for real-time visualization updates
     */
    public void registerDataHandler(String strategyId, String symbol, Consumer<StrategyVisualizationData> handler) {
        String key = strategyId + "_" + symbol;
        dataHandlers.put(key, handler);
    }

    /**
     * Register a global handler for all strategy updates
     */
    public void registerGlobalDataHandler(Consumer<StrategyVisualizationData> handler) {
        dataHandlers.put("global", handler);
    }

    /**
     * Register a strategy with its symbols (called during strategy initialization)
     */
    public void registerStrategy(String strategyId, List<String> symbols) {
        registeredStrategies.put(strategyId, new CopyOnWriteArrayList<>(symbols));
        System.out.println("Registered strategy for visualization: " + strategyId + " with symbols: " + symbols);
    }

    /**
     * Get all available strategies (includes both registered and active strategies)
     */
    public List<String> getAvailableStrategies() {
        // Combine registered strategies with strategies that have data
        var dataStrategies = strategyData.keySet().stream()
                .map(key -> key.split("_")[0])
                .distinct()
                .toList();
        
        var allStrategies = new java.util.HashSet<String>();
        allStrategies.addAll(registeredStrategies.keySet());
        allStrategies.addAll(dataStrategies);
        
        return List.copyOf(allStrategies);
    }

    /**
     * Get all symbols for a strategy
     */
    public List<String> getStrategySymbols(String strategyId) {
        // First check registered symbols
        List<String> registeredSymbols = registeredStrategies.get(strategyId);
        if (registeredSymbols != null && !registeredSymbols.isEmpty()) {
            return List.copyOf(registeredSymbols);
        }
        
        // Fallback to symbols with data
        return strategyData.keySet().stream()
                .filter(key -> key.startsWith(strategyId + "_"))
                .map(key -> key.substring(strategyId.length() + 1))
                .toList();
    }

    /**
     * Clear old data for a strategy
     */
    public void clearStrategyData(String strategyId) {
        strategyData.entrySet().removeIf(entry -> entry.getKey().startsWith(strategyId + "_"));
    }

    /**
     * Get summary statistics for visualization
     */
    public VisualizationSummary getVisualizationSummary() {
        int totalStrategies = getAvailableStrategies().size();
        int totalDataPoints = strategyData.values().stream().mapToInt(List::size).sum();
        
        return new VisualizationSummary(totalStrategies, totalDataPoints, strategyData.size());
    }

    public static class VisualizationSummary {
        private final int totalStrategies;
        private final int totalDataPoints;
        private final int totalSymbolStrategyCombinations;

        public VisualizationSummary(int totalStrategies, int totalDataPoints, int totalSymbolStrategyCombinations) {
            this.totalStrategies = totalStrategies;
            this.totalDataPoints = totalDataPoints;
            this.totalSymbolStrategyCombinations = totalSymbolStrategyCombinations;
        }

        public int getTotalStrategies() { return totalStrategies; }
        public int getTotalDataPoints() { return totalDataPoints; }
        public int getTotalSymbolStrategyCombinations() { return totalSymbolStrategyCombinations; }
    }
}
