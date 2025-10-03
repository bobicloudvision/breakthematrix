# Visualization Shape Classes

## Overview

The shape classes provide a type-safe, clean way to create visualization elements for indicators. Instead of using raw `Map<String, Object>`, you can now use dedicated classes for different shape types.

## Available Shape Classes

### 1. BoxShape (Rectangles/Zones)

Used for support/resistance zones, order blocks, supply/demand areas, etc.

```java
import org.cloudvision.trading.bot.visualization.BoxShape;

BoxShape box = BoxShape.builder()
    .time1(1759495199)                    // Start time (Unix timestamp)
    .time2(1759503119)                    // End time (Unix timestamp)
    .price1(new BigDecimal("120163.495")) // Top price
    .price2(new BigDecimal("120120.0"))   // Bottom price
    .color("rgba(0, 255, 0, 0.2)")        // Fill color
    .borderColor("#00FF00")               // Border color
    .label("Bullish OB")                  // Optional label
    .build();

// Convert to Map for API response
Map<String, Object> boxMap = box.toMap();
```

### 2. LineShape (Trend Lines)

Used for trend lines, support/resistance lines, channel lines, etc.

```java
import org.cloudvision.trading.bot.visualization.LineShape;

LineShape line = LineShape.builder()
    .time1(1759495199)                    // Start time
    .price1(new BigDecimal("120000"))     // Start price
    .time2(1759503119)                    // End time
    .price2(new BigDecimal("121000"))     // End price
    .color("#FF0000")                     // Line color
    .lineWidth(2)                         // Width in pixels
    .lineStyle("dashed")                  // "solid", "dashed", "dotted"
    .label("Resistance")                  // Optional label
    .build();

Map<String, Object> lineMap = line.toMap();
```

### 3. MarkerShape (Points)

Used for entry/exit signals, pivots, important levels, etc.

```java
import org.cloudvision.trading.bot.visualization.MarkerShape;

MarkerShape marker = MarkerShape.builder()
    .time(1759495199)                     // Time
    .price(new BigDecimal("120500"))      // Price
    .shape("circle")                      // "circle", "square", "triangle", "diamond"
    .color("#00FF00")                     // Marker color
    .position("below")                    // "above" or "below" the bar
    .text("BUY")                          // Optional text
    .size(10)                             // Size in pixels
    .build();

Map<String, Object> markerMap = marker.toMap();
```

### 4. ArrowShape (Directional Signals)

Used for buy/sell arrows, trend direction, breakout signals, etc.

```java
import org.cloudvision.trading.bot.visualization.ArrowShape;

ArrowShape arrow = ArrowShape.builder()
    .time(1759495199)                     // Time
    .price(new BigDecimal("120500"))      // Price
    .direction("up")                      // "up" or "down"
    .color("#00FF00")                     // Arrow color
    .text("BUY")                          // Optional text
    .size(12)                             // Size in pixels
    .build();

Map<String, Object> arrowMap = arrow.toMap();
```

## Using Shapes in Indicators

### Example: Creating an indicator with box visualization

```java
import org.cloudvision.trading.bot.visualization.BoxShape;
import java.util.*;
import java.util.stream.Collectors;

public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                Map<String, Object> params,
                                                Object previousState) {
    // Your indicator logic here
    // ...
    
    // Create BoxShape objects
    List<BoxShape> boxShapes = new ArrayList<>();
    
    BoxShape box1 = BoxShape.builder()
        .time1(candles.get(0).getCloseTime().getEpochSecond())
        .time2(candles.get(candles.size() - 1).getCloseTime().getEpochSecond())
        .price1(highPrice)
        .price2(lowPrice)
        .color("rgba(0, 255, 0, 0.2)")
        .borderColor("#00FF00")
        .label("Support Zone")
        .build();
    
    boxShapes.add(box1);
    
    // Convert to Map for API response
    List<Map<String, Object>> boxes = boxShapes.stream()
        .map(BoxShape::toMap)
        .collect(Collectors.toList());
    
    // Return in the expected format
    Map<String, Object> result = new HashMap<>();
    result.put("values", indicatorValues);
    result.put("state", state);
    result.put("boxes", boxes);  // or "lines", "markers", "arrows"
    
    return result;
}
```

### Example: Mixed shapes (boxes + markers)

```java
public Map<String, Object> calculateProgressive(List<CandlestickData> candles, 
                                                Map<String, Object> params,
                                                Object previousState) {
    // Create different shape types
    List<BoxShape> boxes = new ArrayList<>();
    List<MarkerShape> markers = new ArrayList<>();
    
    // Add support zone
    boxes.add(BoxShape.builder()
        .time1(startTime)
        .time2(endTime)
        .price1(highPrice)
        .price2(lowPrice)
        .color("rgba(0, 255, 0, 0.2)")
        .borderColor("#00FF00")
        .label("Support")
        .build());
    
    // Add entry signal
    markers.add(MarkerShape.builder()
        .time(signalTime)
        .price(entryPrice)
        .shape("triangle")
        .color("#00FF00")
        .position("below")
        .text("ENTRY")
        .size(12)
        .build());
    
    // Convert to Maps and return
    Map<String, Object> result = new HashMap<>();
    result.put("values", indicatorValues);
    result.put("state", state);
    result.put("boxes", boxes.stream().map(BoxShape::toMap).collect(Collectors.toList()));
    result.put("markers", markers.stream().map(MarkerShape::toMap).collect(Collectors.toList()));
    
    return result;
}
```

## Benefits

1. **Type Safety**: Compile-time checking of shape properties
2. **Clean API**: Builder pattern makes code readable
3. **IDE Support**: Auto-completion for all shape properties
4. **Consistency**: Standardized format across all indicators
5. **Easy Refactoring**: Changes to shape structure are centralized
6. **Documentation**: Self-documenting code with clear property names

## API Response Format

When shapes are returned from the API, they are automatically categorized:

```json
{
  "indicatorId": "orderblock",
  "symbol": "BTCUSDT",
  "interval": "1m",
  "count": 200,
  "metadata": { ... },
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
        "label": "Bullish OB"
      }
    ],
    "markers": [
      {
        "time": 1759495199,
        "price": 120500.0,
        "shape": "circle",
        "color": "#00FF00",
        "position": "below",
        "text": "BUY",
        "size": 10
      }
    ]
  },
  "shapesSummary": {
    "boxes": 5,
    "markers": 2
  }
}
```

## Future Enhancements

Potential additional shape types:
- `TextShape`: Floating text annotations
- `ZoneShape`: More complex multi-point zones
- `PathShape`: Custom drawn paths
- `IconShape`: Custom SVG icons

