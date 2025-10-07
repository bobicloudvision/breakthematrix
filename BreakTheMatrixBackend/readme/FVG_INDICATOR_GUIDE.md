# Fair Value Gap (FVG) Indicator Guide

## Overview

The **Fair Value Gap (FVG) Indicator** detects price imbalances where the market moved inefficiently, leaving gaps between candles. These gaps represent areas where institutional traders may have entered positions, and price often returns to "fill" these gaps.

**Based on:** LuxAlgo's Fair Value Gap indicator for TradingView  
**License:** CC BY-NC-SA 4.0  
**Implementation:** `FairValueGapIndicator.java`

---

## What is a Fair Value Gap?

A Fair Value Gap (FVG) is a **3-candle pattern** that reveals a price imbalance:

### Bullish FVG (Gap Up)
```
Current candle low > 2-bars-ago high
      ↓
   ┌────┐           Gap exists here (no overlap)
   │    │  ← Current candle
   └────┘
      ↑ Current Low

   [GAP - Fair Value Gap]  ← Price jumped, leaving inefficiency

   ┌────┐  ← 2-bars-ago candle
   │    │
   └────┘
      ↑ Old High
```

**Detection:** `low[0] > high[2]` AND `close[1] > high[2]`

### Bearish FVG (Gap Down)
```
   ┌────┐  ← 2-bars-ago candle
   │    │
   └────┘
      ↑ Old Low

   [GAP - Fair Value Gap]  ← Price dropped, leaving inefficiency

   ┌────┐
   │    │  ← Current candle
   └────┘
      ↓ Current High

Current candle high < 2-bars-ago low
```

**Detection:** `high[0] < low[2]` AND `close[1] < low[2]`

---

## Trading Concepts

### Why FVGs Matter

1. **Institutional Footprint**: Large orders can create gaps as the market rapidly absorbs liquidity
2. **Magnetic Effect**: Price tends to return to fill inefficiencies
3. **Support/Resistance**: Unfilled FVGs act as future support (bullish) or resistance (bearish)
4. **Confirmation**: When FVG is respected, it confirms institutional involvement

### Mitigation (Filling)

A Fair Value Gap is considered **mitigated** when:

- **Bullish FVG**: Price closes back **below** the gap bottom (returns from above)
- **Bearish FVG**: Price closes back **above** the gap top (returns from below)

Mitigation indicates the imbalance has been filled and the area has lost significance.

---

## Algorithm

### Detection Process

```
For each new candle:
1. Get 3-candle window: [current, middle, old]
2. Check for Bullish FVG:
   - current.low > old.high
   - middle.close > old.high
   - gap_size > threshold
   
3. Check for Bearish FVG:
   - current.high < old.low
   - middle.close < old.low
   - gap_size > threshold
   
4. Create FVG with:
   - top = current.low (bullish) or old.low (bearish)
   - bottom = old.high (bullish) or current.high (bearish)
   - timestamp = middle.closeTime
```

### Threshold Calculation

**Auto Threshold Mode:**
```java
threshold = cumulative_sum((high - low) / low) / bar_index
```
Automatically filters insignificant gaps based on average volatility.

**Manual Threshold Mode:**
```java
threshold = thresholdPercent / 100.0
```
Fixed percentage requirement (e.g., 0.5% minimum gap size).

### Mitigation Check

```
For each existing FVG:
  If bullish FVG:
    If current.close < fvg.bottom:
      Mark as mitigated
      Remove from active list
      
  If bearish FVG:
    If current.close > fvg.top:
      Mark as mitigated
      Remove from active list
```

### Dynamic Mode

When enabled, FVG levels **shrink** as price approaches:

**Bullish FVG (shrinks from top):**
```java
max = max(min(close, max), min)  // Top shrinks down toward bottom
```

**Bearish FVG (shrinks from bottom):**
```java
min = min(max(close, min), max)  // Bottom shrinks up toward top
```

