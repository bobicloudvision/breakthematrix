# Indicator Instance Management - Practical Examples

## Quick Start Example

### 1. Activate Multiple Indicators for a Symbol

```bash
#!/bin/bash
# activate_btc_indicators.sh

API_BASE="http://localhost:8080/api/indicators/instances"

echo "Activating indicators for BTCUSDT 5m..."

# SMA 20
curl -X POST "$API_BASE/activate" \
  -H "Content-Type: application/json" \
  -d '{
    "indicatorId": "sma",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {
      "period": 20,
      "color": "#2962FF"
    }
  }'

echo ""

# SMA 50
curl -X POST "$API_BASE/activate" \
  -H "Content-Type: application/json" \
  -d '{
    "indicatorId": "sma",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {
      "period": 50,
      "color": "#FF6B6B"
    }
  }'

echo ""

# Volume
curl -X POST "$API_BASE/activate" \
  -H "Content-Type: application/json" \
  -d '{
    "indicatorId": "volume",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {}
  }'

echo ""
echo "All indicators activated! Check stats:"
curl "$API_BASE/stats"
```

### 2. WebSocket Client Example (JavaScript)

```javascript
// indicator-websocket-client.js

const WebSocket = require('ws');

// Connect to indicator WebSocket
const ws = new WebSocket('ws://localhost:8080/indicator-ws');

ws.on('open', () => {
    console.log('✅ Connected to indicator WebSocket');
    
    // Subscribe to all BTCUSDT 5m indicators
    ws.send(JSON.stringify({
        action: 'subscribeContext',
        provider: 'Binance',
        symbol: 'BTCUSDT',
        interval: '5m'
    }));
});

ws.on('message', (data) => {
    const message = JSON.parse(data);
    
    switch (message.type) {
        case 'connected':
            console.log('📡', message.message);
            break;
            
        case 'contextSubscribed':
            console.log('✅ Subscribed to context:', message.context);
            console.log('   Active instances:', message.activeInstances);
            break;
            
        case 'indicatorUpdate':
            console.log('\n📊 Indicator Update:');
            console.log('   Indicator:', message.indicatorId);
            console.log('   Symbol:', message.symbol);
            console.log('   Time:', new Date(message.timestamp));
            console.log('   Values:', message.values);
            
            if (message.additionalData) {
                console.log('   Additional:', message.additionalData);
            }
            
            // Process candle data
            console.log('   Candle:', {
                close: message.candle.close,
                volume: message.candle.volume
            });
            break;
            
        case 'error':
            console.error('❌ Error:', message.error);
            break;
            
        default:
            console.log('📨 Message:', message);
    }
});

ws.on('error', (error) => {
    console.error('❌ WebSocket error:', error);
});

ws.on('close', () => {
    console.log('❌ Disconnected from indicator WebSocket');
});
```

### 3. Python Client Example

