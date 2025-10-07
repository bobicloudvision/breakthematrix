# Chart Architecture

## Overview

The chart system has been refactored into three separate modules for better separation of concerns and easier extensibility (especially for replay functionality).

## File Structure

### 1. **ChartDataService.js** - Data Layer
**Purpose:** All API requests and data fetching logic (for initial/historical data)

**Responsibilities:**
- Fetch historical candle data from the backend
- Fetch strategy visualization data
- Fetch indicator data
- Transform API data into chart-compatible format
- Provide utility functions (e.g., `getIntervalSeconds`)

**Key Functions:**
- `fetchHistoricalData(provider, symbol, interval, limit)` - Fetch candles
- `fetchStrategyData(strategyId, symbol)` - Fetch single strategy data
- `fetchAllStrategyData(activeStrategies, symbol)` - Fetch all active strategies
- `fetchIndicators(provider, symbol, interval, count)` - Fetch indicator data
- `transformApiData(apiData)` - Transform backend data to chart format

**Why separate?**
- Easy to swap with replay data service
- Can mock for testing
- Clear API contract
- Single source of truth for data fetching

---

### 2. **chartUpdateHandlers.js** - Shared Update Logic
**Purpose:** Reusable functions for updating chart data (used by both WebSocket and replay)

**Responsibilities:**
- Handle candle updates (single or batch)
- Handle indicator updates
- Extract data from WebSocket messages
- Provide consistent update logic across live and replay modes

**Key Functions:**
- `handleCandleUpdate(candleData, seriesRef, seriesManagerRef, onDataUpdate, onRealCountUpdate)` - Update single candle
- `handleIndicatorUpdate(indicatorData, seriesManagerRef, onStatusUpdate)` - Update indicator values
- `handleBatchCandleUpdate(candles, seriesRef, seriesManagerRef, onDataUpdate)` - Update multiple candles at once
- `extractCandleFromMessage(message)` - Parse WebSocket message for candle data
- `extractIndicatorFromMessage(message)` - Parse WebSocket message for indicator data

**Why shared?**
- **Reusable**: Same logic for WebSocket (live) and replay mode
- **Consistent**: Ensures both modes behave identically
- **Testable**: Can be tested independently
- **No duplication**: Write update logic once, use everywhere

---

### 3. **useChartWebSocket.js** - Real-Time Data Layer
**Purpose:** Custom React hook for WebSocket connection and real-time updates

**Responsibilities:**
- Manage WebSocket connection lifecycle
- Handle real-time candle updates
- Handle real-time indicator updates
- Provide connection status and messages
- Expose reconnect functionality

**Key Functions:**
- `useChartWebSocket(params)` - Custom hook that returns `{ status, message, reconnect }`
- `getWebSocketStatusColors(status)` - Utility for UI status colors
- `handleCandleUpdate()` - Process real-time candle updates
- `handleIndicatorUpdate()` - Process real-time indicator updates

**Hook Parameters:**
```javascript
{
    provider,           // Trading provider
    symbol,             // Trading symbol
    interval,           // Time interval
    seriesRef,          // Ref to main series
    seriesManagerRef,   // Ref to series manager
    onDataUpdate,       // Callback for candle updates
    onRealCountUpdate,  // Callback for real count updates
    enabled,            // Enable/disable WebSocket (false for replay)
    reconnectTrigger    // Trigger reconnection
}
```

**Why a custom hook?**
- Encapsulates all WebSocket logic
- Easy to disable for replay mode (`enabled: false`)
- Reusable across different chart components
- Follows React best practices
- Testable in isolation

---

### 4. **ChartRenderer.jsx** - Presentation Layer
**Purpose:** All chart rendering and manipulation logic

**Responsibilities:**
- Create and configure the TradingView chart
- Apply data to the chart (candles, indicators, shapes)
- Manage chart series (lines, areas, histograms, etc.)
- Handle chart lifecycle (resize, cleanup)
- Render indicators, boxes, arrows, markers, fills
- Display loading/error states

**Key Features:**
- **100% Pure Presentation** - No data fetching, only rendering
- Receives all data and callbacks as props
- `onFetchIndicators` prop for indicator loading (provided by parent)
- Manages chart instance and series
- Handles all visual elements
- Manages refs for external updates (WebSocket)

**Props:**
- `data` - Candle data to render
- `onFetchIndicators` - **Callback function** for fetching indicators
- `provider`, `symbol`, `interval` - Context information
- `seriesRef`, `seriesManagerRef` - External refs for updates
- `enabledIndicators`, `strategyData` - Indicator/strategy configuration