This creates a "magnetic" effect visualization as price approaches the gap.

---

## Parameters

### Detection Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `thresholdPercent` | Decimal | 0.0 | Minimum gap size % (0 = all gaps) |
| `autoThreshold` | Boolean | false | Auto-calculate threshold from volatility |

**Threshold Examples:**
- `0.0` = Detect all gaps regardless of size
- `0.5` = Only gaps > 0.5% of price
- `1.0` = Only gaps > 1.0% of price

### Display Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `dynamic` | Boolean | false | Dynamic shrinking mode |
| `extend` | Integer | 20 | Width of FVG boxes in bars (5-100) |
| `showLast` | Integer | 0 | Number of unmitigated levels to show (0 = all) |
| `mitigationLevels` | Boolean | false | Show dashed lines when mitigated |

**Display Modes:**

**Static Mode** (`dynamic=false`):
- FVG boxes have fixed width (controlled by `extend` parameter)
- Box width = `extend` bars × average candle duration
- Boxes don't stretch indefinitely - they have a clean, consistent visual appearance
- Best for identifying historical gaps without cluttering the chart
- Clear visualization of exact gap zones

**Dynamic Mode** (`dynamic=true`):
- FVG levels shrink as price approaches
- Creates real-time "magnetic" effect
- Shows active price attraction to gaps
- No boxes displayed - only dynamic zone fills

**Box Width Control:**
The `extend` parameter controls how wide FVG boxes appear visually:

```
extend: 10 (narrow)          extend: 20 (default)         extend: 50 (wide)
┌──┐                         ┌────────┐                   ┌──────────────────┐
│FV│                         │  FVG   │                   │       FVG        │
└──┘                         └────────┘                   └──────────────────┘
 ↑                            ↑                            ↑
Created                     Created                      Created
```

- `extend: 10` = Narrow boxes (10 bars wide) - Clean, minimal
- `extend: 20` = Medium boxes (20 bars wide, default) - Balanced visibility
- `extend: 50` = Wide boxes (50 bars wide) - Maximum visibility

**How it works:**
1. Indicator tracks average candle duration (e.g., 15 minutes for 15m chart)
2. Box width = `extend` × average candle duration
3. Example: On a 15m chart with `extend: 20`, boxes are 20 × 15min = 300 minutes (5 hours) wide
4. Boxes never extend beyond the current candle time

**Important:** The box width is purely visual. FVGs remain active and can be mitigated regardless of box width. The `extend` parameter just controls how far the box extends on the chart for clarity.

### Visual Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `bullishColor` | String | #089981 | Color for bullish FVGs (green) |
| `bearishColor` | String | #f23645 | Color for bearish FVGs (red) |
| `showDashboard` | Boolean | false | Display statistics panel |

---

## Usage Examples

### Example 1: Default Settings (All FVGs)

```json
{
  "indicatorId": "fvg",
  "params": {
    "thresholdPercent": 0.0,
    "autoThreshold": false,
    "dynamic": false,
    "extend": 20,
    "showLast": 0
  }
}
```

**Result:** Shows all Fair Value Gaps as static boxes 20 bars wide, regardless of gap size.

### Example 2: Significant FVGs Only (Narrow Boxes)

```json
{
  "indicatorId": "fvg",
  "params": {
    "thresholdPercent": 0.5,
    "autoThreshold": false,
    "dynamic": false,
    "extend": 10,
    "showLast": 5
  }
}
```

**Result:** Only shows gaps > 0.5% with narrow 10-bar-wide boxes, plus horizontal lines at the 5 most recent unmitigated levels.

### Example 3: Dynamic Mode with Auto Threshold

```json
{
  "indicatorId": "fvg",
  "params": {
    "autoThreshold": true,
    "dynamic": true,
    "showDashboard": true
  }
}
```

**Result:** Automatically filters gaps, shows dynamic shrinking, displays statistics.

### Example 4: Conservative (Large Gaps Only)

