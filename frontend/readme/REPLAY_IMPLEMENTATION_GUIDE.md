# Replay Mode Implementation Guide

## Overview

The chart architecture now has **shared update handlers** that can be used by both WebSocket (live) and replay modes. This makes implementing replay functionality straightforward and consistent.

---

## Shared Update Handlers

### File: `chartUpdateHandlers.js`

Contains reusable functions for updating chart data:

#### 1. **`handleCandleUpdate()`**
Updates a single candle on the chart (live or replay)

```javascript
handleCandleUpdate(candleData, seriesRef, seriesManagerRef, onDataUpdate, onRealCountUpdate);
```

**Parameters:**
- `candleData` - Candle object with `{ timestamp, open, high, low, close, closed }`
- `seriesRef` - Ref to main candlestick series
- `seriesManagerRef` - Ref to series manager
- `onDataUpdate` - Callback to update data array (usually `setData`)
- `onRealCountUpdate` - Optional callback to update real count (usually `setRealCount`)

---

#### 2. **`handleIndicatorUpdate()`**
Updates indicator values on the chart (live or replay)

```javascript
handleIndicatorUpdate(indicatorData, seriesManagerRef, onStatusUpdate);
```

**Parameters:**
- `indicatorData` - Object with `{ indicatorId, instanceKey, timestamp, values: { seriesName: value } }`
- `seriesManagerRef` - Ref to series manager
- `onStatusUpdate` - Optional callback for status messages

---

#### 3. **`handleBatchCandleUpdate()`**
Updates multiple candles at once (useful for replay seeking)

```javascript
handleBatchCandleUpdate(candles, seriesRef, seriesManagerRef, onDataUpdate);
```

**Parameters:**
- `candles` - Array of candle objects
- `seriesRef` - Ref to main candlestick series
- `seriesManagerRef` - Ref to series manager
- `onDataUpdate` - Callback to update data array (usually `setData`)

---

#### 4. **`extractCandleFromMessage()`**
Parse WebSocket message and extract candle data

#### 5. **`extractIndicatorFromMessage()`**
Parse WebSocket message and extract indicator data

---

## Implementation Example: ReplayChart Component

### Basic Replay Component

