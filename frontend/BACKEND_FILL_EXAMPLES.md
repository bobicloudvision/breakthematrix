# Backend Fill Examples

## Complete Pine Script `fill()` Implementation

All three Pine Script fill() variations are now supported. Here's how to use them from your backend:

---

## 1. Fill Between Two Plots (plot1, plot2, color)

**Pine Script:**
```pinescript
plot_price = plot(close, display = display.none)
plot_ts = plot(ts, 'Trailing Stop', color = color.red)
fill(plot_price, plot_ts, color.new(color.green, 90))
```

**Backend JSON Response:**
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
      { "time": 1234567900, "value": 50150 }
    ]
  },
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "series",
      "source1": "close",
      "source2": null,
      "color": "rgba(76, 175, 80, 0.1)",
      "colorMode": "static"
    }
  }
}
```

**Python Example:**
```python
@app.post("/api/indicators/trailing_stop/historical")
async def trailing_stop_indicator(params: dict):
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
            "trailing_stop": trailing_stop_data
        },
        "shapes": {
            "fill": {
                "enabled": True,
                "mode": "series",
                "source1": "close",  # Fill from close price
                "source2": None,      # to indicator line (from series data)
                "color": "rgba(76, 175, 80, 0.1)"
            }
        }
    }
```

---

## 2. Fill Between Two Horizontal Lines (hline1, hline2, color)

**Pine Script:**
```pinescript
hline(50, "Upper Band", color.gray)
hline(30, "Lower Band", color.gray)
fill(hline(50), hline(30), color.new(color.blue, 90))
```

**Backend JSON Response:**
```json
{
  "metadata": {},
  "series": {},
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "hline",
      "hline1": 70,
      "hline2": 30,
      "color": "rgba(33, 150, 243, 0.1)",
      "colorMode": "static"
    }
  }
}
```

**Python Example:**
```python
@app.post("/api/indicators/rsi/historical")
async def rsi_indicator(params: dict):
    rsi_data = calculate_rsi(params)
    
    return {
        "metadata": {
            "rsi": {
                "seriesType": "line",
                "separatePane": True,
                "config": {"color": "#9C27B0", "lineWidth": 2}
            }
        },
        "series": {
            "rsi": rsi_data
        },
        "shapes": {
            "fill": {
                "enabled": True,
                "mode": "hline",
                "hline1": 70,  # Overbought level
                "hline2": 30,  # Oversold level
                "color": "rgba(156, 39, 176, 0.05)"
            }
        }
    }
```

---

## 3. Gradient Fill (plot1, plot2, top_value, bottom_value, top_color, bottom_color)

**Pine Script:**
```pinescript
plot_upper = plot(upper_band, color=color.green)
plot_lower = plot(lower_band, color=color.red)
fill(plot_upper, plot_lower, 
     top_value=upper_band, bottom_value=lower_band,
     top_color=color.new(color.green, 80), 
     bottom_color=color.new(color.red, 80))
```

**Backend JSON Response:**
```json
{
  "metadata": {
    "upper_band": {
      "seriesType": "line",
      "displayName": "Upper Band",
      "config": {"color": "#4CAF50", "lineWidth": 1}
    },
    "lower_band": {
      "seriesType": "line",
      "displayName": "Lower Band",
      "config": {"color": "#F44336", "lineWidth": 1}
    }
  },
  "series": {
    "upper_band": [
      { "time": 1234567890, "value": 51000 }
    ],
    "lower_band": [
      { "time": 1234567890, "value": 49000 }
    ]
  },
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "series",
      "source1": null,
      "source2": null,
      "colorMode": "gradient",
      "topColor": "rgba(76, 175, 80, 0.2)",
      "bottomColor": "rgba(244, 67, 54, 0.2)"
    }
  }
}
```

**Python Example:**
```python
@app.post("/api/indicators/bollinger/historical")
async def bollinger_bands(params: dict):
    bb_data = calculate_bollinger_bands(params)
    
    return {
        "metadata": {
            "bb_upper": {
                "seriesType": "line",
                "displayName": "BB Upper",
                "config": {"color": "#4CAF50", "lineWidth": 1, "lineStyle": 2}
            },
            "bb_lower": {
                "seriesType": "line",
                "displayName": "BB Lower",
                "config": {"color": "#F44336", "lineWidth": 1, "lineStyle": 2}
            }
        },
        "series": {
            "bb_upper": bb_data["upper"],
            "bb_lower": bb_data["lower"]
        },
        "shapes": {
            "fill": {
                "enabled": True,
                "mode": "series",
                "source1": bb_data["upper"],  # Upper band data
                "source2": bb_data["lower"],  # Lower band data
                "colorMode": "gradient",
                "topColor": "rgba(76, 175, 80, 0.15)",
                "bottomColor": "rgba(244, 67, 54, 0.15)"
            }
        }
    }