```json
{
  "indicatorId": "fvg",
  "params": {
    "thresholdPercent": 1.0,
    "showLast": 3,
    "mitigationLevels": true
  }
}
```

**Result:** Only large gaps (>1%), shows 3 nearest levels, displays mitigation history.

---

## Trading Strategies

### Strategy 1: FVG Retest Entry

**Setup:**
1. Wait for Bullish FVG to form during uptrend
2. Price moves above the gap
3. Price returns to test the FVG top

**Entry:**
- **Long**: When price bounces from FVG top
- **Stop Loss**: Below FVG bottom
- **Target**: Next resistance or 1:2 risk/reward

**Confirmation:**
- Volume decreases on pullback
- RSI oversold on pullback
- Price respects FVG without closing below

### Strategy 2: FVG Mitigation Reversal

**Setup:**
1. Multiple FVGs in one direction indicate exhaustion
2. Price fails to respect FVG (closes through it)

**Entry:**
- **Reversal**: When FVG is mitigated
- **Stop Loss**: Beyond the mitigated FVG
- **Target**: Previous swing or major support/resistance

**Confirmation:**
- Multiple FVGs mitigated in succession
- Volume increases on mitigation
- Candlestick reversal patterns

### Strategy 3: FVG Confluence Zones

**Setup:**
1. Identify multiple unmitigated FVGs near same level
2. Combine with other support/resistance
3. Wait for price to approach confluence zone

**Entry:**
- **Long/Short**: At confluence zone
- **Stop Loss**: Beyond the FVG cluster
- **Target**: Next major level

**Confluence Factors:**
- FVG + Order Block
- FVG + Fibonacci level
- FVG + Moving Average
- FVG + Pivot Point

### Strategy 4: Breakout Confirmation

**Setup:**
1. Price consolidates near key level
2. Breakout creates FVG (shows strength)

**Entry:**
- **Breakout trade**: In direction of FVG
- **Stop Loss**: Inside the FVG
- **Target**: Measured move from consolidation

**Logic:** Strong breakouts leave FVGs, weak ones don't.

---

## Output Data Structure

### Values

```java
Map<String, BigDecimal> values = {
    // Dynamic mode values (if enabled)
    "maxBullFvg": 50250.00,      // Top of current bullish FVG
    "minBullFvg": 50150.00,      // Bottom of current bullish FVG
    "maxBearFvg": 49850.00,      // Top of current bearish FVG
    "minBearFvg": 49750.00,      // Bottom of current bearish FVG
    
    // Statistics
    "bullCount": 15,              // Total bullish FVGs detected
    "bearCount": 12,              // Total bearish FVGs detected
    "bullMitigated": 10,          // Bullish FVGs filled
    "bearMitigated": 8,           // Bearish FVGs filled
    "bullMitigationRate": 66.67,  // % of bullish FVGs mitigated
    "bearMitigationRate": 66.67   // % of bearish FVGs mitigated
}
```

### Boxes (Static Mode)

```java
List<Map<String, Object>> boxes = [
    {
        "time1": 1704067200,           // Start timestamp (seconds)
        "time2": 1704153600,           // End timestamp (seconds)
        "price1": 50250.00,            // Top price
        "price2": 50150.00,            // Bottom price
        "color": "rgba(8, 153, 129, 0.30)",  // Fill color
        "borderColor": "transparent",   // Border color
        "label": "Bull FVG"            // Label text
    }
    // ... more boxes
]
```

### Lines (Unmitigated Levels)

```java
List<Map<String, Object>> lines = [
    {
        "time1": 1704067200,           // Start timestamp
        "price1": 50150.00,            // Price level
        "time2": 1704153600,           // End timestamp (current)
        "price2": 50150.00,            // Same price (horizontal)
        "color": "#089981",            // Line color
        "lineWidth": 2,                // Width in pixels
        "lineStyle": "solid",          // solid/dashed/dotted
        "label": "Bull FVG Level"      // Label
    }
    // ... more lines
]
```

