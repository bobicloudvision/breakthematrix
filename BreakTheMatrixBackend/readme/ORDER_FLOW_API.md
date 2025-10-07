# Order Flow API Documentation

Complete guide to using Order Flow data via WebSocket and REST API.

## 📊 Overview

The Order Flow API provides real-time market microstructure data including:
- **Individual Trades** - Every trade that hits the tape (high frequency)
- **Aggregate Trades** - Compressed trade data (recommended for analysis)
- **Order Book Depth** - Bid/Ask levels with quantities
- **Book Ticker** - Best bid/ask updates (lightweight)
- **Footprint Candles** - Volume profile candles showing buy/sell volume at each price level

---

## 🌐 WebSocket API

### Available WebSocket Endpoints

There are **two WebSocket endpoints** for receiving trading data:

1. **`/orderflow-ws`** - Dedicated order flow endpoint with filtering
   - Supports symbol and type filtering
   - Subscribe/unsubscribe controls
   - Real-time statistics
   - **Recommended for order flow only**

2. **`/trading-ws`** - General trading data endpoint
   - Broadcasts ALL trading data (klines + order flow)
   - No filtering (receives everything)
   - **Recommended for full market data feed**

### Connection URLs
```
ws://localhost:8080/orderflow-ws   (Order Flow with filters)
ws://localhost:8080/trading-ws     (All trading data)
```

### Message Format

All messages are JSON formatted.

#### **Subscribe to Order Flow**
```json
{
  "action": "subscribe",
  "symbol": "BTCUSDT",
  "types": ["AGGREGATE_TRADE", "ORDER_BOOK", "BOOK_TICKER"]
}
```

**Parameters:**
- `symbol`: Trading symbol (optional - omit for ALL symbols)
- `types`: Array of data types to receive (optional - omit for ALL types)

**Supported Types:**
- `TRADE` - Individual trades
- `AGGREGATE_TRADE` - Aggregate trades
- `ORDER_BOOK` - Order book depth
- `BOOK_TICKER` - Best bid/ask

#### **Unsubscribe from Order Flow**
```json
{
  "action": "unsubscribe",
  "symbol": "BTCUSDT"
}
```

#### **Get Statistics**
```json
{
  "action": "getStats"
}
```

Returns trade count, buy/sell volume, last price, and spread for all symbols.

---

### Data Output Formats

**Both endpoints output the same data structure**, but:
- `/orderflow-ws` only sends order flow types (filtered by subscription)
- `/trading-ws` sends ALL data types (klines + order flow, no filtering)

---

### Incoming Data Format

#### **Trade Data** (TRADE or AGGREGATE_TRADE)
```json
{
  "type": "orderFlow",
  "dataType": "TRADE",
  "symbol": "BTCUSDT",
  "timestamp": "2025-10-04T12:34:56.789Z",
  "provider": "Binance",
  "trade": {
    "tradeId": 123456789,
    "price": 50000.50,
    "quantity": 0.5,
    "quoteQuantity": 25000.25,
    "isBuyerMaker": false,
    "isAggressiveBuy": true,
    "isAggressiveSell": false,
    "isAggregate": false,
    "firstTradeId": null,
    "lastTradeId": null
  }
}
```

#### **Aggregate Trade Data**
```json
{
  "type": "orderFlow",
  "dataType": "AGGREGATE_TRADE",
  "symbol": "BTCUSDT",
  "timestamp": "2025-10-04T12:34:56.789Z",
  "provider": "Binance",
  "trade": {
    "tradeId": 123456789,
    "price": 50000.50,
    "quantity": 2.5,
    "quoteQuantity": 125001.25,
    "isBuyerMaker": false,
    "isAggressiveBuy": true,
    "isAggressiveSell": false,
    "isAggregate": true,
    "firstTradeId": 123456785,
    "lastTradeId": 123456789
  }
}
```

