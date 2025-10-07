# Bookmap Indicator System - Complete Guide

## 📚 Overview

This guide explains the **extended indicator system** that supports **order book data**, **trade data**, and **Bookmap-style** visualizations.

**What we added:**
- ✅ Order book data processing for indicators
- ✅ Trade data processing for indicators  
- ✅ Data type declaration system (`getRequiredDataTypes()`)
- ✅ Complete Bookmap heatmap indicator example
- ✅ Comprehensive comments throughout the codebase

---

## 🏗️ System Architecture

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         EXCHANGE                                 │
│                      (Binance, etc.)                            │
└────────────┬────────────┬────────────┬─────────────────────────┘
             │            │            │
             │            │            │
     CANDLESTICK    TRADE DATA   ORDER BOOK
       (KLINE)    (AGGREGATE)     (DEPTH)
             │            │            │
             ▼            ▼            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   TradingDataProvider                           │
│              (Converts to TradingData)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        TradingBot                               │
│              (Broadcasts to all handlers)                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│               IndicatorWebSocketHandler                         │
│                  (Routes by data type)                          │
└────────┬────────────┬────────────┬───────────────────────────────┘
         │            │            │
         ▼            ▼            ▼
  processCandleClose  processTrade  processOrderBook
         │            │            │
         ▼            ▼            ▼
┌─────────────────────────────────────────────────────────────────┐
│              IndicatorInstanceManager                           │
│         (Filters by getRequiredDataTypes())                     │
└────────┬────────────┬────────────┬───────────────────────────────┘
         │            │            │
         ▼            ▼            ▼
   onNewCandle()  onTradeUpdate()  onOrderBookUpdate()
         │            │            │
         └────────────┴────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                   YOUR INDICATOR                                │
│              (Processes and returns data)                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  WebSocket Broadcast                            │
│               (Sends to subscribed clients)                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Key Components

### 1. **Indicator Interface** (Extended)

**File:** `src/main/java/org/cloudvision/trading/bot/indicators/Indicator.java`

**New Methods Added:**

```java
// Process order book data
default Map<String, Object> onOrderBookUpdate(
        OrderBookData orderBook, 
        Map<String, Object> params, 
        Object state) {
    return Map.of("values", Map.of(), "state", state);
}

// Process trade data
default Map<String, Object> onTradeUpdate(
        TradeData trade, 
        Map<String, Object> params, 
        Object state) {
    return Map.of("values", Map.of(), "state", state);
}

// Declare required data types
default Set<TradingDataType> getRequiredDataTypes() {
    return Set.of(TradingDataType.KLINE); // Default: only candles
}
```

**What Each Method Does:**

| Method | Called When | Use For | Frequency |
|--------|------------|---------|-----------|
| `onInit()` | Indicator starts | Initialize state | Once |
| `onNewCandle()` | Candle closes | Main calculation | Every candle |
| `onNewTick()` | Price updates | Real-time preview | High (10-100/sec) |
| `onTradeUpdate()` | Trade executes | Order flow analysis | Very high (1000+/sec) |
| `onOrderBookUpdate()` | Order book changes | Market depth | Extremely high (100+/sec) |

---

### 2. **IndicatorInstanceManager** (Extended)

**File:** `src/main/java/org/cloudvision/trading/bot/indicators/IndicatorInstanceManager.java`

**New Methods Added:**

```java
// Update indicator with order book data
public IndicatorResult updateWithOrderBook(String instanceKey, OrderBookData orderBook)

// Update all indicators that need order book data for a symbol
public Map<String, IndicatorResult> updateAllWithOrderBook(
    String provider, String symbol, OrderBookData orderBook)

// Update indicator with trade data
public IndicatorResult updateWithTrade(String instanceKey, TradeData trade)

// Update all indicators that need trade data for a symbol
public Map<String, IndicatorResult> updateAllWithTrade(
    String provider, String symbol, TradeData trade)
```

**What These Do:**

