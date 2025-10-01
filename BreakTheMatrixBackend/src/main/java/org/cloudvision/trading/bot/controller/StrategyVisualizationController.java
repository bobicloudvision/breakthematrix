package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.bot.visualization.StrategyVisualizationData;
import org.cloudvision.trading.bot.visualization.VisualizationManager;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visualization")
@Tag(name = "Strategy Visualization", description = "Endpoints for retrieving strategy analysis and visualization data")
public class StrategyVisualizationController {
    
    private final VisualizationManager visualizationManager;
    private final UniversalTradingDataService tradingDataService;
    private final TradingBot tradingBot;

    public StrategyVisualizationController(VisualizationManager visualizationManager,
                                          UniversalTradingDataService tradingDataService,
                                          TradingBot tradingBot) {
        this.visualizationManager = visualizationManager;
        this.tradingDataService = tradingDataService;
        this.tradingBot = tradingBot;
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
     * Returns multiple series in their optimal formats for visualization
     * 
     * Response includes:
     * - price: Line series for price data
     * - indicators: Each indicator with recommended series type
     * - volume: Histogram series (if available)
     * - markers: Buy/sell signals
     */
    @Operation(summary = "Get TradingView Chart Data", 
               description = "Get strategy data formatted for TradingView Lightweight Charts with multiple series types")
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
        
        // Price as line series (default)
        List<Map<String, Object>> priceSeries = formatLineSeries(data);
        
        // Extract all unique indicator names
        var indicatorNames = data.stream()
            .flatMap(d -> d.getIndicators().keySet().stream())
            .distinct()
            .toList();
        
        // Get strategy metadata to determine indicator visualization
        TradingStrategy strategy = tradingBot.getStrategies().stream()
            .filter(s -> s.getStrategyId().equals(strategyId))
            .findFirst()
            .orElse(null);
        
        Map<String, IndicatorMetadata> indicatorMetadata = strategy != null ? 
            strategy.getIndicatorMetadata() : new java.util.HashMap<>();
        
        // Create series for each indicator using strategy's metadata
        List<Map<String, Object>> indicatorSeries = new java.util.ArrayList<>();
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
            
            // Use strategy's metadata if available, otherwise use smart detection
            IndicatorMetadata metadata = indicatorMetadata.get(indicatorName);
            String seriesType;
            Map<String, Object> config;
            boolean separatePane = false;
            int paneOrder = 0;
            
            if (metadata != null) {
                // Use strategy-defined visualization
                seriesType = metadata.getSeriesType();
                config = new java.util.HashMap<>(metadata.getConfig());
                separatePane = metadata.isSeparatePane();
                paneOrder = metadata.getPaneOrder() != null ? metadata.getPaneOrder() : 0;
            } else {
                // Fallback to smart detection
                seriesType = getRecommendedSeriesType(indicatorName);
                config = getSeriesConfig(indicatorName, seriesType);
            }
            
            Map<String, Object> indicatorInfo = new java.util.HashMap<>();
            indicatorInfo.put("name", indicatorName);
            indicatorInfo.put("type", seriesType);
            indicatorInfo.put("data", series);
            indicatorInfo.put("config", config);
            indicatorInfo.put("separatePane", separatePane);
            indicatorInfo.put("paneOrder", paneOrder);
            
            indicatorSeries.add(indicatorInfo);
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
        
        // Build response with multiple series
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("strategyId", strategyId);
        response.put("symbol", symbol);
        
        // Main series collection
        Map<String, Object> series = new java.util.HashMap<>();
        series.put("price", Map.of(
            "type", "line",
            "data", priceSeries,
            "config", Map.of(
                "color", "#2962FF",
                "lineWidth", 2,
                "title", "Price"
            )
        ));
        series.put("indicators", indicatorSeries);
        
        response.put("series", series);
        response.put("markers", markers);
        response.put("metadata", Map.of(
            "indicatorNames", indicatorNames,
            "dataPoints", data.size(),
            "timeRange", Map.of(
                "from", data.get(0).getTimestamp().getEpochSecond(),
                "to", data.get(data.size() - 1).getTimestamp().getEpochSecond()
            )
        ));
        
        return response;
    }

    /**
     * Format data as line series
     */
    private List<Map<String, Object>> formatLineSeries(List<StrategyVisualizationData> data) {
        return data.stream()
            .map(d -> {
                Map<String, Object> point = new java.util.HashMap<>();
                point.put("time", d.getTimestamp().getEpochSecond());
                point.put("value", d.getPrice());
                return point;
            })
            .toList();
    }
    
    /**
     * Simple fallback for indicators without metadata
     * All indicators default to line series with neutral styling
     * Strategies should define their own IndicatorMetadata for proper visualization
     */
    private String getRecommendedSeriesType(String indicatorName) {
        // Default to line for all unknown indicators
        // Strategies should override getIndicatorMetadata() to specify proper types
        return "line";
    }
    