**Why separate?**
- **Purely presentational** - Doesn't know about data sources
- **Data source agnostic** - Works with live, replay, or any other source
- **Flexible** - Parent controls how indicators are fetched
- Easier to test and maintain
- Clear separation from business logic

---

### 5. **Chart.jsx** - Orchestration Layer
**Purpose:** Main chart component that orchestrates everything

**Responsibilities:**
- Manage application state (data, loading, errors)
- Coordinate data fetching via ChartDataService
- Manage WebSocket connections for real-time updates
- Pass data to ChartRenderer for visualization
- Handle status indicators and reconnection

**Key Features:**
- Uses ChartDataService for all data fetching
- Passes data to ChartRenderer for rendering
- Manages WebSocket for real-time updates
- Coordinates series refs for live updates

**Why this approach?**
- Clean separation of concerns
- Easy to extend (e.g., add replay mode)
- Testable and maintainable
- Clear data flow

---

## Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Chart.jsx                                  │
│                       (Orchestrator)                                │
│                                                                     │
│  • Manages state (data, loading, errors)                           │
│  • Coordinates data fetching & WebSocket                           │
│  • Passes data to renderer                                         │
└────┬──────────────────┬─────────────────────┬──────────────────────┘
     │                  │                     │
     ▼                  ▼                     ▼
┌──────────────┐  ┌────────────────┐  ┌──────────────────────────┐
│ChartDataServ │  │useChartWebSocket│  │  ChartRenderer.jsx       │
│ (Data Layer) │  │  (Real-Time)   │  │ (Presentation Layer)     │
│              │  │                │  │                          │
│• fetchHist() │─▶│• WS connection │─▶│• Creates chart           │
│• fetchStrat()│  │• Uses handlers │  │• Renders data            │
│• fetchIndic()│  │  ↓             │  │• Manages series          │
│• transform() │  │                │  │• Handles shapes          │
└──────────────┘  └────────┬───────┘  └──────────────────────────┘
                           │                   ▲
                           ▼                   │
                  ┌────────────────────┐       │
                  │chartUpdateHandlers │       │
                  │ (Shared Logic) ✨  │       │
                  │                    │       │
                  │• handleCandle()    │───────┘
                  │• handleIndicator() │ (Updates chart via refs)
                  │• handleBatch()     │
                  │• extract...()      │
                  └────────────────────┘
                           ▲
                           │
                  (Also used by replay mode!)
```

---

## Future: Adding Replay Functionality

With this architecture, adding replay is straightforward:

### Option 1: Replay Data Service
Create a `ReplayDataService.js` that implements the same interface as `ChartDataService.js`:

```javascript
// ReplayDataService.js
export const fetchHistoricalData = async (provider, symbol, interval, limit) => {
    // Return cached/stored replay data instead of hitting the API
    return {
        data: replayDataCache[symbol],
        realCount: replayDataCache[symbol].length
    };
};
```

Then in Chart.jsx, disable WebSocket and use replay data:
```javascript
// Import replay data service instead of live data service
import { fetchHistoricalData } from isReplayMode 
    ? './ReplayDataService' 
    : './ChartDataService';

// Disable WebSocket for replay mode
const { status, message, reconnect } = useChartWebSocket({
    provider,
    symbol,
    interval,
    seriesRef,
    seriesManagerRef,
    onDataUpdate: setData,
    onRealCountUpdate: setRealCount,
    enabled: !isReplayMode, // 🔑 Disable WebSocket in replay mode
    reconnectTrigger: wsReconnectTrigger
});
```

### Option 2: Replay Component
Create a `ReplayChart.jsx` that uses ChartRenderer and shared update handlers:

```javascript
// ReplayChart.jsx
import { ChartRenderer } from './ChartRenderer';
import { useChartWebSocket } from './useChartWebSocket';
import { handleCandleUpdate, handleBatchCandleUpdate } from './chartUpdateHandlers'; // ✨ Shared!