### Dashboard (Statistics Panel)

```java
Map<String, Object> dashboard = {
    "bullCount": 15,
    "bearCount": 12,
    "bullMitigated": 10,
    "bearMitigated": 8,
    "bullMitigationRate": "66.7%",
    "bearMitigationRate": "66.7%"
}
```

---

## State Management

### FVGState Class

```java
public static class FVGState {
    // 3-candle buffer for pattern detection
    public List<CandlestickData> candleBuffer;
    
    // All detected FVGs
    public List<FVG> fvgRecords;
    
    // Dynamic mode levels
    public BigDecimal maxBullFvg;
    public BigDecimal minBullFvg;
    public BigDecimal maxBearFvg;
    public BigDecimal minBearFvg;
    
    // Statistics
    public int bullCount;
    public int bearCount;
    public int bullMitigated;
    public int bearMitigated;
    
    // Threshold calculation
    public BigDecimal cumulativeDelta;
    public int barIndex;
}
```

### FVG Data Structure

```java
public static class FVG {
    public BigDecimal max;        // Top of gap
    public BigDecimal min;        // Bottom of gap
    public boolean isBullish;     // Direction
    public Instant time;          // Creation time
    public int barIndex;          // Bar number
    public boolean mitigated;     // Filled status
}
```

---

## Performance Considerations

### Memory Management

**FVG Limit:** Max 500 FVGs stored to prevent memory issues
```java
while (state.fvgRecords.size() > 500) {
    state.fvgRecords.remove(state.fvgRecords.size() - 1);
}
```

**Candle Buffer:** Only keeps last 3 candles
```java
if (state.candleBuffer.size() > 3) {
    state.candleBuffer.remove(0);
}
```

### Computational Complexity

- **Detection:** O(1) per candle (3-bar pattern check)
- **Mitigation:** O(n) per candle (check all active FVGs)
- **Rendering:** O(m) where m = number of unmitigated FVGs

**Optimization Tips:**
1. Use `showLast` parameter to limit visualization
2. Enable `autoThreshold` to reduce noise
3. Only show nearby FVGs in live trading

---

## Interpretation Guide

### Strong Signals

✅ **High Probability Setups:**
- FVG respected multiple times before mitigation
- FVG aligned with major support/resistance
- FVG confirmed by volume profile
- FVG at key Fibonacci levels
- Multiple FVGs in same zone (confluence)

### Weak Signals

⚠️ **Lower Probability Scenarios:**
- Very small FVGs (use threshold filtering)
- FVG in choppy/ranging markets
- FVG immediately mitigated (no respect)
- FVG far from current price action
- FVG against major trend

### Market Context

**Trending Markets:**
- FVGs in trend direction = continuation zones
- FVGs against trend = exhaustion signals
- Respected FVGs = strong trend
- Mitigated FVGs = potential reversal

**Ranging Markets:**
- FVGs less reliable (frequent whipsaws)
- Use with other support/resistance
- Wait for breakout to create significant FVG
- Focus on larger gaps only

**Volatile Markets:**
- More FVGs detected
- Use higher threshold (1-2%)
- Focus on multi-timeframe confluence
- Be cautious of fake-outs

---

## Multi-Timeframe Analysis

### HTF (Higher Timeframe) FVGs

**Daily/4H FVGs:**
- More significant
- Better risk/reward
- Longer-term support/resistance
- Use for swing trading

**Strategy:**
```
1. Identify Daily FVG
2. Switch to 1H chart
3. Wait for price to approach FVG
4. Enter on 15m confirmation
5. Stop below/above Daily FVG
```

### LTF (Lower Timeframe) FVGs

**5m/15m FVGs:**
- Short-term entries
- Scalping opportunities
- Quick fills
- Lower significance

**Strategy:**
```
1. Identify HTF trend/bias
2. Wait for LTF FVG in trend direction
3. Enter immediately on formation
4. Quick profit target (1:1 or 1:2)
```