```python
# indicator_client.py

import requests
import json
import websocket
import threading

API_BASE = "http://localhost:8080/api/indicators/instances"
WS_URL = "ws://localhost:8080/indicator-ws"

class IndicatorClient:
    def __init__(self):
        self.ws = None
        self.active_instances = {}
        
    def activate_indicator(self, indicator_id, provider, symbol, interval, params=None):
        """Activate an indicator instance"""
        data = {
            "indicatorId": indicator_id,
            "provider": provider,
            "symbol": symbol,
            "interval": interval,
            "params": params or {}
        }
        
        response = requests.post(f"{API_BASE}/activate", json=data)
        result = response.json()
        
        if result.get("success"):
            instance_key = result.get("instanceKey")
            self.active_instances[instance_key] = result.get("details")
            print(f"✅ Activated: {instance_key}")
            return instance_key
        else:
            print(f"❌ Failed to activate: {result.get('error')}")
            return None
    
    def deactivate_indicator(self, instance_key):
        """Deactivate an indicator instance"""
        response = requests.delete(f"{API_BASE}/{instance_key}")
        result = response.json()
        
        if result.get("success"):
            if instance_key in self.active_instances:
                del self.active_instances[instance_key]
            print(f"✅ Deactivated: {instance_key}")
        else:
            print(f"❌ Failed to deactivate: {result.get('error')}")
    
    def list_active(self):
        """Get all active indicator instances"""
        response = requests.get(API_BASE)
        result = response.json()
        
        if result.get("success"):
            print(f"\n📊 Active Indicators: {result.get('totalActive')}")
            for instance in result.get("instances", []):
                print(f"  - {instance['instanceKey']}")
                print(f"    {instance['indicatorId']} | {instance['symbol']} | {instance['interval']}")
                print(f"    Updates: {instance['updateCount']} | Last: {instance['lastUpdate']}")
        
        return result
    
    def get_stats(self):
        """Get indicator statistics"""
        response = requests.get(f"{API_BASE}/stats")
        result = response.json()
        
        if result.get("success"):
            stats = result.get("stats", {})
            print(f"\n📈 Indicator Statistics:")
            print(f"  Total Active: {stats.get('totalActive')}")
            print(f"  Unique Contexts: {stats.get('uniqueContexts')}")
            print(f"  By Indicator: {stats.get('byIndicator')}")
            print(f"  By Symbol: {stats.get('bySymbol')}")
            print(f"  By Interval: {stats.get('byInterval')}")
        
        return result
    
    def connect_websocket(self):
        """Connect to WebSocket for real-time updates"""
        def on_message(ws, message):
            data = json.loads(message)
            msg_type = data.get("type")
            
            if msg_type == "connected":
                print(f"✅ {data.get('message')}")
                
            elif msg_type == "indicatorUpdate":
                print(f"\n📊 Update: {data['indicatorId']} ({data['symbol']})")
                print(f"   Values: {data['values']}")
                print(f"   Candle Close: {data['candle']['close']}")
                
            elif msg_type == "contextSubscribed":
                print(f"✅ Subscribed to context: {data['context']}")
                
            elif msg_type == "error":
                print(f"❌ Error: {data.get('error')}")
        
        def on_error(ws, error):
            print(f"❌ WebSocket Error: {error}")
        
        def on_close(ws, close_status_code, close_msg):
            print("❌ WebSocket Closed")
        
        def on_open(ws):
            print("📡 WebSocket Connected")
        
        self.ws = websocket.WebSocketApp(
            WS_URL,
            on_open=on_open,
            on_message=on_message,
            on_error=on_error,
            on_close=on_close
        )
        
        # Run WebSocket in separate thread
        ws_thread = threading.Thread(target=self.ws.run_forever)
        ws_thread.daemon = True
        ws_thread.start()
    
    def subscribe_context(self, provider, symbol, interval):
        """Subscribe to all indicators for a context"""
        if self.ws:
            message = {
                "action": "subscribeContext",
                "provider": provider,
                "symbol": symbol,
                "interval": interval
            }
            self.ws.send(json.dumps(message))
    
    def subscribe_instances(self, instance_keys):
        """Subscribe to specific indicator instances"""
        if self.ws:
            message = {
                "action": "subscribe",
                "instanceKeys": instance_keys
            }
            self.ws.send(json.dumps(message))

# Example usage
if __name__ == "__main__":
    import time
    
    client = IndicatorClient()
    
    # Activate indicators
    print("🚀 Activating indicators...")
    sma20_key = client.activate_indicator(
        "sma", "Binance", "BTCUSDT", "5m", {"period": 20}
    )
    
    sma50_key = client.activate_indicator(
        "sma", "Binance", "BTCUSDT", "5m", {"period": 50}
    )
    
    volume_key = client.activate_indicator(
        "volume", "Binance", "BTCUSDT", "5m"
    )
    
    # List active indicators
    time.sleep(1)
    client.list_active()
    
    # Get statistics
    time.sleep(1)
    client.get_stats()
    
    # Connect WebSocket and subscribe
    print("\n📡 Connecting to WebSocket...")
    client.connect_websocket()
    time.sleep(2)  # Wait for connection
    
    client.subscribe_context("Binance", "BTCUSDT", "5m")
    
    # Keep running to receive updates
    print("\n👀 Listening for indicator updates... (Ctrl+C to stop)")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n\n🛑 Stopping...")
        
        # Clean up
        if sma20_key:
            client.deactivate_indicator(sma20_key)
        if sma50_key:
            client.deactivate_indicator(sma50_key)
        if volume_key:
            client.deactivate_indicator(volume_key)
```

## Advanced Use Cases

### Multi-Timeframe Dashboard

```python
# multi_timeframe_dashboard.py

from indicator_client import IndicatorClient
import time

client = IndicatorClient()

# Activate same indicators across multiple timeframes
timeframes = ["1m", "5m", "15m", "1h"]
indicators = ["sma", "volume"]

print("🚀 Setting up multi-timeframe dashboard...")

for tf in timeframes:
    for indicator in indicators:
        params = {"period": 20} if indicator == "sma" else {}
        client.activate_indicator(
            indicator, 
            "Binance", 
            "BTCUSDT", 
            tf, 
            params
        )
        time.sleep(0.5)

# Connect WebSocket and subscribe to all timeframes
client.connect_websocket()
time.sleep(2)

for tf in timeframes:
    client.subscribe_context("Binance", "BTCUSDT", tf)
    time.sleep(0.5)

client.get_stats()

print("\n👀 Dashboard active - monitoring all timeframes...")
try:
    while True:
        time.sleep(10)
        # Periodically show stats
        client.get_stats()
except KeyboardInterrupt:
    print("\n🛑 Shutting down dashboard...")
```

### Multiple Symbol Scanner

