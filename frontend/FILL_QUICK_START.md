# Fill Between - Quick Start Guide

## 🎯 Overview

The `FillBetweenPrimitive` now supports **all three Pine Script fill() variations** and receives data **directly from your backend**.

---

## 📝 Three Fill Modes

### 1️⃣ **Fill Between Price and Indicator** (Most Common)
```json
{
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "series",
      "source1": "close",
      "colorMode": "dynamic",
      "upFillColor": "rgba(76, 175, 80, 0.15)",
      "downFillColor": "rgba(239, 83, 80, 0.15)"
    }
  }
}
```
✅ Green when price > indicator  
✅ Red when price < indicator

### 2️⃣ **Fill Between Two Horizontal Lines**
```json
{
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "hline",
      "hline1": 70,
      "hline2": 30,
      "color": "rgba(156, 39, 176, 0.1)"
    }
  }
}
```
✅ Perfect for RSI overbought/oversold zones

### 3️⃣ **Gradient Fill Between Two Series**
```json
{
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "series",
      "source1": upperBandData,
      "source2": lowerBandData,
      "colorMode": "gradient",
      "topColor": "rgba(76, 175, 80, 0.2)",
      "bottomColor": "rgba(244, 67, 54, 0.2)"
    }
  }
}
```
✅ Ideal for Bollinger Bands, Keltner Channels

---

## 🚀 Backend Implementation

### Python/FastAPI Example
```python
@app.post("/api/indicators/trailing_stop/historical")
async def trailing_stop_indicator(params: dict):
    # Your indicator calculation
    trailing_stop_data = calculate_trailing_stop(params)
    
    return {
        "metadata": {
            "trailing_stop": {
                "seriesType": "line",
                "displayName": "Trailing Stop",
                "config": {"color": "#FF6B6B", "lineWidth": 2}
            }
        },
        "series": {
            "trailing_stop": trailing_stop_data  # [{ time, value }, ...]
        },
        "shapes": {
            "fill": {
                "enabled": True,
                "mode": "series",
                "source1": "close",
                "colorMode": "dynamic",
                "upFillColor": "rgba(76, 175, 80, 0.1)",
                "downFillColor": "rgba(239, 83, 80, 0.1)"
            }
        }
    }
```

---

## 🎨 Color Modes

| Mode | Use Case | Example |
|------|----------|---------|
| `static` | Single color | Bollinger Bands band |
| `dynamic` | Color based on position | Trailing stop (green above, red below) |
| `gradient` | Smooth color transition | Channel fills |

---

## 📊 Source Options

### String Sources (built-in):
- `"close"` - Close price
- `"open"` - Open price  
- `"high"` - High price
- `"low"` - Low price
- `"hl2"` - (High + Low) / 2
- `"hlc3"` - (High + Low + Close) / 3
- `"ohlc4"` - (Open + High + Low + Close) / 4

### Array Data:
```json
"source1": [
  { "time": 1234567890, "value": 50100 },
  { "time": 1234567900, "value": 50150 }
]
```

---

## ⚙️ Complete Options Reference

```typescript
{
  enabled: boolean,           // Show/hide fill
  mode: "series" | "hline",   // Fill mode
  
  // For series mode
  source1: string | array,    // First data source
  source2: string | array,    // Second data source
  
  // For hline mode
  hline1: number,             // First horizontal line
  hline2: number,             // Second horizontal line
  
  // Colors
  colorMode: "static" | "dynamic" | "gradient",
  color: string,              // Static color
  upFillColor: string,        // Dynamic: price > indicator
  downFillColor: string,      // Dynamic: price < indicator
  neutralFillColor: string,   // Dynamic: price = indicator
  topColor: string,           // Gradient top
  bottomColor: string,        // Gradient bottom
  
  // Options
  fillGaps: boolean,          // Fill missing data
  display: boolean            // Show/hide
}
```

---

## 🔧 Frontend Integration

The frontend **automatically handles** fills from your backend response. No additional code needed!

### Manual Usage (if needed):
```javascript
seriesManagerRef.current.addFillBetween(
  'fill_my_indicator',
  {
    mode: 'series',
    source1: 'close',
    source2: indicatorData,
    colorMode: 'dynamic',
    upFillColor: 'rgba(76, 175, 80, 0.15)',
    downFillColor: 'rgba(239, 83, 80, 0.15)'
  },
  FillBetweenPrimitive
);
```

---

## 📚 Full Examples

See `BACKEND_FILL_EXAMPLES.md` for:
- ✅ Trailing Stop with dynamic fill
- ✅ RSI with overbought/oversold zones
- ✅ Bollinger Bands with gradient fill
- ✅ SuperTrend with conditional coloring
- ✅ Multiple indicators with multiple fills

---

## 💡 Tips

1. **Use low alpha values** (0.05-0.2) for subtle fills
2. **Fills render behind** price and indicator lines automatically
3. **Each indicator** can have its own independent fill
4. **Fills are performant** - only visible areas are drawn
5. **Fills respect** indicator visibility toggle in IndicatorsTab

---

## 🐛 Troubleshooting

**Fill not showing?**
- ✅ Check `enabled: true` in backend response
- ✅ Verify indicator data has matching timestamps
- ✅ Check alpha values (too low = invisible)
- ✅ Ensure indicator is visible in IndicatorsTab

**Wrong colors?**
- ✅ Check `colorMode` setting
- ✅ Verify RGBA format: `"rgba(R, G, B, Alpha)"`
- ✅ Alpha should be 0.0-1.0 (0.1-0.2 recommended)

**Performance issues?**
- ✅ Use `fillGaps: false` for large datasets with missing data
- ✅ Limit to 3-4 fills maximum on screen
- ✅ Use simpler shapes if possible

---

## 🎉 You're Done!

Just add the `shapes.fill` object to your backend indicator response and the fill will automatically appear on the chart!

