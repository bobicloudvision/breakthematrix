# Bookmap Live Updates Feature

## Overview
The Bookmap indicator now supports **real-time live updates** that show the heatmap building up as trades happen, instead of only updating when a candle closes.

## How It Works

### 1. **Throttled Updates**
- Updates are controlled by the `updateInterval` parameter (in seconds)
- Default: 5 seconds (configurable from 0 to 60 seconds)
- Set to `0` to disable live updates (only update on candle close)

### 2. **Live Update Content**
Each live update includes the **full visualization**:
- ✅ Complete heatmap (all price levels with accumulated volume)
- ✅ Volume circles (sized and colored by volume/delta)
- ✅ Significant levels (POC, HVN markers)
- ✅ Summary values (total volume, delta, bid/ask ratio)
- ✅ `isLiveUpdate: true` flag to distinguish from candle-close updates

### 3. **WebSocket Message Format**
Live updates use the **standard** `indicatorTick` format (same as SMA, EMA, etc.):

```json
{
  "type": "indicatorTick",
  "price": 4597.3,
  "data": {
    "indicatorId": "bookmap",
    "instanceKey": "Binance:ETHUSDT:1m:bookmap:abc123",
    "provider": "Binance",
    "symbol": "ETHUSDT",
    "interval": "1m",
    "params": {
      "tickSize": "0.01",
      "lookback": 50,
      "updateInterval": 5,
      "showLevels": "true"
    },
    "timestamp": "2025-10-06T13:23:23.065Z",
    "values": {
      "totalVolume": 430.49,
      "delta": -51.77,
      "pocPrice": 4597.62,
      "bidAskRatio": 4.18
    },
    "additionalData": {
      "isLiveUpdate": true,
      "heatmap": {
        "4597.62": {
          "price": 4597.62,
          "buyVolume": 25.5,
          "sellVolume": 18.3,
          "delta": 7.2,
          "currentBidVolume": 150,
          "currentAskVolume": 100,
          "intensity": 0.85
        },
        // ... more price levels ...
      },
      "levels": [
        {
          "price": 4597.62,
          "type": "POC",
          "volume": 43.8,
          "description": "Point of Control - Highest volume"
        }
      ],
      "markers": [
        {
          "time": 1696599803065,
          "price": 4597.62,
          "size": 10,
          "color": "#FFD700",
          "shape": "circle",
          "label": "POC"
        },
        // ... more circles ...
      ]
    },
    "updateCount": 0
  }
}
```

## Configuration Parameters

### `updateInterval` (NEW)
- **Type:** Integer
- **Default:** 5 seconds
- **Range:** 0-60 seconds
- **Description:** Time between live updates
- **Usage:**
  - `0`: Disabled (only update on candle close)
  - `1-2`: Very frequent updates (high CPU/bandwidth)
  - `5`: Recommended default (good balance)
  - `10-30`: Less frequent (lower overhead)

### Other Parameters
- `tickSize`: Price level granularity (default: "0.01")
- `lookback`: Number of candles to accumulate (default: 50)
- `showLevels`: Show significant level markers (default: "true")

## Usage Example

### Creating Instance with Live Updates (Every 5 seconds)
```json
POST /api/indicators/instances

{
  "indicatorId": "bookmap",
  "provider": "Binance",
  "symbol": "ETHUSDT",
  "interval": "1m",
  "params": {
    "tickSize": "0.01",
    "lookback": 50,
    "updateInterval": 5,
    "showLevels": "true"
  }
}
```

### Creating Instance WITHOUT Live Updates (Only Candle Close)
```json
{
  "indicatorId": "bookmap",
  "provider": "Binance",
  "symbol": "ETHUSDT",
  "interval": "1m",
  "params": {
    "tickSize": "0.01",
    "lookback": 50,
    "updateInterval": 0,  // Disable live updates
    "showLevels": "true"
  }
}
```

### Creating Instance with Fast Updates (Every 2 seconds)
```json
{
  "indicatorId": "bookmap",
  "provider": "Binance",
  "symbol": "ETHUSDT",
  "interval": "1m",
  "params": {
    "tickSize": "0.01",
    "lookback": 50,
    "updateInterval": 2,  // Fast updates
    "showLevels": "true"
  }
}
```

## Performance Considerations