#### **Order Book Data**
```json
{
  "type": "orderFlow",
  "dataType": "ORDER_BOOK",
  "symbol": "BTCUSDT",
  "timestamp": "2025-10-04T12:34:56.789Z",
  "provider": "Binance",
  "orderBook": {
    "lastUpdateId": 987654321,
    "bids": [
      {"price": 50000.00, "quantity": 1.5},
      {"price": 49999.50, "quantity": 2.0}
    ],
    "asks": [
      {"price": 50000.50, "quantity": 1.2},
      {"price": 50001.00, "quantity": 0.8}
    ],
    "bestBid": 50000.00,
    "bestAsk": 50000.50,
    "spread": 0.50,
    "bidVolume5": 10.5,
    "askVolume5": 8.3,
    "bidVolume10": 25.8,
    "askVolume10": 18.9
  }
}
```

#### **Book Ticker Data**
```json
{
  "type": "orderFlow",
  "dataType": "BOOK_TICKER",
  "symbol": "BTCUSDT",
  "timestamp": "2025-10-04T12:34:56.789Z",
  "provider": "Binance",
  "orderBook": {
    "lastUpdateId": 987654321,
    "bids": [{"price": 50000.00, "quantity": 1.5}],
    "asks": [{"price": 50000.50, "quantity": 1.2}],
    "bestBid": 50000.00,
    "bestAsk": 50000.50,
    "spread": 0.50
  }
}
```

---

## 🔌 REST API

Base URL: `http://localhost:8080/api/orderflow`

### Subscribe to Individual Trades
```http
POST /subscribe/trades?provider=Binance&symbol=BTCUSDT
```

**Response:**
```json
{
  "success": true,
  "message": "Subscribed to BTCUSDT trades on Binance",
  "provider": "Binance",
  "symbol": "BTCUSDT",
  "type": "TRADE"
}
```

### Subscribe to Aggregate Trades (Recommended)
```http
POST /subscribe/aggregate-trades?provider=Binance&symbol=BTCUSDT
```

### Subscribe to Order Book
```http
POST /subscribe/orderbook?provider=Binance&symbol=BTCUSDT&depth=20
```

**Parameters:**
- `depth`: Order book depth (5, 10, or 20 levels) - default: 20

### Subscribe to Book Ticker
```http
POST /subscribe/book-ticker?provider=Binance&symbol=BTCUSDT
```

### Subscribe to All Order Flow Types
```http
POST /subscribe/all?provider=Binance&symbol=BTCUSDT&orderBookDepth=20&includeAggregateTrades=true&includeIndividualTrades=false&includeOrderBook=true&includeBookTicker=true
```

**Parameters:**
- `orderBookDepth` (default: 20)
- `includeAggregateTrades` (default: true)
- `includeIndividualTrades` (default: false)
- `includeOrderBook` (default: true)
- `includeBookTicker` (default: true)

### Unsubscribe Endpoints

All subscribe endpoints have corresponding DELETE endpoints:

```http
DELETE /subscribe/trades?provider=Binance&symbol=BTCUSDT
DELETE /subscribe/aggregate-trades?provider=Binance&symbol=BTCUSDT
DELETE /subscribe/orderbook?provider=Binance&symbol=BTCUSDT
DELETE /subscribe/book-ticker?provider=Binance&symbol=BTCUSDT
```

### Get Information
```http
GET /supported-types
GET /info
```

---

## 💡 Usage Examples

### JavaScript/TypeScript (WebSocket)

#### Option 1: Order Flow WebSocket (with filtering)
```javascript
// Connect to order flow endpoint with subscription filtering
const ws = new WebSocket('ws://localhost:8080/orderflow-ws');

ws.onopen = () => {
  console.log('Connected to order flow');
  
  // Subscribe to BTCUSDT order flow only
  ws.send(JSON.stringify({
    action: 'subscribe',
    symbol: 'BTCUSDT',
    types: ['AGGREGATE_TRADE', 'BOOK_TICKER']
  }));
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  if (data.type === 'orderFlow') {
    console.log(`${data.dataType} for ${data.symbol}:`, data);
    
    // Handle trade data
    if (data.trade) {
      console.log(`Trade: ${data.trade.isAggressiveBuy ? 'BUY' : 'SELL'} ${data.trade.quantity} @ ${data.trade.price}`);
    }
    
    // Handle order book data
    if (data.orderBook) {
      console.log(`Spread: ${data.orderBook.spread}, Best Bid: ${data.orderBook.bestBid}, Best Ask: ${data.orderBook.bestAsk}`);
    }
  }
};
```

