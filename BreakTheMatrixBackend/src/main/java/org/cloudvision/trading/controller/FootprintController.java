package org.cloudvision.trading.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.model.*;
import org.cloudvision.trading.service.FootprintCandleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for Footprint Candle data
 */
@RestController
@RequestMapping("/api/footprint")
@Tag(name = "Footprint Candles", description = "Volume profile and footprint candle data")
@CrossOrigin(origins = "*")
public class FootprintController {
    
    private final FootprintCandleService footprintService;
    
    public FootprintController(FootprintCandleService footprintService) {
        this.footprintService = footprintService;
    }
    
    @GetMapping("/historical")
    @Operation(summary = "Get historical footprint candles", description = "Retrieve historical footprint candles with volume profile data")
    public ResponseEntity<?> getHistoricalCandles(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            List<FootprintCandle> candles = footprintService.getHistoricalCandles(symbol, timeInterval, limit);
            
            if (candles.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "No footprint data available yet. Trades need to be collected first.",
                    "candles", new ArrayList<>()
                ));
            }
            
            List<Map<String, Object>> candlesData = candles.stream()
                    .map(this::serializeFootprintCandle)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "symbol", symbol,
                "interval", interval,
                "count", candlesData.size(),
                "candles", candlesData
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @GetMapping("/current")
    @Operation(summary = "Get current (incomplete) footprint candle", description = "Get the current candle being built in real-time")
    public ResponseEntity<?> getCurrentCandle(
            @RequestParam String symbol,
            @RequestParam String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromString(interval);
            FootprintCandle candle = footprintService.getCurrentCandle(symbol, timeInterval);
            
            if (candle == null) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "No current candle data available",
                    "candle", (Object) null
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "symbol", symbol,
                "interval", interval,
                "candle", serializeFootprintCandle(candle)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/tick-size")
    @Operation(summary = "Set tick size for a symbol", description = "Configure the price grouping tick size (e.g., 0.01 for BTC)")
    public ResponseEntity<?> setTickSize(
            @RequestParam String symbol,
            @RequestParam String tickSize) {
        try {
            BigDecimal tick = new BigDecimal(tickSize);
            footprintService.setTickSize(symbol, tick);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tick size set for " + symbol,
                "symbol", symbol,
                "tickSize", tickSize
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @GetMapping("/info")
    @Operation(summary = "Get footprint candle information", description = "Get information about footprint candle capabilities")
    public ResponseEntity<?> getInfo() {
        return ResponseEntity.ok(Map.of(
            "description", "Footprint candles show volume traded at each price level",
            "features", List.of(
                "Volume profile (price ladder)",
                "Buy vs Sell volume per level",
                "Delta (buy - sell volume)",
                "Cumulative delta",
                "Point of Control (POC)",
                "Value Area High/Low"
            ),
            "websocket", Map.of(
                "endpoint", "/orderflow-ws",
                "subscribeMessage", Map.of(
                    "action", "subscribe",
                    "symbol", "BTCUSDT",
                    "types", List.of("AGGREGATE_TRADE")
                )
            ),
            "rest", Map.of(
                "baseUrl", "/api/footprint",
                "endpoints", List.of(
                    "/historical?symbol=BTCUSDT&interval=1m&limit=100",
                    "/current?symbol=BTCUSDT&interval=1m"
                )
            )
        ));
    }
    
    private Map<String, Object> serializeFootprintCandle(FootprintCandle candle) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("symbol", candle.getSymbol());
        data.put("openTime", candle.getOpenTime());
        data.put("closeTime", candle.getCloseTime());
        data.put("interval", candle.getInterval());
        
        // OHLC
        data.put("open", candle.getOpen());
        data.put("high", candle.getHigh());
        data.put("low", candle.getLow());
        data.put("close", candle.getClose());
        
        // Volume metrics
        data.put("totalVolume", candle.getTotalVolume());
        data.put("totalBuyVolume", candle.getTotalBuyVolume());
        data.put("totalSellVolume", candle.getTotalSellVolume());
        data.put("delta", candle.getDelta());
        data.put("cumulativeDelta", candle.getCumulativeDelta());
        data.put("numberOfTrades", candle.getNumberOfTrades());
        
        // Key levels
        data.put("pointOfControl", candle.getPointOfControl());
        data.put("valueAreaHigh", candle.getValueAreaHigh());
        data.put("valueAreaLow", candle.getValueAreaLow());
        
        // Volume profile (sorted by price descending)
        List<Map<String, Object>> profile = candle.getSortedVolumeProfile().stream()
                .map(entry -> {
                    FootprintCandle.PriceLevelVolume level = entry.getValue();
                    Map<String, Object> levelData = new HashMap<>();
                    levelData.put("price", level.getPrice());
                    levelData.put("buyVolume", level.getBuyVolume());
                    levelData.put("sellVolume", level.getSellVolume());
                    levelData.put("totalVolume", level.getTotalVolume());
                    levelData.put("delta", level.getDelta());
                    levelData.put("buyRatio", level.getBuyRatio());
                    levelData.put("tradeCount", level.getTradeCount());
                    return levelData;
                })
                .collect(Collectors.toList());
        
        data.put("volumeProfile", profile);
        
        return data;
    }
}