### Confluence Strategy

**Ideal Setup:**
```
Daily Bullish FVG at 50000
  ↓
4H Bullish FVG at 50100
  ↓
1H Bullish FVG at 50150
  ↓
ENTRY: 50150 (triple confluence)
STOP: 49950 (below Daily FVG)
TARGET: 51000 (next resistance)
```

---

## Integration with Other Indicators

### FVG + Order Blocks

**Confluence:**
- Order Block marks institutional entry
- FVG marks rapid price movement
- Both at same level = ultra-strong zone

### FVG + Smart Money Concepts

**Combined Analysis:**
- Break of Structure (BOS) + FVG = strong continuation
- Change of Character (CHoCH) + FVG = potential reversal
- Equal Highs/Lows + FVG = liquidity zone

### FVG + Volume Profile

**Volume Confirmation:**
- High volume at FVG = more likely to hold
- Low volume at FVG = may get mitigated
- Volume profile POC + FVG = key level

### FVG + Moving Averages

**Trend Confirmation:**
- FVG above 200 MA = bullish context
- FVG below 200 MA = bearish context
- MA crossover + FVG = entry trigger

---

## Statistics & Metrics

### Mitigation Rate

**Formula:**
```
Mitigation Rate = (Mitigated FVGs / Total FVGs) × 100%
```

**Interpretation:**
- High rate (>70%): Price efficiently fills gaps
- Medium rate (40-70%): Normal market behavior
- Low rate (<40%): Strong trending market

### FVG Density

**High Density:**
- Many FVGs in short period
- Indicates volatile, inefficient price action
- Potential exhaustion signal

**Low Density:**
- Few FVGs
- Indicates smooth, efficient price movement
- Potential consolidation or trend strength

---

## API Endpoints

### Add FVG Indicator

**Endpoint:** `POST /api/bot/indicators/instances`

**Request:**
```json
{
  "instanceId": "fvg_btcusdt_15m",
  "indicatorId": "fvg",
  "symbol": "BTCUSDT",
  "interval": "15m",
  "params": {
    "thresholdPercent": 0.5,
    "autoThreshold": false,
    "dynamic": false,
    "showLast": 5,
    "extend": 20,
    "bullishColor": "#089981",
    "bearishColor": "#f23645",
    "showDashboard": true
  }
}
```

**Response:**
```json
{
  "success": true,
  "instanceId": "fvg_btcusdt_15m",
  "message": "Fair Value Gap indicator started successfully"
}
```

### Get FVG Data

**Endpoint:** `GET /api/bot/indicators/instances/{instanceId}/data`

**Response:**
```json
{
  "values": {
    "bullCount": 15,
    "bearCount": 12,
    "bullMitigated": 10,
    "bearMitigated": 8,
    "bullMitigationRate": 66.67,
    "bearMitigationRate": 66.67
  },
  "boxes": [
    {
      "time1": 1704067200,
      "time2": 1704153600,
      "price1": 50250.00,
      "price2": 50150.00,
      "color": "rgba(8, 153, 129, 0.30)",
      "borderColor": "transparent",
      "label": "Bull FVG"
    }
  ],
  "lines": [...],
  "dashboard": {
    "bullCount": 15,
    "bearCount": 12,
    "bullMitigationRate": "66.7%",
    "bearMitigationRate": "66.7%"
  }
}
```

---

## Troubleshooting

### Issue: Too Many FVGs / Cluttered Chart

**Solution:**
```json
{
  "thresholdPercent": 1.0,     // Increase threshold
  "showLast": 10,              // Limit display count
  "extend": 10,                // Use narrower boxes
  "autoThreshold": true        // Use auto-filtering
}
```

### Issue: Missing FVGs

**Solution:**
```json
{
  "thresholdPercent": 0.0,     // Disable filtering
  "autoThreshold": false       // Disable auto-threshold
}
```

### Issue: Boxes Too Wide / Too Narrow

