# Order Flow Indicators Guide

This guide explains how to use the powerful order flow indicators built from footprint candle and order flow data.

## Overview

Five advanced order flow indicators have been created to help identify high-probability trading setups:

1. **Cumulative Volume Delta (CVD)** - Tracks buying vs selling pressure over time
2. **Order Flow Imbalance** - Detects significant bid/ask imbalances
3. **Volume Profile POC** - Shows Point of Control and Value Area
4. **Delta Divergence** - Identifies price/delta divergences
5. **Absorption** - Detects large volume with minimal price movement

All indicators are automatically registered with the system and available through REST API and WebSocket.

---

## 1. Cumulative Volume Delta (CVD)

**ID:** `cvd`  
**Category:** Order Flow  
**Display:** Separate pane below price chart

### Description

Tracks the cumulative difference between buying and selling volume. Shows institutional accumulation/distribution.

### Key Signals

- **Rising CVD** = Accumulation (buying pressure)
- **Falling CVD** = Distribution (selling pressure)
- **Bullish Divergence**: Price makes lower low, CVD makes higher low → Reversal up
- **Bearish Divergence**: Price makes higher high, CVD makes lower high → Reversal down

### Parameters

```json
{
  "interval": "1m",           // Time interval (1m, 5m, 15m, etc.)
  "lookback": 100,            // Number of candles to analyze
  "showDivergences": true     // Highlight divergences
}
```

### API Usage

```bash
# Get CVD indicator data
GET /api/indicators/cvd/calculate?symbol=BTCUSDT&interval=1m&limit=100

# Response
{
  "cvd": 15234.50,
  "dataPoints": [
    {"time": 1234567890, "cvd": 15234.50, "delta": 123.45},
    ...
  ],
  "divergences": [
    {
      "type": "bullish",
      "time": 1234567890,
      "priceStart": 50000,
      "priceEnd": 49500,
      "deltaStart": -1000,
      "deltaEnd": -500
    }
  ]
}
```

### Trading Strategy

```java
// Example: Trade CVD divergences
if (cvdDivergence == "bullish" && price near support) {
    // Strong buy signal
    enterLong();
} else if (cvdDivergence == "bearish" && price near resistance) {
    // Strong sell signal
    enterShort();
}
```

---

## 2. Order Flow Imbalance

**ID:** `ofi`  
**Category:** Order Flow  
**Display:** Separate pane (histogram)

### Description

Identifies significant imbalances between buying and selling volume at specific price levels. Large imbalances indicate strong directional pressure.

### Key Signals

- **Imbalance Ratio > 2.0** = Strong buying pressure (2:1 buy/sell ratio)
- **Imbalance Ratio < 0.5** = Strong selling pressure (1:2 buy/sell ratio)
- **Consecutive Imbalances** = Trend strength confirmation
- **Imbalance at S/R** = High probability breakout/breakdown

### Parameters

```json
{
  "interval": "1m",           // Time interval
  "threshold": 2.0,           // Minimum ratio (2.0 = 2:1)
  "minVolume": 10.0           // Minimum volume to consider
}
```

### API Usage

```bash
# Get order flow imbalance data
GET /api/indicators/ofi/calculate?symbol=BTCUSDT&interval=1m&threshold=2.0

# Response
{
  "imbalance": 2.5,
  "significantImbalances": [
    {
      "time": 1234567890,
      "price": 50000,
      "ratio": 2.5,
      "direction": "buy",
      "buyVolume": 250,
      "sellVolume": 100
    }
  ]
}
```

### Trading Strategy

```java
// Example: Trade strong imbalances
if (buyImbalance > 2.0 && consecutiveImbalances >= 3) {
    // Strong buying pressure
    enterLong();
    setStopLoss(recentLow);
}

if (sellImbalance > 2.0 && price at resistance) {
    // Strong selling pressure at key level
    enterShort();
}
```

---

## 3. Volume Profile POC

**ID:** `vpoc`  
**Category:** Order Flow  
**Display:** Overlays on price chart

### Description

Shows the price level with the highest traded volume (Point of Control) and Value Area boundaries (70% of volume). POC acts as strong support/resistance.

### Key Concepts

