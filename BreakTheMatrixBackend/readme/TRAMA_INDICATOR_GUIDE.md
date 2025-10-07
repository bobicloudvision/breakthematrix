# TRAMA Indicator Guide

## Overview

The **Trend Regularity Adaptive Moving Average (TRAMA)** is an adaptive moving average indicator that automatically adjusts its responsiveness based on trend regularity.

**License**: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)  
**Author**: © LuxAlgo

## How It Works

TRAMA uses a unique approach to determine market conditions:

### Key Concepts

1. **Trend Detection**
   - Monitors highest and lowest values over a specified period
   - Detects when new highs or new lows are being made
   - These signals indicate the presence of a trend

2. **Trend Consistency (TC)**
   - Calculates how consistently the market is trending
   - TC is the squared SMA of trend signals (0 or 1)
   - Higher TC = stronger trend = faster response
   - Lower TC = sideways market = slower response (less noise)

3. **Adaptive Smoothing**
   - Uses the formula: `AMA = AMA[prev] + TC × (price - AMA[prev])`
   - Similar to an EMA but with dynamic alpha (TC)
   - When TC is high, the indicator follows price closely
   - When TC is low, the indicator provides strong filtering

### Mathematical Formula

```
hh = 1 if highest(length) increased, else 0
ll = 1 if lowest(length) decreased, else 0
signal = 1 if (hh OR ll), else 0
tc = (SMA(signal, length))²
AMA = AMA[prev] + tc × (price - AMA[prev])
```

## Parameters

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `length` | Integer | 99 | 1-500 | Period for highest/lowest calculation and trend consistency |
| `source` | String | "close" | - | Price source: close, open, high, low, hl2, hlc3, ohlc4 |
| `color` | String | "#ff1100" | - | Line color for chart display |
| `lineWidth` | Integer | 2 | 1-10 | Width of the TRAMA line |

## Usage

### REST API

#### Add TRAMA to Chart

```http
POST /api/trading-bot/indicators/{instanceId}/add
Content-Type: application/json

{
  "indicatorId": "trama",
  "params": {
    "length": 99,
    "source": "close",
    "color": "#ff1100",
    "lineWidth": 2
  }
}
```

#### Update TRAMA Parameters

```http
PUT /api/trading-bot/indicators/{instanceId}/update
Content-Type: application/json

{
  "params": {
    "length": 50
  }
}
```

#### Remove TRAMA

```http
DELETE /api/trading-bot/indicators/{instanceId}/remove/{indicatorUUID}
```

### Trading Strategy Example

```java
// In your strategy class
private static final int TRAMA_LENGTH = 99;

@Override
public void onCandlestick(CandlestickData candle) {
    // Calculate TRAMA
    Map<String, Object> params = Map.of("length", TRAMA_LENGTH);
    Map<String, Object> result = tramaIndicator.onNewCandle(candle, params, state);
    
    Map<String, BigDecimal> values = (Map<String, BigDecimal>) result.get("values");
    BigDecimal trama = values.get("trama");
    state = result.get("state");
    
    // Trading logic
    if (candle.getClose().compareTo(trama) > 0) {
        // Price above TRAMA - bullish
        System.out.println("💚 Bullish - Price above TRAMA");
    } else {
        // Price below TRAMA - bearish
        System.out.println("❤️ Bearish - Price below TRAMA");
    }
}
```

## Trading Applications

### 1. Trend Identification
- **Price above TRAMA** → Uptrend
- **Price below TRAMA** → Downtrend
- **TRAMA slope** → Trend strength

### 2. Support/Resistance
- In uptrends: TRAMA acts as dynamic support
- In downtrends: TRAMA acts as dynamic resistance
- Pullbacks to TRAMA offer entry opportunities

### 3. Entry Signals
- **Crossover**: Price crosses above TRAMA → Long entry
- **Crossunder**: Price crosses below TRAMA → Short entry
- **Bounce**: Price bounces off TRAMA in direction of trend

### 4. Exit Signals
- **Opposite crossover**: Close opposite direction trades
- **TRAMA flattening**: Trend weakening, consider taking profits
- **Failed bounce**: Price breaks through TRAMA → exit

## Advantages vs Traditional MAs

| Feature | TRAMA | SMA/EMA |
|---------|-------|---------|
| **Trend Response** | Fast in trends | Fixed speed |
| **Sideways Response** | Slow/stable | Fixed speed |
| **Whipsaws** | Fewer | More |
| **Lag** | Adaptive | Fixed |
| **Noise Filtering** | Excellent | Moderate |

