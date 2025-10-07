# Indicator Parameter Update Guide

## Overview

You can now update the parameters of an active indicator instance **IN-PLACE** and have all historical data automatically recalculated with the new parameters. This is useful when you want to adjust indicator settings (like SMA period, RSI thresholds, etc.) without losing the active instance context.

## How It Works

When you update an indicator's parameters:
1. The indicator parameters are updated **in-place** on the same instance
2. The indicator is reinitialized with the new parameters
3. All historical data (up to 5000 candles) is automatically fetched and recalculated
4. **The same instance key is returned** - no need to track a new key!

✅ **Key Benefit:** The instance key remains unchanged, making frontend integration much simpler!

## API Endpoint

### Update Indicator Parameters

**Endpoint:** `PATCH /api/indicators/instances/{instanceKey}`

**Description:** Updates the parameters of an active indicator instance and recalculates all historical data.

## Request Examples

### Example 1: Update SMA Period

Change an SMA indicator from period 20 to period 50:

```http
PATCH /api/indicators/instances/Binance:BTCUSDT:5m:sma:7a8b9c
Content-Type: application/json

{
  "params": {
    "period": 50,
    "color": "#2962FF"
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Indicator parameters updated successfully",
  "instanceKey": "Binance:BTCUSDT:5m:sma:7a8b9c",
  "details": {
    "indicatorId": "sma",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "oldParams": {
      "period": 20
    },
    "newParams": {
      "period": 50,
      "color": "#2962FF"
    },
    "updatedAt": "2025-10-05T10:30:00Z",
    "initializedWithCandles": 50,
    "historicalResultsStored": 4950
  }
}
```

**Note:** The `instanceKey` in the response is the **same** as the one you sent in the request!

### Example 2: Update RSI Settings

Update RSI period and overbought/oversold levels:

```http
PATCH /api/indicators/instances/Binance:ETHUSDT:1m:rsi:abc123
Content-Type: application/json

{
  "params": {
    "period": 14,
    "overbought": 70,
    "oversold": 30
  }
}
```

### Example 3: Update AI Support/Resistance Parameters

Update the AI S/R indicator with new detection settings:

```http
PATCH /api/indicators/instances/Binance:BTCUSDT:15m:ai-sr-zones:def456
Content-Type: application/json

{
  "params": {
    "lookback": 100,
    "minTouches": 3,
    "strengthThreshold": 0.7,
    "showSupport": true,
    "showResistance": true
  }
}
```

## Frontend Integration

### JavaScript/TypeScript Example

```javascript
async function updateIndicatorParams(instanceKey, newParams) {
  try {
    const response = await fetch(
      `/api/indicators/instances/${instanceKey}`,
      {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ params: newParams }),
      }
    );
    
    const result = await response.json();
    
    if (result.success) {
      console.log('Indicator updated successfully');
      console.log('Instance key:', result.instanceKey); // Same key!
      console.log('Old params:', result.details.oldParams);
      console.log('New params:', result.details.newParams);
      
      // ✅ No need to update the key - it stays the same!
      return true;
    } else {
      console.error('Failed to update:', result.error);
      return false;
    }
  } catch (error) {
    console.error('Error updating indicator:', error);
    return false;
  }
}

// Usage example - simple and clean!
const instanceKey = 'Binance:BTCUSDT:5m:sma:7a8b9c';
const newParams = { period: 50, color: '#2962FF' };
const success = await updateIndicatorParams(instanceKey, newParams);

// Continue using the SAME instanceKey - no changes needed!
if (success) {
  fetchHistoricalData(instanceKey); // Still works with same key
}
```

### React Hook Example

```typescript
import { useState } from 'react';

interface IndicatorParams {
  [key: string]: any;
}

interface UpdateResult {
  success: boolean;
  instanceKey: string; // Same key, unchanged!
  details: {
    indicatorId: string;
    provider: string;
    symbol: string;
    interval: string;
    oldParams: IndicatorParams;
    newParams: IndicatorParams;
    historicalResultsStored: number;
  };
}

export function useIndicatorParamsUpdate() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const updateParams = async (
    instanceKey: string,
    newParams: IndicatorParams
  ): Promise<boolean> => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await fetch(
        `/api/indicators/instances/${instanceKey}`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ params: newParams }),
        }
      );
      
      const result: UpdateResult = await response.json();
      
      if (result.success) {
        return true; // Success - same key still valid!
      } else {
        setError(result.error);
        return false;
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      return false;
    } finally {
      setLoading(false);
    }
  };
  
  return { updateParams, loading, error };
}

// Usage in component - much simpler now!
function IndicatorSettings({ instanceKey }) {
  const { updateParams, loading, error } = useIndicatorParamsUpdate();
  
  const handleUpdate = async () => {
    const success = await updateParams(instanceKey, {
      period: 50,
      color: '#2962FF'
    });
    
    if (success) {
      // ✅ No need to update instanceKey - it's the same!
      // Just refresh your data or trigger a re-render
      console.log('Parameters updated successfully!');
    }
  };
  
  return (
    <div>
      <button onClick={handleUpdate} disabled={loading}>
        {loading ? 'Updating...' : 'Update Parameters'}
      </button>
      {error && <div className="error">{error}</div>}
    </div>
  );
}
```

