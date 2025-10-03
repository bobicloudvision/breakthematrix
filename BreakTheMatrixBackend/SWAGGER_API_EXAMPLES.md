# Swagger API Documentation - Indicators

## Accessing Swagger UI

Once your Spring Boot application is running, access the Swagger UI at:

```
http://localhost:8080/swagger-ui.html
```

Or the OpenAPI JSON spec at:
```
http://localhost:8080/v3/api-docs
```

## API Endpoints with Examples

### 1. GET `/api/indicators` - List All Indicators

**Description:** Returns a list of all available technical indicators with optional category filtering.

**Query Parameters:**
- `category` (optional): Filter by category - `TREND`, `MOMENTUM`, `VOLATILITY`, `VOLUME`, `OVERLAY`, `CUSTOM`

**Example Requests:**

```bash
# Get all indicators
curl -X GET "http://localhost:8080/api/indicators"

# Filter by TREND category
curl -X GET "http://localhost:8080/api/indicators?category=TREND"

# Filter by VOLUME category
curl -X GET "http://localhost:8080/api/indicators?category=VOLUME"
```

**Example Response:**

```json
[
  {
    "id": "sma",
    "name": "Simple Moving Average",
    "description": "Arithmetic mean of closing prices over a specified period",
    "category": "TREND",
    "categoryDisplayName": "Trend Indicators"
  },
  {
    "id": "volume",
    "name": "Volume",
    "description": "Trading volume with color-coded bars",
    "category": "VOLUME",
    "categoryDisplayName": "Volume Indicators"
  }
]
```

---

### 2. GET `/api/indicators/{id}` - Get Indicator Details

**Description:** Retrieves detailed information about a specific indicator including parameters, visualization metadata, and requirements.

**Path Parameters:**
- `id` (required): Indicator identifier (e.g., `sma`, `volume`)

**Example Request:**

```bash
curl -X GET "http://localhost:8080/api/indicators/sma"
```

**Example Response:**

```json
{
  "id": "sma",
  "name": "Simple Moving Average",
  "description": "Arithmetic mean of closing prices over a specified period. Smooths price data to identify trends.",
  "category": "TREND",
  "categoryDisplayName": "Trend Indicators",
  "parameters": {
    "period": {
      "name": "period",
      "displayName": "Period",
      "description": "Number of periods for the moving average",
      "type": "INTEGER",
      "defaultValue": 20,
      "minValue": 1,
      "maxValue": 500,
      "required": true
    },
    "source": {
      "name": "source",
      "displayName": "Price Source",
      "description": "Price type to use for calculation",
      "type": "STRING",
      "defaultValue": "close",
      "required": false
    },
    "color": {
      "name": "color",
      "displayName": "Line Color",
      "description": "Color for the SMA line on the chart",
      "type": "STRING",
      "defaultValue": "#2962FF",
      "required": false
    }
  },
  "visualizationMetadata": {
    "sma": {
      "key": "sma",
      "displayName": "SMA(20)",
      "visualType": "LINE",
      "color": "#2962FF",
      "lineWidth": 2,
      "separatePane": false,
      "paneOrder": 0
    }
  },
  "minRequiredCandles": 20
}
```

---

### 3. POST `/api/indicators/{id}/calculate` - Calculate Current Value

**Description:** Calculates the current value of a technical indicator using the latest market data.

**Path Parameters:**
- `id` (required): Indicator identifier

**Request Body Examples:**

#### Example 1: SMA(50) for BTCUSDT

```bash
curl -X POST "http://localhost:8080/api/indicators/sma/calculate" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {
      "period": 50,
      "color": "#FF6B6B"
    }
  }'
```

**Response:**

```json
{
  "indicatorId": "sma",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "values": {
    "sma": "95234.56"
  }
}
```

#### Example 2: SMA(20) for ETHUSDT

```bash
curl -X POST "http://localhost:8080/api/indicators/sma/calculate" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "ETHUSDT",
    "interval": "1m",
    "params": {
      "period": 20,
      "source": "close"
    }
  }'
```

**Response:**

```json
{
  "indicatorId": "sma",
  "symbol": "ETHUSDT",
  "interval": "1m",
  "values": {
    "sma": "3542.18"
  }
}
```

#### Example 3: Volume for BTCUSDT

```bash
curl -X POST "http://localhost:8080/api/indicators/volume/calculate" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "15m",
    "params": {}
  }'
```

**Response:**

```json
{
  "indicatorId": "volume",
  "symbol": "BTCUSDT",
  "interval": "15m",
  "values": {
    "volume": "1245.67890000"
  }
}
```

---

### 4. POST `/api/indicators/{id}/historical` - Get Historical Data

**Description:** Retrieves historical indicator values for charting. Returns time-series data that can be plotted directly.

**Path Parameters:**
- `id` (required): Indicator identifier

**Request Body Examples:**

#### Example 1: SMA(20) - 200 Data Points

```bash
curl -X POST "http://localhost:8080/api/indicators/sma/historical" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "count": 200,
    "params": {
      "period": 20,
      "color": "#2962FF",
      "lineWidth": 2
    }
  }'
```

**Response:**

