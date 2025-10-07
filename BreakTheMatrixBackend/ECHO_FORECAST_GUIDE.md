# Echo Forecast Indicator Guide

## Overview

The **Echo Forecast** indicator by LuxAlgo is a sophisticated pattern-matching forecast tool that uses correlation analysis to predict future price movements. It identifies similar historical price patterns and projects them forward to create a forecast.

This implementation is licensed under **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**.

## How It Works

### Core Algorithm

1. **Pattern Matching**: The indicator maintains a sliding window of historical candles and searches for patterns similar to recent price action.

2. **Correlation Analysis**: It uses statistical correlation to measure similarity between:
   - **Reference Window**: The most recent N candles (where N = forecast window)
   - **Evaluation Windows**: Historical windows of the same size sliding through the evaluation period

3. **Forecast Construction**: Once the best matching historical pattern is found, the indicator projects the sequence of price changes from that pattern into the future.

### Three Forecast Modes

#### 1. Cumulative Mode (Default)
Sequentially adds price changes from the matched pattern to the last known price:
```
Forecast[i] = Forecast[i-1] + Change[matched_pattern[i]]
```

#### 2. Mean Mode
Uses the mean of the reference window plus changes:
```
Forecast[i] = Mean(reference_window) + Change[matched_pattern[i]]
```

#### 3. Linear Regression Mode
Projects a linear regression trend and adds changes:
```
Forecast[i] = (Alpha * time[i] + Beta) + Change[matched_pattern[i]]
```
where Alpha (slope) and Beta (intercept) are calculated from the reference window.

### Similarity vs Dissimilarity

- **Similarity Mode**: Finds the historical pattern with the highest positive correlation (most similar behavior)
- **Dissimilarity Mode**: Finds the historical pattern with the lowest correlation (opposite behavior)

## Parameters

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `evaluationWindow` | Integer | 50 | 10-200 | Number of candles to search for similar patterns |
| `forecastWindow` | Integer | 50 | 1-200 | Number of candles to forecast into the future |
| `forecastMode` | String | "Similarity" | Similarity/Dissimilarity | Whether to find similar or opposite patterns |
| `constructionMode` | String | "Cumulative" | Cumulative/Mean/Linreg | Method for constructing the forecast |
| `source` | String | "close" | close/open/high/low/hl2/hlc3/ohlc4 | Price source for calculations |
| `forecastColor` | String | "#2157f3" | Any hex color | Color of forecast lines |
| `forecastStyle` | String | "dotted" | solid/dashed/dotted | Line style for forecast |
| `showAreas` | Boolean | true | true/false | Show evaluation, reference, and correlation windows |
| `refAreaColor` | String | "rgba(255, 93, 0, 0.2)" | Any rgba color | Reference window highlight color |
| `corrAreaColor` | String | "rgba(8, 153, 129, 0.2)" | Any rgba color | Correlation window highlight color |
| `evalAreaColor` | String | "rgba(128, 128, 128, 0.2)" | Any rgba color | Evaluation window highlight color |

## API Usage

### Creating an Instance

```bash
POST /api/indicators/instances
{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "name": "Echo Forecast BTC 1H",
  "parameters": {
    "evaluationWindow": 50,
    "forecastWindow": 50,
    "forecastMode": "Similarity",
    "constructionMode": "Cumulative",
    "source": "close",
    "forecastColor": "#2157f3",
    "forecastStyle": "dotted",
    "showAreas": true,
    "refAreaColor": "rgba(255, 93, 0, 0.2)",
    "corrAreaColor": "rgba(8, 153, 129, 0.2)",
    "evalAreaColor": "rgba(128, 128, 128, 0.2)"
  }
}
```

### Response Structure

The indicator returns:

