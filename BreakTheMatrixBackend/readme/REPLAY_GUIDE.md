# 🎬 Indicator Replay System - Complete Guide

## Overview

The Indicator Replay System allows you to replay historical candlestick data with real-time indicator updates. It supports full playback controls including play, pause, stop, speed adjustment, and seeking.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend UI                          │
│  - Replay controls (play/pause/stop/speed)             │
│  - Chart with real-time updates                        │
│  - Indicator visualization                             │
└────────────────┬────────────────────────────────────────┘
                 │
         ┌───────┴───────┐
         │               │
    REST API        WebSocket
         │               │
         ▼               ▼
┌─────────────┐   ┌────────────────┐
│   Replay    │   │    Replay      │
│ Controller  │   │  WebSocket     │
└──────┬──────┘   └────────┬───────┘
       │                   │
       └───────┬───────────┘
               ▼
       ┌───────────────┐
       │ ReplayService │
       └───────┬───────┘
               ▼
       ┌───────────────┐
       │ ReplaySession │
       │ (Playback)    │
       └───────┬───────┘
               │
       ┌───────┴────────┐
       ▼                ▼
┌──────────────┐  ┌────────────────┐
│History       │  │ Indicator      │
│Service       │  │ Manager        │
└──────────────┘  └────────────────┘
```

---

## 📦 Core Components

### 1. **ReplayConfig**
Configuration for a replay session.

```java
ReplayConfig config = ReplayConfig.builder()
    .provider("Binance")
    .symbol("BTCUSDT")
    .interval("5m")
    .startTime(Instant.parse("2024-01-01T00:00:00Z"))
    .endTime(Instant.parse("2024-01-10T00:00:00Z"))
    .speed(1.0)
    .addIndicator("sma", Map.of("period", 20))
    .addIndicator("rsi", Map.of("period", 14))
    .build();
```

### 2. **ReplaySession**
Manages the playback of historical candles with indicator updates.

**Features:**
- Load and validate historical data
- Initialize indicators
- Playback control (play/pause/resume/stop)
- Speed adjustment
- Index seeking (jump to specific candle)
- Event broadcasting via WebSocket

**States:**
- `INITIALIZING` - Loading data and setting up indicators
- `READY` - Ready to start playback
- `PLAYING` - Actively playing candles
- `PAUSED` - Paused, can be resumed
- `STOPPED` - Stopped, session can be cleaned up
- `COMPLETED` - Finished playing all candles
- `ERROR` - Error occurred

### 3. **ReplayService**
Orchestrates replay sessions.

**Responsibilities:**
- Create and manage multiple concurrent sessions
- Provide playback controls
- Session lifecycle management

### 4. **ReplayController**
REST API endpoints for replay control.

### 5. **ReplayWebSocketHandler**
Streams real-time updates to connected clients.

---

## 🚀 Usage Guide

### Step 1: Create a Replay Session

**REST API:**
```bash
POST /api/replay/create
Content-Type: application/json

{
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "startTime": "2024-01-01T00:00:00Z",
  "endTime": "2024-01-10T00:00:00Z",
  "speed": 1.0,
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

**Response:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": {
    "state": "READY",
    "currentIndex": 0,
    "totalCandles": 2880,
    "progress": 0.0,
    "speed": 1.0,
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "indicatorCount": 2,
    "createdAt": "2024-01-01T10:00:00Z"
  },
  "message": "Replay session created successfully"
}
```

### Step 2: Connect to WebSocket

**WebSocket Endpoint:** `ws://localhost:8080/replay-ws`

**Subscribe to session:**
```json
{
  "action": "subscribe",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "type": "subscribed",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": {
    "state": "READY",
    "currentIndex": 0,
    "totalCandles": 2880
  }
}
```

### Step 3: Control Playback

**Start playing:**
```bash
POST /api/replay/{sessionId}/play
```

**Pause:**
```bash
POST /api/replay/{sessionId}/pause
```

**Resume:**
```bash
POST /api/replay/{sessionId}/resume
```

**Change speed:**
```bash
POST /api/replay/{sessionId}/speed
Content-Type: application/json

{
  "speed": 2.0
}
```

**Jump to index:**
```bash
POST /api/replay/{sessionId}/jump
Content-Type: application/json

{
  "index": 500
}
```

**Stop and cleanup:**
```bash
DELETE /api/replay/{sessionId}
```