1. **Filter** indicators by `getRequiredDataTypes()`
2. **Call** appropriate method (`onOrderBookUpdate()` or `onTradeUpdate()`)
3. **Extract** results and update state
4. **Return** results for broadcasting

**Performance Optimization:**
- Only calls indicators that explicitly need the data type
- Avoids wasting CPU on indicators that don't care about order books/trades

---

### 3. **IndicatorWebSocketHandler** (Extended)

**File:** `src/main/java/org/cloudvision/trading/bot/websocket/IndicatorWebSocketHandler.java`

**New Processing Methods:**

```java
private void processData(TradingData data) {
    // Routes data by type:
    if (data.hasCandlestickData()) processCandleClose() or processTick()
    if (data.hasTradeData())       processTrade()
    if (data.hasOrderBookData())   processOrderBook()
}

private void processTrade(TradeData trade) {
    // 1. Update all indicators that need trade data
    // 2. Broadcast results to WebSocket clients
}

private void processOrderBook(OrderBookData orderBook) {
    // 1. Update all indicators that need order book data
    // 2. Broadcast results to WebSocket clients
}
```

**New Broadcast Methods:**

```java
private void broadcastTradeIndicatorUpdate(...)
    // Sends: { "type": "indicatorTrade", "data": {...}, "trade": {...} }

private void broadcastOrderBookIndicatorUpdate(...)
    // Sends: { "type": "indicatorOrderBook", "data": {...}, "orderBookSummary": {...} }
```

---

## 📖 Creating a Bookmap-Style Indicator

### Step 1: Declare Required Data Types

```java
@Override
public Set<TradingDataType> getRequiredDataTypes() {
    return Set.of(
        TradingDataType.ORDER_BOOK,       // For market depth
        TradingDataType.AGGREGATE_TRADE,  // For executed volume
        TradingDataType.KLINE             // For timing
    );
}
```

**Available Data Types:**
- `KLINE` - Candlestick/OHLC data (most common)
- `TRADE` - Individual trades (very high frequency)
- `AGGREGATE_TRADE` - Compressed trades (recommended)
- `ORDER_BOOK` - Full order book depth (extremely high frequency)
- `BOOK_TICKER` - Best bid/ask only (lightweight)

---

### Step 2: Initialize State

```java
@Override
public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
    // Create state to track volume at each price level
    BookmapState state = new BookmapState(tickSize, lookback);
    return state;
}
```

**State Should Store:**
- Volume profile (map of price → volume data)
- Current order book snapshot
- Accumulated buy/sell volumes
- Configuration (tick size, lookback, etc.)

---

### Step 3: Process Trades

```java
@Override
public Map<String, Object> onTradeUpdate(TradeData trade, Map<String, Object> params, Object state) {
    BookmapState bookmapState = (BookmapState) state;
    
    // Accumulate volume at this price level
    bookmapState.addTrade(
        trade.getPrice(), 
        trade.getQuantity(), 
        trade.isAggressiveBuy()
    );
    
    // Don't output on every trade (too frequent)
    // Just update state silently
    return Map.of(
        "values", Map.of(),    // Empty values
        "state", bookmapState  // Updated state
    );
}
```

**⚠️ Performance Warning:**
This method is called **1000+ times per second** on active pairs!
- Accumulate data in state
- Only output on candle close
- Keep processing lightweight

---

### Step 4: Process Order Book

```java
@Override
public Map<String, Object> onOrderBookUpdate(OrderBookData orderBook, 
                                             Map<String, Object> params, 
                                             Object state) {
    BookmapState bookmapState = (BookmapState) state;
    
    // Store latest order book snapshot
    bookmapState.setCurrentOrderBook(orderBook);
    
    // Update volume profile with current bid/ask data
    updateProfileWithOrderBook(bookmapState, orderBook);
    
    // Return current bid/ask ratio for real-time display
    BigDecimal bidVolume = orderBook.getTotalBidVolume(10);
    BigDecimal askVolume = orderBook.getTotalAskVolume(10);
    
    Map<String, BigDecimal> values = Map.of(
        "bidAskRatio", bidVolume.divide(askVolume, 4, RoundingMode.HALF_UP)
    );
    
    return Map.of(
        "values", values,
        "state", bookmapState
    );
}
```