- **POC**: Price with maximum volume → Strong support/resistance
- **Value Area High (VAH)**: Upper boundary of 70% volume
- **Value Area Low (VAL)**: Lower boundary of 70% volume
- **Price above POC**: Bullish control
- **Price below POC**: Bearish control

### Parameters

```json
{
  "interval": "1m",           // Time interval
  "lookback": 50,             // Candles to include in profile
  "valueAreaPercent": 70.0    // % of volume for value area
}
```

### API Usage

```bash
# Get Volume Profile POC data
GET /api/indicators/vpoc/calculate?symbol=BTCUSDT&interval=5m&lookback=50

# Response
{
  "poc": 50000,
  "vah": 50500,
  "val": 49500,
  "volumeProfile": {
    "50000": 1500,
    "50100": 1200,
    ...
  }
}
```

### Trading Strategy

```java
// Example: Trade POC bounces
if (price touches POC from above && buyVolume increases) {
    // POC acting as support
    enterLong();
    setTarget(VAH);
}

if (price breaks POC with high volume) {
    // POC breakdown
    enterShort();
    setTarget(VAL);
}

// Mean reversion
if (price > VAH && showing exhaustion) {
    // Price extended above value area
    enterShort();
    setTarget(POC);
}
```

---

## 4. Delta Divergence

**ID:** `delta_div`  
**Category:** Order Flow  
**Display:** Separate pane (histogram)

### Description

Identifies divergences between price movement and volume delta. These divergences signal potential reversals or weakening trends.

### Divergence Types

1. **Bullish Divergence**: Price lower low, delta higher low → Accumulation
2. **Bearish Divergence**: Price higher high, delta lower high → Distribution
3. **Hidden Bullish**: Price higher low, delta lower low → Continuation up
4. **Hidden Bearish**: Price lower high, delta higher high → Continuation down

### Parameters

```json
{
  "interval": "1m",           // Time interval
  "swingLookback": 5,         // Candles for swing detection
  "divLookback": 50           // Max bars for divergence patterns
}
```

### API Usage

```bash
# Get delta divergence data
GET /api/indicators/delta_div/calculate?symbol=BTCUSDT&interval=5m

# Response
{
  "delta": 123.45,
  "divergenceSignal": 1,
  "divergences": [
    {
      "type": "bullish",
      "priceStart": 50000,
      "priceEnd": 49500,
      "deltaStart": -1000,
      "deltaEnd": -500,
      "time": 1234567890
    }
  ],
  "priceSwings": [...],
  "deltaSwings": [...]
}
```

### Trading Strategy

```java
// Example: Trade divergences with confirmation
if (bullishDivergence && price at support && RSI < 30) {
    // Triple confluence
    enterLong();
    setStopLoss(below support);
    setTarget(resistance);
}

if (bearishDivergence && price at resistance && RSI > 70) {
    // High probability short
    enterShort();
    setStopLoss(above resistance);
}
```

---

## 5. Absorption Indicator

**ID:** `absorption`  
**Category:** Order Flow  
**Display:** Separate pane (histogram)

### Description

Identifies "absorption" zones where large volume trades with minimal price movement. Indicates strong buyers/sellers defending a price level.

### Key Concepts

- **Buy Absorption**: High volume but price doesn't rise → Sellers present
- **Sell Absorption**: High volume but price doesn't fall → Buyers present
- **Absorption at Resistance**: Potential reversal (buyers absorbing)
- **Absorption at Support**: Potential reversal (sellers absorbing)

### Parameters

```json
{
  "interval": "1m",           // Time interval
  "volumeThreshold": 1.5,     // Volume multiplier (1.5 = 150% of avg)
  "priceMovementMax": 0.5,    // Max price movement % (0.5%)
  "lookbackAvg": 20           // Candles for average volume
}
```

### API Usage

```bash
# Get absorption data
GET /api/indicators/absorption/calculate?symbol=BTCUSDT&interval=1m

# Response
{
  "absorptionScore": 75.5,
  "absorptionEvents": [
    {
      "time": 1234567890,
      "price": 50000,
      "volume": 500,
      "absorptionScore": 85.2,
      "delta": 100,
      "direction": "buy"
    }
  ],
  "avgVolume": 250
}
```