```json
{
  "values": {},
  "lines": [
    {
      "time1": 1696723200,
      "price1": "28450.50",
      "time2": 1696726800,
      "price2": "28475.25",
      "color": "#2157f3",
      "lineWidth": 2,
      "lineStyle": "dotted"
    }
  ],
  "boxes": [
    {
      "time1": 1696500000,
      "time2": 1696680000,
      "price1": "28800.00",
      "price2": "28200.00",
      "color": "rgba(128, 128, 128, 0.2)",
      "borderColor": "transparent",
      "label": "Evaluation Window"
    },
    {
      "time1": 1696680000,
      "time2": 1696723200,
      "price1": "28800.00",
      "price2": "28200.00",
      "color": "rgba(255, 93, 0, 0.2)",
      "borderColor": "transparent",
      "label": "Reference Window"
    },
    {
      "time1": 1696550000,
      "time2": 1696593200,
      "price1": "28800.00",
      "price2": "28200.00",
      "color": "rgba(8, 153, 129, 0.2)",
      "borderColor": "transparent",
      "label": "Best Match Window"
    }
  ]
}
```

### Visualization Components

1. **Forecast Lines**: Series of connected lines projecting the forecast
2. **Evaluation Window (Gray)**: The entire historical search space
3. **Reference Window (Orange)**: The most recent pattern being matched
4. **Correlation Window (Green)**: The best matching historical pattern

## Use Cases

### 1. Trend Continuation
- Use **Similarity Mode** with **Cumulative Construction**
- Good for trending markets where patterns tend to repeat
- Example: Identify continuation patterns after pullbacks

### 2. Mean Reversion
- Use **Dissimilarity Mode** with **Mean Construction**
- Good for ranging markets
- Example: Forecast reversals when extreme patterns occur

### 3. Trend Analysis
- Use **Similarity Mode** with **Linreg Construction**
- Combines trend projection with pattern matching
- Example: Project both trend and cyclical components

### 4. Volatility Forecasting
- Compare different construction modes
- Analyze forecast divergence as uncertainty measure
- Example: When modes disagree, expect higher uncertainty

## Trading Strategies

### Strategy 1: Forecast Confirmation
```
Entry Signal:
- Forecast shows bullish projection (upward slope)
- Current price breaks above reference window high
- Correlation window shows similar historical pattern

Exit Signal:
- Forecast turns bearish
- Price falls below forecast projection
```

### Strategy 2: Divergence Detection
```
Entry Signal:
- Price makes new low
- Forecast projection shows higher lows (bullish divergence)
- Similarity mode active with high correlation

Exit Signal:
- Price reaches forecast target
- Divergence resolves
```

### Strategy 3: Pattern Confirmation
```
Setup:
- Enable showAreas to visualize windows
- Look for correlation window with clear directional pattern
- Wait for reference window to complete similar pattern

Entry:
- When forecast projects in expected direction
- Use linear regression mode for trend-following

Exit:
- After forecast window completes (time-based)
- Or when new forecast significantly changes direction
```

## Technical Details

### Correlation Calculation

The indicator uses Pearson correlation coefficient:

```
Correlation(A, B) = Covariance(A, B) / (StdDev(A) * StdDev(B))
```

Where:
- A = Reference window (recent prices)
- B = Evaluation window (historical prices)

### Linear Regression

For Linreg construction mode:
```
Slope = Covariance(X, Y) / Variance(X)
Intercept = Mean(Y) - Slope * Mean(X)
```

Where X is time indices and Y is prices.

### Buffer Requirements

The indicator requires:
```
Minimum Candles = evaluationWindow + (forecastWindow * 2)
```

Example: With default settings (50 + 50*2 = 150 candles)

## Best Practices

### Window Sizing

1. **Short-term Trading (Intraday)**
   - Evaluation Window: 20-30
   - Forecast Window: 10-20
   - Timeframe: 5m-15m

2. **Medium-term Trading (Swing)**
   - Evaluation Window: 50-100
   - Forecast Window: 20-50
   - Timeframe: 1h-4h

3. **Long-term Analysis (Position)**
   - Evaluation Window: 100-200
   - Forecast Window: 50-100
   - Timeframe: 1d

### Construction Mode Selection

- **Trending Markets**: Use Cumulative or Linreg
- **Ranging Markets**: Use Mean
- **Volatile Markets**: Compare all three modes

### Mode Selection

- **Bull Markets**: Use Similarity mode
- **Bear Markets**: Use Similarity mode
- **Reversals**: Experiment with Dissimilarity mode
- **Consolidation**: Use Similarity with Mean construction