**⚠️ Performance Warning:**
This method is called **100+ times per second**!
- Store snapshot in state
- Only output essential metrics
- Frontend should throttle rendering

---

### Step 5: Output on Candle Close

```java
@Override
public Map<String, Object> onNewCandle(CandlestickData candle, 
                                       Map<String, Object> params, 
                                       Object state) {
    BookmapState bookmapState = (BookmapState) state;
    
    // Build heatmap from accumulated data
    Map<String, Object> heatmap = buildHeatmap(bookmapState);
    
    // Detect significant levels
    List<Map<String, Object>> levels = detectSignificantLevels(bookmapState);
    
    // Calculate summary values
    Map<String, BigDecimal> values = Map.of(
        "pocPrice", findPOC(bookmapState),
        "totalVolume", bookmapState.getTotalVolume(),
        "delta", bookmapState.getDelta()
    );
    
    // Return complete result
    return Map.of(
        "values", values,
        "state", bookmapState,
        "heatmap", heatmap,      // For visualization
        "levels", levels          // Significant price levels
    );
}
```

---

## 📊 Example: BookmapIndicator

**File:** `src/main/java/org/cloudvision/trading/bot/indicators/impl/BookmapIndicator.java`

This is a **complete, working example** of a Bookmap-style indicator that:

✅ Accumulates trade volume at each price level
✅ Shows current order book depth
✅ Detects high/low volume nodes
✅ Finds Point of Control (POC)
✅ Calculates bid/ask ratio
✅ Outputs heatmap data for visualization

**Key Features:**

```java
// Accumulates volume in state
private void addTrade(BigDecimal price, BigDecimal volume, boolean isBuy)

// Updates with current order book
private void updateProfileWithOrderBook(OrderBookData orderBook)

// Builds heatmap for visualization
private Map<String, Object> buildHeatmap()

// Finds price with highest volume
private BigDecimal findPOC()

// Detects HVN (High Volume Nodes)
private List<Map<String, Object>> detectSignificantLevels()
```

---

## 🔧 How to Use

### 1. Register Your Indicator

Your indicator is automatically registered if it's a `@Component`:

```java
@Component
public class BookmapIndicator extends AbstractIndicator {
    // ...
}
```

Spring will find it and add it to the `IndicatorInstanceManager`.

---

### 2. Activate via REST API

```bash
POST /api/indicators/instance/activate
Content-Type: application/json

{
  "indicatorId": "bookmap",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "params": {
    "tickSize": "0.01",
    "lookback": 50
  }
}
```

**Response:**
```json
{
  "instanceKey": "Binance:BTCUSDT:5m:bookmap:a1b2c3",
  "status": "active"
}
```

---

### 3. Subscribe via WebSocket

```javascript
const ws = new WebSocket('ws://localhost:8080/indicators-ws');

// Subscribe to indicator updates
ws.send(JSON.stringify({
  action: 'subscribe',
  instanceKeys: ['Binance:BTCUSDT:5m:bookmap:a1b2c3']
}));

// Or subscribe to all indicators for a context
ws.send(JSON.stringify({
  action: 'subscribeContext',
  provider: 'Binance',
  symbol: 'BTCUSDT',
  interval: '5m'
}));
```

---

### 4. Receive Updates

**Candle Close Update:**
```json
{
  "type": "indicatorUpdate",
  "data": {
    "indicatorId": "bookmap",
    "values": {
      "pocPrice": 50000.00,
      "totalVolume": 1234.56,
      "delta": 123.45
    },
    "heatmap": {
      "50000.00": {
        "buyVolume": 100,
        "sellVolume": 80,
        "intensity": 0.85
      }
    },
    "levels": [...]
  }
}
```

