# 🎯 Replay Modes - Complete Guide

## Overview

The replay system now supports **two modes** for indicators:

1. **Mode 1: Create New Indicators** - Isolated instances with custom parameters
2. **Mode 2: Attach to Existing Indicators** - Reuse live indicators already in memory

---

## 📊 Mode Comparison

| Feature | Mode 1: Create New | Mode 2: Attach Existing |
|---------|-------------------|------------------------|
| **Memory Usage** | Creates new instances | Reuses existing |
| **Parameters** | Custom parameters | Use existing params |
| **Independence** | Isolated from live | Shares with live |
| **Cleanup** | Deactivates on stop | Keeps indicators active |
| **Use Case** | Testing different params | Visualize live indicators |
| **Speed** | Slower (initialization) | Faster (already warmed up) |

---

## 🎬 Mode 1: Create New Indicators

### When to Use
- ✅ Testing different indicator parameters
- ✅ Isolated backtesting
- ✅ Comparing multiple configurations
- ✅ No live indicators running

### Example: Create Session

```bash
POST /api/replay/create
Content-Type: application/json

{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "speed": 2.0,
  "indicators": [
    {
      "indicatorId": "sma",
      "params": {
        "period": 20
      }
    },
    {
      "indicatorId": "rsi",
      "params": {
        "period": 14
      }
    }
  ]
}
```

### Behavior
1. Creates **new** indicator instances
2. Initializes with historical data
3. Updates as replay progresses
4. **Deactivates** indicators when session stops

### JavaScript Example

```javascript
const response = await fetch('http://localhost:8080/api/replay/create', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'Binance',
    symbol: 'BTCUSDT',
    interval: '5m',
    speed: 1.0,
    indicators: [
      { indicatorId: 'sma', params: { period: 20 } },
      { indicatorId: 'ema', params: { period: 50 } },
      { indicatorId: 'rsi', params: { period: 14 } }
    ]
  })
});

const { sessionId } = await response.json();
console.log('Created session:', sessionId);
```

---

## 🔗 Mode 2: Attach to Existing Indicators

### When to Use
- ✅ Visualizing live indicators historically
- ✅ Saving memory (no duplication)
- ✅ Replaying what live system showed
- ✅ Multiple replays of same indicators
- ✅ Faster initialization

### Step 1: Get Available Indicators

```bash
# Get all active indicators
GET /api/replay/available-indicators

# Filter by context
GET /api/replay/available-indicators?provider=Binance&symbol=BTCUSDT&interval=5m
```

**Response:**
```json
{
  "totalIndicators": 3,
  "indicators": [
    {
      "instanceKey": "Binance:BTCUSDT:5m:sma:a1b2c3d4",
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
      "instanceKey": "Binance:BTCUSDT:5m:rsi:e5f6g7h8",
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
    }
  ],
  "context": {
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m"
  }
}
```

### Step 2: Create Replay with Existing Indicators

```bash
POST /api/replay/create
Content-Type: application/json

{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "speed": 2.0,
  "useExistingIndicators": true,
  "existingIndicatorKeys": [
    "Binance:BTCUSDT:5m:sma:a1b2c3d4",
    "Binance:BTCUSDT:5m:rsi:e5f6g7h8"
  ]
}
```

### Behavior
1. Attaches to **existing** indicator instances
2. No initialization needed (already warmed up)
3. Reads indicator values as replay progresses
4. **Keeps** indicators active when session stops

### JavaScript Example (Complete Workflow)

```javascript
// 1. Get available indicators
async function getAvailableIndicators(provider, symbol, interval) {
  const response = await fetch(
    `http://localhost:8080/api/replay/available-indicators?` +
    `provider=${provider}&symbol=${symbol}&interval=${interval}`
  );
  return await response.json();
}

// 2. Create replay with existing indicators
async function createReplayWithExisting(provider, symbol, interval, speed = 1.0) {
  // Get available indicators
  const { indicators } = await getAvailableIndicators(provider, symbol, interval);
  
  if (indicators.length === 0) {
    console.error('No indicators available for this context');
    return null;
  }
  
  // Extract instance keys
  const instanceKeys = indicators.map(ind => ind.instanceKey);
  
  console.log('Found indicators:', indicators.map(i => i.indicatorId));
  console.log('Instance keys:', instanceKeys);
  
  // Create replay session
  const response = await fetch('http://localhost:8080/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider,
      symbol,
      interval,
      speed,
      useExistingIndicators: true,
      existingIndicatorKeys: instanceKeys
    })
  });
  
  const { sessionId } = await response.json();
  console.log('Created replay session:', sessionId);
  
  return sessionId;
}

// Usage
const sessionId = await createReplayWithExisting('Binance', 'BTCUSDT', '5m', 2.0);
```

---

## 🆚 Comparison Examples

### Scenario: Testing SMA with Different Periods

**Mode 1 (Create New):** Best choice
```javascript
// Test SMA(20) vs SMA(50) vs SMA(100)
const configs = [20, 50, 100].map(period => ({
  provider: 'Binance',
  symbol: 'BTCUSDT',
  interval: '5m',
  speed: 10.0,
  indicators: [
    { indicatorId: 'sma', params: { period } }
  ]
}));

