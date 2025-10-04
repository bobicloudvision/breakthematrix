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
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
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
    private IndicatorInstanceManager indicatorManager;
    
    @Autowired
    private org.cloudvision.trading.service.CandlestickHistoryService historyService;
    
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
                indicators = indicatorManager.getIndicatorsByCategory(cat);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            // Return all indicators if no category specified
            indicators = indicatorManager.getAllIndicators();
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
            Indicator indicator = indicatorManager.getIndicator(id);
            
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
     * POST /api/indicators/calculate
     * 
     * Body: {
     *   "indicatorId": "sma",
     *   "provider": "Binance",
     *   "symbol": "BTCUSDT",
     *   "interval": "1m",
     *   "params": {
     *     "period": 20
     *   }
     * }
     */
    @Operation(
        summary = "Get current values for all active indicators",
        description = "Returns current values for ALL active indicators in the given context (provider/symbol/interval). " +
                     "Indicators must be activated first via /api/indicators/instances/activate"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Context parameters to retrieve current values for all active indicators",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "BTCUSDT 5m - All indicators",
                    description = "Get current values for all active indicators on BTCUSDT 5-minute chart",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m"
                    }
                    """
                ),
                @ExampleObject(
                    name = "ETHUSDT 1m - All indicators",
                    description = "Get current values for all active indicators on ETHUSDT 1-minute chart",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "ETHUSDT",
                      "interval": "1m"
                    }
                    """
                )
            }
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved current values for all active indicators",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Multiple Active Indicators",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "timestamp": "2025-10-04T10:35:00Z",
                      "indicatorCount": 2,
                      "indicators": [
                        {
                          "indicatorId": "sma",
                          "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
                          "params": { "period": 20 },
                          "timestamp": "2025-10-04T10:35:00Z",
                          "values": { "sma": "95234.56" },
                          "additionalData": {},
                          "updateCount": 150
                        },
                        {
                          "indicatorId": "volume",
                          "instanceKey": "Binance:BTCUSDT:5m:volume:1f2e3d",
                          "params": {},
                          "timestamp": "2025-10-04T10:35:00Z",
                          "values": { "volume": "1234567.89" },
                          "additionalData": { "color": "#26a69a" },
                          "updateCount": 150
                        }
                      ],
                      "fromActiveInstances": true
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No active indicator instances found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "error": "No active indicator instances found",
                      "message": "Please activate indicators first using POST /api/indicators/instances/activate",
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m"
                    }
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @PostMapping("/calculate")
    public ResponseEntity<?> calculateIndicator(@RequestBody CalculateRequest request) {
        try {
            // Get all active instances for this context
            List<IndicatorInstanceManager.IndicatorInstance> activeInstances = 
                indicatorManager.getInstancesForContext(
                    request.getProvider(),
                    request.getSymbol(),
                    request.getInterval()
                );
            
            if (activeInstances.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "No active indicator instances found",
                    "message", "Please activate indicators first using POST /api/indicators/instances/activate",
                    "provider", request.getProvider(),
                    "symbol", request.getSymbol(),
                    "interval", request.getInterval()
                ));
            }
            
            // Get the latest candle (shared across all indicators)
            org.cloudvision.trading.model.CandlestickData latestCandle = 
                historyService.getLatestCandlestick(
                    request.getProvider(),
                    request.getSymbol(),
                    request.getInterval()
                );
            
            // Calculate current values for all active indicators
            List<Map<String, Object>> indicatorsData = new ArrayList<>();
            
            for (IndicatorInstanceManager.IndicatorInstance instance : activeInstances) {
                // Get current values from the active instance (no recalculation!)
                IndicatorInstanceManager.IndicatorResult result = 
                    indicatorManager.updateWithCandle(instance.getInstanceKey(), latestCandle);
                
                Map<String, Object> indicatorData = new HashMap<>();
                indicatorData.put("indicatorId", instance.getIndicatorId());
                indicatorData.put("instanceKey", instance.getInstanceKey());
                indicatorData.put("params", instance.getParams());
                indicatorData.put("timestamp", result.getTimestamp());
                indicatorData.put("values", result.getValues());
                indicatorData.put("additionalData", result.getAdditionalData());
                indicatorData.put("updateCount", instance.getUpdateCount());
                
                indicatorsData.add(indicatorData);
            }
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("provider", request.getProvider());
            response.put("symbol", request.getSymbol());
            response.put("interval", request.getInterval());
            response.put("timestamp", latestCandle.getCloseTime());
            response.put("indicatorCount", indicatorsData.size());
            response.put("indicators", indicatorsData);
            response.put("fromActiveInstances", true);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace(); // Log the full stack trace
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of(
                "error", errorMessage
            ));
        }
    }
    
    /**
     * Get historical indicator data for charting
     * POST /api/indicators/historical
     * 
     * Body: {
     *   "indicatorId": "sma",
     *   "provider": "Binance",
     *   "symbol": "BTCUSDT",
     *   "interval": "1m",
     *   "count": 5000,
     *   "params": {
     *     "period": 20
     *   }
     * }
     */
    @Operation(
        summary = "Get historical indicator data",
        description = "Returns historical indicator values from active indicator instances for charting. " +
                     "If indicatorId is provided, returns data for that specific indicator. " +
                     "If indicatorId is omitted, returns data for ALL active indicators in the given context. " +
                     "Indicators must be activated first via /api/indicators/instances/activate"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Historical data request parameters. The indicatorId is optional - omit it to get data for all active indicators in the context.",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "All active indicators",
                    description = "Get historical data for ALL active indicators in the context (no indicatorId provided)",
                    value = """
                    {
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "count": 100
                    }
                    """
                ),
                @ExampleObject(
                    name = "Specific indicator - SMA(20)",
                    description = "Get 200 data points of SMA(20) for BTCUSDT 5-minute chart",
                    value = """
                    {
                      "indicatorId": "sma",
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "count": 5000,
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
                      "indicatorId": "sma",
                      "provider": "Binance",
                      "symbol": "ETHUSDT",
                      "interval": "1h",
                      "count": 5000,
                      "params": {
                        "period": 50,
                        "source": "close"
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "Volume - 500 points",
                    description = "Get 500 data points of volume for BTCUSDT 1-minute chart with custom colors",
                    value = """
                    {
                      "indicatorId": "volume",
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "1m",
                      "count": 5000,
                      "params": {
                        "bullishColor": "#00C853",
                        "bearishColor": "#D50000"
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
            description = "Successfully retrieved historical data from active instance",
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
                          "count": 5000,
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
                          "series": {
                            "sma": [
                              { "time": 1704067200, "value": "94500.00" },
                              { "time": 1704067500, "value": "94523.45" },
                              { "time": 1704067800, "value": "94550.12" }
                            ]
                          },
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
                        name = "Volume with Color-Coded Bars",
                        description = "Volume indicator returns color per bar based on candle direction (bullish/bearish)",
                        value = """
                        {
                          "indicatorId": "volume",
                          "symbol": "BTCUSDT",
                          "interval": "1m",
                          "count": 5000,
                          "data": [
                            {
                              "time": 1704067200,
                              "values": {
                                "volume": "1234.56"
                              },
                              "color": "#26a69a"
                            },
                            {
                              "time": 1704067260,
                              "values": {
                                "volume": "987.65"
                              },
                              "color": "#ef5350"
                            },
                            {
                              "time": 1704067320,
                              "values": {
                                "volume": "1567.89"
                              },
                              "color": "#26a69a"
                            }
                          ],
                          "series": {
                            "volume": [
                              { "time": 1704067200, "value": "1234.56", "color": "#26a69a" },
                              { "time": 1704067260, "value": "987.65", "color": "#ef5350" },
                              { "time": 1704067320, "value": "1567.89", "color": "#26a69a" }
                            ]
                          },
                          "metadata": {
                            "volume": {
                              "key": "volume",
                              "displayName": "Volume",
                              "visualType": "HISTOGRAM",
                              "color": "#26a69a",
                              "separatePane": true,
                              "paneOrder": 1
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
                          "count": 5000,
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
        @ApiResponse(
            responseCode = "404",
            description = "No active indicator instance found",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "error": "No active indicator instance found",
                      "message": "Please activate this indicator first using POST /api/indicators/instances/activate"
                    }
                    """
                )
            )
        )
    })
    @PostMapping("/historical")
    public ResponseEntity<?> getHistoricalData(@RequestBody HistoricalRequest request) {
        try {
            // Return data for ALL active indicators in this context
            return getHistoricalDataForAllActiveIndicators(request);
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }
    }
    
    /**
     * Get historical data for ALL active indicators in a given context
     */
    private ResponseEntity<?> getHistoricalDataForAllActiveIndicators(HistoricalRequest request) {
        try {
            int requestedCount = request.getCount() != null ? request.getCount() : 5000;
            
            // Get all active instances for this context
            List<IndicatorInstanceManager.IndicatorInstance> activeInstances =
                indicatorManager.getInstancesForContext(
                    request.getProvider(),
                    request.getSymbol(),
                    request.getInterval()
                );
            
            if (activeInstances.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "No active indicator instances found",
                    "message", "Please activate indicators first using POST /api/indicators/instances/activate",
                    "provider", request.getProvider(),
                    "symbol", request.getSymbol(),
                    "interval", request.getInterval()
                ));
            }
            
            // Process data for each active indicator
            // NOTE: We get stored results that were calculated in real-time, NOT recalculated!
            List<Map<String, Object>> indicatorsData = new ArrayList<>();
            
            for (IndicatorInstanceManager.IndicatorInstance instance : activeInstances) {
                String indicatorId = instance.getIndicatorId();
                String instanceKey = instance.getInstanceKey();
                
                // Get historical data from stored results (ensures consistency with real-time state)
                List<IndicatorInstanceManager.IndicatorResult> dataPoints = 
                    indicatorManager.getHistoricalData(instanceKey, requestedCount);
                
                // Collect shapes
                Map<String, List<Map<String, Object>>> shapesByType = new HashMap<>();
                boolean hasShapes = false;
                
                for (IndicatorInstanceManager.IndicatorResult dp : dataPoints) {
                    if (dp.getAdditionalData() != null) {
                        Map<String, List<Map<String, Object>>> shapesFromPoint = 
                            ShapeRegistry.extractShapes(dp.getAdditionalData());
                        
                        if (!shapesFromPoint.isEmpty()) {
                            hasShapes = true;
                            for (Map.Entry<String, List<Map<String, Object>>> entry : shapesFromPoint.entrySet()) {
                                shapesByType.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                          .addAll(entry.getValue());
                            }
                        }
                    }
                }
                
                // Deduplicate shapes
                Map<String, List<Map<String, Object>>> uniqueShapesByType = new HashMap<>();
                for (Map.Entry<String, List<Map<String, Object>>> entry : shapesByType.entrySet()) {
                    List<Map<String, Object>> uniqueShapes = ShapeRegistry.deduplicate(
                        entry.getKey(), 
                        entry.getValue()
                    );
                    uniqueShapesByType.put(entry.getKey(), uniqueShapes);
                }
                
                // Create series data
                Map<String, List<Map<String, Object>>> seriesData = new HashMap<>();
                if (!dataPoints.isEmpty()) {
                    Map<String, BigDecimal> firstValues = dataPoints.get(0).getValues();
                    
                    for (String key : firstValues.keySet()) {
                        List<Map<String, Object>> seriesPoints = dataPoints.stream()
                            .map(dp -> {
                                Map<String, Object> seriesPoint = new HashMap<>();
                                seriesPoint.put("time", dp.getTimestamp().getEpochSecond());
                                BigDecimal value = dp.getValues().get(key);
                                seriesPoint.put("value", value != null ? value : BigDecimal.ZERO);
                                
                                if (dp.getAdditionalData() != null && !dp.getAdditionalData().isEmpty()) {
                                    Map<String, Object> additionalData = new HashMap<>(dp.getAdditionalData());
                                    ShapeRegistry.extractShapes(additionalData);
                                    if (!additionalData.isEmpty()) {
                                        seriesPoint.putAll(additionalData);
                                    }
                                }
                                
                                return seriesPoint;
                            })
                            .collect(Collectors.toList());
                        seriesData.put(key, seriesPoints);
                    }
                }
                
                // Get visualization metadata
                Map<String, IndicatorMetadata> metadata = indicatorManager.getVisualizationMetadata(
                    indicatorId,
                    instance.getParams()
                );
                
                // Build response for this indicator
                Map<String, Object> indicatorResponse = new HashMap<>();
                indicatorResponse.put("indicatorId", indicatorId);
                indicatorResponse.put("instanceKey", instanceKey);
                indicatorResponse.put("params", instance.getParams());
                indicatorResponse.put("count", dataPoints.size());
                indicatorResponse.put("metadata", metadata);
                indicatorResponse.put("updateCount", instance.getUpdateCount());
                
                if (metadata != null && !metadata.isEmpty()) {
                    indicatorResponse.put("series", seriesData);
                }
                
                if (hasShapes && !uniqueShapesByType.isEmpty()) {
                    indicatorResponse.put("supportsShapes", true);
                    indicatorResponse.put("shapes", uniqueShapesByType);
                    
                    Map<String, Integer> shapeSummary = new HashMap<>();
                    for (Map.Entry<String, List<Map<String, Object>>> entry : uniqueShapesByType.entrySet()) {
                        shapeSummary.put(entry.getKey(), entry.getValue().size());
                    }
                    indicatorResponse.put("shapesSummary", shapeSummary);
                }
                
                indicatorsData.add(indicatorResponse);
            }
            
            // Build final response
            Map<String, Object> response = new HashMap<>();
            response.put("provider", request.getProvider());
            response.put("symbol", request.getSymbol());
            response.put("interval", request.getInterval());
            response.put("requestedCount", requestedCount);
            response.put("indicatorCount", indicatorsData.size());
            response.put("indicators", indicatorsData);
            response.put("fromActiveInstances", true);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
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
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
    }
    
    public static class HistoricalRequest {
        private String provider;
        private String symbol;
        private String interval;
        private Integer count;
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }
}

