# 🚀 Replay Quick Start

## Test in 5 Minutes

### Prerequisites
- Backend running on `localhost:8080`
- Historical data loaded for `Binance:BTCUSDT:5m`

---

## Option 1: Using cURL

### Step 1: Create Session
```bash
curl -X POST http://localhost:8080/api/replay/create \
  -H "Content-Type: application/json" \
  -d '{
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
      }
    ]
  }'
```

**Response:**
```json
{
  "sessionId": "abc-123-def-456",
  "status": { ... },
  "message": "Replay session created successfully"
}
```

### Step 2: Start Playing
```bash
# Replace SESSION_ID with your actual sessionId
curl -X POST http://localhost:8080/api/replay/SESSION_ID/play
```

### Step 3: Check Status
```bash
curl http://localhost:8080/api/replay/SESSION_ID/status
```

### Step 4: Control Playback
```bash
# Pause
curl -X POST http://localhost:8080/api/replay/SESSION_ID/pause

# Change speed to 5x
curl -X POST http://localhost:8080/api/replay/SESSION_ID/speed \
  -H "Content-Type: application/json" \
  -d '{"speed": 5.0}'

# Resume
curl -X POST http://localhost:8080/api/replay/SESSION_ID/resume

# Stop
curl -X DELETE http://localhost:8080/api/replay/SESSION_ID
```

---

## Option 2: Using JavaScript Console

Open browser console and paste:

```javascript
// 1. Create session
const createResponse = await fetch('http://localhost:8080/api/replay/create', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'Binance',
    symbol: 'BTCUSDT',
    interval: '5m',
    speed: 2.0,
    indicators: [
      { indicatorId: 'sma', params: { period: 20 } }
    ]
  })
});

const { sessionId } = await createResponse.json();
console.log('Session ID:', sessionId);

// 2. Connect WebSocket
const ws = new WebSocket('ws://localhost:8080/replay-ws');

ws.onopen = () => {
  console.log('WebSocket connected');
  ws.send(JSON.stringify({
    action: 'subscribe',
    sessionId: sessionId
  }));
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Received:', data.type);
  
  if (data.type === 'replayUpdate') {
    console.log(`Progress: ${data.progress.toFixed(2)}%`);
    console.log('Candle:', data.candle);
    console.log('Indicators:', data.indicators);
  }
};

// 3. Start playing
await fetch(`http://localhost:8080/api/replay/${sessionId}/play`, {
  method: 'POST'
});

console.log('Replay started! Watch updates above.');

// 4. Control functions
window.replayPause = async () => {
  await fetch(`http://localhost:8080/api/replay/${sessionId}/pause`, {
    method: 'POST'
  });
  console.log('Paused');
};

window.replayResume = async () => {
  await fetch(`http://localhost:8080/api/replay/${sessionId}/resume`, {
    method: 'POST'
  });
  console.log('Resumed');
};

window.replaySpeed = async (speed) => {
  await fetch(`http://localhost:8080/api/replay/${sessionId}/speed`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ speed })
  });
  console.log(`Speed set to ${speed}x`);
};

window.replayStop = async () => {
  await fetch(`http://localhost:8080/api/replay/${sessionId}`, {
    method: 'DELETE'
  });
  ws.close();
  console.log('Stopped');
};

// Usage:
// replayPause()
// replayResume()
// replaySpeed(10)
// replayStop()
```

---

## Option 3: Python Script

```python
import requests
import websocket
import json
import threading
import time

BASE_URL = "http://localhost:8080"
WS_URL = "ws://localhost:8080/replay-ws"

# 1. Create session
response = requests.post(f"{BASE_URL}/api/replay/create", json={
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
        }
    ]
})

session_id = response.json()["sessionId"]
print(f"Session ID: {session_id}")

# 2. WebSocket handler
def on_message(ws, message):
    data = json.loads(message)
    if data["type"] == "replayUpdate":
        print(f"Progress: {data['progress']:.2f}%")
        print(f"Candle: {data['candle']['close']}")
        print(f"Indicators: {list(data.get('indicators', {}).keys())}")
        print("---")

def on_open(ws):
    print("WebSocket connected")
    ws.send(json.dumps({
        "action": "subscribe",
        "sessionId": session_id
    }))

# 3. Connect WebSocket in thread
ws = websocket.WebSocketApp(
    WS_URL,
    on_message=on_message,
    on_open=on_open
)

ws_thread = threading.Thread(target=ws.run_forever)
ws_thread.daemon = True
ws_thread.start()

time.sleep(1)  # Wait for connection

# 4. Start playing
requests.post(f"{BASE_URL}/api/replay/{session_id}/play")
print("Replay started!")

# 5. Control functions
def pause():
    requests.post(f"{BASE_URL}/api/replay/{session_id}/pause")
    print("Paused")

def resume():
    requests.post(f"{BASE_URL}/api/replay/{session_id}/resume")
    print("Resumed")

def set_speed(speed):
    requests.post(
        f"{BASE_URL}/api/replay/{session_id}/speed",
        json={"speed": speed}
    )
    print(f"Speed: {speed}x")

def stop():
    requests.delete(f"{BASE_URL}/api/replay/{session_id}")
    ws.close()
    print("Stopped")

# Keep running
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    stop()
```

---

## Verify It Works

### Check Historical Data
```bash
curl http://localhost:8080/api/trading/historical/Binance/BTCUSDT/5m?limit=100
```

Should return candles. If empty, data needs to be loaded first.

### Check Active Sessions
```bash
curl http://localhost:8080/api/replay/sessions
```

### View Logs
Look for these messages in backend logs:
```
🎬 Creating replay session: ...
📊 Loaded 2880 candles for replay
✅ Activated indicator: sma ...
✅ Replay session initialized: ...
▶️ Replay started: ...
```

---

## Common Issues

### "No historical data available"
**Solution:** Subscribe to live data first or load from exchange:
```bash
# Start live data collection
POST /api/trading/subscribe/Binance/BTCUSDT/5m

# Wait a few minutes for candles to accumulate
```

### WebSocket not connecting
**Solution:** Check if WebSocket endpoint is registered:
```bash
curl http://localhost:8080/actuator/mappings | grep replay-ws
```

### Indicators not updating
**Solution:** Check indicator IDs are correct:
```bash
curl http://localhost:8080/api/indicators
```

---

## Visual Testing (Frontend)

If you have a frontend:

```javascript
// Simple chart update example
function updateChart(candle) {
  // TradingView Lightweight Charts
  candleSeries.update({
    time: candle.timestamp,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  });
  
  // Update indicator line
  if (indicators['sma']) {
    smaLine.update({
      time: candle.timestamp,
      value: indicators['sma'].values.sma
    });
  }
}
```

---

## Performance Test

Test high-speed replay:

```bash
# Create session at 100x speed
curl -X POST http://localhost:8080/api/replay/create \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "speed": 100.0,
    "indicators": []
  }'

# Should replay 2880 candles (10 days) in ~30 seconds
```

---

## Next Steps

1. ✅ Test basic replay (this guide)
2. 🎨 Build frontend UI with playback controls
3. 📊 Add multiple indicators visualization
4. 🤖 Add strategy execution (future)
5. 📈 Build backtesting features (future)

---

## Cleanup

Stop all sessions when done:
```bash
curl -X POST http://localhost:8080/api/replay/stop-all
```

---

**Happy Replaying! 🎬**