// Create 3 separate replays to compare
for (const config of configs) {
  const response = await fetch('/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config)
  });
}
```

### Scenario: Visualizing Live Indicators

**Mode 2 (Attach Existing):** Best choice
```javascript
// Show what your LIVE indicators showed historically
const sessionId = await createReplayWithExisting('Binance', 'BTCUSDT', '5m');

// This replays using the EXACT same indicators 
// that are running live right now
```

---

## 💡 Pro Tips

### Tip 1: Hybrid Approach

You can't mix modes in a single session, but you can run multiple sessions:

```javascript
// Session 1: Test new parameters
const testSession = await fetch('/api/replay/create', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'Binance',
    symbol: 'BTCUSDT',
    interval: '5m',
    speed: 5.0,
    indicators: [
      { indicatorId: 'sma', params: { period: 30 } }  // Test period=30
    ]
  })
});

// Session 2: Compare with live indicator
const liveSession = await fetch('/api/replay/create', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'Binance',
    symbol: 'BTCUSDT',
    interval: '5m',
    speed: 5.0,
    useExistingIndicators: true,
    existingIndicatorKeys: ['Binance:BTCUSDT:5m:sma:live123']  // Live SMA(20)
  })
});

// Now compare both replays side-by-side!
```

### Tip 2: Check Before Attaching

Always verify indicators exist before creating session:

```javascript
async function safeAttachToIndicators(provider, symbol, interval, indicatorIds) {
  // Get available indicators
  const { indicators } = await getAvailableIndicators(provider, symbol, interval);
  
  // Filter by indicator IDs
  const filtered = indicators.filter(ind => indicatorIds.includes(ind.indicatorId));
  
  if (filtered.length === 0) {
    throw new Error('No matching indicators found. Create them first!');
  }
  
  // Create replay
  return await fetch('/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider,
      symbol,
      interval,
      speed: 1.0,
      useExistingIndicators: true,
      existingIndicatorKeys: filtered.map(i => i.instanceKey)
    })
  });
}

// Usage
try {
  await safeAttachToIndicators('Binance', 'BTCUSDT', '5m', ['sma', 'rsi']);
} catch (error) {
  console.error(error.message);
}
```

### Tip 3: Memory Management

For long-running replays with many indicators, Mode 2 is more efficient:

```javascript
// BAD: Creates 10 new indicator instances (high memory)
for (let i = 0; i < 10; i++) {
  await fetch('/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider: 'Binance',
      symbol: 'BTCUSDT',
      interval: '5m',
      speed: 1.0,
      indicators: [
        { indicatorId: 'sma', params: { period: 20 } }
      ]
    })
  });
}

// GOOD: Reuses same indicator instance (low memory)
const { indicators } = await getAvailableIndicators('Binance', 'BTCUSDT', '5m');
const smaKey = indicators.find(i => i.indicatorId === 'sma')?.instanceKey;

for (let i = 0; i < 10; i++) {
  await fetch('/api/replay/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      provider: 'Binance',
      symbol: 'BTCUSDT',
      interval: '5m',
      speed: 1.0,
      useExistingIndicators: true,
      existingIndicatorKeys: [smaKey]
    })
  });
}
```

---

## 🔍 Debugging

### Check Active Indicators

```bash
# List all active indicators
curl http://localhost:8080/api/replay/available-indicators

# Check specific context
curl "http://localhost:8080/api/replay/available-indicators?provider=Binance&symbol=BTCUSDT&interval=5m"
```

### Session Status

Check which mode a session is using:

```bash
curl http://localhost:8080/api/replay/{sessionId}/status
```

The response includes:
```json
{
  "sessionId": "...",
  "indicatorCount": 2,
  // If > 0, check logs to see which mode was used
}
```

### Logs

Look for these messages:

**Mode 1:**
```
🎬 Initializing replay session: ...
   Mode: Create new indicators
   Indicators to create: 2
✅ Activated indicator: sma (instance: ...)
```

**Mode 2:**
```
🎬 Initializing replay session: ...
   Mode: Attach to existing indicators
   Existing indicators: 2
✅ Attached to existing indicator: Binance:BTCUSDT:5m:sma:...
```

---

## 📝 Summary

### Use Mode 1 When:
- 🔬 Testing different parameters
- 🧪 Running experiments
- 📊 Isolated backtesting
- 🎯 Parameter optimization

### Use Mode 2 When:
- 👀 Visualizing live indicators
- 💾 Saving memory
- ⚡ Faster initialization
- 🔄 Multiple replays of same config
- 📈 Comparing live vs historical

### Best Practice:
- Start with **Mode 2** if live indicators exist
- Fall back to **Mode 1** if no matching indicators
- Use **Mode 1** for parameter testing
- Use **Mode 2** for visualization

---

**Happy Replaying! 🎬**

