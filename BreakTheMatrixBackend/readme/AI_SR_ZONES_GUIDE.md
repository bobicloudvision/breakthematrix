# AI Support/Resistance Zones Indicator

## Overview

The **AI Support/Resistance Zones Indicator** is an advanced, intelligent indicator that automatically detects high-probability support and resistance zones using machine learning-inspired techniques. Unlike traditional pivot-based indicators, this AI indicator uses:

- **Price Clustering**: Groups similar price levels using kernel density estimation
- **Volume Weighting**: Prioritizes zones with high trading activity
- **Rejection Analysis**: Identifies strong zones based on price rejection wicks
- **Multi-Factor Scoring**: Combines multiple factors for zone strength assessment
- **Adaptive Learning**: Continuously updates zones based on new price action

## Key Features

### 🤖 AI-Powered Detection
- Automatically identifies the most significant support and resistance zones
- Uses clustering algorithms to find price areas where significant trading activity occurs
- No manual configuration required - adapts to market conditions

### 📊 Multi-Factor Zone Scoring
Each zone is scored based on:
1. **Volume Weight** (default 30%): How much trading volume occurred at this level
2. **Touch Weight** (default 30%): How many times price has tested this level
3. **Rejection Weight** (default 40%): Strength of price rejections (wick analysis)
4. **Recency Factor**: Recent zones have higher priority than old ones

### 🎨 Visual Representation
- Zones displayed as colored boxes on the chart
- **Green boxes** = Support zones (below current price)
- **Red boxes** = Resistance zones (above current price)
- **Opacity intensity** = Zone strength (stronger zones are more opaque)
- Optional labels showing zone type and strength percentage

## Parameters

### Core Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| **Lookback Period** | 200 | 50-1000 | Number of candles to analyze for zone detection |
| **Max Zones** | 5 | 1-20 | Maximum number of zones to display simultaneously |
| **Zone Width %** | 0.5% | 0.1-2.0% | Width of each zone as a percentage of price |
| **Cluster Sensitivity** | 1.5 | 0.5-5.0 | Clustering sensitivity (lower = fewer, stronger zones) |

### Weight Parameters

| Parameter | Default | Range | Description |
|-----------|---------|-------|-------------|
| **Volume Weight** | 0.3 | 0.0-1.0 | Importance of volume in zone scoring |
| **Rejection Weight** | 0.4 | 0.0-1.0 | Importance of price rejections in scoring |
| **Touch Weight** | 0.3 | 0.0-1.0 | Importance of touch count in scoring |

> **Note**: The three weights should ideally sum to 1.0 for balanced scoring

### Visual Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| **Support Color** | `rgba(0, 255, 0, 0.15)` | Color for support zones |
| **Resistance Color** | `rgba(255, 0, 0, 0.15)` | Color for resistance zones |
| **Show Labels** | `true` | Display zone strength labels |

## How It Works

### 1. Data Collection Phase
The indicator collects price points from each candle:
- **Highs** (potential resistance)
- **Lows** (potential support)
- **Closes** (weighted higher for importance)
- **Rejection wicks** (strong price rejections get extra weight)

### 2. Clustering Phase
Uses a clustering algorithm to group similar price levels:
```
Price Range: $50,000 - $52,000
Cluster Threshold: 1.5% sensitivity

Points at $50,100, $50,150, $50,200 → Cluster at $50,150
Points at $51,800, $51,900, $51,850 → Cluster at $51,850
```

### 3. Zone Detection
Converts clusters into zones:
- Each cluster becomes a zone centered at the cluster's weighted average
- Zone width is determined by the `Zone Width %` parameter
- Zones with fewer than 3 touch points are discarded

### 4. Scoring & Ranking
Each zone receives a composite strength score:

```
Zone Strength = (Volume Score × Volume Weight) +
                (Touch Score × Touch Weight) +
                (Rejection Score × Rejection Weight) ×
                Recency Factor
```

- **Volume Score**: Normalized by highest volume zone (0-1)
- **Touch Score**: Normalized by most-touched zone (0-1)
- **Rejection Score**: Based on wick-to-body ratio (0-1)
- **Recency Factor**: Exponential decay over time

### 5. Visualization
Top N zones (based on score) are displayed:
- Boxes extend from current time backwards
- Opacity reflects zone strength
- Labels show zone type and strength percentage

## Usage Examples

### Example 1: Conservative Trading Setup
**Goal**: Find only the strongest, most reliable zones

```json
{
  "lookbackPeriod": 300,
  "maxZones": 3,
  "clusterSensitivity": 1.0,
  "volumeWeight": 0.4,
  "rejectionWeight": 0.4,
  "touchWeight": 0.2
}
```

This configuration:
- Analyzes more history (300 candles)
- Shows only top 3 zones
- Lower sensitivity = fewer, stronger zones
- Prioritizes volume and rejections over touch count

### Example 2: Active Trading Setup
**Goal**: Identify more potential zones for scalping

```json
{
  "lookbackPeriod": 100,
  "maxZones": 8,
  "clusterSensitivity": 2.5,
  "volumeWeight": 0.3,
  "rejectionWeight": 0.3,
  "touchWeight": 0.4
}
```

This configuration:
- Analyzes recent history only (100 candles)
- Shows more zones (8)
- Higher sensitivity = more zones detected
- Prioritizes touch count (tested levels)

### Example 3: Volume-Focused Setup
**Goal**: Trade only where significant volume occurs

```json
{
  "lookbackPeriod": 200,
  "maxZones": 5,
  "clusterSensitivity": 1.5,
  "volumeWeight": 0.7,
  "rejectionWeight": 0.2,
  "touchWeight": 0.1
}
```

This configuration:
- Standard lookback and zone count
- Very high volume weight (70%)
- Perfect for finding institutional levels