### Trading Strategy

```java
// Example: Trade absorption at key levels
if (absorptionScore > 70 && price at support && direction == "buy") {
    // Strong buyers defending support
    enterLong();
    setStopLoss(below absorption zone);
    setTarget(nextResistance);
}

if (absorptionScore > 70 && price at resistance && direction == "sell") {
    // Strong sellers defending resistance
    enterShort();
    setStopLoss(above absorption zone);
}

// Failed absorption = strong breakout
if (absorptionZone && price breaks through with high volume) {
    // Absorption failed
    enterInBreakoutDirection();
}
```

---

## Combined Strategy Examples

### 1. Institutional Accumulation Setup

```java
// Confluence of multiple order flow signals
boolean bullishSetup = 
    cvd.showsHigherLows() &&              // Accumulation
    deltaDiv.hasBullishDivergence() &&    // Smart money buying
    absorption.detectsBuyAbsorption() &&   // Support defense
    price.nearSupport();                   // Key level

if (bullishSetup) {
    enterLong();
    // Very high probability setup
}
```

### 2. Distribution Top Pattern

```java
// Signs of institutional distribution
boolean bearishSetup =
    cvd.showsLowerHighs() &&              // Distribution
    ofi.showsSellImbalances() &&          // Strong selling
    price.aboveVAH() &&                    // Extended
    deltaDiv.hasBearishDivergence();      // Weakening

if (bearishSetup) {
    enterShort();
    setTarget(vpoc.getPOC());
}
```

### 3. POC Bounce with Confirmation

```java
// High probability mean reversion
if (price.touchesPOC() && 
    absorption.detectsBuyAbsorption() &&
    ofi.showsBuyImbalance() > 2.0) {
    
    enterLong();
    setStopLoss(below POC);
    setTarget(VAH);
}
```

### 4. Breakout Confirmation

```java
// Order flow confirms breakout
if (price.breaksResistance() &&
    cvd.breaksToNewHigh() &&              // New CVD high
    ofi.showsConsecutiveBuyImbalances() && // Strong buying
    volumeProfile.pocBreaks()) {           // Volume confirms
    
    enterLong();
    // Validated breakout
}
```

---

## REST API Endpoints

### List All Indicators

```bash
GET /api/indicators/list

# Filter by category
GET /api/indicators/list?category=ORDER_FLOW
```

### Calculate Indicator

```bash
GET /api/indicators/{indicatorId}/calculate?symbol=BTCUSDT&interval=1m&limit=100

# With custom parameters
GET /api/indicators/cvd/calculate?symbol=BTCUSDT&interval=5m&lookback=200&showDivergences=true
```

### Get Indicator Metadata

```bash
GET /api/indicators/{indicatorId}/info

# Response
{
  "id": "cvd",
  "name": "Cumulative Volume Delta",
  "description": "Tracks cumulative buying vs selling pressure...",
  "category": "ORDER_FLOW",
  "parameters": {
    "interval": {
      "displayName": "Time Interval",
      "type": "STRING",
      "defaultValue": "1m",
      "required": false
    },
    ...
  }
}
```

---

## WebSocket Real-Time Updates

Subscribe to real-time indicator updates:

```javascript
// Connect to trading WebSocket
const ws = new WebSocket('ws://localhost:8080/trading-ws');

// Subscribe to footprint candles (required for order flow indicators)
ws.send(JSON.stringify({
  action: 'subscribe',
  symbols: ['BTCUSDT']
}));

// Listen for updates
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  if (data.type === 'FOOTPRINT') {
    // Footprint candle received
    // Order flow indicators automatically update
    console.log('CVD:', data.footprintCandle.cumulativeDelta);
    console.log('Delta:', data.footprintCandle.delta);
    console.log('POC:', data.footprintCandle.pointOfControl);
  }
};
```

---

## Integration with Trading Strategies

### Create Custom Strategy Using Order Flow Indicators