#### Option 2: Trading WebSocket (all data, no filtering)
```javascript
// Connect to general trading endpoint - receives EVERYTHING
const ws = new WebSocket('ws://localhost:8080/trading-ws');

ws.onopen = () => {
  console.log('Connected to trading data stream');
  // No subscription needed - receives all data automatically
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  if (data.type === 'tradingData') {
    console.log(`${data.dataType} for ${data.symbol}`);
    
    // Handle klines
    if (data.candlestick) {
      console.log(`Kline: O:${data.candlestick.open} H:${data.candlestick.high} L:${data.candlestick.low} C:${data.candlestick.close}`);
    }
    
    // Handle trades
    if (data.trade) {
      console.log(`Trade: ${data.trade.isAggressiveBuy ? 'BUY' : 'SELL'} ${data.trade.quantity} @ ${data.trade.price}`);
    }
    
    // Handle order book
    if (data.orderBook) {
      console.log(`Order Book: Spread=${data.orderBook.spread}`);
    }
  }
};
```

### Python (WebSocket)

```python
import websocket
import json

def on_message(ws, message):
    data = json.loads(message)
    if data['type'] == 'orderFlow':
        print(f"{data['dataType']} for {data['symbol']}")
        
        if 'trade' in data:
            trade = data['trade']
            side = 'BUY' if trade['isAggressiveBuy'] else 'SELL'
            print(f"Trade: {side} {trade['quantity']} @ {trade['price']}")

def on_open(ws):
    subscribe_msg = {
        "action": "subscribe",
        "symbol": "BTCUSDT",
        "types": ["AGGREGATE_TRADE", "ORDER_BOOK"]
    }
    ws.send(json.dumps(subscribe_msg))

ws = websocket.WebSocketApp(
    "ws://localhost:8080/orderflow-ws",
    on_open=on_open,
    on_message=on_message
)

ws.run_forever()
```

### cURL (REST API)

```bash
# Subscribe to aggregate trades
curl -X POST "http://localhost:8080/api/orderflow/subscribe/aggregate-trades?provider=Binance&symbol=BTCUSDT"

# Subscribe to order book with 20 levels
curl -X POST "http://localhost:8080/api/orderflow/subscribe/orderbook?provider=Binance&symbol=BTCUSDT&depth=20"

# Subscribe to everything
curl -X POST "http://localhost:8080/api/orderflow/subscribe/all?provider=Binance&symbol=BTCUSDT&includeIndividualTrades=false&includeAggregateTrades=true&includeOrderBook=true&includeBookTicker=true"

# Unsubscribe
curl -X DELETE "http://localhost:8080/api/orderflow/subscribe/aggregate-trades?provider=Binance&symbol=BTCUSDT"
```

---

## 📈 Use Cases

### Scalping / High-Frequency Trading
```javascript
// Subscribe to aggregate trades + book ticker (lightweight, fast)
ws.send(JSON.stringify({
  action: 'subscribe',
  symbol: 'BTCUSDT',
  types: ['AGGREGATE_TRADE', 'BOOK_TICKER']
}));
```

### Order Flow Analysis / Footprint Charts
```javascript
// Subscribe to aggregate trades + order book depth
ws.send(JSON.stringify({
  action: 'subscribe',
  symbol: 'BTCUSDT',
  types: ['AGGREGATE_TRADE', 'ORDER_BOOK']
}));
```