```javascript
import React, { useEffect, useRef, useState } from 'react';
import { ChartRenderer } from './ChartRenderer';
import { useChartWebSocket, getWebSocketStatusColors } from './useChartWebSocket';
import { handleCandleUpdate, handleBatchCandleUpdate } from './chartUpdateHandlers';

export function ReplayChart({ 
    replayData,          // Full dataset for replay
    replayIndicators,    // Indicator data for replay
    onReplayEnd,         // Callback when replay ends
    ...chartProps 
}) {
    const [data, setData] = useState([]);
    const [realCount, setRealCount] = useState(0);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isPlaying, setIsPlaying] = useState(false);
    const [playbackSpeed, setPlaybackSpeed] = useState(1);
    
    const seriesRef = useRef(null);
    const seriesManagerRef = useRef(null);
    const playbackTimerRef = useRef(null);
    
    // WebSocket disabled for replay mode
    const { status, message } = useChartWebSocket({
        provider: null,
        symbol: null,
        interval: null,
        seriesRef,
        seriesManagerRef,
        onDataUpdate: setData,
        onRealCountUpdate: setRealCount,
        enabled: false, // ❌ WebSocket disabled
        reconnectTrigger: 0
    });

    // Initialize with first N candles
    useEffect(() => {
        if (replayData && replayData.length > 0) {
            const initialCount = 50; // Show first 50 candles
            const initialData = replayData.slice(0, initialCount);
            
            // Use batch update for initial data
            handleBatchCandleUpdate(
                initialData,
                seriesRef,
                seriesManagerRef,
                setData
            );
            
            setCurrentIndex(initialCount);
            setRealCount(initialCount);
        }
    }, [replayData]);

    // Playback control
    useEffect(() => {
        if (isPlaying && currentIndex < replayData.length) {
            playbackTimerRef.current = setInterval(() => {
                setCurrentIndex(prev => {
                    const next = prev + 1;
                    
                    // Check if replay ended
                    if (next >= replayData.length) {
                        setIsPlaying(false);
                        if (onReplayEnd) onReplayEnd();
                        return prev;
                    }
                    
                    // Update chart with next candle
                    const nextCandle = replayData[next];
                    handleCandleUpdate(
                        nextCandle,
                        seriesRef,
                        seriesManagerRef,
                        setData,
                        setRealCount
                    );
                    
                    return next;
                });
            }, 1000 / playbackSpeed); // Adjust speed
            
            return () => {
                if (playbackTimerRef.current) {
                    clearInterval(playbackTimerRef.current);
                }
            };
        }
    }, [isPlaying, currentIndex, playbackSpeed, replayData]);

    // Seek to specific index
    const seekTo = (index) => {
        if (index >= 0 && index < replayData.length) {
            const seekData = replayData.slice(0, index + 1);
            
            // Use batch update for seeking
            handleBatchCandleUpdate(
                seekData,
                seriesRef,
                seriesManagerRef,
                setData
            );
            
            setCurrentIndex(index);
            setRealCount(index + 1);
        }
    };

    // Playback controls
    const play = () => setIsPlaying(true);
    const pause = () => setIsPlaying(false);
    const stepForward = () => seekTo(currentIndex + 1);
    const stepBackward = () => seekTo(currentIndex - 1);
    const reset = () => seekTo(49); // Back to initial state

    const statusColors = getWebSocketStatusColors(status);

    return (
        <div className="relative w-full h-full">
            {/* Replay Status Indicator */}
            <div className="absolute top-3 right-3 z-10">
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border backdrop-blur-md bg-blue-500/20 border-blue-400/50 text-blue-300 shadow-lg">
                    <div className="flex items-center gap-2">
                        <div className="w-2 h-2 rounded-full bg-blue-400"></div>
                        <span className="text-xs font-medium">▶ Replay Mode</span>
                    </div>
                    <div className="text-xs opacity-80 border-l border-white/20 pl-2">
                        {currentIndex} / {replayData.length}
                    </div>
                </div>
            </div>

            {/* Replay Controls */}
            <div className="absolute bottom-3 left-1/2 transform -translate-x-1/2 z-10">
                <div className="flex items-center gap-2 px-4 py-2 rounded-lg border backdrop-blur-md bg-gray-900/90 border-gray-700 shadow-lg">
                    <button
                        onClick={reset}
                        className="px-2 py-1 text-xs rounded bg-gray-700 hover:bg-gray-600"
                        title="Reset"
                    >
                        ⏮
                    </button>
                    <button
                        onClick={stepBackward}
                        className="px-2 py-1 text-xs rounded bg-gray-700 hover:bg-gray-600"
                        title="Step Backward"
                    >
                        ⏪
                    </button>
                    <button
                        onClick={isPlaying ? pause : play}
                        className="px-3 py-1 text-sm rounded bg-blue-600 hover:bg-blue-500"
                        title={isPlaying ? "Pause" : "Play"}
                    >
                        {isPlaying ? '⏸' : '▶'}
                    </button>
                    <button
                        onClick={stepForward}
                        className="px-2 py-1 text-xs rounded bg-gray-700 hover:bg-gray-600"
                        title="Step Forward"
                    >
                        ⏩
                    </button>
                    
                    {/* Speed Control */}
                    <select
                        value={playbackSpeed}
                        onChange={(e) => setPlaybackSpeed(Number(e.target.value))}
                        className="px-2 py-1 text-xs rounded bg-gray-700 border border-gray-600"
                    >
                        <option value={0.5}>0.5x</option>
                        <option value={1}>1x</option>
                        <option value={2}>2x</option>
                        <option value={5}>5x</option>
                        <option value={10}>10x</option>
                    </select>
                    
                    {/* Progress Bar */}
                    <input
                        type="range"
                        min={0}
                        max={replayData.length - 1}
                        value={currentIndex}
                        onChange={(e) => {
                            pause();
                            seekTo(Number(e.target.value));
                        }}
                        className="w-64"
                    />
                </div>
            </div>

            {/* Chart Renderer */}
            <ChartRenderer 
                data={data}
                loading={false}
                error={null}
                realCount={realCount}
                seriesRef={seriesRef}
                seriesManagerRef={seriesManagerRef}
                onFetchIndicators={() => {
                    // Return replay indicators instead of fetching from API
                    return Promise.resolve({ indicators: replayIndicators });
                }}
                {...chartProps}
            />
        </div>
    );
}
```

