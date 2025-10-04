# Fill Between Usage Guide

This guide shows how to fill the area between the price line and an indicator line (similar to Pine Script's `fill()` function).

## Overview

The `FillBetweenPrimitive` creates a filled area between the main price series (candlesticks) and any indicator line, with support for dynamic coloring based on conditions.

## Basic Usage

### 1. In Your Backend API Response

If you want your indicator to include a fill, structure your API response like this:

```json
{
  "metadata": {
    "trailing_stop": {
      "seriesType": "line",
      "displayName": "Trailing Stop",
      "config": {
        "color": "#FF6B6B",
        "lineWidth": 2
      }
    }
  },
  "series": {
    "trailing_stop": [
      { "time": 1234567890, "value": 50100 },
      { "time": 1234567900, "value": 50150 },
      ...
    ]
  },
  "shapes": {
    "fill": {
      "enabled": true,
      "colorMode": "dynamic",
      "upFillColor": "rgba(76, 175, 80, 0.15)",
      "downFillColor": "rgba(239, 83, 80, 0.15)",
      "neutralFillColor": "rgba(158, 158, 158, 0.1)"
    }
  }
}
```

### 2. Manual Usage in Chart Code

```javascript
import { FillBetweenPrimitive } from './FillBetweenPrimitive';

// After adding your indicator series
const indicatorData = [
  { time: 1234567890, value: 50100 },
  { time: 1234567900, value: 50150 },
  // ...
];

seriesManagerRef.current.addFillBetween(
  'fill_trailing_stop',
  indicatorData,
  {
    colorMode: 'dynamic',
    upFillColor: 'rgba(76, 175, 80, 0.15)',    // Green when price > indicator
    downFillColor: 'rgba(239, 83, 80, 0.15)',  // Red when price < indicator
    neutralFillColor: 'rgba(158, 158, 158, 0.1)' // Gray when price = indicator
  },
  FillBetweenPrimitive
);
```

### 3. Update Chart.jsx to Handle Fill in API Response

Add this code in your indicator fetching logic:

```javascript
// After adding series from API response
if (apiResponse.shapes?.fill?.enabled && apiResponse.series) {
  const seriesKeys = Object.keys(apiResponse.series);
  if (seriesKeys.length > 0) {
    const indicatorData = apiResponse.series[seriesKeys[0]];
    const fillOptions = {
      colorMode: apiResponse.shapes.fill.colorMode || 'dynamic',
      upFillColor: apiResponse.shapes.fill.upFillColor,
      downFillColor: apiResponse.shapes.fill.downFillColor,
      neutralFillColor: apiResponse.shapes.fill.neutralFillColor,
      fillColor: apiResponse.shapes.fill.fillColor
    };
    
    seriesManagerRef.current.addFillBetween(
      `fill_${instanceId}`,
      indicatorData,
      fillOptions,
      FillBetweenPrimitive
    );
  }
}
```

## Color Modes

### 1. Dynamic Mode (Default)

Fill color changes based on price position relative to indicator:

```javascript
{
  colorMode: 'dynamic',
  upFillColor: 'rgba(76, 175, 80, 0.15)',    // Price > Indicator
  downFillColor: 'rgba(239, 83, 80, 0.15)',  // Price < Indicator
  neutralFillColor: 'rgba(158, 158, 158, 0.1)' // Price = Indicator
}
```

### 2. Static Mode

Single color for all fill areas:

```javascript
{
  colorMode: 'static',
  fillColor: 'rgba(41, 98, 255, 0.1)'
}
```

### 3. Conditional Mode

Custom color function based on your logic:

```javascript
{
  colorMode: 'conditional',
  getColor: (pricePoint, indicatorValue, index) => {
    // Your custom logic
    const price = pricePoint.close || pricePoint.value;
    const diff = price - indicatorValue;
    
    if (diff > 100) return 'rgba(76, 175, 80, 0.3)'; // Strong up
    if (diff > 0) return 'rgba(76, 175, 80, 0.15)';  // Weak up
    if (diff < -100) return 'rgba(239, 83, 80, 0.3)'; // Strong down
    return 'rgba(239, 83, 80, 0.15)'; // Weak down
  }
}
```

## Complete Example: Trailing Stop with Fill

### Backend (Python/FastAPI)

```python
@app.post("/api/indicators/trailing_stop/historical")
async def trailing_stop_indicator(params: dict):
    # Calculate trailing stop
    trailing_stop_data = calculate_trailing_stop(params)
    
    return {
        "metadata": {
            "trailing_stop": {
                "seriesType": "line",
                "displayName": "Trailing Stop",
                "config": {
                    "color": "#FF6B6B",
                    "lineWidth": 2,
                    "lineStyle": 0
                }
            }
        },
        "series": {
            "trailing_stop": trailing_stop_data
        },
        "shapes": {
            "fill": {
                "enabled": True,
                "colorMode": "dynamic",
                "upFillColor": "rgba(76, 175, 80, 0.1)",
                "downFillColor": "rgba(239, 83, 80, 0.1)"
            }
        }
    }
```

### Frontend Integration

Update your Chart.jsx indicator handling:

```javascript
// In the fetchAndAddIndicators function, after addFromApiResponse:
if (apiResponse.shapes?.fill?.enabled) {
  const seriesKey = Object.keys(apiResponse.series)[0];
  const indicatorData = apiResponse.series[seriesKey];
  
  seriesManagerRef.current.addFillBetween(
    `fill_${instanceId}`,
    indicatorData,
    {
      colorMode: apiResponse.shapes.fill.colorMode || 'dynamic',
      upFillColor: apiResponse.shapes.fill.upFillColor,
      downFillColor: apiResponse.shapes.fill.downFillColor,
      neutralFillColor: apiResponse.shapes.fill.neutralFillColor
    },
    FillBetweenPrimitive
  );
}
```

## Management Functions

```javascript
// Add fill
seriesManager.addFillBetween(id, indicatorData, options, FillBetweenPrimitive);

// Update fill with new data
seriesManager.updateFillBetween(id, newIndicatorData);

// Update fill options
seriesManager.updateFillBetween(id, indicatorData, newOptions);

// Remove specific fill
seriesManager.removeFillBetween(id);

// Remove all fills
seriesManager.removeAllFillBetween();

// Clear all shapes including fills
seriesManager.clearAllShapes();
```

## Tips

1. **Performance**: Fill primitives are efficient as they only redraw visible areas
2. **Z-Index**: Fills are drawn behind the price series and indicator lines
3. **Colors**: Use RGBA with low alpha values (0.1-0.2) for subtle fills
4. **Updates**: When indicator data changes, call `updateFillBetween()` instead of removing and re-adding

## Pine Script Equivalent

This implementation is similar to Pine Script's `fill()` function:

```pinescript
// Pine Script
plot_price = plot(close, display = display.none)
plot_ts = plot(ts, 'Trailing Stop', color = color.red)
fill(plot_price, plot_ts, color = color.new(color.green, 90))
```

```javascript
// JavaScript Equivalent
seriesManager.addFillBetween('fill_ts', trailingStopData, {
  colorMode: 'dynamic',
  upFillColor: 'rgba(76, 175, 80, 0.1)',
  downFillColor: 'rgba(239, 83, 80, 0.1)'
}, FillBetweenPrimitive);
```

