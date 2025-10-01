package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visualization")
@Tag(name = "Strategy Visualization", description = "Endpoints for retrieving strategy analysis and visualization data")
public class StrategyVisualizationController {
    
    private final VisualizationManager visualizationManager;

    public StrategyVisualizationController(VisualizationManager visualizationManager) {
        this.visualizationManager = visualizationManager;
    }

    @Operation(summary = "Get Available Strategies", description = "Retrieve list of all available trading strategies for visualization.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Strategies retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/strategies")
    public List<String> getAvailableStrategies() {
        return visualizationManager.getAvailableStrategies();
    }

    @Operation(summary = "Get Strategy Symbols", description = "Get all trading symbols supported by a specific strategy.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Symbols retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Strategy not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/strategies/{strategyId}/symbols")
    public List<String> getStrategySymbols(
            @Parameter(description = "Strategy identifier", example = "moving-average-crossover")
            @PathVariable String strategyId) {
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

    /**
     * Get data formatted for TradingView Lightweight Charts
     * Returns price series, indicator series, and markers in the format expected by Lightweight Charts
     */
    @Operation(summary = "Get TradingView Chart Data", 
               description = "Get strategy data formatted for TradingView Lightweight Charts")
    @GetMapping("/strategies/{strategyId}/symbols/{symbol}/tradingview")
    public Map<String, Object> getTradingViewData(
            @PathVariable String strategyId,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "200") int limit) {
        
        List<StrategyVisualizationData> data = visualizationManager.getVisualizationData(strategyId, symbol);
        
        if (data.isEmpty()) {
            return Map.of("error", "No data available");
        }
        
        // Limit data points
        if (data.size() > limit) {
            data = data.subList(data.size() - limit, data.size());
        }
        
        // Main price series (line chart format)
        List<Map<String, Object>> priceSeries = data.stream()
            .map(d -> {
                Map<String, Object> point = new java.util.HashMap<>();
                point.put("time", d.getTimestamp().getEpochSecond());
                point.put("value", d.getPrice());
                return point;
            })
            .toList();
        
        // Extract all unique indicator names
        var indicatorNames = data.stream()
            .flatMap(d -> d.getIndicators().keySet().stream())
            .distinct()
            .toList();
        
        // Create series for each indicator
        Map<String, List<Map<String, Object>>> indicatorSeries = new java.util.HashMap<>();
        for (String indicatorName : indicatorNames) {
            List<Map<String, Object>> series = data.stream()
                .filter(d -> d.getIndicators().containsKey(indicatorName))
                .map(d -> {
                    Map<String, Object> point = new java.util.HashMap<>();
                    point.put("time", d.getTimestamp().getEpochSecond());
                    point.put("value", d.getIndicators().get(indicatorName));
                    return point;
                })
                .toList();
            indicatorSeries.put(indicatorName, series);
        }
        
        // Create markers for buy/sell signals
        List<Map<String, Object>> markers = data.stream()
            .filter(d -> !d.getAction().equals("HOLD"))
            .map(d -> {
                boolean isBuy = d.getAction().equals("BUY");
                Map<String, Object> marker = new java.util.HashMap<>();
                marker.put("time", d.getTimestamp().getEpochSecond());
                marker.put("position", isBuy ? "belowBar" : "aboveBar");
                marker.put("color", isBuy ? "#2196F3" : "#e91e63");
                marker.put("shape", isBuy ? "arrowUp" : "arrowDown");
                marker.put("text", d.getAction());
                return marker;
            })
            .toList();
        
        return Map.of(
            "strategyId", strategyId,
            "symbol", symbol,
            "priceSeries", priceSeries,
            "indicators", indicatorSeries,
            "markers", markers,
            "metadata", Map.of(
                "indicatorNames", indicatorNames,
                "dataPoints", data.size(),
                "timeRange", Map.of(
                    "from", data.get(0).getTimestamp().getEpochSecond(),
                    "to", data.get(data.size() - 1).getTimestamp().getEpochSecond()
                )
            )
        );
    }
}
