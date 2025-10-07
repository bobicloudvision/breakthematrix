# Historical Data Auto-Save Guide

## Overview

The `BinanceTradingProvider` now **automatically saves historical data to JSON files** by default when you fetch:
- **Aggregate Trades** (via `getHistoricalAggregateTrades`)
- **Order Book Snapshots** (via `getOrderBookSnapshot`)

This solves the problem where Binance returns different data each time you request historical data (because live markets are constantly updating).

---

## Why Binance Returns Different Data

### The Problem
When you call `getHistoricalAggregateTrades(symbol, limit)` without specifying a time range:
- Binance returns the **most recent N trades**
- Since trading is happening live, the "most recent trades" change constantly
- Each time you call the API, you get different results

### Example
```
Call 1 (10:00:00): Returns trades from 09:59:50 to 10:00:00
Call 2 (10:00:05): Returns trades from 09:59:55 to 10:00:05  ← Different!
Call 3 (10:00:10): Returns trades from 10:00:00 to 10:00:10  ← Different again!
```

### The Solution
**Auto-save to JSON files** with the actual data time range in the filename, so you:
1. Have a permanent record of the exact data you fetched
2. Can see the time range directly from the filename
3. Can identify and avoid duplicate time ranges
4. Can easily reconstruct historical timelines

---

## Auto-Save Feature (ENABLED by Default)

### 🟢 Enabled by Default
Auto-save is **ON by default**. Every time you fetch historical data, it's automatically saved to JSON.

### File Location
All files are saved in: `historical_data/` directory (created automatically)

### File Naming Convention

#### Aggregate Trades Files
```
{SYMBOL}_aggTrades_{START_TIME}_{END_TIME}.json
```
Example: `BTCUSDT_aggTrades_20250106_140000_20250106_143000.json`

- `START_TIME`: Timestamp of the first trade in the data
- `END_TIME`: Timestamp of the last trade in the data
- **This shows the actual time range of the trades, not when you fetched them**

**Benefits:**
- ✅ **Know the data range without opening the file**
- ✅ **Easily identify overlapping or duplicate data**
- ✅ **Sort files chronologically by data time (not fetch time)**
- ✅ **Find specific time ranges quickly**

Example filenames showing different time ranges:
```
BTCUSDT_aggTrades_20250106_140000_20250106_143000.json  ← 14:00 to 14:30
BTCUSDT_aggTrades_20250106_143000_20250106_150000.json  ← 14:30 to 15:00
BTCUSDT_aggTrades_20250106_150000_20250106_153000.json  ← 15:00 to 15:30
```

#### Order Book Files
```
{SYMBOL}_orderBook_{TIMESTAMP}.json
```
Example: `BTCUSDT_orderBook_20250106_143052.json`

- `TIMESTAMP`: The order book snapshot timestamp (when the order book was captured)

---

## Usage Examples

### 1. Fetch Aggregate Trades (Auto-saves by default)

```java
// Auto-save is enabled by default
BinanceTradingProvider provider = new BinanceTradingProvider();
provider.connect();

// Fetch last 1000 trades - automatically saved to JSON
List<TradeData> trades = provider.getHistoricalAggregateTrades("BTCUSDT", 1000);

// Console output:
// ✅ Fetched 1000 historical aggregate trades for BTCUSDT
// 💾 Auto-saving to JSON...
// ✅ Saved 1000 trades to: historical_data/BTCUSDT_aggTrades_20250106_142530_20250106_143052.json
```

### 2. Fetch Order Book Snapshot (Auto-saves by default)

```java
// Fetch order book with 100 levels - automatically saved to JSON
OrderBookData orderBook = provider.getOrderBookSnapshot("BTCUSDT", 100);

// Console output:
// ✅ Fetched order book snapshot: 100 bids, 100 asks
//    Best bid: 45123.50, Best ask: 45123.51, Spread: 0.01
// 💾 Auto-saving to JSON...
// ✅ Saved order book snapshot to: historical_data/BTCUSDT_orderBook_20250106_143100.json
```