### Step 4: Receive Real-Time Updates

**WebSocket message format:**
```json
{
  "type": "replayUpdate",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "currentIndex": 42,
  "totalCandles": 2880,
  "progress": 1.46,
  "state": "PLAYING",
  "speed": 1.0,
  "candle": {
    "openTime": "2024-01-01T03:30:00Z",
    "closeTime": "2024-01-01T03:35:00Z",
    "time": 1704078600,
    "timestamp": 1704078600,
    "timeMs": 1704078600000,
    "open": 42150.50,
    "high": 42180.25,
    "low": 42140.00,
    "close": 42165.75,
    "volume": 125.45,
    "interval": "5m",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "closed": true
  },
  "indicators": {
    "Binance:BTCUSDT:5m:sma:...": {
      "instanceKey": "Binance:BTCUSDT:5m:sma:...",
      "indicatorId": "sma",
      "values": {
        "sma": 42100.35
      },
      "timestamp": "2024-01-01T03:30:00Z"
    },
    "Binance:BTCUSDT:5m:rsi:...": {
      "instanceKey": "Binance:BTCUSDT:5m:rsi:...",
      "indicatorId": "rsi",
      "values": {
        "rsi": 65.32
      },
      "timestamp": "2024-01-01T03:30:00Z"
    }
  }
}
```

**Completion notification:**
```json
{
  "type": "replayUpdate",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "currentIndex": 2880,
  "totalCandles": 2880,
  "progress": 100.0,
  "state": "COMPLETED",
  "speed": 1.0
}
```

---

## 📋 API Reference

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/replay/create` | Create a new replay session |
| `POST` | `/api/replay/{sessionId}/play` | Start playback |
| `POST` | `/api/replay/{sessionId}/pause` | Pause playback |
| `POST` | `/api/replay/{sessionId}/resume` | Resume playback |
| `POST` | `/api/replay/{sessionId}/speed` | Change playback speed |
| `POST` | `/api/replay/{sessionId}/jump` | Jump to specific index |
| `GET` | `/api/replay/{sessionId}/status` | Get session status |
| `GET` | `/api/replay/sessions` | Get all active sessions |
| `DELETE` | `/api/replay/{sessionId}` | Stop and delete session |
| `POST` | `/api/replay/stop-all` | Stop all active sessions |

### WebSocket Messages

**Client → Server:**

| Action | Description | Payload |
|--------|-------------|---------|
| `subscribe` | Subscribe to replay session | `{"action": "subscribe", "sessionId": "..."}` |
| `unsubscribe` | Unsubscribe from updates | `{"action": "unsubscribe"}` |

**Server → Client:**

| Type | Description |
|------|-------------|
| `connected` | Connection established |
| `subscribed` | Subscription confirmed |
| `unsubscribed` | Unsubscription confirmed |
| `replayUpdate` | Candle and indicator update |
| `error` | Error message |

---

## 🎮 Frontend Integration Example

### JavaScript/TypeScript

```typescript
class ReplayPlayer {
  private ws: WebSocket;
  private sessionId: string;
  
  async createSession(config: ReplayConfig): Promise<string> {
    const response = await fetch('http://localhost:8080/api/replay/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config)
    });
    
    const data = await response.json();
    this.sessionId = data.sessionId;
    return this.sessionId;
  }
  
  connectWebSocket() {
    this.ws = new WebSocket('ws://localhost:8080/replay-ws');
    
    this.ws.onopen = () => {
      // Subscribe to session
      this.ws.send(JSON.stringify({
        action: 'subscribe',
        sessionId: this.sessionId
      }));
    };
    
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      
      if (message.type === 'replayUpdate') {
        this.updateChart(message.candle);
        this.updateIndicators(message.indicators);
        this.updateProgress(message.progress);
      }
    };
  }
  
  async play() {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}/play`, {
      method: 'POST'
    });
  }
  
  async pause() {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}/pause`, {
      method: 'POST'
    });
  }
  
  async setSpeed(speed: number) {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}/speed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ speed })
    });
  }
  
  async stop() {
    await fetch(`http://localhost:8080/api/replay/${this.sessionId}`, {
      method: 'DELETE'
    });
    this.ws.close();
  }
  
  private updateChart(candle: any) {
    // Update your chart library (TradingView, Chart.js, etc.)
    console.log('New candle:', candle);
  }
  
  private updateIndicators(indicators: any) {
    // Update indicator overlays
    console.log('Indicators:', indicators);
  }
  
  private updateProgress(progress: number) {
    // Update progress bar
    console.log('Progress:', progress + '%');
  }
}

