# Indicator Instance Management System

## Overview

The Indicator Instance Management system provides a centralized way to track, manage, and update active indicators across multiple trading symbols, providers, and timeframes. This enables you to:

- **Activate indicators** for specific symbol/provider/interval combinations
- **Track state** of all active indicators in real-time
- **Receive updates** automatically when new candles arrive
- **Query indicators** by various criteria (symbol, provider, interval, indicator type)
- **Stream updates** via WebSocket for real-time monitoring

## Architecture

### Components

1. **`IndicatorInstanceManager`** - Core service managing active indicator instances
2. **`IndicatorInstanceController`** - REST API for managing indicator instances
3. **`IndicatorWebSocketHandler`** - WebSocket handler for real-time updates
4. **`IndicatorService`** - Base indicator calculation service
5. **`IndicatorState`** - State container for individual indicators

### Key Concepts

#### Instance Key
Each active indicator is uniquely identified by a composite key:
```
provider:symbol:interval:indicatorId:paramsHash
```

Example: `Binance:BTCUSDT:5m:sma:7a8b9c`

#### Context Key
Groups indicators by trading context:
```
provider:symbol:interval
```

Example: `Binance:BTCUSDT:5m`

## REST API Usage

### Base URL
```
http://localhost:8080/api/indicators/instances
```

### Activate an Indicator

Initializes an indicator with historical data and starts tracking it.

**Request:**
```http
POST /api/indicators/instances/activate
Content-Type: application/json

{
  "indicatorId": "sma",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "params": {
    "period": 20,
    "color": "#2962FF"
  }
}
```

**Response:**
```json
{
  "success": true,
  "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
  "message": "Indicator activated successfully",
  "details": {
    "indicatorId": "sma",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {
      "period": 20,
      "color": "#2962FF"
    },
    "createdAt": "2025-10-04T10:30:00Z",
    "initializedWithCandles": 50
  }
}
```

### List Active Indicators

Get all currently active indicators.

**Request:**
```http
GET /api/indicators/instances
```

**Query Parameters (optional):**
- `provider` - Filter by provider (e.g., "Binance")
- `symbol` - Filter by symbol (e.g., "BTCUSDT")
- `interval` - Filter by interval (e.g., "5m")
- `indicatorId` - Filter by indicator ID (e.g., "sma")

**Response:**
```json
{
  "success": true,
  "totalActive": 3,
  "instances": [
    {
      "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
      "indicatorId": "sma",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": {"period": 20},
      "createdAt": "2025-10-04T10:30:00Z",
      "lastUpdate": "2025-10-04T10:35:00Z",
      "updateCount": 150
    }
  ]
}
```

### Get Specific Instance

Get details about a specific indicator instance.

**Request:**
```http
GET /api/indicators/instances/{instanceKey}
```

**Response:**
```json
{
  "success": true,
  "instance": {
    "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
    "indicatorId": "sma",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {"period": 20},
    "createdAt": "2025-10-04T10:30:00Z",
    "lastUpdate": "2025-10-04T10:35:00Z",
    "updateCount": 150
  }
}
```

### Get Statistics

Get aggregated statistics about active indicators.

**Request:**
```http
GET /api/indicators/instances/stats
```

**Response:**
```json
{
  "success": true,
  "stats": {
    "totalActive": 5,
    "uniqueContexts": 2,
    "byIndicator": {
      "sma": 2,
      "volume": 2,
      "rsi": 1
    },
    "bySymbol": {
      "BTCUSDT": 3,
      "ETHUSDT": 2
    },
    "byInterval": {
      "1m": 2,
      "5m": 2,
      "15m": 1
    }
  }
}
```

### Deactivate Indicator

Stop tracking a specific indicator instance.

**Request:**
```http
DELETE /api/indicators/instances/{instanceKey}
```

**Response:**
```json
{
  "success": true,
  "message": "Indicator deactivated successfully",
  "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c"
}
```

### Deactivate by Context

Remove all indicators for a specific context.

**Request:**
```http
DELETE /api/indicators/instances/context?provider=Binance&symbol=BTCUSDT&interval=5m
```

**Response:**
```json
{
  "success": true,
  "message": "Deactivated 3 indicator(s)",
  "count": 3,
  "context": {
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m"
  }
}
```