## Best Practices

### Timeframe Selection
- **Short-term (1m-5m)**: length = 30-50
- **Medium-term (15m-1h)**: length = 50-100
- **Long-term (4h-1d)**: length = 100-200

### Combining with Other Indicators
1. **TRAMA + Volume**: Confirm breakouts with volume
2. **TRAMA + RSI**: Avoid overbought/oversold entries
3. **TRAMA + ATR**: Size positions based on volatility
4. **Multiple TRAMAs**: Use different lengths for trend hierarchy

### Risk Management
- Don't enter on TRAMA alone - wait for confirmation
- Use stop losses below/above recent TRAMA touches
- Consider trend strength (TRAMA slope) for position sizing
- Avoid choppy markets (when TRAMA is flat)

## Example Configurations

### Scalping Setup (Short-term)
```json
{
  "length": 30,
  "source": "close"
}
```

### Swing Trading Setup (Medium-term)
```json
{
  "length": 99,
  "source": "close"
}
```

### Position Trading Setup (Long-term)
```json
{
  "length": 150,
  "source": "hlc3"
}
```

### Dual TRAMA System
```json
[
  {
    "length": 50,
    "source": "close",
    "color": "#ff1100"
  },
  {
    "length": 150,
    "source": "close",
    "color": "#0066ff"
  }
]
```

## Implementation Details

### Event-Driven Architecture
TRAMA uses the standard event-driven indicator architecture:

```java
// 1. Initialize with historical data
Object state = tramaIndicator.onInit(historicalCandles, params);

// 2. Process each new candle
for (CandlestickData candle : newCandles) {
    Map<String, Object> result = tramaIndicator.onNewCandle(candle, params, state);
    BigDecimal trama = ((Map<String, BigDecimal>) result.get("values")).get("trama");
    state = result.get("state");
}

// 3. Optional: Process real-time ticks
Map<String, Object> tickResult = tramaIndicator.onNewTick(currentPrice, params, state);
```

### State Management
The indicator maintains internal state including:
- Price buffer for highest/lowest tracking
- Trend signal buffer for TC calculation
- Previous highest/lowest values
- Current AMA value

### Performance
- **Memory**: O(length) per instance
- **Computation**: O(1) per candle after initialization
- **Thread-safe**: Yes, state is synchronized

## WebSocket Updates

When added to a chart, TRAMA values are automatically broadcast via WebSocket:

```json
{
  "type": "INDICATOR_UPDATE",
  "timestamp": 1633036800000,
  "indicator": "trama",
  "values": {
    "trama": 50245.67
  }
}
```

## Testing

To test the TRAMA indicator:

```bash
# 1. Start the application
mvn spring-boot:run

# 2. Create a trading bot instance
POST /api/trading-bot/instances

# 3. Add TRAMA indicator
POST /api/trading-bot/indicators/{instanceId}/add
{
  "indicatorId": "trama",
  "params": {"length": 99}
}

# 4. Subscribe to data
POST /api/trading-bot/instances/{instanceId}/subscribe
{
  "provider": "binance",
  "symbol": "BTCUSDT",
  "interval": "5m"
}

# 5. Monitor WebSocket for TRAMA values
ws://localhost:8080/ws/trading-bot
```

## Troubleshooting

### TRAMA Not Updating
- Ensure sufficient historical data (need `length` candles)
- Check WebSocket connection status
- Verify indicator was added successfully

### Unexpected Values
- Check `source` parameter matches your expected price
- Verify `length` is appropriate for timeframe
- Confirm candle data quality

### Performance Issues
- Reduce `length` for faster calculations
- Limit number of concurrent TRAMA instances
- Consider using fewer simultaneous indicators

## Resources

- [LuxAlgo Original Pine Script](https://www.tradingview.com/script/UJQnV8C6-Trend-Regularity-Adaptive-Moving-Average-TRAMA/)
- [Creative Commons License](https://creativecommons.org/licenses/by-nc-sa/4.0/)
- [Indicator Architecture Guide](INDICATOR_INSTANCE_MANAGEMENT.md)

## License

This implementation is licensed under Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0).

You are free to:
- Share — copy and redistribute the material
- Adapt — remix, transform, and build upon the material

Under the following terms:
- Attribution — You must give appropriate credit
- NonCommercial — You may not use the material for commercial purposes
- ShareAlike — If you remix, transform, or build upon the material, you must distribute your contributions under the same license

© LuxAlgo