    /**
     * Simple fallback configuration for indicators without metadata
     * Provides basic neutral styling for all TradingView series types
     */
    private Map<String, Object> getSeriesConfig(String indicatorName, String seriesType) {
        Map<String, Object> config = new java.util.HashMap<>();
        
        // Simple neutral defaults based on series type
        switch (seriesType) {
            case "line":
                config.put("color", "#808080");
                config.put("lineWidth", 1);
                break;
                
            case "area":
                config.put("topColor", "rgba(128, 128, 128, 0.3)");
                config.put("bottomColor", "rgba(128, 128, 128, 0.0)");
                config.put("lineColor", "rgba(128, 128, 128, 1)");
                config.put("lineWidth", 1);
                break;
                
            case "histogram":
                config.put("color", "#808080");
                break;
                
            case "baseline":
                config.put("topLineColor", "#26a69a");
                config.put("bottomLineColor", "#ef5350");
                config.put("topFillColor1", "rgba(38, 166, 154, 0.28)");
                config.put("topFillColor2", "rgba(38, 166, 154, 0.05)");
                config.put("bottomFillColor1", "rgba(239, 83, 80, 0.05)");
                config.put("bottomFillColor2", "rgba(239, 83, 80, 0.28)");
                config.put("baseValue", Map.of("type", "price", "price", 0));
                break;
                
            case "candlestick":
                config.put("upColor", "#26a69a");
                config.put("downColor", "#ef5350");
                config.put("wickUpColor", "#26a69a");
                config.put("wickDownColor", "#ef5350");
                config.put("borderVisible", false);
                break;
                
            case "bar":
                config.put("upColor", "#26a69a");
                config.put("downColor", "#ef5350");
                config.put("openVisible", true);
                config.put("thinBars", false);
                break;
                
            default:
                config.put("color", "#808080");
                config.put("lineWidth", 1);
                break;
        }
        
        config.put("title", indicatorName);
        return config;
    }

    /**
     * Get real OHLC candlestick data for TradingView Candlestick/Bar charts
     * This fetches actual market data from the trading provider
     */
    @Operation(summary = "Get OHLC Candlestick Data",
               description = "Get real OHLC candlestick data for proper candlestick/bar charts. Overlays strategy signals.")
    @GetMapping("/symbols/{symbol}/ohlc")
    public Map<String, Object> getOHLCData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "Binance") String provider,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String strategyId) {
        
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            List<CandlestickData> candlesticks = tradingDataService.getHistoricalKlines(
                provider, symbol, timeInterval, limit
            );
            
            if (candlesticks.isEmpty()) {
                return Map.of("error", "No candlestick data available");
            }
            
            // Format for TradingView Candlestick/Bar series
            List<Map<String, Object>> ohlcSeries = candlesticks.stream()
                .map(c -> {
                    Map<String, Object> candle = new java.util.HashMap<>();
                    candle.put("time", c.getCloseTime().getEpochSecond());
                    candle.put("open", c.getOpen());
                    candle.put("high", c.getHigh());
                    candle.put("low", c.getLow());
                    candle.put("close", c.getClose());
                    return candle;
                })
                .toList();
            
            // Format volume as histogram
            List<Map<String, Object>> volumeSeries = candlesticks.stream()
                .map(c -> {
                    Map<String, Object> vol = new java.util.HashMap<>();
                    vol.put("time", c.getCloseTime().getEpochSecond());
                    vol.put("value", c.getVolume());
                    // Color based on price movement (green for up candle, red for down)
                    vol.put("color", c.getClose().compareTo(c.getOpen()) >= 0 ? 
                        "rgba(38, 166, 154, 0.5)" : "rgba(239, 83, 80, 0.5)");
                    return vol;
                })
                .toList();
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("symbol", symbol);
            response.put("interval", interval);
            response.put("provider", provider);
            response.put("ohlcSeries", ohlcSeries);
            response.put("volumeSeries", volumeSeries);
            
            // If strategy is provided, overlay strategy signals
            if (strategyId != null && !strategyId.isEmpty()) {
                List<StrategyVisualizationData> strategyData = 
                    visualizationManager.getVisualizationData(strategyId, symbol);
                
                if (!strategyData.isEmpty()) {
                    // Create markers for buy/sell signals
                    List<Map<String, Object>> markers = strategyData.stream()
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
                    
                    response.put("markers", markers);
                    response.put("strategyId", strategyId);
                }
            }
            
            response.put("metadata", Map.of(
                "dataPoints", candlesticks.size(),
                "timeRange", Map.of(
                    "from", candlesticks.get(0).getCloseTime().getEpochSecond(),
                    "to", candlesticks.get(candlesticks.size() - 1).getCloseTime().getEpochSecond()
                )
            ));
            
            return response;
            
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid interval: " + interval);
        } catch (Exception e) {
            return Map.of("error", "Failed to fetch candlestick data: " + e.getMessage());
        }
    }
}
