# Replay WebSocket Data Specification

## Overview

This document specifies the **exact data structure** the frontend expects from the backend for replay WebSocket messages.

---

## WebSocket Connection

**Endpoint:** `ws://localhost:8080/replay-ws`

**Client → Server (Subscribe):**
```json
{
  "action": "subscribe",
  "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85"
}
```

---

## Server → Client Messages

### 1. **Connection Established**
```json
{
  "type": "connected",
  "message": "Connected to replay WebSocket"
}
```

---

### 2. **Subscription Confirmed**
```json
{
  "type": "subscribed",
  "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85",
  "status": {
    "state": "PAUSED",
    "currentIndex": 0,
    "totalCandles": 820,
    "progress": 0.0,
    "speed": 1.0,
    "currentTime": "2025-10-05T04:00:00Z",
    "symbol": "ETHUSDT",
    "interval": "1m",
    "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85"
  }
}
```

---

### 3. **Replay Update** ✨ (Most Important)

This is sent for **each candle** during replay playback.

```json
{
  "type": "replayUpdate",
  "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85",
  "state": "PLAYING",
  "currentIndex": 31,
  "totalCandles": 820,
  "progress": 3.7804878048780486,
  "speed": 1.0,
  
  "candle": {
    "symbol": "ETHUSDT",
    "provider": "Binance",
    "interval": "1m",
    "openTime": "2025-10-05T04:31:00Z",
    "closeTime": "2025-10-05T04:31:59.999Z",
    "time": 1759638660,
    "timestamp": 1759638660,
    "timeMs": 1759638660000,
    "open": 4534.36,
    "high": 4536.79,
    "low": 4533.47,
    "close": 4536.78,
    "volume": 210.6221,
    "closed": true
  },
  
  "indicators": {
    "Binance:ETHUSDT:1m:volume:b4fb2792": {
      "indicatorId": "volume",
      "instanceKey": "Binance:ETHUSDT:1m:volume:b4fb2792",
      "provider": "Binance",
      "symbol": "ETHUSDT",
      "interval": "1m",
      "params": {
        "bearishColor": "#ef5350",
        "bullishColor": "#26a69a"
      },
      "timestamp": "2025-10-05T04:31:00Z",
      "values": {
        "volume": 210.62210000
      },
      "additionalData": {
        "color": "#26a69a"
      }
    },
    "Binance:ETHUSDT:1m:sma:a1b2c3d4": {
      "indicatorId": "sma",
      "instanceKey": "Binance:ETHUSDT:1m:sma:a1b2c3d4",
      "provider": "Binance",
      "symbol": "ETHUSDT",
      "interval": "1m",
      "params": {
        "period": 20,
        "color": "#2196F3"
      },
      "timestamp": "2025-10-05T04:31:00Z",
      "values": {
        "sma": 4535.45
      }
    }
  }
}
```

---

### 4. **Replay Complete**
```json
{
  "type": "complete",
  "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85",
  "message": "Replay completed successfully",
  "finalIndex": 820
}
```

---

### 5. **Error Message**
```json
{
  "type": "error",
  "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85",
  "message": "Error description here"
}
```

---

## Detailed Field Specifications

### **Candle Object** (Required)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `symbol` | string | Yes | Trading symbol (e.g., "ETHUSDT") |
| `provider` | string | Yes | Provider name (e.g., "Binance") |
| `interval` | string | Yes | Time interval (e.g., "1m", "5m", "1h") |
| `openTime` | string | Yes | ISO 8601 timestamp (e.g., "2025-10-05T04:31:00Z") |
| `closeTime` | string | Yes | ISO 8601 timestamp (e.g., "2025-10-05T04:31:59.999Z") |
| `time` | number | Yes | Unix timestamp in **seconds** (e.g., 1759638660) |
| `timestamp` | number | Yes | Same as `time` (Unix timestamp in seconds) |
| `timeMs` | number | Optional | Unix timestamp in **milliseconds** (e.g., 1759638660000) |
| `open` | number | Yes | Opening price |
| `high` | number | Yes | Highest price |
| `low` | number | Yes | Lowest price |
| `close` | number | Yes | Closing price |
| `volume` | number | Yes | Trading volume |
| `closed` | boolean | Yes | Always `true` for replay (candle is complete) |