```python
# multi_symbol_scanner.py

from indicator_client import IndicatorClient
import time

client = IndicatorClient()

# Monitor volume across multiple symbols
symbols = ["BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "SOLUSDT"]

print("🚀 Setting up volume scanner...")

for symbol in symbols:
    client.activate_indicator(
        "volume", 
        "Binance", 
        symbol, 
        "5m"
    )
    time.sleep(0.5)

# Connect and subscribe
client.connect_websocket()
time.sleep(2)

for symbol in symbols:
    client.subscribe_context("Binance", symbol, "5m")
    time.sleep(0.5)

print(f"\n👀 Scanning {len(symbols)} symbols for volume...")
client.get_stats()

try:
    while True:
        time.sleep(30)
except KeyboardInterrupt:
    print("\n🛑 Scanner stopped")
```

### Strategy Indicator Setup

```python
# strategy_indicators.py

from indicator_client import IndicatorClient
import time

class TradingStrategyIndicators:
    """Manage indicators for a trading strategy"""
    
    def __init__(self, provider, symbol, interval):
        self.client = IndicatorClient()
        self.provider = provider
        self.symbol = symbol
        self.interval = interval
        self.instance_keys = []
        
    def setup_trend_following(self):
        """Setup indicators for trend following strategy"""
        print("📈 Setting up trend following indicators...")
        
        # Fast SMA
        key = self.client.activate_indicator(
            "sma", self.provider, self.symbol, self.interval,
            {"period": 20, "color": "#2962FF"}
        )
        if key:
            self.instance_keys.append(key)
        
        # Slow SMA
        key = self.client.activate_indicator(
            "sma", self.provider, self.symbol, self.interval,
            {"period": 50, "color": "#FF6B6B"}
        )
        if key:
            self.instance_keys.append(key)
        
        # Volume confirmation
        key = self.client.activate_indicator(
            "volume", self.provider, self.symbol, self.interval
        )
        if key:
            self.instance_keys.append(key)
            
        time.sleep(1)
        self.client.list_active()
        
    def start_monitoring(self):
        """Start WebSocket monitoring"""
        self.client.connect_websocket()
        time.sleep(2)
        self.client.subscribe_context(
            self.provider, self.symbol, self.interval
        )
        
    def shutdown(self):
        """Clean up all indicators"""
        print("\n🧹 Cleaning up indicators...")
        for key in self.instance_keys:
            self.client.deactivate_indicator(key)

# Example usage
if __name__ == "__main__":
    strategy = TradingStrategyIndicators("Binance", "BTCUSDT", "5m")
    
    try:
        strategy.setup_trend_following()
        strategy.start_monitoring()
        
        print("\n👀 Strategy monitoring active...")
        while True:
            time.sleep(10)
            strategy.client.get_stats()
            
    except KeyboardInterrupt:
        strategy.shutdown()
        print("\n✅ Strategy stopped")
```

## Testing the System

### Manual Testing Steps

1. **Start the backend**
```bash
mvn spring-boot:run
```

2. **Activate an indicator**
```bash
curl -X POST http://localhost:8080/api/indicators/instances/activate \
  -H "Content-Type: application/json" \
  -d '{
    "indicatorId": "sma",
    "provider": "Binance",
    "symbol": "BTCUSDT",
    "interval": "5m",
    "params": {"period": 20}
  }'
```

3. **Verify activation**
```bash
curl http://localhost:8080/api/indicators/instances
```

4. **Connect WebSocket** (using wscat)
```bash
wscat -c ws://localhost:8080/indicator-ws

# After connection
> {"action":"subscribeContext","provider":"Binance","symbol":"BTCUSDT","interval":"5m"}
```

5. **Wait for candle updates** - You'll receive updates when new 5m candles close

6. **Check statistics**
```bash
curl http://localhost:8080/api/indicators/instances/stats
```

7. **Clean up**
```bash
# Get instance key from step 2 response
curl -X DELETE http://localhost:8080/api/indicators/instances/Binance:BTCUSDT:5m:sma:7a8b9c
```

## Monitoring and Debugging

### View Active Indicators

```bash
# All active
curl http://localhost:8080/api/indicators/instances | jq

# Filter by symbol
curl "http://localhost:8080/api/indicators/instances?symbol=BTCUSDT" | jq

# Filter by indicator
curl "http://localhost:8080/api/indicators/instances?indicatorId=sma" | jq
```

### Get Statistics

```bash
curl http://localhost:8080/api/indicators/instances/stats | jq
```

### Check Instance Details

```bash
# Replace with actual instance key
curl "http://localhost:8080/api/indicators/instances/Binance:BTCUSDT:5m:sma:7a8b9c" | jq
```

## Performance Tips

1. **Batch Activation**: Activate all needed indicators at once, then start WebSocket monitoring

2. **Context Subscriptions**: Use context subscriptions instead of individual instance subscriptions for cleaner code

3. **Clean Up Regularly**: Deactivate indicators you no longer need to free memory

4. **Monitor Statistics**: Check stats endpoint regularly to track indicator count and distribution

## Next Steps

- Explore available indicators: `GET /api/indicators`
- Review indicator parameters: `GET /api/indicators/{id}`
- Integrate with your trading strategies
- Build custom dashboards using WebSocket updates