## Performance Considerations

### Computational Complexity

- **Time Complexity**: O(E * F) per candle
  - E = Evaluation window
  - F = Forecast window
  
- **Space Complexity**: O(E + 2F) for price buffer

### Optimization Tips

1. Use smaller windows for real-time trading (faster updates)
2. Use larger windows for backtesting (more reliable patterns)
3. Disable areas (`showAreas: false`) if not needed (reduces data transfer)

## Limitations

1. **Lagging Nature**: Forecast updates only on new candles
2. **Pattern Dependency**: Requires historical patterns to exist
3. **Market Regime Changes**: May fail during unprecedented market conditions
4. **Correlation Threshold**: Low correlation matches may produce unreliable forecasts

## Advanced Features

### Multiple Timeframe Analysis

Create multiple instances with different parameters:

```javascript
// Short-term forecast
{
  "evaluationWindow": 20,
  "forecastWindow": 10,
  "forecastColor": "#2157f3"
}

// Long-term forecast
{
  "evaluationWindow": 100,
  "forecastWindow": 50,
  "forecastColor": "#ff6b00"
}
```

### Ensemble Forecasting

Combine multiple construction modes:
1. Create three instances (Cumulative, Mean, Linreg)
2. Compare forecast directions
3. Higher agreement = Higher confidence

### Adaptive Parameters

Adjust parameters based on market conditions:
- **High Volatility**: Reduce forecast window
- **Low Volatility**: Increase evaluation window
- **Trending**: Use Cumulative mode
- **Ranging**: Use Mean mode

## Troubleshooting

### No Forecast Lines Displayed

**Cause**: Insufficient historical data
**Solution**: Ensure at least `evaluationWindow + (forecastWindow * 2)` candles are available

### Erratic Forecast

**Cause**: Low correlation matches or excessive noise
**Solution**: 
- Increase evaluation window
- Use smoother price source (e.g., hlc3 instead of close)
- Consider larger timeframe

### Windows Not Visible

**Cause**: `showAreas` disabled or window times outside chart range
**Solution**: 
- Set `showAreas: true`
- Zoom out on chart to see full historical windows

## Examples

### Example 1: Bitcoin Hourly Forecast

```json
{
  "indicatorId": "echo_forecast",
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "parameters": {
    "evaluationWindow": 100,
    "forecastWindow": 24,
    "forecastMode": "Similarity",
    "constructionMode": "Linreg",
    "source": "hlc3",
    "showAreas": true
  }
}
```

Projects next 24 hours using 100-hour historical pattern matching with trend-following.

### Example 2: Ethereum 15-Minute Scalping

```json
{
  "indicatorId": "echo_forecast",
  "symbol": "ETHUSDT",
  "timeframe": "15m",
  "parameters": {
    "evaluationWindow": 30,
    "forecastWindow": 12,
    "forecastMode": "Similarity",
    "constructionMode": "Cumulative",
    "source": "close",
    "forecastColor": "#089981",
    "showAreas": false
  }
}
```

Fast 3-hour forecast for short-term trading without visual clutter.

### Example 3: Altcoin Swing Trading

```json
{
  "indicatorId": "echo_forecast",
  "symbol": "ADAUSDT",
  "timeframe": "4h",
  "parameters": {
    "evaluationWindow": 60,
    "forecastWindow": 30,
    "forecastMode": "Similarity",
    "constructionMode": "Mean",
    "source": "hlc3",
    "forecastStyle": "dashed"
  }
}
```

5-day forecast using mean reversion for altcoin swing trades.

## References

- Original indicator by LuxAlgo
- Licensed under CC BY-NC-SA 4.0: https://creativecommons.org/licenses/by-nc-sa/4.0/

## See Also

- [TRAMA Indicator](TRAMA_INDICATOR_GUIDE.md) - Adaptive moving average
- [Pattern Detection](PATTERN_DETECTION_GUIDE.md) - Pattern recognition
- [Indicator Instance Management](INDICATOR_INSTANCE_MANAGEMENT.md)