```

---

## 4. Dynamic Color Fill (conditional coloring)

**Pine Script:**
```pinescript
css_area = (close - ts) * os < 0 ? retCss : css
fill(plot_price, plot_ts, color.new(css_area, areaTransp))
```

**Backend JSON Response:**
```json
{
  "shapes": {
    "fill": {
      "enabled": true,
      "mode": "series",
      "source1": "close",
      "colorMode": "dynamic",
      "upFillColor": "rgba(76, 175, 80, 0.15)",
      "downFillColor": "rgba(239, 83, 80, 0.15)",
      "neutralFillColor": "rgba(158, 158, 158, 0.1)"
    }
  }
}
```

**Python Example:**
```python
@app.post("/api/indicators/supertrend/historical")
async def supertrend_indicator(params: dict):
    st_data = calculate_supertrend(params)
    
    return {
        "metadata": {
            "supertrend": {
                "seriesType": "line",
                "displayName": "SuperTrend",
                "config": {"color": "#FF9800", "lineWidth": 2}
            }
        },
        "series": {
            "supertrend": st_data
        },
        "shapes": {
            "fill": {
                "enabled": True,
                "mode": "series",
                "source1": "close",
                "source2": None,  # Uses indicator series data
                "colorMode": "dynamic",
                "upFillColor": "rgba(76, 175, 80, 0.1)",   # Price above indicator
                "downFillColor": "rgba(239, 83, 80, 0.1)", # Price below indicator
                "neutralFillColor": "rgba(158, 158, 158, 0.05)"
            }
        }
    }
```

---

## Complete Fill Options Reference

```json
{
  "shapes": {
    "fill": {
      // Required
      "enabled": true,
      "mode": "series",  // "series" | "hline"
      
      // For series mode
      "source1": "close",  // "close" | "open" | "high" | "low" | "hl2" | "hlc3" | "ohlc4" | array
      "source2": null,     // array data or null (uses indicator series)
      
      // For hline mode
      "hline1": 70,
      "hline2": 30,
      
      // Color modes
      "colorMode": "static",  // "static" | "dynamic" | "gradient"
      
      // Static color
      "color": "rgba(41, 98, 255, 0.1)",
      
      // Dynamic colors (when colorMode = "dynamic")
      "upFillColor": "rgba(76, 175, 80, 0.15)",
      "downFillColor": "rgba(239, 83, 80, 0.15)",
      "neutralFillColor": "rgba(158, 158, 158, 0.1)",
      
      // Gradient colors (when colorMode = "gradient")
      "topColor": "rgba(76, 175, 80, 0.2)",
      "bottomColor": "rgba(244, 67, 54, 0.2)",
      
      // Additional options
      "fillGaps": true,    // Fill gaps when data is missing
      "display": true      // Show/hide fill
    }
  }
}
```

---

## Source Options

### Built-in Sources (strings):
- `"close"` or `"price"` - Close price
- `"open"` - Open price
- `"high"` - High price
- `"low"` - Low price
- `"hl2"` - (High + Low) / 2
- `"hlc3"` - (High + Low + Close) / 3
- `"ohlc4"` - (Open + High + Low + Close) / 4

### Custom Data Sources:
Pass an array of `{time, value}` objects:
```python
"source1": [
    {"time": 1234567890, "value": 50100},
    {"time": 1234567900, "value": 50150}
]
```

---

## Advanced Example: Multiple Fills

You can have multiple indicators each with their own fills:

```python
# Indicator 1: Trailing Stop with dynamic fill
{
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

# Indicator 2: Support/Resistance zones with hline fill
{
  "shapes": {
    "fill": {
      "enabled": True,
      "mode": "hline",
      "hline1": 51000,
      "hline2": 50800,
      "color": "rgba(255, 235, 59, 0.1)"
    }
  }
}

# Indicator 3: Bollinger Bands with gradient fill
{
  "shapes": {
    "fill": {
      "enabled": True,
      "mode": "series",
      "source1": upper_band_data,
      "source2": lower_band_data,
      "colorMode": "gradient",
      "topColor": "rgba(76, 175, 80, 0.15)",
      "bottomColor": "rgba(244, 67, 54, 0.15)"
    }
  }
}
```

Each indicator's fill is independent and can be toggled separately via the IndicatorsTab.