### Clear All Indicators

Remove all active indicators (use with caution).

**Request:**
```http
DELETE /api/indicators/instances/all
```

**Response:**
```json
{
  "success": true,
  "message": "Cleared all active indicators",
  "count": 5
}
```

## WebSocket Real-Time Updates

### Connection

Connect to the indicator WebSocket:
```
ws://localhost:8080/indicator-ws
```

### Subscribe to Specific Indicators

**Send:**
```json
{
  "action": "subscribe",
  "instanceKeys": [
    "Binance:BTCUSDT:5m:sma:7a8b9c",
    "Binance:BTCUSDT:5m:volume:default"
  ]
}
```

**Response:**
```json
{
  "type": "subscribed",
  "validSubscriptions": [
    "Binance:BTCUSDT:5m:sma:7a8b9c",
    "Binance:BTCUSDT:5m:volume:default"
  ]
}
```

### Subscribe to Context

Subscribe to all indicators for a specific symbol/provider/interval.

**Send:**
```json
{
  "action": "subscribeContext",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m"
}
```

**Response:**
```json
{
  "type": "contextSubscribed",
  "context": {
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m"
  },
  "activeInstances": 3
}
```

### Receive Updates

When a new candle arrives, you'll receive updates for subscribed indicators:

```json
{
  "type": "indicatorUpdate",
  "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
  "indicatorId": "sma",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "timestamp": "2025-10-04T10:35:00Z",
  "values": {
    "sma": "94523.45"
  },
  "candle": {
    "openTime": "2025-10-04T10:30:00Z",
    "closeTime": "2025-10-04T10:35:00Z",
    "open": "94500.00",
    "high": "94600.00",
    "low": "94450.00",
    "close": "94550.00",
    "volume": "123.45"
  }
}
```

### List Active Indicators

**Send:**
```json
{
  "action": "listActive"
}
```

**Response:**
```json
{
  "type": "activeInstances",
  "totalActive": 3,
  "instances": [
    {
      "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
      "indicatorId": "sma",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": {"period": 20}
    }
  ]
}
```

### Unsubscribe

**Send:**
```json
{
  "action": "unsubscribe"
}
```

**Response:**
```json
{
  "type": "unsubscribed",
  "message": "Unsubscribed from all indicator updates"
}
```

## Java API Usage

### Programmatic Usage

```java
@Autowired
private IndicatorInstanceManager instanceManager;

// Activate an indicator
String instanceKey = instanceManager.activateIndicator(
    "sma",              // indicatorId
    "Binance",          // provider
    "BTCUSDT",          // symbol
    "5m",               // interval
    Map.of("period", 20) // params
);

// Get instance details
IndicatorInstance instance = instanceManager.getInstance(instanceKey);

// Update with new candle (automatically done by WebSocket handler)
IndicatorResult result = instanceManager.updateWithCandle(instanceKey, candle);

// Query instances
List<IndicatorInstance> allInstances = instanceManager.getAllInstances();
List<IndicatorInstance> btcInstances = instanceManager.getInstancesBySymbol("BTCUSDT");
List<IndicatorInstance> smaInstances = instanceManager.getInstancesByIndicator("sma");

// Deactivate
instanceManager.deactivateIndicator(instanceKey);
```

### Automatic Updates

The `IndicatorWebSocketHandler` automatically:
1. Listens for incoming candlestick data from `TradingBot`
2. Updates all active indicators for that context
3. Broadcasts results to subscribed WebSocket clients

No manual intervention needed - indicators update automatically!

## Use Cases

### 1. Multi-Timeframe Analysis

Track the same indicator across multiple timeframes:

```bash
# Activate SMA(20) on different timeframes
curl -X POST http://localhost:8080/api/indicators/instances/activate \
  -H "Content-Type: application/json" \
  -d '{"indicatorId":"sma","provider":"Binance","symbol":"BTCUSDT","interval":"1m","params":{"period":20}}'

curl -X POST http://localhost:8080/api/indicators/instances/activate \
  -H "Content-Type: application/json" \
  -d '{"indicatorId":"sma","provider":"Binance","symbol":"BTCUSDT","interval":"5m","params":{"period":20}}'

curl -X POST http://localhost:8080/api/indicators/instances/activate \
  -H "Content-Type: application/json" \
  -d '{"indicatorId":"sma","provider":"Binance","symbol":"BTCUSDT","interval":"15m","params":{"period":20}}'
```