**Trade Update:**
```json
{
  "type": "indicatorTrade",
  "trade": {
    "price": 50000.00,
    "quantity": 0.5,
    "isBuy": true
  },
  "data": { ... }
}
```

**Order Book Update:**
```json
{
  "type": "indicatorOrderBook",
  "orderBookSummary": {
    "bestBid": 49999.50,
    "bestAsk": 50000.50,
    "spread": 1.00
  },
  "data": { ... }
}
```

---

## 🎨 Visualization

### Heatmap Data Structure

```json
{
  "heatmap": {
    "50000.00": {
      "buyVolume": 100,        // Executed buy volume
      "sellVolume": 80,        // Executed sell volume
      "bidVolume": 50,         // Current order book bid
      "askVolume": 30,         // Current order book ask
      "totalTraded": 180,      // Total executed volume
      "delta": 20,             // buyVolume - sellVolume
      "intensity": 0.85        // 0-1 for color intensity
    },
    "49999.00": { ... },
    "50001.00": { ... }
  }
}
```

### Frontend Rendering

```javascript
// Example: Render heatmap with Canvas
function renderBookmap(heatmap) {
  for (const [price, data] of Object.entries(heatmap)) {
    const y = priceToY(price);
    const intensity = data.intensity;
    
    // Draw buy volume (green)
    ctx.fillStyle = `rgba(0, 255, 0, ${intensity})`;
    ctx.fillRect(x, y, buyWidth, height);
    
    // Draw sell volume (red)
    ctx.fillStyle = `rgba(255, 0, 0, ${intensity})`;
    ctx.fillRect(x + buyWidth, y, sellWidth, height);
    
    // Draw order book (blue overlay)
    ctx.fillStyle = `rgba(0, 0, 255, 0.3)`;
    ctx.fillRect(x, y, bidWidth, height);
  }
}
```

---

## 🔍 Available Data

### CandlestickData (KLINE)

```java
BigDecimal getOpen()
BigDecimal getHigh()
BigDecimal getLow()
BigDecimal getClose()
BigDecimal getVolume()
Instant getOpenTime()
Instant getCloseTime()
boolean isClosed()
```

### TradeData (TRADE/AGGREGATE_TRADE)

```java
long getTradeId()
BigDecimal getPrice()
BigDecimal getQuantity()
BigDecimal getQuoteQuantity()
boolean isBuyerMaker()        // true = sell order, false = buy order
boolean isAggressiveBuy()     // Market buy (taker)
boolean isAggressiveSell()    // Market sell (taker)
Instant getTimestamp()
boolean isAggregateTrade()
```

### OrderBookData (ORDER_BOOK)

```java
List<OrderBookLevel> getBids()
List<OrderBookLevel> getAsks()
BigDecimal getBestBid()
BigDecimal getBestAsk()
BigDecimal getSpread()
BigDecimal getTotalBidVolume(int levels)
BigDecimal getTotalAskVolume(int levels)
Instant getTimestamp()
```

---

## 📈 Performance Tips

### 1. **Throttle High-Frequency Updates**

```java
// Don't output on every trade
if (tradeCount % 100 == 0) {
    // Only output every 100th trade
    return buildResult();
}
return Map.of("values", Map.of(), "state", state);
```

### 2. **Use Aggregate Trades**

```java
// ✅ GOOD: Use aggregate trades (compressed)
Set.of(TradingDataType.AGGREGATE_TRADE)

// ❌ BAD: Individual trades (very high frequency)
Set.of(TradingDataType.TRADE)
```

### 3. **Accumulate in State**

```java
// ✅ GOOD: Accumulate in state, output on candle close
public Map<String, Object> onTradeUpdate(...) {
    state.addTrade(trade);
    return Map.of("values", Map.of(), "state", state);
}

// ❌ BAD: Calculate and output on every trade
public Map<String, Object> onTradeUpdate(...) {
    state.addTrade(trade);
    return Map.of("values", calculateAll(), "state", state); // Too slow!
}
```

