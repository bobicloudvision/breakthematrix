# 🎬 Simplified Replay API

## Overview

The replay system now uses **automatic indicator discovery**. You just specify the market context (`provider`, `symbol`, `interval`), and the system **automatically finds and uses** all existing indicators for that context.

**No more complex indicator configuration needed!** ✨

---

## 📝 Super Simple Request

### Create Replay Session

```bash
POST /api/replay/create
Content-Type: application/json

{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "speed": 2.0
}
```

**That's it!** The system automatically:
1. Finds all active indicators for `Binance:BTCUSDT:5m`
2. Attaches to them
3. Replays with indicator updates

---

## 🎯 Complete Example

### Step 1: Create Session

```bash
curl -X POST http://localhost:8080/api/replay/create \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "speed": 1.0
  }'
```

**Response:**
```json
{
  "sessionId": "abc-123-def-456",
  "status": {
    "state": "READY",
    "currentIndex": 0,
    "totalCandles": 2880,
    "progress": 0.0,
    "speed": 1.0,
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "indicatorCount": 3
  },
  "message": "Replay session created successfully"
}
```

### Step 2: Check Logs

The backend logs will show:
```
🎬 Initializing replay session: abc-123-def-456
   Candles: 2880
   Context: Binance:BTCUSDT:5m
   Found 3 existing indicator(s)
✅ Attached to indicator: sma (Binance:BTCUSDT:5m:sma:...)
✅ Attached to indicator: rsi (Binance:BTCUSDT:5m:rsi:...)
✅ Attached to indicator: ema (Binance:BTCUSDT:5m:ema:...)
✅ Replay session initialized: abc-123-def-456
```

### Step 3: Play

```bash
curl -X POST http://localhost:8080/api/replay/abc-123-def-456/play
```

---

## 🔍 Optional: Preview Indicators

Want to see what indicators will be used before creating the session?

```bash
GET /api/replay/available-indicators?provider=Binance&symbol=BTCUSDT&interval=5m
```

**Response:**
```json
{
  "totalIndicators": 3,
  "indicators": [
    {
      "instanceKey": "Binance:BTCUSDT:5m:sma:a1b2c3",
      "indicatorId": "sma",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": {
        "period": 20
      },
      "createdAt": "2024-01-01T10:00:00Z",
      "lastUpdate": "2024-01-01T15:30:00Z",
      "updateCount": 2154
    },
    {
      "instanceKey": "Binance:BTCUSDT:5m:rsi:e5f6g7",
      "indicatorId": "rsi",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": {
        "period": 14
      },
      "createdAt": "2024-01-01T10:00:00Z",
      "lastUpdate": "2024-01-01T15:30:00Z",
      "updateCount": 2154
    },
    {
      "instanceKey": "Binance:BTCUSDT:5m:ema:i9j0k1",
      "indicatorId": "ema",
      "provider": "Binance",
      "symbol": "BTCUSDT",
      "interval": "5m",
      "params": {
        "period": 50
      },
      "createdAt": "2024-01-01T10:00:00Z",
      "lastUpdate": "2024-01-01T15:30:00Z",
      "updateCount": 2154
    }
  ],
  "context": {
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m"
  }
}
```

---

## 💻 JavaScript Examples

### Basic Replay

```javascript
async function createReplay(provider, symbol, interval, speed = 1.0) {
  const response = await fetch('http://localhost:8080/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, symbol, interval, speed })
  });
  
  const { sessionId, status } = await response.json();
  console.log('Session created:', sessionId);
  console.log('Indicators:', status.indicatorCount);
  
  return sessionId;
}

// Usage
const sessionId = await createReplay('Binance', 'BTCUSDT', '5m', 2.0);
```

### With Preview

```javascript
async function createReplayWithPreview(provider, symbol, interval, speed = 1.0) {
  // 1. Check what indicators exist
  const indicatorsResponse = await fetch(
    `http://localhost:8080/api/replay/available-indicators?` +
    `provider=${provider}&symbol=${symbol}&interval=${interval}`
  );
  
  const { totalIndicators, indicators } = await indicatorsResponse.json();
  
  console.log(`Found ${totalIndicators} indicator(s):`);
  indicators.forEach(ind => {
    console.log(`  - ${ind.indicatorId}:`, ind.params);
  });
  
  // 2. Create replay (automatically uses these indicators)
  const response = await fetch('http://localhost:8080/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, symbol, interval, speed })
  });
  
  const { sessionId } = await response.json();
  console.log('Replay created with all indicators');
  
  return sessionId;
}

// Usage
await createReplayWithPreview('Binance', 'BTCUSDT', '5m');
```

### Complete Workflow

```javascript
class SimpleReplayPlayer {
  constructor() {
    this.ws = null;
    this.sessionId = null;
  }
  
  async create(provider, symbol, interval, speed = 1.0) {
    // Create session
    const response = await fetch('http://localhost:8080/api/replay/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider, symbol, interval, speed })
    });
    
