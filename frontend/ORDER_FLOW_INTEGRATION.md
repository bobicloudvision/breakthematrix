# Order Flow Integration Guide

## Quick Start - Add Footprint Data to Your Chart

Follow these steps to show order flow data (buy/sell volume) on every candle in your chart.

## Step 1: Update OrderFlowChart to Use Service

Update your `OrderFlowChart.jsx` to use the `OrderFlowService`:

```javascript
import { getOrderFlowService } from './OrderFlowService';
import { FootprintPrimitive, VolumeProfilePrimitive } from './FootprintPrimitive';

// In your component:
const orderFlowService = useRef(null);
const footprintRef = useRef(null);

// Initialize service
useEffect(() => {
    orderFlowService.current = getOrderFlowService(interval);
    
    // Subscribe to updates
    const unsubscribe = orderFlowService.current.subscribe((data) => {
        if (footprintRef.current) {
            footprintRef.current.updateData(data);
        }
    });
    
    return () => unsubscribe();
}, [interval]);

// When trades come in via WebSocket:
ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    
    if (data.dataType === 'AGGREGATE_TRADE' && orderFlowService.current) {
        orderFlowService.current.addTrade({
            price: data.trade.price,
            quantity: data.trade.quantity,
            timestamp: data.timestamp,
            isBuyerMaker: data.trade.isBuyerMaker,
            isAggressiveSell: data.trade.isAggressiveSell
        });
    }
};
```

## Step 2: Add Footprint Primitive to Your Chart

Update your `Chart.jsx` to support footprint display:

```javascript
import { FootprintPrimitive } from './FootprintPrimitive';
import { getOrderFlowService } from './OrderFlowService';

// In ChartComponent:
const footprintRef = useRef(null);
const [showFootprint, setShowFootprint] = useState(false);

// Add footprint primitive
useEffect(() => {
    if (!chartRef.current || !seriesRef.current || !showFootprint) return;
    
    const footprint = new FootprintPrimitive();
    footprintRef.current = footprint;
    
    seriesRef.current.attachPrimitive(footprint);
    
    // Get order flow service
    const orderFlowService = getOrderFlowService(interval);
    
    // Subscribe to updates
    const unsubscribe = orderFlowService.subscribe((data) => {
        if (footprintRef.current) {
            footprintRef.current.updateData(data);
            chartRef.current?.timeScale()?.fitContent();
        }
    });
    
    // Load initial data
    footprint.updateData(orderFlowService.getOrderFlowData());
    
    return () => {
        if (footprintRef.current && seriesRef.current) {
            seriesRef.current.detachPrimitive(footprintRef.current);
        }
        unsubscribe();
    };
}, [showFootprint, interval]);
```

## Step 3: Add Toggle Button

Add a toggle button in your UI to show/hide footprint data:

```javascript
// In App.jsx or Chart.jsx:
<button
    onClick={() => setShowFootprint(!showFootprint)}
    className={`px-4 py-2 text-sm font-medium rounded-lg ${
        showFootprint 
            ? 'bg-cyan-500/30 text-cyan-100' 
            : 'bg-slate-800/40 text-slate-300'
    }`}
>
    {showFootprint ? '📊 Footprint ON' : '📊 Footprint OFF'}
</button>
```

## Step 4: Backend Integration (Optional - For Historical Data)

If you want to load historical order flow data from your backend:

### Backend Endpoint (Java/Spring Boot example):

```java
@GetMapping("/api/trading/{provider}/orderflow/historical")
public List<CandleOrderFlow> getHistoricalOrderFlow(
    @PathVariable String provider,
    @RequestParam String symbol,
    @RequestParam String interval,
    @RequestParam(defaultValue = "500") int limit
) {
    // Aggregate trades by candle and price level
    return orderFlowService.getHistoricalOrderFlow(provider, symbol, interval, limit);
}
```

### Frontend Fetch:

```javascript
useEffect(() => {
    const fetchHistoricalOrderFlow = async () => {
        try {
            const response = await fetch(
                `http://localhost:8080/api/trading/${provider}/orderflow/historical?` +
                `symbol=${symbol}&interval=${interval}&limit=500`
            );
            if (response.ok) {
                const data = await response.json();
                const orderFlowService = getOrderFlowService(interval);
                orderFlowService.loadHistoricalData(data);
            }
        } catch (error) {
            console.error('Error fetching historical order flow:', error);
        }
    };
    
    if (provider && symbol) {
        fetchHistoricalOrderFlow();
    }
}, [provider, symbol, interval]);
```

## What You'll See

### Footprint Display:
- **Green numbers (+)**: More buying pressure than selling at that price
- **Red numbers (-)**: More selling pressure than buying at that price
- **Gray numbers**: Balanced or low volume

### Example Output:
```
Candle at 12:00:
  Price 42,100: +2.5  (2.5 more BTC bought than sold)
  Price 42,095: -1.2  (1.2 more BTC sold than bought)
  Price 42,090: +0.8
```

## Display Modes

### 1. Delta Mode (Default)
Shows buy/sell pressure difference:
```javascript
footprint.setShowDelta(true);
```

### 2. Volume Mode
Shows total volume:
```javascript
footprint.setShowDelta(false);
```

## Current Setup

With your existing WebSocket (`ws://localhost:8080/orderflow-ws`), you already receive:
- `AGGREGATE_TRADE` - Individual trades with buy/sell flags
- `ORDER_BOOK` - Current order book state

The `OrderFlowService` will automatically:
1. ✅ Aggregate trades into candle periods
2. ✅ Group volume by price level
3. ✅ Calculate buy/sell delta
4. ✅ Update the chart in real-time

## Example: Complete Integration

See the updated `OrderFlowChart.jsx` component which now includes:
- Real-time trade aggregation
- Footprint visualization
- Order book display
- Recent trades panel

## Performance Tips

1. **Limit Price Levels**: Service automatically rounds to appropriate tick size
2. **Cleanup**: Service keeps last 500 candles by default
3. **Throttle Updates**: Service batches updates for better performance

## Next Steps

1. ✅ Created `FootprintPrimitive.js` - Visualization component
2. ✅ Created `OrderFlowService.js` - Data aggregation service
3. 📝 Integrate into your Chart component
4. 📝 Add UI toggle for footprint display
5. 📝 (Optional) Add backend endpoint for historical data

## Questions?

- **"How do I customize colors?"** - Edit `_buyColor`, `_sellColor` in `FootprintPrimitive.js`
- **"Can I show volume profile?"** - Yes! Use `VolumeProfilePrimitive` instead
- **"How to change tick size?"** - Modify `determineTickSize()` in `OrderFlowService.js`
- **"Performance issues?"** - Reduce visible candles or increase tick size