```java
@Component
public class OrderFlowStrategy extends AbstractTradingStrategy {
    
    private final IndicatorService indicatorService;
    
    @Override
    public void analyze(List<CandlestickData> candles, TradingBot bot) {
        // Calculate order flow indicators
        Map<String, BigDecimal> cvd = indicatorService.calculate("cvd", candles, 
            Map.of("interval", "5m", "lookback", 100));
        
        Map<String, BigDecimal> ofi = indicatorService.calculate("ofi", candles,
            Map.of("interval", "5m", "threshold", 2.0));
        
        Map<String, BigDecimal> vpoc = indicatorService.calculate("vpoc", candles,
            Map.of("interval", "5m", "lookback", 50));
        
        // Trading logic
        BigDecimal currentPrice = candles.get(candles.size() - 1).getClose();
        BigDecimal poc = vpoc.get("poc");
        BigDecimal imbalance = ofi.get("imbalance");
        
        // Buy signal: Price at POC with buy imbalance
        if (currentPrice.subtract(poc).abs().compareTo(BigDecimal.valueOf(10)) < 0 &&
            imbalance.compareTo(BigDecimal.valueOf(2.0)) > 0) {
            
            enterLong(bot, currentPrice);
        }
        
        // Sell signal: Price breaks POC down with sell imbalance
        if (currentPrice.compareTo(poc) < 0 &&
            imbalance.compareTo(BigDecimal.valueOf(0.5)) < 0) {
            
            enterShort(bot, currentPrice);
        }
    }
}
```

---

## Best Practices

### 1. Multiple Timeframe Analysis

```java
// Analyze order flow on multiple timeframes
Map<String, BigDecimal> cvd1m = calculate("cvd", "1m");
Map<String, BigDecimal> cvd5m = calculate("cvd", "5m");
Map<String, BigDecimal> cvd15m = calculate("cvd", "15m");

// All timeframes must align
if (cvd1m.isRising() && cvd5m.isRising() && cvd15m.isRising()) {
    // Strong trend confirmation
}
```

### 2. Context is Key

Order flow indicators work best when combined with:
- **Market structure** (support/resistance)
- **Traditional indicators** (RSI, MACD)
- **Volume analysis** (high volume = more reliable)
- **Time of day** (consider liquidity)

### 3. Volume Matters

- High volume = more reliable signals
- Low volume = less reliable, increase thresholds
- Check `minVolume` parameter for imbalance indicators

### 4. Divergence Confirmation

Don't trade divergences alone:
- Wait for price action confirmation (e.g., bullish candle after bullish divergence)
- Check higher timeframe alignment
- Verify with other order flow indicators

### 5. Absorption Zones

- Absorption at key levels = strong signal
- Failed absorption = powerful breakout
- Multiple absorption events at same level = very strong S/R

---

## Performance Tips

1. **Cache Indicator Results**: Order flow calculations can be intensive
2. **Use Appropriate Lookback**: Don't fetch more data than needed
3. **WebSocket for Real-Time**: More efficient than polling REST API
4. **Batch Calculations**: Calculate multiple indicators at once when possible
5. **Progressive Calculation**: Use `calculateProgressive` for historical analysis

---

## Troubleshooting

### No Indicator Data

```bash
# Ensure footprint service is collecting data
GET /api/footprint/BTCUSDT/current/1m

# Check order flow subscriptions
# In TradingConfig.java:
SUBSCRIBE_TO_AGGREGATE_TRADES = true  // Required for footprint candles
```

### Unexpected Values

- **CVD resetting**: Check if historical candles are being loaded correctly
- **No imbalances detected**: Lower `threshold` parameter
- **POC not showing**: Ensure `lookback` period has enough data

### Performance Issues

- Reduce `lookback` parameters
- Increase indicator update intervals
- Use caching for frequently accessed data

---

## Summary

These five order flow indicators provide deep insights into market microstructure:

- **CVD** shows cumulative pressure and divergences
- **Order Flow Imbalance** identifies strong directional pressure
- **Volume Profile POC** marks key support/resistance levels
- **Delta Divergence** signals potential reversals
- **Absorption** detects institutional activity at key levels

Combined with proper risk management and market context, these indicators significantly improve trading edge.

For more information, see:
- `ORDER_FLOW_API.md` - Order flow data API
- Strategy examples in `/src/main/java/org/cloudvision/trading/bot/strategy/`
- Indicator implementations in `/src/main/java/org/cloudvision/trading/bot/indicators/impl/`