// Usage
const player = new ReplayPlayer();

await player.createSession({
  provider: 'Binance',
  symbol: 'BTCUSDT',
  interval: '5m',
  startTime: '2024-01-01T00:00:00Z',
  endTime: '2024-01-10T00:00:00Z',
  speed: 1.0,
  indicators: [
    { indicatorId: 'sma', params: { period: 20 } },
    { indicatorId: 'rsi', params: { period: 14 } }
  ]
});

player.connectWebSocket();
await player.play();
```

---

## ⚡ Performance Considerations

### Speed Settings

- **1x**: Normal speed (100ms delay between candles)
- **2x**: 2x speed (50ms delay)
- **5x**: 5x speed (20ms delay)
- **10x**: 10x speed (10ms delay)
- **100x**: Maximum speed (1ms delay)

### Resource Usage

- Each session consumes memory for:
  - Historical candles (up to 5000 candles)
  - Indicator instances and state
  - Event listeners
- Recommended: Limit to 5 concurrent sessions

### Network Optimization

- WebSocket sends only changed data
- Consider throttling updates at very high speeds
- Use binary format for large datasets (future enhancement)

---

## 🔧 Configuration

### CandlestickHistoryService

Ensure enough historical data is loaded:

```java
historyService.setMaxCandles("Binance", "BTCUSDT", "5m", 5000);
```

### Thread Pool (Optional)

For multiple concurrent replays:

```java
@Configuration
public class ReplayConfig {
    @Bean
    public ExecutorService replayExecutor() {
        return Executors.newFixedThreadPool(5);
    }
}
```

---

## 🐛 Troubleshooting

### Issue: "No historical data available"

**Solution:** Ensure data is loaded in `CandlestickHistoryService`
```bash
GET /api/trading/historical/Binance/BTCUSDT/5m?limit=5000
```

### Issue: WebSocket not receiving updates

**Solution:** 
1. Check WebSocket connection
2. Verify subscription to correct sessionId
3. Ensure session is in PLAYING state

### Issue: Indicators not updating

**Solution:**
1. Check indicator activation logs
2. Verify indicator IDs are correct
3. Ensure enough warm-up data

---

## 🎯 Use Cases

### 1. **Strategy Backtesting Visualization**
- Replay historical data
- Visualize strategy signals
- Analyze indicator behavior

### 2. **Education & Training**
- Study market patterns
- Learn indicator interactions
- Practice chart reading

### 3. **Indicator Development**
- Test indicator logic
- Debug calculations
- Optimize parameters

### 4. **Historical Analysis**
- Review past market events
- Analyze volatility periods
- Study trend formations

---

## 🚀 Future Enhancements

- [ ] Multiple timeframe replay
- [ ] Order execution visualization
- [ ] Trade simulation
- [ ] Snapshot/bookmark system
- [ ] Replay groups (compare multiple assets)
- [ ] Export replay to video
- [ ] Drawing tools persistence
- [ ] Speed presets (0.5x, 0.25x for slow-mo)
- [ ] Auto-pause on signals

---

## 📝 Example Scenarios

### Scenario 1: Slow-Motion Analysis
```bash
# Create session at 0.5x speed for detailed analysis
{
  "speed": 0.5,
  "indicators": ["sma", "rsi", "macd"]
}
```

### Scenario 2: Fast Scanning
```bash
# Quickly scan through days of data at 50x speed
{
  "speed": 50,
  "indicators": ["vwap"]
}
```

### Scenario 3: Educational Walkthrough
```bash
# Normal speed with multiple indicators for learning
{
  "speed": 1.0,
  "indicators": ["sma", "ema", "rsi", "bollinger_bands", "volume"]
}
```

---

## 🏁 Quick Start Checklist

- [ ] Ensure historical data is loaded
- [ ] Start backend server
- [ ] Create replay session via REST API
- [ ] Connect WebSocket client
- [ ] Subscribe to session
- [ ] Start playback
- [ ] Enjoy replay! 🎉

---

## 📞 Support

For issues or questions:
- Check logs for error messages
- Verify API endpoints are accessible
- Ensure WebSocket connection is stable
- Review indicator configuration

---

**Built with ❤️ for traders and developers**

