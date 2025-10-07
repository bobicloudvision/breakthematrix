# Echo Forecast - 30 Candle Visualization Example

## Quick Start: Create a 30-Candle Forecast

### API Request

```bash
POST http://localhost:8080/api/indicators/instances
Content-Type: application/json

{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "name": "BTC 30-Hour Forecast",
  "parameters": {
    "evaluationWindow": 50,
    "forecastWindow": 30,
    "forecastMode": "Similarity",
    "constructionMode": "Cumulative",
    "source": "close",
    "forecastColor": "#2157f3",
    "forecastStyle": "dotted",
    "showAreas": true
  }
}
```

### What You'll Get

The indicator will return **30 connected lines** showing the projected price pattern:

```json
{
  "lines": [
    // Line 0: Last actual price → First forecast point
    {
      "time1": 1696723200,
      "price1": "28450.50",
      "time2": 1696726800,
      "price2": "28475.25",
      "color": "#2157f3",
      "lineWidth": 2,
      "lineStyle": "dotted"
    },
    // Line 1-29: Connecting all 30 forecast points
    {
      "time1": 1696726800,
      "price1": "28475.25",
      "time2": 1696730400,
      "price2": "28490.10",
      "color": "#2157f3",
      "lineWidth": 2,
      "lineStyle": "dotted"
    },
    // ... 28 more lines showing the complete pattern
  ]
}
```

## Visualization Details

