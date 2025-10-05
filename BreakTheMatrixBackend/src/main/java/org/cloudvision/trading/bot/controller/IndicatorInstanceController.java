package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager.IndicatorInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Active Indicator Instance Management
 * 
 * Provides endpoints to:
 * - Activate/deactivate indicators for specific symbols/timeframes
 * - Query active indicator instances
 * - Get statistics about active indicators
 * - Manage indicator lifecycle across multiple trading contexts
 */
@RestController
@RequestMapping("/api/indicators/instances")
@CrossOrigin(origins = "*")
@Tag(name = "Indicator Instances", description = "Manage active indicator instances across multiple symbols and timeframes")
public class IndicatorInstanceController {
    
    @Autowired
    private IndicatorInstanceManager instanceManager;
    
    /**
     * Activate an indicator for a specific symbol/provider/interval
     * POST /api/indicators/instances/activate
     * 
     * Body: {
     *   "indicatorId": "sma",
     *   "provider": "Binance",
     *   "symbol": "BTCUSDT",
     *   "interval": "5m",
     *   "params": {
     *     "period": 20
     *   }
     * }
     */
    @Operation(
        summary = "Activate an indicator instance",
        description = "Creates and activates an indicator for a specific symbol/provider/interval combination. " +
                     "The indicator will be initialized with historical data and will be ready to receive updates."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Indicator activation request. The indicator will be automatically initialized with maximum available historical data (up to 5000 candles).",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "SMA(20) for BTCUSDT",
                    description = "Activates SMA(20) for BTCUSDT 5m chart with full available history",
                    value = """
                    {
                      "indicatorId": "sma",
                      "provider": "Binance",
                      "symbol": "BTCUSDT",
                      "interval": "5m",
                      "params": {
                        "period": 20,
                        "color": "#2962FF"
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "SMA(50) for ETHUSDT",
                    description = "Activates SMA(50) for ETHUSDT 1m chart with full available history",
                    value = """
                    {
                      "indicatorId": "sma",
                      "provider": "Binance",
                      "symbol": "ETHUSDT",
                      "interval": "1m",
                      "params": {
                        "period": 50
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "Volume indicator",
                    description = "Activates Volume indicator with full available history",
                    value = """
                    {
                      "indicatorId": "volume",
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
            description = "Indicator activated successfully with historical data loaded",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "Default history response",
                        value = """
                        {
                          "success": true,
                          "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
                          "message": "Indicator activated successfully",
                          "details": {
                            "indicatorId": "sma",
                            "provider": "Binance",
                            "symbol": "BTCUSDT",
                            "interval": "5m",
                            "params": {
                              "period": 20
                            },
                            "createdAt": "2025-10-04T10:30:00Z",
                            "initializedWithCandles": 20,
                            "historicalResultsStored": 4980
                          }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Full history response",
                        value = """
                        {
                          "success": true,
                          "instanceKey": "Binance:ETHUSDT:1m:sma:a1b2c3",
                          "message": "Indicator activated successfully",
                          "details": {
                            "indicatorId": "sma",
                            "provider": "Binance",
                            "symbol": "ETHUSDT",
                            "interval": "1m",
                            "params": {
                              "period": 50
                            },
                            "createdAt": "2025-10-04T10:30:00Z",
                            "initializedWithCandles": 50,
                            "historicalResultsStored": 4950
                          }
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or indicator not found"
        )
    })
    @PostMapping("/activate")
    public ResponseEntity<?> activateIndicator(@RequestBody ActivateRequest request) {
        try {
            String instanceKey = instanceManager.activateIndicator(
                request.getIndicatorId(),
                request.getProvider(),
                request.getSymbol(),
                request.getInterval(),
                request.getParams() != null ? request.getParams() : new HashMap<>()
            );
            
            IndicatorInstance instance = instanceManager.getInstance(instanceKey);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("instanceKey", instanceKey);
            response.put("message", "Indicator activated successfully");
            
            Map<String, Object> details = new HashMap<>();
            details.put("indicatorId", instance.getIndicatorId());
            details.put("provider", instance.getProvider());
            details.put("symbol", instance.getSymbol());
            details.put("interval", instance.getInterval());
            details.put("params", instance.getParams());
            details.put("createdAt", instance.getCreatedAt());
            details.put("initializedWithCandles", instance.getState().getCandleCount());
            details.put("historicalResultsStored", instance.getHistoricalResultCount());
            
            response.put("details", details);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Update parameters for an existing indicator instance
     * PATCH /api/indicators/instances/{instanceKey}
     */
    @Operation(
        summary = "Update indicator instance parameters",
        description = "Updates the parameters of an active indicator instance and recalculates all historical data with the new parameters. " +
                     "Returns a new instance key that reflects the updated parameters. " +
                     "The old instance will be deactivated and a new one created with the new parameters."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "New parameters to apply to the indicator. The indicator will be reinitialized and all historical data recalculated.",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "Update SMA period",
                    description = "Changes SMA period from 20 to 50",
                    value = """
                    {
                      "params": {
                        "period": 50,
                        "color": "#2962FF"
                      }
                    }
                    """
                ),
                @ExampleObject(
                    name = "Update RSI settings",
                    description = "Updates RSI period and overbought/oversold levels",
                    value = """
                    {
                      "params": {
                        "period": 14,
                        "overbought": 70,
                        "oversold": 30
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
            description = "Indicator parameters updated successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "success": true,
                      "message": "Indicator parameters updated successfully",
                      "oldInstanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
                      "newInstanceKey": "Binance:BTCUSDT:5m:sma:f3e2d1",
                      "details": {
                        "indicatorId": "sma",
                        "provider": "Binance",
                        "symbol": "BTCUSDT",
                        "interval": "5m",
                        "oldParams": {
                          "period": 20
                        },
                        "newParams": {
                          "period": 50,
                          "color": "#2962FF"
                        },
                        "createdAt": "2025-10-05T10:30:00Z",
                        "initializedWithCandles": 50,
                        "historicalResultsStored": 4950
                      }
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Instance not found"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid parameters"
        )
    })
    @PatchMapping("/{instanceKey}")
    public ResponseEntity<?> updateIndicatorParams(
            @PathVariable @Parameter(description = "Current instance key") String instanceKey,
            @RequestBody UpdateParamsRequest request) {
        
        try {
            // Get old instance for comparison
            IndicatorInstance oldInstance = instanceManager.getInstance(instanceKey);
            
            if (oldInstance == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "Instance not found: " + instanceKey
                ));
            }
            
            Map<String, Object> oldParams = oldInstance.getParams();
            
            // Update parameters and get new instance key
            String newInstanceKey = instanceManager.updateIndicatorParams(
                instanceKey,
                request.getParams() != null ? request.getParams() : new HashMap<>()
            );
            
            // Get new instance
            IndicatorInstance newInstance = instanceManager.getInstance(newInstanceKey);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Indicator parameters updated successfully");
            response.put("oldInstanceKey", instanceKey);
            response.put("newInstanceKey", newInstanceKey);
            
            Map<String, Object> details = new HashMap<>();
            details.put("indicatorId", newInstance.getIndicatorId());
            details.put("provider", newInstance.getProvider());
            details.put("symbol", newInstance.getSymbol());
            details.put("interval", newInstance.getInterval());
            details.put("oldParams", oldParams);
            details.put("newParams", newInstance.getParams());
            details.put("createdAt", newInstance.getCreatedAt());
            details.put("initializedWithCandles", newInstance.getState().getCandleCount());
            details.put("historicalResultsStored", newInstance.getHistoricalResultCount());
            
            response.put("details", details);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Deactivate an indicator instance
     * DELETE /api/indicators/instances/{instanceKey}
     */
    @Operation(
        summary = "Deactivate an indicator instance",
        description = "Removes an active indicator instance. The indicator will stop receiving updates."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Indicator deactivated successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Instance not found"
        )
    })
    @DeleteMapping("/{instanceKey}")
    public ResponseEntity<?> deactivateIndicator(
            @PathVariable @Parameter(description = "Instance key from activation") String instanceKey) {
        
        boolean deactivated = instanceManager.deactivateIndicator(instanceKey);
        
        if (deactivated) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Indicator deactivated successfully",
                "instanceKey", instanceKey
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Instance not found: " + instanceKey
            ));
        }
    }
    
    /**
     * Deactivate all indicators for a specific context
     * DELETE /api/indicators/instances/context
     */
    @Operation(
        summary = "Deactivate all indicators for a context",
        description = "Removes all active indicators for a specific provider/symbol/interval combination."
    )
    @DeleteMapping("/context")
    public ResponseEntity<?> deactivateContext(
            @RequestParam String provider,
            @RequestParam String symbol,
            @RequestParam String interval) {
        
        int deactivated = instanceManager.deactivateAllForContext(provider, symbol, interval);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Deactivated " + deactivated + " indicator(s)",
            "count", deactivated,
            "context", Map.of(
                "provider", provider,
                "symbol", symbol,
                "interval", interval
            )
        ));
    }
    
    /**
     * Get all active indicator instances
     * GET /api/indicators/instances
     */
    @Operation(
        summary = "List all active indicator instances",
        description = "Returns a list of all currently active indicator instances. " +
                     "Supports optional filtering by context (provider/symbol/interval) or by symbol only. " +
                     "Query Parameters: " +
                     "- provider, symbol, interval: Filter by specific trading context (all three required together) " +
                     "- symbol: Filter by symbol only " +
                     "- No params: Returns all active instances across all contexts"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved active instances",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "success": true,
                      "totalActive": 3,
                      "instances": [
                        {
                          "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
                          "indicatorId": "sma",
                          "provider": "Binance",
                          "symbol": "BTCUSDT",
                          "interval": "5m",
                          "params": {"period": 20},
                          "createdAt": "2025-10-04T10:30:00Z",
                          "lastUpdate": "2025-10-04T10:35:00Z",
                          "updateCount": 150
                        },
                        {
                          "instanceKey": "Binance:BTCUSDT:5m:volume:default",
                          "indicatorId": "volume",
                          "provider": "Binance",
                          "symbol": "BTCUSDT",
                          "interval": "5m",
                          "params": {},
                          "createdAt": "2025-10-04T10:30:00Z",
                          "lastUpdate": "2025-10-04T10:35:00Z",
                          "updateCount": 150
                        }
                      ]
                    }
                    """
                )
            )
        )
    })
    @GetMapping
    public ResponseEntity<?> getAllInstances(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String interval) {
        
        List<IndicatorInstance> instances;
        
        // Filter by query parameters
        if (provider != null && symbol != null && interval != null) {
            instances = instanceManager.getInstancesForContext(provider, symbol, interval);
        } else if (symbol != null) {
            instances = instanceManager.getInstancesBySymbol(symbol);
        } else {
            instances = instanceManager.getAllInstances();
        }
        
        List<Map<String, Object>> instanceData = instances.stream()
            .map(this::instanceToMap)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "totalActive", instances.size(),
            "instances", instanceData
        ));
    }
    
    /**
     * Get a specific indicator instance
     * GET /api/indicators/instances/{instanceKey}
     */
    @Operation(
        summary = "Get indicator instance details",
        description = "Retrieves detailed information about a specific active indicator instance."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved instance details"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Instance not found"
        )
    })
    @GetMapping("/{instanceKey}")
    public ResponseEntity<?> getInstance(@PathVariable String instanceKey) {
        IndicatorInstance instance = instanceManager.getInstance(instanceKey);
        
        if (instance == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Instance not found: " + instanceKey
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "instance", instanceToMap(instance)
        ));
    }
    
    /**
     * Get statistics about active indicators
     * GET /api/indicators/instances/stats
     */
    @Operation(
        summary = "Get indicator statistics",
        description = "Returns aggregated statistics about all active indicator instances."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved statistics",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                      "success": true,
                      "stats": {
                        "totalActive": 5,
                        "uniqueContexts": 2,
                        "byIndicator": {
                          "sma": 2,
                          "volume": 2,
                          "rsi": 1
                        },
                        "bySymbol": {
                          "BTCUSDT": 3,
                          "ETHUSDT": 2
                        },
                        "byInterval": {
                          "1m": 2,
                          "5m": 2,
                          "15m": 1
                        }
                      }
                    }
                    """
                )
            )
        )
    })
    @GetMapping("/stats")
    public ResponseEntity<?> getStatistics() {
        Map<String, Object> stats = instanceManager.getStatistics();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "stats", stats
        ));
    }
    
    /**
     * Clear all active indicators
     * DELETE /api/indicators/instances/all
     */
    @Operation(
        summary = "Clear all active indicators",
        description = "Deactivates all active indicator instances. Use with caution!"
    )
    @DeleteMapping("/all")
    public ResponseEntity<?> clearAll() {
        int count = instanceManager.getActiveCount();
        instanceManager.clearAll();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Cleared all active indicators",
            "count", count
        ));
    }
    
    // ============================================================
    // Helper Methods
    // ============================================================
    
    private Map<String, Object> instanceToMap(IndicatorInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("instanceKey", instance.getInstanceKey());
        map.put("indicatorId", instance.getIndicatorId());
        map.put("provider", instance.getProvider());
        map.put("symbol", instance.getSymbol());
        map.put("interval", instance.getInterval());
        map.put("params", instance.getParams());
        map.put("createdAt", instance.getCreatedAt());
        map.put("lastUpdate", instance.getLastUpdate());
        map.put("updateCount", instance.getUpdateCount());
        return map;
    }
    
    // ============================================================
    // DTOs
    // ============================================================
    
    public static class ActivateRequest {
        private String indicatorId;
        private String provider;
        private String symbol;
        private String interval;
        private Map<String, Object> params;
        
        public String getIndicatorId() { return indicatorId; }
        public void setIndicatorId(String indicatorId) { this.indicatorId = indicatorId; }
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
        
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }
    
    public static class UpdateParamsRequest {
        private Map<String, Object> params;
        
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }
}