### 4. **Frontend Throttling**

```javascript
// Throttle order book updates to 10 per second
const throttle = (func, delay) => {
  let lastCall = 0;
  return (...args) => {
    const now = Date.now();
    if (now - lastCall >= delay) {
      lastCall = now;
      func(...args);
    }
  };
};

ws.onmessage = throttle((event) => {
  if (event.type === 'indicatorOrderBook') {
    renderHeatmap(event.data);
  }
}, 100); // Max 10 updates per second
```

---

## 🐛 Troubleshooting

### Indicator Not Receiving Data

**Problem:** Indicator activated but no updates

**Solution:** Check `getRequiredDataTypes()`

```java
@Override
public Set<TradingDataType> getRequiredDataTypes() {
    // Make sure you declare all data types you need!
    return Set.of(
        TradingDataType.ORDER_BOOK,
        TradingDataType.AGGREGATE_TRADE,
        TradingDataType.KLINE
    );
}
```

### Too Many Updates (Performance)

**Problem:** Frontend lagging from too many updates

**Solution 1:** Don't output on every update
```java
// Only output meaningful changes
if (hasSignificantChange()) {
    return buildResult();
}
return Map.of("values", Map.of(), "state", state);
```

**Solution 2:** Throttle on frontend
```javascript
// Use requestAnimationFrame for rendering
let updatePending = false;
ws.onmessage = (event) => {
  if (!updatePending) {
    updatePending = true;
    requestAnimationFrame(() => {
      renderHeatmap(event.data);
      updatePending = false;
    });
  }
};
```

### Order Book Data Not Available

**Problem:** Order book updates not coming through

**Solution:** Check TradingConfig

```java
// In TradingConfig.java or application.properties
SUBSCRIBE_TO_ORDER_BOOK = true
ORDER_BOOK_DEPTH = 20  // Number of levels
```

---

## 📚 Additional Resources

### Related Files

- **Indicator Interface:** `src/main/java/org/cloudvision/trading/bot/indicators/Indicator.java`
- **IndicatorInstanceManager:** `src/main/java/org/cloudvision/trading/bot/indicators/IndicatorInstanceManager.java`
- **IndicatorWebSocketHandler:** `src/main/java/org/cloudvision/trading/bot/websocket/IndicatorWebSocketHandler.java`
- **BookmapIndicator Example:** `src/main/java/org/cloudvision/trading/bot/indicators/impl/BookmapIndicator.java`

### Related Guides

- `INDICATOR_INSTANCE_EXAMPLES.md` - Basic indicator usage
- `INDICATOR_INSTANCE_MANAGEMENT.md` - Instance lifecycle
- `ORDER_FLOW_API.md` - Order flow data API
- `ORDER_FLOW_INDICATORS.md` - Order flow indicator examples

---

## ✅ Summary

**What We Added:**

1. ✅ **Extended Indicator Interface**
   - `onOrderBookUpdate()` for order book processing
   - `onTradeUpdate()` for trade processing
   - `getRequiredDataTypes()` for data type declaration

2. ✅ **Extended IndicatorInstanceManager**
   - `updateWithOrderBook()` and `updateAllWithOrderBook()`
   - `updateWithTrade()` and `updateAllWithTrade()`
   - Automatic filtering by required data types

3. ✅ **Extended IndicatorWebSocketHandler**
   - `processOrderBook()` for routing order book data
   - `processTrade()` for routing trade data
   - New broadcast methods for each data type

4. ✅ **Complete Bookmap Example**
   - `BookmapIndicator.java` - Working implementation
   - Accumulates trade volume at price levels
   - Shows current order book depth
   - Detects significant levels (HVN, POC)

**Next Steps:**

1. Start server and verify BookmapIndicator is registered
2. Activate indicator via REST API
3. Subscribe to updates via WebSocket
4. Build frontend visualization for heatmap data
5. Create your own order flow indicators!

---

**Happy Trading! 📈**