**Example:**
```json
{
  "symbol": "ETHUSDT",
  "provider": "Binance",
  "interval": "1m",
  "openTime": "2025-10-05T04:31:00Z",
  "closeTime": "2025-10-05T04:31:59.999Z",
  "time": 1759638660,
  "timestamp": 1759638660,
  "timeMs": 1759638660000,
  "open": 4534.36,
  "high": 4536.79,
  "low": 4533.47,
  "close": 4536.78,
  "volume": 210.6221,
  "closed": true
}
```

---

### **Indicators Object** (Optional)

Structure: `{ [instanceKey: string]: IndicatorData }`

Each indicator is keyed by its **instance key** (e.g., `"Binance:ETHUSDT:1m:volume:b4fb2792"`).

#### **IndicatorData Object**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `indicatorId` | string | Yes | Indicator type (e.g., "volume", "sma", "rsi") |
| `instanceKey` | string | Yes | Unique instance identifier |
| `provider` | string | Yes | Provider name |
| `symbol` | string | Yes | Trading symbol |
| `interval` | string | Yes | Time interval |
| `params` | object | Optional | Indicator parameters (colors, periods, etc.) |
| `timestamp` | string | Yes | ISO 8601 timestamp matching the candle |
| `values` | object | Yes | **Indicator values** (key-value pairs) |
| `additionalData` | object | Optional | Extra data (colors, metadata, etc.) |

#### **values Object** ⚠️ **Critical**

The `values` object contains the **actual indicator values** as key-value pairs.

**Examples:**

**Single value indicator (SMA):**
```json
{
  "indicatorId": "sma",
  "values": {
    "sma": 4535.45
  }
}
```

**Multi-value indicator (Bollinger Bands):**
```json
{
  "indicatorId": "bollinger",
  "values": {
    "upper": 4550.23,
    "middle": 4535.45,
    "lower": 4520.67
  }
}
```

**Volume indicator:**
```json
{
  "indicatorId": "volume",
  "values": {
    "volume": 210.62210000
  }
}
```

**RSI indicator:**
```json
{
  "indicatorId": "rsi",
  "values": {
    "rsi": 67.5
  }
}
```

**Full Example:**
```json
{
  "indicatorId": "volume",
  "instanceKey": "Binance:ETHUSDT:1m:volume:b4fb2792",
  "provider": "Binance",
  "symbol": "ETHUSDT",
  "interval": "1m",
  "params": {
    "bearishColor": "#ef5350",
    "bullishColor": "#26a69a"
  },
  "timestamp": "2025-10-05T04:31:00Z",
  "values": {
    "volume": 210.62210000
  },
  "additionalData": {
    "color": "#26a69a"
  }
}
```

---

## Frontend Processing

### How the Frontend Handles This Data

#### **1. Candle Update**
```javascript
// Extract candle from message
const candleData = {
  timestamp: message.candle.timestamp,  // Unix seconds
  open: message.candle.open,
  high: message.candle.high,
  low: message.candle.low,
  close: message.candle.close,
  closed: message.candle.closed
};

// Apply to chart
handleCandleUpdate(candleData, seriesRef, seriesManagerRef, setChartData, setRealCount);
```

#### **2. Indicator Update**
```javascript
// Process each indicator
Object.entries(message.indicators).forEach(([instanceKey, indicatorObject]) => {
  const indicatorData = {
    indicatorId: indicatorObject.indicatorId,      // "volume", "sma", etc.
    instanceKey: indicatorObject.instanceKey,      // Unique key
    timestamp: indicatorObject.timestamp,          // ISO 8601 string
    values: indicatorObject.values                 // { volume: 210.62 }
  };
  
  // Apply to chart
  handleIndicatorUpdate(indicatorData, seriesManagerRef, null);
});
```

---

## Message Frequency

**Recommendation:** Send one `replayUpdate` message per candle at the specified `speed`.