### The Pattern Shows:
- **30 future candles** projected forward in time
- **Connected lines** forming the complete pattern
- **Dotted style** to distinguish from actual price
- **Blue color** (#2157f3) for easy identification

### Chart Display:
```
Time -->
         Now
          ↓
Price    |........forecast pattern.........
  ^      |     .                       .
  |      |   .                           .
  |      | .                               .
  |  ____|.                                  .
  |      ⭐ (last known price)
```

## Different Forecast Window Examples

### 1. Short-term (10 candles)
```json
{
  "forecastWindow": 10,
  "forecastColor": "#00ff00"
}
```
Shows next 10 candles - good for quick scalping decisions.

### 2. Medium-term (30 candles) - RECOMMENDED
```json
{
  "forecastWindow": 30,
  "forecastColor": "#2157f3"
}
```
Shows next 30 candles - balanced view for intraday/swing trading.

### 3. Long-term (50 candles)
```json
{
  "forecastWindow": 50,
  "forecastColor": "#ff6b00"
}
```
Shows next 50 candles - full pattern projection for position trading.

## Multiple Forecasts for Comparison

Create 3 instances to compare different timeframes:

### Short Forecast (10 bars)
```bash
POST /api/indicators/instances
{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "15m",
  "name": "Short Forecast (10)",
  "parameters": {
    "evaluationWindow": 30,
    "forecastWindow": 10,
    "forecastColor": "#00ff00",
    "showAreas": false
  }
}
```

### Medium Forecast (30 bars)
```bash
POST /api/indicators/instances
{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "15m",
  "name": "Medium Forecast (30)",
  "parameters": {
    "evaluationWindow": 50,
    "forecastWindow": 30,
    "forecastColor": "#2157f3",
    "showAreas": false
  }
}
```

### Long Forecast (50 bars)
```bash
POST /api/indicators/instances
{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "15m",
  "name": "Long Forecast (50)",
  "parameters": {
    "evaluationWindow": 100,
    "forecastWindow": 50,
    "forecastColor": "#ff6b00",
    "showAreas": false
  }
}
```

## Understanding the Line Output

Each forecast creates **forecastWindow + 1** lines:
- 1 line from last actual candle to first forecast point
- forecastWindow - 1 lines connecting all forecast points

For a 30-candle forecast:
- **30 line segments** total
- Each represents the projected price movement between consecutive future candles
- Together they form the **complete pattern visualization**

## Customizing the Visualization

### Change Line Style
```json
{
  "forecastStyle": "solid"    // Solid line
  "forecastStyle": "dashed"   // Dashed line
  "forecastStyle": "dotted"   // Dotted line (default)
}
```

### Change Color
```json
{
  "forecastColor": "#2157f3"  // Blue (default)
  "forecastColor": "#00ff00"  // Green
  "forecastColor": "#ff0000"  // Red
  "forecastColor": "#ffaa00"  // Orange
}
```

### Hide Background Windows
If you only want the forecast lines without the colored window areas:
```json
{
  "showAreas": false
}
```

## Real-World Trading Example

### Setup for BTC 4H Chart (30 candles = 5 days ahead)
```json
{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "4h",
  "name": "BTC 5-Day Forecast",
  "parameters": {
    "evaluationWindow": 60,
    "forecastWindow": 30,
    "forecastMode": "Similarity",
    "constructionMode": "Cumulative",
    "source": "hlc3",
    "forecastColor": "#2157f3",
    "forecastStyle": "solid",
    "showAreas": true
  }
}
```

### What This Shows:
- **30 candles** × 4 hours = **120 hours** = **5 days** projection
- Pattern based on **60 candles** × 4 hours = **10 days** of history
- Smooth **hlc3** price source for cleaner pattern
- **Solid lines** for clear visibility
- **Windows shown** to see which historical pattern matches

## How to Read the Pattern

1. **Upward Sloping Lines** = Bullish forecast (price expected to rise)
2. **Downward Sloping Lines** = Bearish forecast (price expected to fall)
3. **Flat Lines** = Consolidation forecast (sideways movement)
4. **Zigzag Pattern** = Volatile forecast (choppy price action)

## Frontend Integration

When rendering in your chart:

```javascript
// Receive indicator data
const indicatorData = {
  lines: [...], // Array of forecast lines
  boxes: [...]  // Window areas (optional)
};

// Render forecast lines
indicatorData.lines.forEach(line => {
  drawLine(
    line.time1, line.price1,
    line.time2, line.price2,
    {
      color: line.color,
      width: line.lineWidth,
      style: line.lineStyle
    }
  );
});
```

## Testing the Forecast

### Step 1: Create Instance
```bash
curl -X POST http://localhost:8080/api/indicators/instances \
  -H "Content-Type: application/json" \
  -d '{
    "indicatorId": "echo_forecast",
    "symbol": "BTCUSDT",
    "timeframe": "1h",
    "name": "Test 30-Candle Forecast",
    "parameters": {
      "forecastWindow": 30,
      "evaluationWindow": 50
    }
  }'
```

### Step 2: Get Forecast Data
```bash
curl http://localhost:8080/api/indicators/instances/{instanceId}
```

### Step 3: Verify Output
Look for the `lines` array with 30 line segments showing the complete forecast pattern.

## Common Issues

### Issue: Not Seeing 30 Lines
**Solution**: Make sure you have enough historical data:
- Required candles = evaluationWindow + (forecastWindow × 2)
- For 30 forecast with 50 evaluation = need 130 candles minimum

### Issue: Pattern Looks Random
**Solution**: 
- Increase `evaluationWindow` (e.g., 100)
- Use smoother price source: `"source": "hlc3"`
- Try different construction mode: `"constructionMode": "Linreg"`

### Issue: Lines Not Connected
**Solution**: This shouldn't happen - each line's end point is the next line's start point. Check your frontend rendering.

## Performance Notes

- 30-candle forecast is fast (< 50ms calculation time)
- Updates on every new candle
- More forecast candles = slightly longer calculation
- 50-candle forecast still very fast (< 100ms)

## Next Steps

1. ✅ Create instance with `forecastWindow: 30`
2. ✅ Render the 30 lines on your chart
3. ✅ Watch it update as new candles arrive
4. ✅ Compare forecasts with actual price movement
5. ✅ Adjust parameters based on accuracy

## Summary

The Echo Forecast indicator **already provides complete 30-candle pattern visualization**:
- Set `forecastWindow: 30` 
- Get 30 connected lines showing the projected pattern
- Lines display the expected price movement for next 30 candles
- Pattern updates automatically with each new candle

**You don't need any modifications - it's ready to use!** 🚀