### 3. Disable Auto-Save (if you don't want files)

```java
BinanceTradingProvider provider = new BinanceTradingProvider();

// Disable auto-save
provider.setAutoSaveToJson(false);

// Now data is NOT saved to files
List<TradeData> trades = provider.getHistoricalAggregateTrades("BTCUSDT", 1000);
```

### 4. Re-enable Auto-Save

```java
// Enable auto-save
provider.setAutoSaveToJson(true);

// Check if enabled
boolean isEnabled = provider.isAutoSaveToJson();
System.out.println("Auto-save: " + isEnabled);  // true
```

---

## JSON File Structure

### Aggregate Trades JSON

The JSON file includes **metadata** and the **trades array**:

```json
{
  "symbol": "BTCUSDT",
  "fetchTimestamp": "2025-01-06T14:30:52.123Z",
  "startTime": "2025-01-06T14:25:30.456Z",
  "endTime": "2025-01-06T14:30:52.789Z",
  "count": 1000,
  "trades": [
    {
      "tradeId": 123456789,
      "symbol": "BTCUSDT",
      "price": 45123.50,
      "quantity": 0.025,
      "quoteQuantity": 1128.09,
      "timestamp": "2025-01-06T14:25:30.456Z",
      "buyerMaker": false,
      "provider": "Binance",
      "firstTradeId": 123456780,
      "lastTradeId": 123456789
    },
    // ... 999 more trades
  ]
}
```

**Metadata Fields:**
- `symbol`: Trading pair
- `fetchTimestamp`: When you fetched this data
- `startTime`: First trade timestamp
- `endTime`: Last trade timestamp
- `count`: Number of trades
- `trades`: Array of all trade data

### Order Book JSON

```json
{
  "symbol": "BTCUSDT",
  "fetchTimestamp": "2025-01-06T14:31:00.123Z",
  "orderBookTimestamp": "2025-01-06T14:31:00.120Z",
  "lastUpdateId": 987654321,
  "bidCount": 100,
  "askCount": 100,
  "bestBid": 45123.50,
  "bestAsk": 45123.51,
  "spread": 0.01,
  "orderBook": {
    "symbol": "BTCUSDT",
    "lastUpdateId": 987654321,
    "timestamp": "2025-01-06T14:31:00.120Z",
    "bids": [
      {"price": 45123.50, "quantity": 1.5},
      {"price": 45123.49, "quantity": 2.3},
      // ... 98 more bids
    ],
    "asks": [
      {"price": 45123.51, "quantity": 1.2},
      {"price": 45123.52, "quantity": 1.8},
      // ... 98 more asks
    ],
    "provider": "Binance"
  }
}
```

**Metadata Fields:**
- `symbol`: Trading pair
- `fetchTimestamp`: When you fetched this data
- `orderBookTimestamp`: Order book snapshot timestamp
- `lastUpdateId`: Binance update ID
- `bidCount`: Number of bid levels
- `askCount`: Number of ask levels
- `bestBid`: Best bid price
- `bestAsk`: Best ask price
- `spread`: Bid-ask spread
- `orderBook`: Full order book data

---

## Loading Saved Data

### Load Aggregate Trades from File

```java
BinanceTradingProvider provider = new BinanceTradingProvider();

// Load from specific file (with start and end time in filename)
Path filePath = Paths.get("historical_data/BTCUSDT_aggTrades_20250106_142530_20250106_143052.json");
List<TradeData> trades = provider.loadAggregateTradesFromJson(filePath);

// Console output:
// ✅ Loaded 1000 trades for BTCUSDT
// 📊 Time range: 2025-01-06T14:25:30.456Z to 2025-01-06T14:30:52.789Z
// 🕐 Fetched at: 2025-01-06T14:30:52.123Z
```

### Load Order Book from File