---

## Advanced Example: Replay with Indicators

```javascript
import { handleIndicatorUpdate } from './chartUpdateHandlers';

export function ReplayChartWithIndicators({ replayData, replayIndicators, ...props }) {
    // ... (same state and setup as above)
    
    // Update indicators during playback
    useEffect(() => {
        if (isPlaying && currentIndex < replayData.length) {
            playbackTimerRef.current = setInterval(() => {
                setCurrentIndex(prev => {
                    const next = prev + 1;
                    
                    if (next >= replayData.length) {
                        setIsPlaying(false);
                        return prev;
                    }
                    
                    // Update candle
                    const nextCandle = replayData[next];
                    handleCandleUpdate(
                        nextCandle,
                        seriesRef,
                        seriesManagerRef,
                        setData,
                        setRealCount
                    );
                    
                    // Update indicators for this candle
                    const timestamp = nextCandle.timestamp;
                    const indicatorUpdates = replayIndicators.filter(
                        ind => ind.timestamp === timestamp
                    );
                    
                    indicatorUpdates.forEach(indUpdate => {
                        handleIndicatorUpdate(
                            indUpdate,
                            seriesManagerRef,
                            null // No status callback needed
                        );
                    });
                    
                    return next;
                });
            }, 1000 / playbackSpeed);
            
            return () => {
                if (playbackTimerRef.current) {
                    clearInterval(playbackTimerRef.current);
                }
            };
        }
    }, [isPlaying, currentIndex, playbackSpeed, replayData, replayIndicators]);
    
    // ... (rest of component)
}
```

---

## Usage in App

```javascript
import { Chart } from './Chart'; // Live mode
import { ReplayChart } from './ReplayChart'; // Replay mode

function App() {
    const [mode, setMode] = useState('live'); // 'live' or 'replay'
    const [replayData, setReplayData] = useState([]);
    
    // Load replay data
    const loadReplaySession = async (sessionId) => {
        const data = await fetch(`/api/replay/${sessionId}`).then(r => r.json());
        setReplayData(data.candles);
        setMode('replay');
    };
    
    return (
        <div>
            <div className="controls">
                <button onClick={() => setMode('live')}>Live Mode</button>
                <button onClick={() => loadReplaySession('session123')}>
                    Load Replay
                </button>
            </div>
            
            {mode === 'live' ? (
                <Chart 
                    provider="Binance"
                    symbol="BTCUSDT"
                    interval="1m"
                    activeStrategies={[]}
                    enabledIndicators={[]}
                />
            ) : (
                <ReplayChart
                    replayData={replayData}
                    onReplayEnd={() => console.log('Replay ended')}
                />
            )}
        </div>
    );
}
```

---

## Key Benefits

### ✅ Consistent Update Logic
Both live and replay modes use the **same update handlers**, ensuring consistent behavior.

### ✅ No Code Duplication
Update logic is written once in `chartUpdateHandlers.js` and reused everywhere.

### ✅ Easy to Test
Mock the handlers for testing both WebSocket and replay functionality.

### ✅ Flexible Playback
- Play/pause
- Seek to any position
- Variable speed (0.5x to 10x)
- Step forward/backward
- Progress bar

### ✅ Indicator Support
Replay mode can update indicators using the same `handleIndicatorUpdate()` function.

---

## Data Format

### Candle Data Format

```javascript
{
    timestamp: 1234567890,  // Unix timestamp in seconds
    open: 50000,
    high: 51000,
    low: 49500,
    close: 50500,
    closed: true            // Optional: true if candle is closed
}
```

### Indicator Data Format

```javascript
{
    indicatorId: 'sma',
    instanceKey: 'Binance:BTCUSDT:1m:sma:9998834d',
    timestamp: '2025-10-05T12:00:00Z',  // ISO string
    values: {
        sma: 50250,          // Indicator values
        ema: 50300
    }
}
```

---

## Next Steps

1. **Create ReplayDataService.js** to load replay sessions
2. **Create ReplayChart.jsx** component using the example above
3. **Add replay controls UI** for better UX
4. **Store replay sessions** in backend
5. **Add replay session browser** in UI

Your chart architecture is now **fully prepared** for replay mode! 🎉