### Liquidity Analysis
```javascript
// Subscribe to order book only (full depth)
ws.send(JSON.stringify({
  action: 'subscribe',
  symbol: 'BTCUSDT',
  types: ['ORDER_BOOK']
}));
```

### Tape Reading (Advanced)
```javascript
// Subscribe to individual trades (very high frequency!)
ws.send(JSON.stringify({
  action: 'subscribe',
  symbol: 'BTCUSDT',
  types: ['TRADE']
}));
```

---

## ⚙️ Configuration

Order flow is configured in `TradingConfig.java`:

```java
// Order Flow Configuration
private static final boolean ENABLE_ORDER_FLOW = true;
private static final boolean SUBSCRIBE_TO_TRADES = false; // Individual trades (HIGH frequency)
private static final boolean SUBSCRIBE_TO_AGGREGATE_TRADES = true; // Recommended
private static final boolean SUBSCRIBE_TO_ORDER_BOOK = true; // Market depth
private static final boolean SUBSCRIBE_TO_BOOK_TICKER = true; // Best bid/ask (lightweight)
private static final int ORDER_BOOK_DEPTH = 20; // 5, 10, or 20 levels
```

---

## 🎯 Best Practices

1. **Start with Aggregate Trades** - Lower frequency than individual trades, easier to process
2. **Use Book Ticker for Quotes** - Lightweight alternative to full order book
3. **Avoid Individual Trades** - Unless you specifically need tape reading, use aggregate trades
4. **Filter by Symbol** - Only subscribe to symbols you're actively trading
5. **Monitor Performance** - Order flow can be high volume, ensure your client can keep up

---

## 🔍 Swagger Documentation

REST API documentation is available at:
```
http://localhost:8080/swagger-ui.html
```

Look for the **"Order Flow"** section.

---

## 🚀 Quick Start

### Method 1: Order Flow WebSocket (Filtered)

1. **Start the application** - Order flow subscriptions happen automatically on startup
2. **Connect to WebSocket** - `ws://localhost:8080/orderflow-ws`
3. **Subscribe to data** - Send subscribe message with symbol and types
4. **Receive real-time data** - Process incoming order flow messages

**Example:**
```javascript
const ws = new WebSocket('ws://localhost:8080/orderflow-ws');
ws.onopen = () => {
  ws.send(JSON.stringify({
    action: 'subscribe',
    symbol: 'BTCUSDT',
    types: ['AGGREGATE_TRADE', 'BOOK_TICKER']
  }));
};
ws.onmessage = (e) => console.log(JSON.parse(e.data));
```

### Method 2: Trading WebSocket (All Data)

1. **Start the application**
2. **Connect to WebSocket** - `ws://localhost:8080/trading-ws`
3. **Receive everything** - No subscription needed, all data flows automatically

**Example:**
```javascript
const ws = new WebSocket('ws://localhost:8080/trading-ws');
ws.onopen = () => console.log('Connected! Receiving all data...');
ws.onmessage = (e) => console.log(JSON.parse(e.data));
```

---

## 🎯 Which WebSocket Should I Use?

| Use Case | Recommended Endpoint | Reason |
|----------|---------------------|---------|
| **Order flow analysis only** | `/orderflow-ws` | Filter by symbol/type, less data |
| **Full market feed (klines + order flow)** | `/trading-ws` | Everything in one stream |
| **Multiple symbols, selective types** | `/orderflow-ws` | Subscribe/unsubscribe dynamically |
| **One symbol, all data** | `/trading-ws` | Simpler, no subscription management |
| **Dashboard with all data** | `/trading-ws` | Single connection for everything |

That's it! You're now receiving real-time order flow data! 🎉

---

## 🦶 Footprint Candles

Footprint candles show the volume traded at each price level within a candle, separated by buy and sell volume. Essential for order flow analysis and tape reading.

### Features