## Integration with Trading Strategies

### Reading Zone Data

The indicator outputs the following values:

```json
{
  "nearestSupport": 50150.00,
  "nearestResistance": 51800.00,
  "supportStrength": 0.85,
  "resistanceStrength": 0.72,
  "totalZones": 5
}
```

### Strategy Integration Example

```java
// Get indicator values
BigDecimal nearestSupport = values.get("nearestSupport");
BigDecimal nearestResistance = values.get("nearestResistance");
BigDecimal supportStrength = values.get("supportStrength");

// Trading logic
if (currentPrice.compareTo(nearestSupport) <= 0 && 
    supportStrength.doubleValue() > 0.7) {
    // Strong support zone - potential buy opportunity
    enterLong();
}

if (currentPrice.compareTo(nearestResistance) >= 0 && 
    resistanceStrength.doubleValue() > 0.7) {
    // Strong resistance zone - potential sell opportunity
    enterShort();
}
```

## Performance Considerations

### Computational Complexity
- **Initial Load**: O(n²) for clustering historical data
- **Per Candle**: O(n) for updating zones
- **Re-detection**: Performed every 5 candles to balance accuracy and performance

### Memory Usage
- Maintains a rolling window of candles (default 200)
- Stores 3× price points per candle (high, low, close + rejections)
- Typical memory: ~10KB per 100 candles

### Optimization Tips
1. **Reduce Lookback Period**: Use 100-150 candles for faster calculations
2. **Lower Max Zones**: Show 3-5 zones instead of 10-20
3. **Increase Cluster Sensitivity**: Higher values = faster clustering

## Comparison with Traditional Indicators

| Feature | AI SR Zones | Traditional SR | Pivot Points |
|---------|-------------|----------------|--------------|
| Volume Awareness | ✅ Yes | ❌ No | ❌ No |
| Adaptive to Market | ✅ Yes | ⚠️ Partial | ❌ No |
| Rejection Analysis | ✅ Yes | ⚠️ Manual | ❌ No |
| Zone Strength Scoring | ✅ Multi-factor | ⚠️ Touch count only | ❌ N/A |
| Auto-adjusting Width | ✅ Yes | ❌ Fixed | ❌ N/A |
| Historical Learning | ✅ Yes | ⚠️ Limited | ❌ No |

## Best Practices

### ✅ Do's
- Use with volume indicators for confirmation
- Adjust sensitivity based on timeframe (lower for higher TF)
- Monitor zone strength - only trade high-strength zones (>0.6)
- Combine with trend indicators for direction bias
- Wait for price to reach zone + confirmation before entering

### ❌ Don'ts
- Don't use as a standalone signal
- Don't set cluster sensitivity too high (creates too many weak zones)
- Don't ignore the strength score - weak zones break easily
- Don't use very short lookback periods (<50 candles)
- Don't overcrowd the chart with too many zones

## Troubleshooting

### Issue: Too many zones displayed
**Solution**: 
- Decrease `maxZones` parameter
- Lower `clusterSensitivity` (try 1.0-1.5)
- Increase weight parameters for quality filtering

### Issue: Zones not appearing
**Solution**:
- Increase `lookbackPeriod` (need more data)
- Increase `clusterSensitivity` (try 2.0-3.0)
- Check that you have enough historical candles loaded

### Issue: Zones are inaccurate
**Solution**:
- Adjust weight parameters based on your market
- Increase `lookbackPeriod` for better statistical sample
- Fine-tune `zoneWidthPercent` for your asset's volatility

## API Usage

### Adding to Chart via REST API

```bash
POST /api/indicators/add
Content-Type: application/json

{
  "indicatorId": "ai_sr_zones",
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "params": {
    "lookbackPeriod": 200,
    "maxZones": 5,
    "clusterSensitivity": 1.5,
    "volumeWeight": 0.3,
    "rejectionWeight": 0.4,
    "touchWeight": 0.3
  }
}
```

### Response

```json
{
  "success": true,
  "indicatorInstanceId": "ai_sr_zones_abc123",
  "zones": [
    {
      "type": "resistance",
      "centerPrice": 51800.00,
      "topPrice": 51858.00,
      "bottomPrice": 51742.00,
      "strength": 0.85,
      "touchCount": 7
    }
  ]
}
```

## Advanced Customization

### Custom Color Schemes

**Dark Theme:**
```json
{
  "supportColor": "rgba(0, 255, 128, 0.2)",
  "resistanceColor": "rgba(255, 64, 64, 0.2)"
}
```

**Light Theme:**
```json
{
  "supportColor": "rgba(0, 128, 0, 0.15)",
  "resistanceColor": "rgba(255, 0, 0, 0.15)"
}
```

### Weight Configuration Presets

**Volume-Heavy (Institutional Zones):**
```json
{ "volumeWeight": 0.7, "rejectionWeight": 0.2, "touchWeight": 0.1 }
```

**Rejection-Heavy (Price Action Zones):**
```json
{ "volumeWeight": 0.2, "rejectionWeight": 0.6, "touchWeight": 0.2 }
```

**Balanced (All-Around):**
```json
{ "volumeWeight": 0.33, "rejectionWeight": 0.34, "touchWeight": 0.33 }
```

## Future Enhancements

Planned features for future versions:
- [ ] Machine learning model for zone strength prediction
- [ ] Order flow integration for institutional zone detection
- [ ] Breakout prediction using zone characteristics
- [ ] Multi-timeframe zone correlation
- [ ] Historical backtesting with zone accuracy metrics

## Support

For issues, questions, or feature requests, please refer to the main project documentation or contact the development team.

---

**Version**: 1.0.0  
**Last Updated**: October 2025  
**License**: Proprietary

