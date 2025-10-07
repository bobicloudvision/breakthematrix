# CVD (Cumulative Volume Delta) Indicator Guide

## What is CVD?

CVD tracks the **cumulative difference between buy volume and sell volume** over time. It shows you the net directional flow of volume and helps identify trend strength, divergences, and institutional accumulation/distribution.

## How to Use

### Activate the Indicator

**REST API:**
```bash
POST http://localhost:8080/api/indicators/instances/activate
```

**Request Body:**
```json
{
  "indicatorId": "cvd",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "params": {
    "resetOnNewSession": false,
    "smoothingPeriod": 0
  }
}
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resetOnNewSession` | boolean | `false` | Reset CVD at start of each trading day |
| `smoothingPeriod` | integer | `0` | Smooth CVD line over N candles (0 = no smoothing) |

### Output

The indicator returns 3 values per candle:

```json
{
  "values": {
    "cvd": 12345.67,           // Current cumulative delta
    "candleDelta": 123.45,     // Delta for this candle only  
    "deltaChange": 45.67       // Change from previous candle
  },
  "color": "#26a69a"           // Green = uptrend, Red = downtrend
}
```

## Trading Signals

### 🟢 Bullish Signals

1. **CVD Trending Up**
   - CVD line consistently rising
   - Indicates sustained buying pressure
   - Confirms uptrend

2. **Bullish Divergence**
   - Price makes lower low
   - CVD makes higher low
   - **Signal**: Buying pressure increasing despite price drop → potential reversal up

3. **CVD Breakout**
   - CVD breaks above previous high
   - Shows buyers taking control
   - Entry signal for long

### 🔴 Bearish Signals

1. **CVD Trending Down**
   - CVD line consistently falling
   - Indicates sustained selling pressure
   - Confirms downtrend

2. **Bearish Divergence**
   - Price makes higher high
   - CVD makes lower high
   - **Signal**: Selling pressure increasing despite price rise → potential reversal down

3. **CVD Breakdown**
   - CVD breaks below previous low
   - Shows sellers taking control
   - Entry signal for short

## Examples

### Example 1: Bullish Divergence
```
Time    Price    CVD
10:00   50000    1000
10:05   49500    1100  ← Price down, CVD up
10:10   49800    1250  ← Bullish divergence!
10:15   50500    1400  ← Price rallies
```

### Example 2: Bearish Divergence
```
Time    Price    CVD
10:00   50000    1000
10:05   50500    900   ← Price up, CVD down
10:10   51000    800   ← Bearish divergence!
10:15   50200    700   ← Price drops
```

### Example 3: Strong Trend
```
Time    Price    CVD
10:00   50000    1000
10:05   50200    1150  ← Both rising
10:10   50400    1300  ← Strong uptrend
10:15   50600    1450  ← Continue long
```

## Tips

### Best Timeframes
- **Scalping**: 1m, 3m (quick signals)
- **Day Trading**: 5m, 15m (balanced)
- **Swing Trading**: 1h, 4h (big picture)

### Combine With
- **Price Action**: CVD confirms breakouts
- **Support/Resistance**: CVD shows where buyers/sellers step in
- **Other Indicators**: Use CVD to confirm RSI, MACD signals

### Common Mistakes to Avoid
1. ❌ Trading CVD alone without price confirmation
2. ❌ Ignoring divergences (powerful signals!)
3. ❌ Using on very low volume pairs (unreliable)
4. ❌ Not waiting for candle close confirmation

## Visualization

CVD displays in a **separate pane below the main chart**:

```
┌─────────────────────────────────────┐
│                                     │
│         Price Chart (OHLC)          │
│                                     │
├─────────────────────────────────────┤
│                                     │
│    CVD Line (green up, red down)    │
│                                     │
└─────────────────────────────────────┘
```

**Colors:**
- 🟢 **Green line**: CVD increasing (net buying)
- 🔴 **Red line**: CVD decreasing (net selling)

## Advanced Usage

### Reset on New Session
Set `resetOnNewSession: true` to reset CVD at the start of each trading day:

```json
{
  "params": {
    "resetOnNewSession": true
  }
}
```

**Use when:**
- ✅ Day trading (focus on intraday flow)
- ✅ Markets with clear sessions (stocks)

**Don't use when:**
- ❌ Swing trading (need cumulative data)
- ❌ Crypto 24/7 markets

### Smoothing
Set `smoothingPeriod` to smooth the CVD line:

```json
{
  "params": {
    "smoothingPeriod": 5
  }
}
```

**Use when:**
- ✅ Noisy data (lots of small trades)
- ✅ Want cleaner signals

**Don't use when:**
- ❌ Need precise entry/exit timing
- ❌ Scalping (delay can hurt)

## Real-World Example

**BTCUSDT 5m Chart:**

```
Price makes new high: $51,000
CVD makes lower high: Bearish divergence!
→ Short signal

Result: Price drops to $50,200 within 30 minutes
Profit: 1.6% move
```

This is a classic bearish divergence - price looked strong, but CVD showed sellers were in control.

## API Endpoints

### Activate CVD
```bash
POST /api/indicators/instances/activate
Content-Type: application/json

{
  "indicatorId": "cvd",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "params": {}
}
```

### Get CVD Historical Data
```bash
GET /api/indicators/historical?instanceKey={instanceKey}&count=100
```

### Deactivate CVD
```bash
DELETE /api/indicators/instances/{instanceKey}
```

## WebSocket Updates

CVD sends real-time updates via WebSocket as trades occur:

```json
{
  "type": "indicatorUpdate",
  "instanceKey": "Binance:BTCUSDT:5m:cvd:abc123",
  "timestamp": "2025-10-06T14:43:00Z",
  "data": {
    "cvd": 12345.67,
    "candleDelta": 123.45,
    "deltaChange": 45.67
  },
  "color": "#26a69a"
}
```

## Performance Notes

- **Historical Loading**: Loads up to 5000 candles with full trade data
- **Live Updates**: Real-time delta calculation on every trade
- **Memory**: Very lightweight (just tracks cumulative number)
- **Speed**: Instant calculations (simple addition/subtraction)

## Troubleshooting

### "No historical data"
- Check that trades are being loaded: Look for console message "✅ Loaded X historical trades"
- Trades may only cover recent candles (like Bookmap)

### "CVD not updating live"
- Ensure WebSocket is connected
- Check that trades are coming in real-time

### "CVD seems wrong"
- Verify aggressive buy/sell detection in trade data
- Check if `resetOnNewSession` is causing unexpected resets

## Next Steps

1. **Activate CVD** on your favorite symbol
2. **Watch for divergences** (most powerful signal)
3. **Compare with price** to confirm trends
4. **Combine with Bookmap** for full order flow picture!

Happy Trading! 🚀