```json
{
  "indicatorId": "sma",
  "symbol": "BTCUSDT",
  "interval": "5m",
  "count": 200,
  "data": [
    {
      "time": 1704067200,
      "values": {
        "sma": "94500.00"
      }
    },
    {
      "time": 1704067500,
      "values": {
        "sma": "94523.45"
      }
    },
    {
      "time": 1704067800,
      "values": {
        "sma": "94550.12"
      }
    }
    // ... 197 more data points
  ],
  "metadata": {
    "sma": {
      "key": "sma",
      "displayName": "SMA(20)",
      "visualType": "LINE",
      "color": "#2962FF",
      "lineWidth": 2,
      "separatePane": false,
      "paneOrder": 0
    }
  }
}
```

#### Example 2: SMA(50) for ETHUSDT - 1 Hour Interval

```bash
curl -X POST "http://localhost:8080/api/indicators/sma/historical" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "ETHUSDT",
    "interval": "1h",
    "count": 100,
    "params": {
      "period": 50,
      "source": "close"
    }
  }'
```

#### Example 3: Volume History - 500 Points

```bash
curl -X POST "http://localhost:8080/api/indicators/volume/historical" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "1m",
    "count": 500,
    "params": {
      "bullishColor": "#26a69a",
      "bearishColor": "#ef5350"
    }
  }'
```

---

## Common Request Body Parameters

### Required Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `provider` | String | Data provider name | `"Binance"` |
| `symbol` | String | Trading pair symbol | `"BTCUSDT"`, `"ETHUSDT"` |
| `interval` | String | Timeframe for candles | `"1m"`, `"5m"`, `"15m"`, `"1h"`, `"4h"`, `"1d"` |
| `params` | Object | Indicator-specific parameters | `{"period": 20}` |

### Optional Fields (for historical endpoint)

| Field | Type | Description | Default |
|-------|------|-------------|---------|
| `count` | Integer | Number of data points to return | `100` |

---

## Indicator-Specific Parameters

### SMA (Simple Moving Average)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | Integer | 20 | Number of periods for calculation |
| `source` | String | "close" | Price type: `close`, `open`, `high`, `low`, `hl2`, `hlc3`, `ohlc4` |
| `color` | String | "#2962FF" | Line color for visualization |
| `lineWidth` | Integer | 2 | Line width (1-10) |

### Volume

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `bullishColor` | String | "#26a69a" | Color for bullish volume bars |
| `bearishColor` | String | "#ef5350" | Color for bearish volume bars |

---

## Error Responses

### 400 Bad Request

```json
{
  "error": "No historical data available for BTCUSDT"
}
```

or

```json
{
  "error": "Required parameter missing: period"
}
```

### 404 Not Found

```json
{
  "error": "Indicator not found: invalid_id"
}
```

---

## Using Swagger UI

1. **Start your application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Open Swagger UI:**
   ```
   http://localhost:8080/swagger-ui.html
   ```

3. **Navigate to "Indicators" section**

4. **Click on any endpoint to expand it**

5. **Click "Try it out" button**

6. **Select an example from the dropdown** (you'll see all the examples we defined!)

7. **Click "Execute" to send the request**

8. **View the response below**

---

## Frontend Integration Examples

### JavaScript/TypeScript

```javascript
// Fetch all indicators
async function getAllIndicators() {
  const response = await fetch('http://localhost:8080/api/indicators');
  return await response.json();
}

// Get SMA(20) historical data
async function getSMAHistory(symbol, interval, count) {
  const response = await fetch(
    'http://localhost:8080/api/indicators/sma/historical',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'Binance',
        symbol: symbol,
        interval: interval,
        count: count,
        params: {
          period: 20,
          color: '#2962FF'
        }
      })
    }
  );
  return await response.json();
}

// Calculate current SMA value
async function calculateSMA(symbol, period) {
  const response = await fetch(
    'http://localhost:8080/api/indicators/sma/calculate',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'Binance',
        symbol: symbol,
        interval: '5m',
        params: { period: period }
      })
    }
  );
  return await response.json();
}
```

### React Hook Example

```tsx
import { useState, useEffect } from 'react';

function useSMAIndicator(symbol: string, period: number) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    async function fetchSMA() {
      try {
        const response = await fetch(
          'http://localhost:8080/api/indicators/sma/historical',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              provider: 'Binance',
              symbol: symbol,
              interval: '5m',
              count: 200,
              params: { period }
            })
          }
        );
        const result = await response.json();
        setData(result);
      } catch (error) {
        console.error('Failed to fetch SMA:', error);
      } finally {
        setLoading(false);
      }
    }
    
    fetchSMA();
  }, [symbol, period]);
  
  return { data, loading };
}
```

---

## Testing Tips

1. **Use the test HTML page:**
   - Open `test-indicators-api.html` in your browser
   - All endpoints are pre-configured with working examples

2. **Use Swagger UI:**
   - Best for exploring all available examples
   - Interactive "Try it out" functionality
   - Automatic request/response validation

3. **Use Postman:**
   - Import the OpenAPI spec from `http://localhost:8080/v3/api-docs`
   - All examples will be imported automatically

4. **Use curl:**
   - Copy examples from this document
   - Great for scripting and automation

---

## Next Steps

1. **Explore Swagger UI** to see all examples in action
2. **Try different symbols:** ETHUSDT, BNBUSDT, SOLUSDT, etc.
3. **Experiment with parameters:** Change periods, colors, intervals
4. **Add more indicators:** RSI, MACD, Bollinger Bands, etc.
5. **Build your frontend:** Use the examples as a reference

For more information, see `INDICATOR_ARCHITECTURE.md`