## Important Notes

### Instance Key Remains Unchanged

✅ **The instance key DOES NOT change** when you update parameters! This makes frontend integration much simpler:

1. ✅ No need to track a new instance key
2. ✅ No need to update references or state
3. ✅ No need to update WebSocket subscriptions
4. ✅ Just call PATCH and continue using the same key!

**Example:** If your instance key is `Binance:BTCUSDT:5m:sma:7a8b9c`, it will **remain** `Binance:BTCUSDT:5m:sma:7a8b9c` after the update.

### Historical Data Recalculation

✅ **All historical data is automatically recalculated** with the new parameters:

- Up to 5000 historical candles are fetched
- The indicator is reinitialized with the new parameters
- All historical results are recalculated and stored in-place
- Future updates will continue with the new parameters
- **The instance key stays the same throughout this process**

### Performance Considerations

- The update process may take a few seconds depending on the amount of historical data
- The indicator remains active during the update (parameters and state are updated in-place)
- Consider showing a loading indicator in your UI during the update
- Real-time updates continue to work with the same instance key

## Error Handling

### Instance Not Found (404)

```json
{
  "success": false,
  "error": "Instance not found: Binance:BTCUSDT:5m:sma:7a8b9c"
}
```

**Cause:** The instance key doesn't exist (already deactivated or never activated)

**Solution:** Verify the instance key or activate a new indicator instance

### Invalid Parameters (400)

```json
{
  "success": false,
  "error": "Invalid parameter value: period must be greater than 0"
}
```

**Cause:** The provided parameters are invalid for the indicator

**Solution:** Check the indicator's parameter requirements and provide valid values

## Testing

### Using cURL

```bash
# Update SMA period from 20 to 50
curl -X PATCH "http://localhost:8080/api/indicators/instances/Binance:BTCUSDT:5m:sma:7a8b9c" \
  -H "Content-Type: application/json" \
  -d '{
    "params": {
      "period": 50,
      "color": "#2962FF"
    }
  }'
```

### Using Swagger UI

1. Navigate to `http://localhost:8080/swagger-ui.html`
2. Find the **Indicator Instances** section
3. Locate the `PATCH /api/indicators/instances/{instanceKey}` endpoint
4. Click "Try it out"
5. Enter the instance key
6. Enter the new parameters in JSON format
7. Click "Execute"

## Complete Workflow Example

```javascript
// 1. Activate an indicator
const activateResponse = await fetch('/api/indicators/instances/activate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    indicatorId: 'sma',
    provider: 'Binance',
    symbol: 'BTCUSDT',
    interval: '5m',
    params: { period: 20 }
  })
});
const { instanceKey } = await activateResponse.json();
console.log('Activated:', instanceKey);
// => "Binance:BTCUSDT:5m:sma:7a8b9c"

// 2. Use the indicator for a while...
let currentKey = instanceKey;

// 3. Update parameters (in-place, same key!)
const updateResponse = await fetch(
  `/api/indicators/instances/${currentKey}`,
  {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      params: { period: 50, color: '#FF5722' }
    })
  }
);
const updateResult = await updateResponse.json();
console.log('Updated instance:', updateResult.instanceKey);
// => "Binance:BTCUSDT:5m:sma:7a8b9c" (SAME KEY!)

// ✅ currentKey is still valid - no need to update it!

// 4. Continue using with the SAME key
const historyResponse = await fetch(
  `/api/indicators/instances/${currentKey}/historical?count=100`
);
const history = await historyResponse.json();
console.log('Historical data recalculated with new params:', history);

// 5. Update params again - still works!
await fetch(`/api/indicators/instances/${currentKey}`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    params: { period: 100, color: '#4CAF50' }
  })
});
// currentKey STILL valid!

// 6. Deactivate when done - using the original key
await fetch(`/api/indicators/instances/${currentKey}`, {
  method: 'DELETE'
});
```

**Key Takeaway:** The instance key `Binance:BTCUSDT:5m:sma:7a8b9c` stays the same throughout the entire lifecycle, even after multiple parameter updates!

## Related Endpoints

- **Activate Indicator:** `POST /api/indicators/instances/activate`
- **Get Instance:** `GET /api/indicators/instances/{instanceKey}`
- **Get Historical Data:** `GET /api/indicators/instances/{instanceKey}/historical`
- **Deactivate Instance:** `DELETE /api/indicators/instances/{instanceKey}`
- **List All Instances:** `GET /api/indicators/instances`

## See Also

- [INDICATOR_INSTANCE_MANAGEMENT.md](./INDICATOR_INSTANCE_MANAGEMENT.md) - Complete indicator instance management guide
- [INDICATOR_INSTANCE_EXAMPLES.md](./INDICATOR_INSTANCE_EXAMPLES.md) - More usage examples
- [API Documentation](http://localhost:8080/swagger-ui.html) - Interactive API documentation