- **Volume Profile** - Price ladder showing volume at each level
- **Buy vs Sell Volume** - Volume breakdown per price level
- **Delta** - Buy volume minus sell volume
- **Cumulative Delta** - Running total of delta across candles
- **Point of Control (POC)** - Price level with highest volume
- **Value Area** - Price range containing 70% of volume
- **Imbalance Detection** - Identify buy/sell pressure at each level

### REST API Endpoints

#### Get Historical Footprint Candles
```http
GET /api/footprint/historical?symbol=BTCUSDT&interval=1m&limit=100
```

**Response:**
```json
{
  "success": true,
  "symbol": "BTCUSDT",
  "interval": "1m",
  "count": 100,
  "candles": [
    {
      "symbol": "BTCUSDT",
      "openTime": "2025-10-04T12:34:00Z",
      "closeTime": "2025-10-04T12:35:00Z",
      "interval": "1m",
      "open": 50000.00,
      "high": 50010.00,
      "low": 49990.00,
      "close": 50005.00,
      "totalVolume": 15.5,
      "totalBuyVolume": 9.2,
      "totalSellVolume": 6.3,
      "delta": 2.9,
      "cumulativeDelta": 15.3,
      "numberOfTrades": 245,
      "pointOfControl": 50000.00,
      "valueAreaHigh": 50005.00,
      "valueAreaLow": 49995.00,
      "volumeProfile": [
        {
          "price": 50010.00,
          "buyVolume": 0.5,
          "sellVolume": 0.2,
          "totalVolume": 0.7,
          "delta": 0.3,
          "buyRatio": 0.7143,
          "tradeCount": 8
        },
        {
          "price": 50005.00,
          "buyVolume": 2.1,
          "sellVolume": 1.8,
          "totalVolume": 3.9,
          "delta": 0.3,
          "buyRatio": 0.5385,
          "tradeCount": 42
        }
      ]
    }
  ]
}
```

#### Get Current (Incomplete) Footprint Candle
```http
GET /api/footprint/current?symbol=BTCUSDT&interval=1m
```

Returns the footprint candle currently being built in real-time.

#### Set Tick Size
```http
POST /api/footprint/tick-size?symbol=BTCUSDT&tickSize=0.01
```

Configure the price grouping (tick size) for a symbol. Default is 0.01 for BTC/ETH.

### Usage Examples

#### JavaScript/TypeScript
```javascript
// Get historical footprint candles
async function getFootprintData() {
  const response = await fetch(
    'http://localhost:8080/api/footprint/historical?symbol=BTCUSDT&interval=5m&limit=50'
  );
  const data = await response.json();
  
  data.candles.forEach(candle => {
    console.log(`${candle.symbol} ${candle.openTime}`);
    console.log(`OHLC: ${candle.open}/${candle.high}/${candle.low}/${candle.close}`);
    console.log(`Delta: ${candle.delta}, POC: ${candle.pointOfControl}`);
    
    // Volume profile
    candle.volumeProfile.forEach(level => {
      const imbalance = level.buyRatio > 0.6 ? 'BUY' : level.buyRatio < 0.4 ? 'SELL' : 'NEUTRAL';
      console.log(`  ${level.price}: ${level.totalVolume} (${imbalance})`);
    });
  });
}

// Get current candle (real-time)
async function getCurrentFootprint() {
  const response = await fetch(
    'http://localhost:8080/api/footprint/current?symbol=BTCUSDT&interval=1m'
  );
  const data = await response.json();
  
  if (data.candle) {
    console.log('Current candle delta:', data.candle.delta);
    console.log('POC:', data.candle.pointOfControl);
  }
}

// Poll every second for real-time updates
setInterval(getCurrentFootprint, 1000);
```