### 2. Multi-Symbol Monitoring

Track indicators across multiple symbols:

```bash
# Activate Volume indicator for multiple symbols
for symbol in BTCUSDT ETHUSDT BNBUSDT; do
  curl -X POST http://localhost:8080/api/indicators/instances/activate \
    -H "Content-Type: application/json" \
    -d "{\"indicatorId\":\"volume\",\"provider\":\"Binance\",\"symbol\":\"$symbol\",\"interval\":\"5m\",\"params\":{}}"
done
```

### 3. Strategy Dashboard

Create a dashboard showing multiple indicators for a trading strategy:

```bash
# Activate indicators for a complete setup
indicators=("sma" "volume" "rsi")
for indicator in "${indicators[@]}"; do
  curl -X POST http://localhost:8080/api/indicators/instances/activate \
    -H "Content-Type: application/json" \
    -d "{\"indicatorId\":\"$indicator\",\"provider\":\"Binance\",\"symbol\":\"BTCUSDT\",\"interval\":\"5m\",\"params\":{}}"
done

# Subscribe to all via WebSocket
wscat -c ws://localhost:8080/indicator-ws
> {"action":"subscribeContext","provider":"Binance","symbol":"BTCUSDT","interval":"5m"}
```

### 4. Different Parameter Sets

Track the same indicator with different parameters:

```bash
# Multiple SMA periods
for period in 20 50 200; do
  curl -X POST http://localhost:8080/api/indicators/instances/activate \
    -H "Content-Type: application/json" \
    -d "{\"indicatorId\":\"sma\",\"provider\":\"Binance\",\"symbol\":\"BTCUSDT\",\"interval\":\"5m\",\"params\":{\"period\":$period}}"
done
```

## Performance Considerations

### Memory Management
- Each active indicator instance maintains its own state
- For large numbers of indicators (100+), consider memory usage
- Use `deactivateIndicator()` to clean up unused instances

### Update Frequency
- Indicators update only on **closed candles**, not on every tick
- This ensures accurate calculations and reduces computational overhead
- For real-time needs, use the tick update API (if supported by indicator)

### Concurrent Updates
- All internal data structures use `ConcurrentHashMap` for thread safety
- Multiple candles can update different indicators simultaneously
- No locking issues when scaling to many symbols/timeframes

## Best Practices

1. **Activate Once, Update Many**: Activate indicators at startup or when needed, let them update automatically

2. **Use Context Subscriptions**: Subscribe to contexts rather than individual instances for easier management

3. **Clean Up**: Deactivate indicators when no longer needed to free resources

4. **Monitor Statistics**: Use `/stats` endpoint to track active indicator count and distribution

5. **Parameter Hashing**: Different parameter combinations create separate instances (intentional design)

## Troubleshooting

### Issue: Indicator not receiving updates
**Solution**: Check that:
- The indicator is active (`GET /api/indicators/instances`)
- The provider/symbol/interval match incoming candle data
- Candles are marked as `closed` (only closed candles trigger updates)

### Issue: High memory usage
**Solution**: 
- Check active indicator count (`GET /api/indicators/instances/stats`)
- Deactivate unused indicators
- Consider shorter history requirements for indicators

### Issue: WebSocket not receiving messages
**Solution**:
- Verify subscription with `listActive` action
- Check that instanceKey or context matches active indicators
- Ensure WebSocket connection is open

## Future Enhancements

Potential improvements to consider:

1. **Persistence** - Save/restore active indicators on restart
2. **Batching** - Batch multiple indicator activations in one request
3. **Alerts** - Trigger notifications when indicator conditions are met
4. **Historical Replay** - Replay historical data through active indicators
5. **Performance Metrics** - Track calculation time per indicator

## See Also

- [Indicator Architecture](./ORDER_FLOW_INDICATORS.md) - Core indicator system
- [Order Flow API](./ORDER_FLOW_API.md) - Trading data flow
- [WebSocket Configuration](./src/main/java/org/cloudvision/WebSocketConfig.java) - WebSocket setup