### Trade Processing
- Trades are accumulated in-memory continuously
- Live updates are throttled by `updateInterval` (not sent on every trade)
- Typical trade rate: 100-1000 trades/second
- Typical live update rate: 1 update every 5 seconds

### Network Bandwidth
Each live update sends:
- ~50-200 price levels (heatmap data)
- ~10-20 circle markers
- ~3-5 significant level markers
- Summary values

**Estimated size:** ~5-15 KB per update (compressed)

### CPU Usage
- Minimal: Trade accumulation (simple map updates)
- Moderate: Heatmap generation (sorting/filtering price levels)
- Moderate: Circle creation (top 20 levels only)
- Low: Level detection (POC/HVN calculation)

### Recommendations
- **High-frequency trading:** `updateInterval: 10-30` (reduce overhead)
- **Normal usage:** `updateInterval: 5` (default, balanced)
- **Detailed monitoring:** `updateInterval: 2-3` (more responsive)
- **Bandwidth constrained:** `updateInterval: 0` (candle close only)

## Frontend Integration

### Detecting Live Updates
```javascript
websocket.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  if (message.type === "indicatorTick" && 
      message.data.indicatorId === "bookmap") {
    
    const isLive = message.data.additionalData?.isLiveUpdate === true;
    
    if (isLive) {
      console.log("📡 Live update received");
      updateHeatmapSmoothly(message.data);
    } else {
      console.log("📊 Candle close update received");
      updateHeatmapFull(message.data);
    }
  }
};
```

### Rendering Optimization
Since live updates are frequent, consider:
1. **Debouncing renders:** Wait 100-200ms before re-rendering
2. **Incremental updates:** Only update changed price levels
3. **Canvas rendering:** Use canvas for heatmap (faster than DOM)
4. **Marker pooling:** Reuse circle elements instead of recreating

### Example Rendering Strategy
```javascript
let pendingUpdate = null;

function handleBookmapUpdate(data) {
  // Store update but don't render immediately
  pendingUpdate = data;
  
  // Debounce rendering to avoid too many DOM updates
  if (!renderScheduled) {
    renderScheduled = true;
    requestAnimationFrame(() => {
      if (pendingUpdate) {
        renderHeatmap(pendingUpdate);
        renderCircles(pendingUpdate.additionalData.markers);
        pendingUpdate = null;
      }
      renderScheduled = false;
    });
  }
}
```

## Comparison: Live vs Candle Close

### Candle Close Only (`updateInterval: 0`)
- ✅ Minimal bandwidth usage
- ✅ Lower CPU usage
- ✅ Clean, discrete updates
- ❌ Delayed visualization (up to 1 minute)
- ❌ Miss intra-candle dynamics

### Live Updates (`updateInterval: 5`)
- ✅ Real-time market flow visibility
- ✅ See volume building up
- ✅ Catch fast moves early
- ✅ Better for scalping/day trading
- ⚠️ Higher bandwidth usage
- ⚠️ More frequent re-renders

## Technical Implementation

### State Management
```java
public class BookmapState {
  // Real-time tracking
  private long lastBroadcastTime;  // Throttling
  private CandlestickData lastCandle;  // Reference for time
  private int updateIntervalSeconds;  // Configuration
  
  // Check if it's time to broadcast
  public boolean shouldBroadcastLiveUpdate() {
    long now = System.currentTimeMillis();
    long elapsedSeconds = (now - lastBroadcastTime) / 1000;
    return elapsedSeconds >= updateIntervalSeconds;
  }
}
```

### Update Flow
```
1. Trade arrives → onTradeUpdate() called
2. Accumulate volume in state
3. Check if updateInterval has elapsed
4. If yes:
   - Generate heatmap
   - Create circles
   - Calculate values
   - Broadcast update
   - Reset timer
5. If no:
   - Return empty (silent accumulation)
```

## Summary

The live updates feature provides **real-time visibility** into order flow without overwhelming the system:

- **Configurable throttling** prevents performance issues
- **Standard message format** ensures frontend compatibility
- **Complete visualization** includes heatmap, circles, and levels
- **Efficient implementation** only sends updates when meaningful
- **Flexible parameters** allow tuning for different use cases

This makes the Bookmap indicator perfect for active traders who need to see market dynamics as they unfold! 📊🔥