export function ReplayChart({ replayData, ... }) {
    const [currentIndex, setCurrentIndex] = useState(0);
    const [data, setData] = useState([]);
    const [isPlaying, setIsPlaying] = useState(false);
    const seriesRef = useRef(null);
    const seriesManagerRef = useRef(null);
    
    // WebSocket disabled for replay
    const { status, message } = useChartWebSocket({
        provider: null,
        symbol: null,
        interval: null,
        seriesRef,
        seriesManagerRef,
        onDataUpdate: setData,
        onRealCountUpdate: () => {},
        enabled: false, // 🔑 Disabled for replay
        reconnectTrigger: 0
    });
    
    // Initialize with batch update
    useEffect(() => {
        if (replayData.length > 0) {
            const initial = replayData.slice(0, 50);
            handleBatchCandleUpdate(initial, seriesRef, seriesManagerRef, setData);
            setCurrentIndex(50);
        }
    }, [replayData]);
    
    // Playback: update candle-by-candle using shared handler
    useEffect(() => {
        if (isPlaying && currentIndex < replayData.length) {
            const timer = setInterval(() => {
                const nextCandle = replayData[currentIndex];
                handleCandleUpdate(nextCandle, seriesRef, seriesManagerRef, setData);
                setCurrentIndex(prev => prev + 1);
            }, 1000);
            return () => clearInterval(timer);
        }
    }, [isPlaying, currentIndex]);
    
    return (
        <>
            <ReplayControls 
                isPlaying={isPlaying}
                onPlay={() => setIsPlaying(true)}
                onPause={() => setIsPlaying(false)}
                currentIndex={currentIndex}
                maxIndex={replayData.length}
            />
            <ChartRenderer 
                data={data} 
                seriesRef={seriesRef}
                seriesManagerRef={seriesManagerRef}
                {...props} 
            />
        </>
    );
}
```

---

## Benefits of This Architecture

### 1. **Separation of Concerns**
- Data fetching ≠ Data rendering
- Each file has one clear responsibility
- Easier to understand and maintain

### 2. **Testability**
- Can mock ChartDataService for ChartRenderer tests
- Can test data fetching independently
- Can test orchestration logic separately

### 3. **Flexibility**
- Easy to swap data sources (live, replay, backtest, mock)
- Can reuse ChartRenderer with different data sources
- Can add new features without touching rendering logic

### 4. **Maintainability**
- Smaller, focused files
- Clear boundaries
- Easier to debug
- Easier to extend

### 5. **Replay-Ready**
- ChartRenderer doesn't care where data comes from
- Just need to implement replay data provider
- WebSocket can be disabled with a single flag (`enabled: false`)
- Can control data flow externally

### 6. **WebSocket Isolation**
- WebSocket logic in a custom hook
- Easy to disable for replay/testing
- Reusable across components
- Status/reconnect handled in one place

### 7. **Shared Update Handlers**
- Same update logic for WebSocket (live) and replay mode
- No code duplication
- Consistent behavior across modes
- Easy to test and maintain
- Located in `chartUpdateHandlers.js`

---

## Migration Notes

### Breaking Changes
None! The public API remains the same:

```jsx
<Chart 
    provider={provider}
    symbol={symbol}
    interval={interval}
    activeStrategies={activeStrategies}
    enabledIndicators={enabledIndicators}
/>
```

### Internal Changes
- Old `ChartComponent` → Now `ChartRenderer`
- Old inline data fetching → Now `ChartDataService`
- Old inline WebSocket logic → Now `useChartWebSocket` hook
- Old mixed concerns → Now clear separation

### WebSocket Updates
WebSocket is now in a custom hook:
- Chart.jsx uses `useChartWebSocket` hook
- Can be easily disabled (`enabled: false`)
- Updates still applied via `seriesRef` and `seriesManagerRef`
- ChartRenderer exposes refs for external updates
- Status/reconnect handled by the hook

---

## Example: Implementing Replay

```javascript
// 1. Create ReplayDataService.js
export const fetchHistoricalData = async (provider, symbol, interval) => {
    const replayData = await loadReplaySession(sessionId);
    return {
        data: replayData.candles,
        realCount: replayData.candles.length
    };
};

// 2. Modify Chart.jsx (or create ReplayChart.jsx)
import { fetchHistoricalData } from props.replayMode 
    ? './ReplayDataService' 
    : './ChartDataService';

// 3. Disable WebSocket in replay mode
const { status, message, reconnect } = useChartWebSocket({
    provider,
    symbol,
    interval,
    seriesRef,
    seriesManagerRef,
    onDataUpdate: setData,
    onRealCountUpdate: setRealCount,
    enabled: !props.replayMode, // 🔑 Disable WebSocket for replay
    reconnectTrigger: wsReconnectTrigger
});

// 4. Use ChartRenderer as-is!
<ChartRenderer data={data} {...props} />
```

That's it! The renderer doesn't need to change at all, and WebSocket is automatically disabled in replay mode.

