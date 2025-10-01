package org.cloudvision.trading.bot.controller;

import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visualization")
public class StrategyVisualizationController {
    
    private final VisualizationManager visualizationManager;

    public StrategyVisualizationController(VisualizationManager visualizationManager) {
        this.visualizationManager = visualizationManager;
    }

    @GetMapping("/strategies")
    public List<String> getAvailableStrategies() {
        return visualizationManager.getAvailableStrategies();
    }

    @GetMapping("/strategies/{strategyId}/symbols")
    public List<String> getStrategySymbols(@PathVariable String strategyId) {
        return visualizationManager.getStrategySymbols(strategyId);
    }

    @GetMapping("/strategies/{strategyId}/data")
    public Map<String, List<StrategyVisualizationData>> getStrategyData(@PathVariable String strategyId) {
        return visualizationManager.getStrategyVisualizationData(strategyId);
    }

    @GetMapping("/strategies/{strategyId}/symbols/{symbol}/data")
    public List<StrategyVisualizationData> getStrategySymbolData(
            @PathVariable String strategyId,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<StrategyVisualizationData> data = visualizationManager.getVisualizationData(strategyId, symbol);
        
        // Return last 'limit' data points
        if (data.size() > limit) {
            return data.subList(data.size() - limit, data.size());
        }
        
        return data;
    }

    @GetMapping("/strategies/{strategyId}/symbols/{symbol}/latest")
    public StrategyVisualizationData getLatestData(
            @PathVariable String strategyId,
            @PathVariable String symbol) {
        
        return visualizationManager.getLatestVisualizationData(strategyId, symbol);
    }

    @GetMapping("/summary")
    public VisualizationManager.VisualizationSummary getSummary() {
        return visualizationManager.getVisualizationSummary();
    }

    @DeleteMapping("/strategies/{strategyId}/data")
    public String clearStrategyData(@PathVariable String strategyId) {
        visualizationManager.clearStrategyData(strategyId);
        return "Data cleared for strategy: " + strategyId;
    }

    // Chart-specific endpoints
    @GetMapping("/strategies/{strategyId}/symbols/{symbol}/chart-data")
    public Map<String, Object> getChartData(
            @PathVariable String strategyId,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<StrategyVisualizationData> data = visualizationManager.getVisualizationData(strategyId, symbol);
        
        // Return last 'limit' data points
        if (data.size() > limit) {
            data = data.subList(data.size() - limit, data.size());
        }
        
        // Format for chart libraries
        return Map.of(
            "timestamps", data.stream().map(StrategyVisualizationData::getTimestamp).toList(),
            "prices", data.stream().map(StrategyVisualizationData::getPrice).toList(),
            "indicators", data.stream().map(StrategyVisualizationData::getIndicators).toList(),
            "actions", data.stream().map(StrategyVisualizationData::getAction).toList(),
            "signals", data.stream().map(StrategyVisualizationData::getSignals).toList()
        );
    }

    @GetMapping("/strategies/{strategyId}/symbols/{symbol}/performance")
    public Map<String, Object> getPerformanceData(
            @PathVariable String strategyId,
            @PathVariable String symbol) {
        
        StrategyVisualizationData latest = visualizationManager.getLatestVisualizationData(strategyId, symbol);
        
        if (latest == null) {
            return Map.of("error", "No data available for strategy " + strategyId + " and symbol " + symbol);
        }
        
        return Map.of(
            "strategyId", strategyId,
            "symbol", symbol,
            "performance", latest.getPerformance(),
            "lastUpdate", latest.getTimestamp()
        );
    }
}