```java
// Load from specific file
Path filePath = Paths.get("historical_data/BTCUSDT_orderBook_20250106_143100.json");
OrderBookData orderBook = provider.loadOrderBookSnapshotFromJson(filePath);

// Console output:
// ✅ Loaded order book for BTCUSDT
// 📚 100 bids, 100 asks
// 💰 Best bid: 45123.50, Best ask: 45123.51, Spread: 0.01
// 🕐 Fetched at: 2025-01-06T14:31:00.123Z
```

---

## Listing Saved Files

### List All Trade Files for a Symbol

```java
BinanceTradingProvider provider = new BinanceTradingProvider();

// List all BTCUSDT trade files
List<Path> files = provider.listSavedTradeFiles("BTCUSDT");

// Console output:
// 📁 Found 5 saved trade files for BTCUSDT

for (Path file : files) {
    System.out.println(file);
}
// Output:
// historical_data/BTCUSDT_aggTrades_20250106_142530_20250106_143052.json
// historical_data/BTCUSDT_aggTrades_20250106_143000_20250106_143110.json
// historical_data/BTCUSDT_aggTrades_20250106_143100_20250106_143125.json
// ...
```

### List All Order Book Files

```java
// List all BTCUSDT order book files
List<Path> files = provider.listSavedOrderBookFiles("BTCUSDT");

// Console output:
// 📁 Found 3 saved order book files for BTCUSDT
```

### List All Files (All Symbols)

```java
// Pass null to list files for all symbols
List<Path> allTradeFiles = provider.listSavedTradeFiles(null);
List<Path> allOrderBookFiles = provider.listSavedOrderBookFiles(null);
```

---

## Manual Save (Without Fetching)

If you already have data and want to save it:

### Save Trades Manually

```java
// You already have trades from somewhere
List<TradeData> trades = ... ;

// Save them
Path savedPath = provider.saveAggregateTradeToJson("BTCUSDT", trades);
System.out.println("Saved to: " + savedPath);
```

### Save Order Book Manually

```java
// You already have an order book
OrderBookData orderBook = ... ;

// Save it
Path savedPath = provider.saveOrderBookSnapshotToJson("BTCUSDT", orderBook);
System.out.println("Saved to: " + savedPath);
```

---

## Fetch AND Save (One Call)

Alternative methods that explicitly fetch and save:

```java
// Fetch and save trades
Path path1 = provider.fetchAndSaveAggregateTrades("BTCUSDT", 1000);

// Fetch and save trades with time range
Instant start = Instant.now().minus(Duration.ofHours(1));
Instant end = Instant.now();
Path path2 = provider.fetchAndSaveAggregateTrades("BTCUSDT", start, end, 1000);

// Fetch and save order book
Path path3 = provider.fetchAndSaveOrderBookSnapshot("BTCUSDT", 100);
```

**Note:** These methods always save, regardless of the `autoSaveToJson` flag.

---

## Best Practices

### 1. Use Time Ranges for Consistency
```java
// Instead of this (returns different data each time):
provider.getHistoricalAggregateTrades("BTCUSDT", 1000);

// Use this (returns consistent data for the time range):
Instant start = Instant.parse("2025-01-06T14:00:00Z");
Instant end = Instant.parse("2025-01-06T15:00:00Z");
provider.getHistoricalAggregateTrades("BTCUSDT", start, end, 1000);
```

### 2. Save Critical Data
```java
// For important analysis, always check if auto-save is enabled
if (!provider.isAutoSaveToJson()) {
    provider.setAutoSaveToJson(true);
}

// Now fetch data
List<TradeData> trades = provider.getHistoricalAggregateTrades("BTCUSDT", 1000);
```

### 3. Organize by Date
```java
// Files are automatically timestamped with data time range
// Organize them manually if needed:
// historical_data/
//   2025-01-06/
//     BTCUSDT_aggTrades_20250106_142530_20250106_143052.json
//     BTCUSDT_aggTrades_20250106_150000_20250106_153000.json
//   2025-01-07/
//     BTCUSDT_aggTrades_20250107_090015_20250107_093000.json
```

### 4. Archive Old Files
```java
// List and archive old files periodically
List<Path> oldFiles = provider.listSavedTradeFiles("BTCUSDT");
// Move files older than 7 days to archive
```

