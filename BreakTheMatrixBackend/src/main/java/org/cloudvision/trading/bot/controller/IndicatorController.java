package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.indicators.Indicator;
import org.cloudvision.trading.bot.indicators.IndicatorParameter;
import org.cloudvision.trading.bot.indicators.IndicatorService;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.ShapeRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Technical Indicators
 * 
 * Provides endpoints to:
 * - List and discover available indicators
 * - Calculate indicator values on demand
 * - Get historical indicator data for charting
 * - Configure indicator parameters dynamically
 */
@RestController
@RequestMapping("/api/indicators")
@CrossOrigin(origins = "*")
@Tag(name = "Indicators", description = "Technical Indicators API - Calculate and visualize technical indicators for trading")
public class IndicatorController {
    
    @Autowired
    private IndicatorService indicatorService;
    
    /**
     * Get all available indicators
     * GET /api/indicators - Returns all indicators
     * GET /api/indicators?category=TREND - Returns indicators filtered by category
     * 
     * @param category Optional category filter (TREND, MOMENTUM, VOLATILITY, VOLUME, OVERLAY, CUSTOM)
     */
    @Operation(
        summary = "List all technical indicators",
        description = "Returns a list of all available technical indicators. Optionally filter by category to get specific types of indicators.",
        parameters = {
            @Parameter(
                name = "category",
                description = "Filter indicators by category",
                required = false,
                example = "TREND",
                schema = @Schema(allowableValues = {"TREND", "MOMENTUM", "VOLATILITY", "VOLUME", "OVERLAY", "CUSTOM"})
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved list of indicators",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Indicator List",
                    value = """
                    [
                      {
                        "id": "sma",
                        "name": "Simple Moving Average",
                        "description": "Arithmetic mean of closing prices over a specified period",
                        "category": "TREND",
                        "categoryDisplayName": "Trend Indicators"
                      },
                      {
                        "id": "volume",
                        "name": "Volume",
                        "description": "Trading volume with color-coded bars",
                        "category": "VOLUME",
                        "categoryDisplayName": "Volume Indicators"
                      }
                    ]
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid category parameter")
    })
    @GetMapping
    public ResponseEntity<List<IndicatorListItem>> getAllIndicators(
            @RequestParam(required = false) String category) {
        
        List<Indicator> indicators;
        
        if (category != null && !category.trim().isEmpty()) {
            // Filter by category if provided
            try {
                Indicator.IndicatorCategory cat = Indicator.IndicatorCategory.valueOf(category.toUpperCase());
                indicators = indicatorService.getIndicatorsByCategory(cat);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            // Return all indicators if no category specified
            indicators = indicatorService.getAllIndicators();
        }
        
        List<IndicatorListItem> items = indicators.stream()
            .map(ind -> new IndicatorListItem(
                ind.getId(),
                ind.getName(),
                ind.getDescription(),
                ind.getCategory().name(),
                ind.getCategory().getDisplayName()
            ))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(items);
    }
    
    /**
     * Get indicator details including parameters and metadata
     * GET /api/indicators/{id}
     */
    @Operation(
        summary = "Get indicator details",
        description = "Retrieves detailed information about a specific indicator including its configuration parameters, visualization metadata, and requirements.",
        parameters = {
            @Parameter(
                name = "id",
                description = "Unique identifier of the indicator",
                required = true,
                example = "sma"
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved indicator details",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "SMA Details",
                    value = """
                    {
                      "id": "sma",
                      "name": "Simple Moving Average",
                      "description": "Arithmetic mean of closing prices over a specified period. Smooths price data to identify trends.",
                      "category": "TREND",
                      "categoryDisplayName": "Trend Indicators",
                      "parameters": {
                        "period": {
                          "name": "period",
                          "displayName": "Period",
                          "description": "Number of periods for the moving average",
                          "type": "INTEGER",
                          "defaultValue": 20,
                          "minValue": 1,
                          "maxValue": 500,
                          "required": true
                        },
                        "source": {
                          "name": "source",
                          "displayName": "Price Source",
                          "description": "Price type to use for calculation",
                          "type": "STRING",
                          "defaultValue": "close",
                          "required": false
                        },
                        "color": {
                          "name": "color",
                          "displayName": "Line Color",
                          "description": "Color for the SMA line on the chart",
                          "type": "STRING",
                          "defaultValue": "#2962FF",
                          "required": false
                        }
                      },
                      "visualizationMetadata": {
                        "sma": {
                          "key": "sma",
                          "displayName": "SMA(20)",
                          "visualType": "LINE",
                          "color": "#2962FF",
                          "lineWidth": 2,
                          "separatePane": false,
                          "paneOrder": 0
                        }
                      },
                      "minRequiredCandles": 20
                    }
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "404", description = "Indicator not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<IndicatorDetails> getIndicatorDetails(@PathVariable String id) {
        try {
            Indicator indicator = indicatorService.getIndicator(id);
            
            // Convert parameters to DTO
            Map<String, ParameterInfo> params = new HashMap<>();
            for (Map.Entry<String, IndicatorParameter> entry : indicator.getParameters().entrySet()) {
                IndicatorParameter param = entry.getValue();
                params.put(entry.getKey(), new ParameterInfo(
                    param.getName(),
                    param.getDisplayName(),
                    param.getDescription(),
                    param.getType().name(),
                    param.getDefaultValue(),
                    param.getMinValue(),
                    param.getMaxValue(),
                    param.isRequired()
                ));
            }
            
            // Get default visualization metadata
            Map<String, IndicatorMetadata> vizMetadata = indicator.getVisualizationMetadata(new HashMap<>());
            
            IndicatorDetails details = new IndicatorDetails(
                indicator.getId(),
                indicator.getName(),
                indicator.getDescription(),
                indicator.getCategory().name(),
                indicator.getCategory().getDisplayName(),
                params,
                vizMetadata,
                indicator.getMinRequiredCandles(new HashMap<>())
            );
            
            return ResponseEntity.ok(details);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Calculate current indicator value
     * POST /api/indicators/{id}/calculate
     * 
     * Body: {
     *   "provider": "Binance",
     *   "symbol": "BTCUSDT",
     *   "interval": "1m",
     *   "params": {
     *     "period": 20
     *   }
     * }
     */
    @Operation(
        summary = "Calculate current indicator value",
        description = "Calculates the current value of a technical indicator for a specific symbol using the latest market data.",
        parameters = {
            @Parameter(
                name = "id",
                description = "Indicator identifier (e.g., 'sma', 'volume', 'rsi')",
                required = true,
                example = "sma"
            )
        }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Calculation request parameters",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "SMA(50) for BTCUSDT",
                    description = "Calculate 50-period SMA for Bitcoin",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "params": {
                        "period": 50,
                        "color": "#FF6B6B"
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "SMA(20) for ETHUSDT",
                    description = "Calculate 20-period SMA for Ethereum",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "ETHUSDT",
                      "interval": "1m",
                      "params": {
                        "period": 20,
                        "source": "close"
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "Volume for BTCUSDT",
                    description = "Get current trading volume",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "15m",
                      "params": {}
                    }
                    """
                )
            }
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully calculated indicator value",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "SMA Result",
                    value = """
                    {
                      "indicatorId": "sma",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "values": {
                        "sma": "95234.56"
                      }
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters or insufficient data",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "error": "No historical data available for BTCUSDT"
                    }
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "404", description = "Indicator not found")
    })
    @PostMapping("/{id}/calculate")
    public ResponseEntity<?> calculateIndicator(
            @PathVariable String id,
            @RequestBody CalculateRequest request) {
        try {
            Map<String, BigDecimal> result = indicatorService.calculate(
                id,
                request.getProvider(),
                request.getSymbol(),
                request.getInterval(),
                request.getParams() != null ? request.getParams() : new HashMap<>()
            );
            
            return ResponseEntity.ok(Map.of(
                "indicatorId", id,
                "symbol", request.getSymbol(),
                "interval", request.getInterval(),
                "values", result
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get historical indicator data for charting
     * POST /api/indicators/{id}/historical
     * 
     * Body: {
     *   "provider": "Binance",
     *   "symbol": "BTCUSDT",
     *   "interval": "1m",
     *   "count": 100,
     *   "params": {
     *     "period": 20
     *   }
     * }
     */
    @Operation(
        summary = "Get historical indicator data",
        description = "Retrieves historical indicator values for charting purposes. Returns time-series data points that can be directly plotted on a chart.",
        parameters = {
            @Parameter(
                name = "id",
                description = "Indicator identifier",
                required = true,
                example = "sma"
            )
        }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Historical data request parameters",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "SMA(20) - 200 points",
                    description = "Get 200 data points of SMA(20) for BTCUSDT 5-minute chart",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "count": 200,
                      "params": {
                        "period": 20,
                        "color": "#2962FF",
                        "lineWidth": 2
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "SMA(50) - 100 points",
                    description = "Get 100 data points of SMA(50) for ETHUSDT hourly chart",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "ETHUSDT",
                      "interval": "1h",
                      "count": 100,
                      "params": {
                        "period": 50,
                        "source": "close"
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "Volume - 500 points",
                    description = "Get 500 data points of volume for BTCUSDT 1-minute chart",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "1m",
                      "count": 500,
                      "params": {
                        "bullishColor": "#26a69a",
                        "bearishColor": "#ef5350"
                      }
                    }
                    """
                )
            }
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved historical data",
            content = @Content(
                mediaType = "application/json",
examples = {
                    @ExampleObject(
                        name = "Historical SMA Data",
                        value = """
                        {
                          "indicatorId": "sma",
                          "symbol": "BTCUSDT",
                          "interval": "5m",
                          "count": 200,
                          "data": [
                            {
                              "time": 1704067200,
                              "values": {
                                "sma": "94500.00"
                              }
                            },
                            {
                              "time": 1704067500,
                              "values": {
                                "sma": "94523.45"
                              }
                            },
                            {
                              "time": 1704067800,
                              "values": {
                                "sma": "94550.12"
                              }
                            }
                          ],
                          "metadata": {
                            "sma": {
                              "key": "sma",
                              "displayName": "SMA(20)",
                              "visualType": "LINE",
                              "color": "#2962FF",
                              "lineWidth": 2,
                              "separatePane": false,
                              "paneOrder": 0
                            }
                          }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Order Block Indicator with Shapes",
                        description = "Optimized response for charting libraries. Shapes are categorized by type (boxes, lines, markers, arrows) for flexible rendering.",
                        value = """
                        {
                          "indicatorId": "orderblock",
                          "symbol": "BTCUSDT",
                          "interval": "1m",
                          "count": 100,
                          "supportsShapes": true,
                          "series": {
                            "marketStructure": [
                              { "time": 1704067200, "value": "0" },
                              { "time": 1704067500, "value": "1" },
                              { "time": 1704067800, "value": "0" }
                            ],
                            "volumeStrength": [
                              { "time": 1704067200, "value": "1.8" },
                              { "time": 1704067500, "value": "2.1" },
                              { "time": 1704067800, "value": "1.5" }
                            ],
                            "activeBullishOBs": [
                              { "time": 1704067200, "value": "2" },
                              { "time": 1704067500, "value": "2" },
                              { "time": 1704067800, "value": "3" }
                            ]
                          },
                          "shapes": {
                            "boxes": [
                              {
                                "time1": 1704060000,
                                "time2": 1704067200,
                                "price1": 50000.0,
                                "price2": 49500.0,
                                "backgroundColor": "rgba(22, 148, 0, 0.15)",
                                "text": "Bullish OB ✓",
                                "textColor": "rgba(22, 148, 0, 0.8)",
                                "volumeStrength": 1.8,
                                "touched": true,
                                "mitigated": false
                              },
                              {
                                "time1": 1704055000,
                                "time2": 1704067200,
                                "price1": 51200.0,
                                "price2": 50800.0,
                                "backgroundColor": "rgba(255, 17, 0, 0.15)",
                                "text": "Bearish OB",
                                "textColor": "rgba(255, 17, 0, 0.8)",
                                "volumeStrength": 2.1,
                                "touched": false,
                                "mitigated": false
                              }
                            ],
                            "lines": [
                              {
                                "time1": 1704060000,
                                "time2": 1704070000,
                                "price1": 49800.0,
                                "price2": 50200.0,
                                "color": "#2962FF",
                                "width": 2,
                                "style": "solid",
                                "text": "Support Line"
                              }
                            ],
                            "markers": [
                              {
                                "time": 1704067200,
                                "price": 50000.0,
                                "shape": "circle",
                                "color": "#26a69a",
                                "text": "Entry"
                              }
                            ]
                          },
                          "shapesSummary": {
                            "boxes": 2,
                            "lines": 1,
                            "markers": 1
                          },
                          "data": [
                            {
                              "time": 1704067200,
                              "values": {
                                "marketStructure": "0",
                                "volumeStrength": "1.8",
                                "activeBullishOBs": "2"
                              }
                            }
                          ],
                          "metadata": {
                            "marketStructure": {
                              "key": "marketStructure",
                              "displayName": "Market Structure",
                              "visualType": "HISTOGRAM",
                              "separatePane": true,
                              "paneOrder": 1
                            }
                          }
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid parameters or insufficient data",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "error": "Insufficient data for BTCUSDT"
                    }
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "404", description = "Indicator not found")
    })
    @PostMapping("/{id}/historical")
    public ResponseEntity<?> getHistoricalData(
            @PathVariable String id,
            @RequestBody HistoricalRequest request) {
        try {
            List<IndicatorService.IndicatorDataPoint> dataPoints = 
                indicatorService.calculateHistorical(
                    id,
                    request.getProvider(),
                    request.getSymbol(),
                    request.getInterval(),
                    request.getParams() != null ? request.getParams() : new HashMap<>(),
                    request.getCount() != null ? request.getCount() : 100
                );
            
            // Collect all shapes using ShapeRegistry (dynamic, no hardcoded types)
            Map<String, List<Map<String, Object>>> shapesByType = new HashMap<>();
            boolean hasShapes = false;
            
            System.out.println("🔍 Collecting shapes from " + dataPoints.size() + " data points");
            
            for (IndicatorService.IndicatorDataPoint dp : dataPoints) {
                if (dp.getAdditionalData() != null) {
                    // Extract all shapes dynamically using ShapeRegistry
                    Map<String, List<Map<String, Object>>> shapesFromPoint = 
                        ShapeRegistry.extractShapes(dp.getAdditionalData());
                    
                    if (!shapesFromPoint.isEmpty()) {
                        hasShapes = true;
                        
                        // Merge shapes into the main collection
                        for (Map.Entry<String, List<Map<String, Object>>> entry : shapesFromPoint.entrySet()) {
                            String shapeType = entry.getKey();
                            List<Map<String, Object>> shapes = entry.getValue();
                            shapesByType.computeIfAbsent(shapeType, k -> new ArrayList<>()).addAll(shapes);
                        }
                    }
                }
            }
            
            // Deduplicate shapes using ShapeRegistry (dynamic deduplication based on type)
            Map<String, List<Map<String, Object>>> uniqueShapesByType = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : shapesByType.entrySet()) {
                String shapeType = entry.getKey();
                List<Map<String, Object>> shapes = entry.getValue();
                
                // Use ShapeRegistry to deduplicate based on shape type
                List<Map<String, Object>> uniqueShapes = ShapeRegistry.deduplicate(shapeType, shapes);
                uniqueShapesByType.put(shapeType, uniqueShapes);
            }
            
            System.out.println("🔍 Total shapes collected by type: " + uniqueShapesByType);
            System.out.println("🔍 Has shapes: " + hasShapes);
            
            // Convert to response format - only for non-shape indicators
            List<Map<String, Object>> data = new ArrayList<>();
            if (!hasShapes) {
                data = dataPoints.stream()
                    .map(dp -> {
                        Map<String, Object> point = new HashMap<>();
                        point.put("time", dp.getTimestamp().getEpochSecond());
                        point.put("values", dp.getValues());
                        return point;
                    })
                    .collect(Collectors.toList());
            }
            
            // Create series-ready format for Lightweight Charts
            // Only generate series for indicators that don't return shapes
            Map<String, List<Map<String, Object>>> seriesData = new HashMap<>();
            if (!hasShapes && !dataPoints.isEmpty()) {
                // Get all value keys from first data point
                Map<String, BigDecimal> firstValues = dataPoints.get(0).getValues();
                
                for (String key : firstValues.keySet()) {
                    List<Map<String, Object>> seriesPoints = dataPoints.stream()
                        .map(dp -> {
                            Map<String, Object> seriesPoint = new HashMap<>();
                            seriesPoint.put("time", dp.getTimestamp().getEpochSecond());
                            BigDecimal value = dp.getValues().get(key);
                            seriesPoint.put("value", value != null ? value : BigDecimal.ZERO);
                            return seriesPoint;
                        })
                        .collect(Collectors.toList());
                    seriesData.put(key, seriesPoints);
                }
            }
            
            // Get visualization metadata
            Map<String, IndicatorMetadata> metadata = indicatorService.getVisualizationMetadata(
                id,
                request.getParams() != null ? request.getParams() : new HashMap<>()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("indicatorId", id);
            response.put("symbol", request.getSymbol());
            response.put("interval", request.getInterval());
            response.put("metadata", metadata);
            
            // For shape-based indicators (like order blocks), only include shapes
            if (hasShapes && !uniqueShapesByType.isEmpty()) {
                response.put("count", dataPoints.size());
                response.put("supportsShapes", true);
                response.put("shapes", uniqueShapesByType);
                
                // Add shape type summary
                Map<String, Integer> shapeSummary = new HashMap<>();
                for (Map.Entry<String, List<Map<String, Object>>> entry : uniqueShapesByType.entrySet()) {
                    shapeSummary.put(entry.getKey(), entry.getValue().size());
                }
                response.put("shapesSummary", shapeSummary);
            } else {
                // For standard indicators (like SMA, RSI, etc.), include data and series
                response.put("count", data.size());
                response.put("data", data); // Original format (backward compatibility)
                response.put("series", seriesData); // Lightweight Charts optimized format
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
    
    // DTOs
    
    public static class IndicatorListItem {
        private String id;
        private String name;
        private String description;
        private String category;
        private String categoryDisplayName;
        
        public IndicatorListItem(String id, String name, String description, 
                                String category, String categoryDisplayName) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.categoryDisplayName = categoryDisplayName;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public String getCategoryDisplayName() { return categoryDisplayName; }
    }
    
    public static class IndicatorDetails {
        private String id;
        private String name;
        private String description;
        private String category;
        private String categoryDisplayName;
        private Map<String, ParameterInfo> parameters;
        private Map<String, IndicatorMetadata> visualizationMetadata;
        private int minRequiredCandles;
        
        public IndicatorDetails(String id, String name, String description,
                              String category, String categoryDisplayName,
                              Map<String, ParameterInfo> parameters,
                              Map<String, IndicatorMetadata> visualizationMetadata,
                              int minRequiredCandles) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.categoryDisplayName = categoryDisplayName;
            this.parameters = parameters;
            this.visualizationMetadata = visualizationMetadata;
            this.minRequiredCandles = minRequiredCandles;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public String getCategoryDisplayName() { return categoryDisplayName; }
        public Map<String, ParameterInfo> getParameters() { return parameters; }
        public Map<String, IndicatorMetadata> getVisualizationMetadata() { return visualizationMetadata; }
        public int getMinRequiredCandles() { return minRequiredCandles; }
    }
    
    public static class ParameterInfo {
        private String name;
        private String displayName;
        private String description;
        private String type;
        private Object defaultValue;
        private Object minValue;
        private Object maxValue;
        private boolean required;
        
        public ParameterInfo(String name, String displayName, String description,
                           String type, Object defaultValue, Object minValue,
                           Object maxValue, boolean required) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.type = type;
            this.defaultValue = defaultValue;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.required = required;
        }
        
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getType() { return type; }
        public Object getDefaultValue() { return defaultValue; }
        public Object getMinValue() { return minValue; }
        public Object getMaxValue() { return maxValue; }
        public boolean isRequired() { return required; }
    }
    
    public static class CalculateRequest {
        private String provider;
        private String symbol;
        private String interval;
        private Map<String, Object> params;
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }
    
    public static class HistoricalRequest extends CalculateRequest {
        private Integer count;
        
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }
}