**Problem:** FVG boxes extend too far or not far enough on the chart.

**Solution:** Adjust the `extend` parameter:
```json
{
  "extend": 10   // For narrow, compact boxes (good for lower timeframes)
}
```
```json
{
  "extend": 30   // For wider boxes (good for higher timeframes or more visibility)
}
```

**Recommendations by Timeframe:**
- **1m-5m charts:** `extend: 10-15` (narrow boxes)
- **15m-1H charts:** `extend: 20` (default, balanced)
- **4H-Daily charts:** `extend: 30-50` (wider boxes)

**Note:** Box width is purely cosmetic. FVGs remain active and trackable regardless of how wide the boxes appear.

### Issue: FVGs Not Mitigating

**Possible Causes:**
1. Strong trending market (gaps respected)
2. Threshold too high (only large gaps detected)
3. Timeframe too high (takes longer to fill)

### Issue: Dynamic Mode Not Working

**Check:**
1. Ensure `dynamic: true` in params
2. Verify FVGs are being detected
3. Check that price is approaching FVGs

---

## Best Practices

### 1. Timeframe Selection

- **Scalping (1m-5m)**: Use low threshold, quick reactions
- **Day Trading (15m-1H)**: Medium threshold, intraday setups
- **Swing Trading (4H-D)**: High threshold, significant gaps only

### 2. Threshold Configuration

**Conservative (Quality over Quantity):**
```json
{
  "thresholdPercent": 1.0,
  "showLast": 5
}
```

**Aggressive (More Opportunities):**
```json
{
  "autoThreshold": true,
  "showLast": 0
}
```

### 3. Combining with Price Action

✅ **Do:**
- Wait for candlestick confirmation at FVG
- Check volume on FVG approach
- Look for multiple rejections from FVG
- Use FVGs with trend direction

❌ **Don't:**
- Blindly enter at every FVG
- Ignore overall market structure
- Trade against major trend
- Use FVGs as only signal

### 4. Risk Management

**Position Sizing:**
```
Risk per trade = Account × 1-2%
Stop loss = Below/Above FVG
Position size = Risk / Stop distance
```

**Entry Refinement:**
- Enter at FVG top/bottom (better risk/reward)
- Scale in as FVG proves itself
- Use limit orders at FVG levels

---

## Code Structure

### Class Hierarchy

```
AbstractIndicator
    ↓
FairValueGapIndicator
    ├── FVG (data class)
    └── FVGState (state class)
```

### Key Methods

```java
// Initialize with historical data
Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params)

// Process each new candle
Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state)

// Detect 3-bar FVG pattern
private void detectFVG(FVGState state, CandlestickData candle, Map<String, Object> params)

// Check if existing FVGs are mitigated
private void checkMitigation(FVGState state, CandlestickData candle, Map<String, Object> params)

// Calculate threshold
private BigDecimal calculateThreshold(FVGState state, Map<String, Object> params, CandlestickData candle)

// Build result with boxes and lines
private Map<String, Object> buildResult(FVGState state, CandlestickData candle, Map<String, Object> params)

// Convert hex to rgba
private String hexToRgba(String hex, double opacity)
```

---

## References

- **Original Indicator:** [Fair Value Gap [LuxAlgo]](https://www.tradingview.com/script/example/) by LuxAlgo
- **License:** CC BY-NC-SA 4.0 (Attribution-NonCommercial-ShareAlike)
- **Documentation:** Based on Smart Money Concepts and Institutional Trading principles

---

## Version History

**v1.0.0** - Initial Implementation
- 3-candle FVG detection (bullish/bearish)
- Auto and manual threshold modes
- Static and dynamic display modes
- Mitigation tracking
- Unmitigated level lines
- Statistics dashboard
- Full API integration

---

## Support

For issues or questions:
1. Check this guide first
2. Review example configurations
3. Test with default parameters
4. Check API response format
5. Verify indicator is running: `GET /api/bot/indicators/instances`