- **Speed 0:** No messages (paused)
- **Speed 1:** One message per second (real-time)
- **Speed 2:** Two messages per second (2x faster)
- **Speed 10:** Ten messages per second (10x faster)

---

## Important Notes

### ⚠️ **Critical Requirements**

1. **Unix Timestamps in Seconds**
   - `time` and `timestamp` fields **must be in seconds**, not milliseconds
   - Frontend converts to milliseconds internally
   - Example: `1759638660` (seconds) ✅, not `1759638660000` (milliseconds) ❌

2. **Indicator Values Structure**
   - The `values` field **must be an object** with key-value pairs
   - Each key is a series name (e.g., "sma", "volume", "upper", "middle")
   - Each value is a number
   - Example: `{ "sma": 4535.45 }` ✅

3. **Instance Keys**
   - Must be unique per indicator instance
   - Format: `{provider}:{symbol}:{interval}:{indicatorId}:{uniqueId}`
   - Example: `"Binance:ETHUSDT:1m:volume:b4fb2792"`

4. **Timestamps Must Match**
   - `indicators[*].timestamp` should match `candle.openTime`
   - Both represent the same candle/bar

---

## Example Complete Message

```json
{
  "type": "replayUpdate",
  "sessionId": "0451560b-cf9f-491b-a158-a35a3e962d85",
  "state": "PLAYING",
  "currentIndex": 100,
  "totalCandles": 1000,
  "progress": 10.0,
  "speed": 2.0,
  "candle": {
    "symbol": "BTCUSDT",
    "provider": "Binance",
    "interval": "5m",
    "openTime": "2025-10-05T12:00:00Z",
    "closeTime": "2025-10-05T12:04:59.999Z",
    "time": 1759671600,
    "timestamp": 1759671600,
    "timeMs": 1759671600000,
    "open": 65000.50,
    "high": 65100.75,
    "low": 64950.25,
    "close": 65050.00,
    "volume": 150.5,
    "closed": true
  },
  "indicators": {
    "Binance:BTCUSDT:5m:sma:abc123": {
      "indicatorId": "sma",
      "instanceKey": "Binance:BTCUSDT:5m:sma:abc123",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": { "period": 20, "color": "#2196F3" },
      "timestamp": "2025-10-05T12:00:00Z",
      "values": { "sma": 65025.30 }
    },
    "Binance:BTCUSDT:5m:rsi:def456": {
      "indicatorId": "rsi",
      "instanceKey": "Binance:BTCUSDT:5m:rsi:def456",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": { "period": 14, "color": "#9c27b0" },
      "timestamp": "2025-10-05T12:00:00Z",
      "values": { "rsi": 58.3 }
    },
    "Binance:BTCUSDT:5m:bollinger:ghi789": {
      "indicatorId": "bollinger",
      "instanceKey": "Binance:BTCUSDT:5m:bollinger:ghi789",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": { "period": 20, "stdDev": 2 },
      "timestamp": "2025-10-05T12:00:00Z",
      "values": {
        "upper": 65200.50,
        "middle": 65025.30,
        "lower": 64850.10
      }
    }
  }
}
```

---

## Testing Checklist

- [ ] Candles display on chart
- [ ] Candle timestamps are correct (Unix seconds)
- [ ] Indicators appear on chart
- [ ] Indicator values update with each candle
- [ ] Multiple indicators work simultaneously
- [ ] Progress bar updates correctly
- [ ] Play/pause/speed controls work
- [ ] Stop clears the chart
- [ ] No console errors

---

## Summary

### **Minimum Required Fields**

**Candle:**
- `time`, `timestamp` (Unix seconds)
- `open`, `high`, `low`, `close`
- `volume`, `closed`

**Indicator:**
- `indicatorId` (type of indicator)
- `instanceKey` (unique identifier)
- `timestamp` (ISO 8601 string)
- `values` (object with indicator values)

### **Data Flow**

```
Backend → WebSocket → Frontend
         ↓
    replayUpdate message
         ↓
    ├── candle → handleCandleUpdate() → Chart
    └── indicators → handleIndicatorUpdate() → Chart
```

---

**This specification ensures the frontend can correctly parse and display your replay data!** 🎉

