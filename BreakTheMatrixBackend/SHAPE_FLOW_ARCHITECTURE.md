# Shape Flow Architecture

## How Shapes Travel from Indicator to REST API

### Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. INDICATOR (OrderBlockIndicator)                              │
│    Creates BoxShape objects and converts them to Maps           │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
        List<BoxShape> boxShapes = new ArrayList<>();
        boxShapes.add(BoxShape.builder()
            .time1(startTime)
            .time2(endTime)
            .price1(topPrice)
            .price2(bottomPrice)
            .color("rgba(0, 255, 0, 0.2)")
            .borderColor("#00FF00")
            .label("Bullish OB")
            .build());
        
        // Convert to Maps
        List<Map<String, Object>> boxes = boxShapes.stream()
            .map(BoxShape::toMap)
            .collect(Collectors.toList());
        
        // Return in progressive calculation
        Map<String, Object> output = new HashMap<>();
        output.put("values", values);           // Indicator values
        output.put("state", state);             // Internal state
        output.put("boxes", boxes);             // Shape data ✨
        return output;
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. INDICATOR SERVICE (IndicatorService.calculateHistorical)     │
│    Extracts shapes from progressive calculation result           │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
        Map<String, Object> progressiveResult = 
            indicator.calculateProgressive(subset, params, previousState);
        
        // Extract values
        Map<String, BigDecimal> values = progressiveResult.get("values");
        
        // Extract state for next iteration
        previousState = progressiveResult.get("state");
        
        // Extract EVERYTHING ELSE into additionalData
        Map<String, Object> additionalData = new HashMap<>();
        for (Map.Entry<String, Object> entry : progressiveResult.entrySet()) {
            if (!entry.getKey().equals("values") && 
                !entry.getKey().equals("state")) {
                additionalData.put(entry.getKey(), entry.getValue());
            }
        }
        // Now additionalData contains: { "boxes": [...] }
        
        // Store in IndicatorDataPoint
        dataPoints.add(new IndicatorDataPoint(
            timestamp,
            values,
            candle,
            additionalData  // ✨ Shapes are here!
        ));
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. INDICATOR CONTROLLER (IndicatorController.getHistoricalData) │
│    Collects shapes from all data points and categorizes them    │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
        // STEP 1: Collect all shapes from data points
        Map<String, List<Map<String, Object>>> shapesByType = new HashMap<>();
        boolean hasShapes = false;
        
        for (IndicatorDataPoint dp : dataPoints) {
            if (dp.getAdditionalData() != null) {
                Map<String, Object> additionalData = dp.getAdditionalData();
                
                // Extract boxes
                if (additionalData.containsKey("boxes")) {
                    hasShapes = true;
                    List<Map<String, Object>> boxes = 
                        (List<Map<String, Object>>) additionalData.get("boxes");
                    shapesByType.computeIfAbsent("boxes", k -> new ArrayList<>())
                                .addAll(boxes);
                }
                
                // Extract lines
                if (additionalData.containsKey("lines")) {
                    hasShapes = true;
                    List<Map<String, Object>> lines = 
                        (List<Map<String, Object>>) additionalData.get("lines");
                    shapesByType.computeIfAbsent("lines", k -> new ArrayList<>())
                                .addAll(lines);
                }
                
                // Same for markers, arrows...
            }
        }
        
        // STEP 2: Deduplicate shapes (same shape may appear in multiple data points)
        Map<String, List<Map<String, Object>>> uniqueShapesByType = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : shapesByType.entrySet()) {
            String shapeType = entry.getKey();
            List<Map<String, Object>> shapes = entry.getValue();
            
            if ("boxes".equals(shapeType)) {
                // Deduplicate boxes by time1, price1, price2
                List<Map<String, Object>> uniqueShapes = shapes.stream()
                    .collect(Collectors.toMap(
                        box -> box.get("time1") + "_" + 
                               box.get("price1") + "_" + 
                               box.get("price2"),
                        box -> box,
                        (existing, replacement) -> replacement
                    ))
                    .values().stream()
                    .collect(Collectors.toList());
                uniqueShapesByType.put(shapeType, uniqueShapes);
            }
            // Similar deduplication for lines, markers, arrows...
        }
        
        // STEP 3: Build response
        Map<String, Object> response = new HashMap<>();
        response.put("indicatorId", id);
        response.put("symbol", symbol);
        response.put("interval", interval);
        response.put("metadata", metadata);
        
        if (hasShapes && !uniqueShapesByType.isEmpty()) {
            // Shape-based indicator response
            response.put("count", dataPoints.size());
            response.put("supportsShapes", true);
            response.put("shapes", uniqueShapesByType);  // ✨ Final shapes!
            
            // Add summary
            Map<String, Integer> shapeSummary = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : 
                    uniqueShapesByType.entrySet()) {
                shapeSummary.put(entry.getKey(), entry.getValue().size());
            }
            response.put("shapesSummary", shapeSummary);
        } else {
            // Standard indicator response (SMA, RSI, etc.)
            response.put("data", data);
            response.put("series", seriesData);
        }
        
        return ResponseEntity.ok(response);
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. FINAL API RESPONSE                                           │
│    JSON response sent to frontend                               │
└─────────────────────────────────────────────────────────────────┘
```

## Final API Response Format

### For Shape-Based Indicators (Order Blocks)

```json
{
  "indicatorId": "orderblock",
  "symbol": "BTCUSDT",
  "interval": "1m",
  "count": 200,
  "metadata": {
    "marketStructure": {
      "name": "marketStructure",
      "displayName": "Market Structure",
      "seriesType": "histogram",
      "config": {
        "color": "#2962FF",
        "baseValue": 0.5,
        "title": "Market Structure",
        "downColor": "#FF6D00"
      },
      "separatePane": true,
      "paneOrder": 1
    }
  },
  "supportsShapes": true,
  "shapes": {
    "boxes": [
      {
        "time1": 1759495199,
        "time2": 1759503119,
        "price1": 120163.495,
        "price2": 120120.0,
        "color": "rgba(0, 255, 0, 0.2)",
        "borderColor": "#00FF00",
        "label": "Bullish OB ✓"
      },
      {
        "time1": 1759498619,
        "time2": 1759505759,
        "price1": 120637.22,
        "price2": 120543.105,
        "color": "rgba(255, 0, 0, 0.2)",
        "borderColor": "#FF0000",
        "label": "Bearish OB"
      }
    ]
  },
  "shapesSummary": {
    "boxes": 2
  }
}
```

### For Standard Indicators (SMA, RSI)

```json
{
  "indicatorId": "sma",
  "symbol": "BTCUSDT",
  "interval": "1m",
  "count": 200,
  "metadata": { ... },
  "data": [
    {
      "time": 1759495199,
      "values": {
        "sma": 120300.5
      }
    }
  ],
  "series": {
    "sma": [
      { "time": 1759495199, "value": 120300.5 },
      { "time": 1759495259, "value": 120305.2 }
    ]
  }
}
```

## Key Points

1. **Type Safety at Creation**: `BoxShape`, `LineShape`, etc. provide type-safe shape creation
2. **Conversion to Maps**: Shapes are converted to Maps via `toMap()` method before returning from indicator
3. **Progressive Calculation**: Shapes are returned in the `calculateProgressive()` result alongside values and state
4. **Automatic Extraction**: `IndicatorService` automatically extracts anything that's not "values" or "state" into `additionalData`
5. **Shape Collection**: `IndicatorController` collects all shapes from all data points
6. **Deduplication**: Duplicate shapes are removed based on their unique properties
7. **Categorization**: Shapes are organized by type (boxes, lines, markers, arrows)
8. **Clean Response**: Shape-based indicators only return shapes (no data/series), standard indicators only return data/series (no shapes)

## Adding New Shape Types

To add a new shape type:

1. Create the shape class (e.g., `TextShape.java`) implementing `Shape` interface
2. In indicator's `calculateProgressive()`, create instances and add them to output:
   ```java
   output.put("texts", textShapes.stream()
       .map(TextShape::toMap)
       .collect(Collectors.toList()));
   ```
3. In `IndicatorController`, add extraction logic:
   ```java
   if (additionalData.containsKey("texts")) {
       hasShapes = true;
       List<Map<String, Object>> texts = 
           (List<Map<String, Object>>) additionalData.get("texts");
       shapesByType.computeIfAbsent("texts", k -> new ArrayList<>())
                   .addAll(texts);
   }
   ```
4. Add deduplication logic if needed
5. Done! The shape will automatically appear in the API response

## Frontend Integration

The frontend can now easily integrate shapes:

```javascript
// Fetch indicator data
const response = await fetch('/api/indicators/orderblock/historical', {
  method: 'POST',
  body: JSON.stringify({
    symbol: 'BTCUSDT',
    interval: '1m',
    count: 200
  })
});

const data = await response.json();

if (data.supportsShapes && data.shapes) {
  // Render boxes
  if (data.shapes.boxes) {
    data.shapes.boxes.forEach(box => {
      chart.addBox({
        time1: box.time1,
        time2: box.time2,
        price1: box.price1,
        price2: box.price2,
        fillColor: box.color,
        borderColor: box.borderColor,
        text: box.label
      });
    });
  }
  
  // Render markers
  if (data.shapes.markers) {
    data.shapes.markers.forEach(marker => {
      chart.addMarker({
        time: marker.time,
        price: marker.price,
        shape: marker.shape,
        color: marker.color,
        text: marker.text
      });
    });
  }
}
```