#### Python
```python
import requests
import pandas as pd

def get_footprint_candles(symbol, interval, limit=100):
    url = f"http://localhost:8080/api/footprint/historical"
    params = {
        "symbol": symbol,
        "interval": interval,
        "limit": limit
    }
    
    response = requests.get(url, params=params)
    data = response.json()
    
    if data['success']:
        candles = data['candles']
        
        # Convert to DataFrame
        df = pd.DataFrame([{
            'time': c['openTime'],
            'open': c['open'],
            'high': c['high'],
            'low': c['low'],
            'close': c['close'],
            'volume': c['totalVolume'],
            'delta': c['delta'],
            'poc': c['pointOfControl']
        } for c in candles])
        
        return df
    else:
        print(f"Error: {data.get('error')}")
        return None

# Get footprint data
df = get_footprint_candles('BTCUSDT', '5m', 100)
print(df.head())

# Analyze delta
positive_delta = df[df['delta'] > 0]
print(f"Candles with positive delta: {len(positive_delta)}")
```

### Building Your Own Footprint Chart

```javascript
// Example: Building a footprint chart visualization
function renderFootprintChart(candles) {
  candles.forEach(candle => {
    // Draw candlestick
    drawCandlestick(candle.open, candle.high, candle.low, candle.close);
    
    // Draw volume profile inside the candle
    candle.volumeProfile.forEach(level => {
      const y = priceToY(level.price);
      const buyWidth = (level.buyVolume / level.totalVolume) * candleWidth;
      const sellWidth = (level.sellVolume / level.totalVolume) * candleWidth;
      
      // Draw buy volume (green)
      drawBar(x, y, buyWidth, 'green');
      
      // Draw sell volume (red)  
      drawBar(x + buyWidth, y, sellWidth, 'red');
      
      // Highlight POC
      if (level.price === candle.pointOfControl) {
        drawPOCLine(y);
      }
    });
    
    // Draw value area
    drawValueArea(candle.valueAreaHigh, candle.valueAreaLow);
  });
}
```

### Key Metrics Explained

| Metric | Description | Usage |
|--------|-------------|-------|
| **Delta** | Buy volume - Sell volume | Positive = buying pressure, Negative = selling pressure |
| **Cumulative Delta** | Running sum of delta | Trend of buying/selling pressure |
| **Point of Control (POC)** | Price with highest volume | Key support/resistance level |
| **Value Area High/Low** | Range containing 70% of volume | Fair value zone |
| **Buy Ratio** | Buy volume / Total volume | >0.6 = strong buying, <0.4 = strong selling |

### Trading Strategies with Footprint Candles

#### 1. Delta Divergence
```javascript
// Look for price going up but delta going down (bearish divergence)
if (candle.close > previousCandle.close && candle.delta < previousCandle.delta) {
  console.log('Bearish divergence detected');
}
```

#### 2. POC as Support/Resistance
```javascript
// Trade bounces off POC
if (candle.low <= candle.pointOfControl && candle.close > candle.pointOfControl) {
  console.log('POC acted as support');
}
```

#### 3. Value Area Trading
```javascript
// Price outside value area = potential reversion
if (currentPrice > candle.valueAreaHigh) {
  console.log('Price above value area - potential short');
} else if (currentPrice < candle.valueAreaLow) {
  console.log('Price below value area - potential long');
}
```

#### 4. Imbalance Detection
```javascript
// Find strong buy/sell imbalances
candle.volumeProfile.forEach(level => {
  if (level.buyRatio > 0.8) {
    console.log(`Strong buying at ${level.price}`);
  } else if (level.buyRatio < 0.2) {
    console.log(`Strong selling at ${level.price}`);
  }
});
```

### How It Works

1. **Automatic Building** - Footprint candles are automatically built from incoming trade data
2. **Multiple Intervals** - Built for 1m, 5m, and 15m intervals simultaneously
3. **Real-time Updates** - Current candle updates with each new trade
4. **Historical Cache** - Last 500 candles cached per symbol/interval

### Configuration

Footprint candles are automatically enabled when order flow is enabled. The system:
- Listens to aggregate trade data
- Groups trades by price level (tick size)
- Calculates buy/sell volume per level
- Computes POC, value area, and delta
- Provides both historical and real-time data

No additional configuration needed - just enable order flow and footprint data will be available!

---