---

## Summary

### ✅ What Changed
1. **Auto-save enabled by default** for aggregate trades and order book snapshots
2. All data saved to `historical_data/` directory
3. Files include metadata (timestamps, counts, best bid/ask, etc.)
4. Easy loading and listing of saved files

### 🔧 Configuration
- **Enable/Disable:** `provider.setAutoSaveToJson(true/false)`
- **Check Status:** `provider.isAutoSaveToJson()`
- **Default:** `true` (enabled)

### 📁 File Format
- JSON with pretty printing
- Includes metadata and full data
- Timestamped filenames

### 🎯 Benefits
- **Reproducible data** - exact data is saved
- **Historical record** - track data over time
- **Debugging** - compare different fetches
- **Analysis** - load saved data for testing

---

## API Reference

### Configuration Methods
```java
void setAutoSaveToJson(boolean autoSave)    // Enable/disable auto-save
boolean isAutoSaveToJson()                   // Check if enabled
```

### Save Methods
```java
Path saveAggregateTradeToJson(String symbol, List<TradeData> trades)
Path saveOrderBookSnapshotToJson(String symbol, OrderBookData orderBook)
Path fetchAndSaveAggregateTrades(String symbol, int limit)
Path fetchAndSaveAggregateTrades(String symbol, Instant start, Instant end, int limit)
Path fetchAndSaveOrderBookSnapshot(String symbol, int limit)
```

### Load Methods
```java
List<TradeData> loadAggregateTradesFromJson(Path filePath)
OrderBookData loadOrderBookSnapshotFromJson(Path filePath)
```

### List Methods
```java
List<Path> listSavedTradeFiles(String symbol)      // null = all symbols
List<Path> listSavedOrderBookFiles(String symbol)  // null = all symbols
```

---

## Troubleshooting

### Files Not Being Saved
```java
// Check if auto-save is enabled
System.out.println("Auto-save: " + provider.isAutoSaveToJson());

// Enable it
provider.setAutoSaveToJson(true);
```

### Can't Find Saved Files
```java
// List all saved files
List<Path> files = provider.listSavedTradeFiles(null);
files.forEach(System.out::println);
```

### Directory Doesn't Exist
The `historical_data/` directory is created automatically on first save. No manual setup needed.

### File Permissions
Ensure your application has write permissions in the directory where it runs.

---

## Example: Complete Workflow

```java
public class HistoricalDataExample {
    public static void main(String[] args) {
        BinanceTradingProvider provider = new BinanceTradingProvider();
        provider.connect();
        
        // 1. Fetch and auto-save trades
        System.out.println("=== Fetching Aggregate Trades ===");
        List<TradeData> trades = provider.getHistoricalAggregateTrades("BTCUSDT", 1000);
        System.out.println("Got " + trades.size() + " trades");
        
        // 2. Fetch and auto-save order book
        System.out.println("\n=== Fetching Order Book ===");
        OrderBookData orderBook = provider.getOrderBookSnapshot("BTCUSDT", 100);
        System.out.println("Got order book with " + orderBook.getBids().size() + " bids");
        
        // 3. List all saved files
        System.out.println("\n=== Saved Files ===");
        List<Path> savedTrades = provider.listSavedTradeFiles("BTCUSDT");
        List<Path> savedOrderBooks = provider.listSavedOrderBookFiles("BTCUSDT");
        
        System.out.println("Trade files: " + savedTrades.size());
        System.out.println("Order book files: " + savedOrderBooks.size());
        
        // 4. Load the most recent file
        if (!savedTrades.isEmpty()) {
            Path latestFile = savedTrades.get(savedTrades.size() - 1);
            System.out.println("\n=== Loading Latest File ===");
            List<TradeData> loadedTrades = provider.loadAggregateTradesFromJson(latestFile);
            System.out.println("Loaded " + loadedTrades.size() + " trades from file");
        }
        
        provider.disconnect();
    }
}
```

---

**🎉 Auto-save is now your default behavior - consistent, reproducible data every time!**