    const { sessionId, status } = await response.json();
    this.sessionId = sessionId;
    
    console.log('✅ Created replay session');
    console.log('   Session ID:', sessionId);
    console.log('   Candles:', status.totalCandles);
    console.log('   Indicators:', status.indicatorCount);
    
    return sessionId;
  }
  
  connectWebSocket() {
    this.ws = new WebSocket('ws://localhost:8080/replay-ws');
    
    this.ws.onopen = () => {
      console.log('🔌 WebSocket connected');
      this.ws.send(JSON.stringify({
        action: 'subscribe',
        sessionId: this.sessionId
      }));
    };
    
    this.ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      
      if (data.type === 'replayUpdate') {
        console.log(`📊 Progress: ${data.progress.toFixed(1)}%`);
        this.onUpdate(data);
      }
    };
  }
  
  onUpdate(data) {
    // Override this in your implementation
    console.log('Candle:', data.candle?.close);
    console.log('Indicators:', Object.keys(data.indicators || {}));
  }
  
  async play() {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}/play`, {
      method: 'POST'
    });
    console.log('▶️ Started playback');
  }
  
  async pause() {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}/pause`, {
      method: 'POST'
    });
    console.log('⏸️ Paused');
  }
  
  async setSpeed(speed) {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}/speed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ speed })
    });
    console.log(`⚡ Speed: ${speed}x`);
  }
  
  async stop() {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}`, {
      method: 'DELETE'
    });
    this.ws?.close();
    console.log('⏹️ Stopped');
  }
}

// Usage
const player = new SimpleReplayPlayer();
await player.create('Binance', 'BTCUSDT', '5m', 2.0);
player.connectWebSocket();
await player.play();

// Later...
await player.pause();
await player.setSpeed(5.0);
await player.play();
await player.stop();
```

---

## 🎯 What Happens Automatically

### Scenario 1: Indicators Exist
```
Provider: Binance
Symbol: BTCUSDT
Interval: 5m
Existing Indicators: SMA(20), RSI(14), EMA(50)

Result: ✅ Replay uses all 3 indicators
```

### Scenario 2: No Indicators
```
Provider: Binance
Symbol: ETHUSDT
Interval: 15m
Existing Indicators: None

Result: ✅ Replay works (candles only)
Log: "⚠️ No existing indicators found for this context"
```

### Scenario 3: Partial Match
```
Provider: Binance
Symbol: BTCUSDT
Interval: 1h
Existing Indicators: VWAP, Bollinger Bands (1h)

Result: ✅ Replay uses both indicators
```

---

## 🔧 Optional Parameters

### Time Range

Specify start/end times to replay a specific period:

```bash
{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "speed": 1.0,
  "startTime": "2024-01-01T00:00:00Z",
  "endTime": "2024-01-10T00:00:00Z"
}
```

### Speed Control

- `0.5` - Slow motion (2x slower)
- `1.0` - Normal speed
- `2.0` - 2x faster
- `10.0` - 10x faster
- `100.0` - Maximum speed

---

## 🎓 Benefits of Auto-Discovery

### ✅ Simpler API
- No need to specify indicators
- No need to configure parameters
- Just specify the market context

### ✅ Always Current
- Uses the **exact same indicators** running live
- No parameter mismatches
- What you see in replay = what was live

### ✅ Memory Efficient
- No duplication
- Reuses existing indicator instances
- Can run many replays without memory issues

### ✅ Faster Initialization
- Indicators already warmed up
- No initialization delay
- Instant replay start

---

## 📝 Migration from Old API

### Old Way (Complex)
```json
{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "speed": 1.0,
  "useExistingIndicators": true,
  "existingIndicatorKeys": [
    "Binance:BTCUSDT:5m:sma:abc123",
    "Binance:BTCUSDT:5m:rsi:def456"
  ]
}
```

### New Way (Simple)
```json
{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "speed": 1.0
}
```

**Same result, 70% less code!** 🎉

---

## 🐛 Troubleshooting

### "No indicators found"

**Log message:**
```
⚠️ No existing indicators found for this context
   Replay will show candles only (no indicators)
```

**Solution:** This is normal! Just means no indicators are active for this context.

To add indicators, use the indicator API:
```bash
POST /api/indicators/activate
{
  "indicatorId": "sma",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "params": {
    "period": 20
  }
}
```

Then create replay again - it will find the new indicator.

### Check What's Available

```bash
curl "http://localhost:8080/api/replay/available-indicators?provider=Binance&symbol=BTCUSDT&interval=5m"
```

---

## 🎉 Summary

### Old API (Complex)
- ❌ Specify indicators manually
- ❌ Configure parameters
- ❌ Manage instance keys
- ❌ Complex request body

### New API (Simple)
- ✅ Automatic indicator discovery
- ✅ Just specify context
- ✅ System finds everything
- ✅ Clean, minimal request

**Result: 70% simpler API, same functionality!** 🚀

---

**Enjoy the simplified replay experience!** 🎬

