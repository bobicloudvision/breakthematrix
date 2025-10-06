package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.bot.visualization.ShapeCollection;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.OrderBookData;
import org.cloudvision.trading.model.TradeData;
import org.cloudvision.trading.model.TradingDataType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================
 * BOOKMAP HEATMAP INDICATOR
 * ============================================================
 * 
 * This indicator visualizes order book depth and trade flow similar to Bookmap software.
 * It provides a heatmap showing volume at each price level from both:
 * 1. ORDER BOOK DATA - Current bid/ask volume at each price
 * 2. TRADE DATA - Historical volume that traded at each price
 * 
 * WHAT THIS INDICATOR DOES:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. ACCUMULATES TRADE VOLUME:
 *    - Tracks every trade at each price level
 *    - Separates buy volume vs sell volume
 *    - Shows where actual executions happened
 *    
 * 2. SHOWS CURRENT ORDER BOOK:
 *    - Real-time bid/ask depth
 *    - Volume available at each price
 *    - Liquidity walls and gaps
 *    
 * 3. DETECTS IMPORTANT LEVELS:
 *    - High volume nodes (HVN)
 *    - Low volume nodes (LVN)
 *    - Liquidity walls (large orders)
 *    - Volume imbalances
 * 
 * DATA REQUIREMENTS:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * This indicator needs BOTH order book and trade data:
 * - ORDER_BOOK: For current market depth visualization
 * - AGGREGATE_TRADE: For executed volume at each price
 * - KLINE: For time-based candle structure
 * 
 * HOW IT WORKS:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. onInit(): 
 *    - Initializes state with empty volume profile
 *    
 * 2. onTradeUpdate():
 *    - Called for every trade (1000+ times per second)
 *    - Accumulates volume at each price level
 *    - Separates buy/sell volume
 *    
 * 3. onOrderBookUpdate():
 *    - Called for every order book update (100+ per second)
 *    - Shows current bid/ask depth
 *    - Detects liquidity walls
 *    
 * 4. onNewCandle():
 *    - Called when candle closes
 *    - Outputs accumulated volume profile
 *    - Resets for next candle
 * 
 * OUTPUT FORMAT:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * {
 *   "values": {
 *     "pocPrice": 50000.00,           // Point of Control (highest volume)
 *     "totalVolume": 1234.56,         // Total traded volume
 *     "delta": 123.45,                // Buy volume - Sell volume
 *     "bidAskRatio": 1.5              // Bid volume / Ask volume
 *   },
 *   "heatmap": {
 *     "50000.00": {
 *       "buyVolume": 100,
 *       "sellVolume": 80,
 *       "bidVolume": 50,               // Current order book bid
 *       "askVolume": 30,               // Current order book ask
 *       "totalTraded": 180,
 *       "delta": 20,                   // Buy - Sell at this level
 *       "intensity": 0.85              // 0-1 for color intensity
 *     },
 *     ...
 *   },
 *   "levels": [
 *     {
 *       "price": 50000.00,
 *       "type": "HVN",                 // High Volume Node
 *       "volume": 500,
 *       "strength": 0.9
 *     }
 *   ],
 *   "markers": [                       // ⭐ NEW: Circle shapes for visualization
 *     {
 *       "time": 1696598400,
 *       "price": 50000.00,
 *       "shape": "circle",
 *       "color": "#FFC107",           // Yellow = POC
 *       "size": 20,                   // Largest circle
 *       "text": "POC"
 *     },
 *     {
 *       "time": 1696598400,
 *       "price": 50050.00,
 *       "shape": "circle",
 *       "color": "#26a69a",           // Green = Buy pressure
 *       "size": 14,                   // Scaled by volume
 *       "position": "inBar"
 *     }
 *   ]
 * }
 * 
 * VISUALIZATION EXAMPLE:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * Price    |  Bids  |  Asks  |  Buy Vol  |  Sell Vol  |  Heatmap
 * ---------|--------|--------|-----------|------------|----------
 * 50100.00 |    0   |   50   |    10     |    20      |  ▓░░░░░░
 * 50050.00 |   20   |   30   |    50     |    40      |  ▓▓░░░░░
 * 50000.00 |  500   |   10   |   500     |   100      |  ▓▓▓▓▓▓▓  ← POC
 * 49950.00 |   30   |   20   |    60     |    70      |  ▓▓░░░░░
 * 49900.00 |   40   |    0   |    15     |    25      |  ▓░░░░░░
 */
@Component
public class BookmapIndicator extends AbstractIndicator {
    
    /**
     * Internal state for Bookmap indicator
     * 
     * WHAT WE TRACK:
     * - volumeProfile: Map of price -> volume data (accumulated trades)
     * - currentOrderBook: Latest order book snapshot
     * - tickSize: Price level granularity
     * - totalBuyVolume: Cumulative buy volume
     * - totalSellVolume: Cumulative sell volume
     * - maxVolume: Highest volume at any price (for normalization)
     * - lastBroadcastTime: For throttling real-time updates
     * - lastCandle: Reference to last candle for live updates
     */
    public static class BookmapState {
        // Accumulated trade volume at each price level
        private final Map<BigDecimal, PriceLevelData> volumeProfile;
        
        // Current order book snapshot (refreshed frequently)
        private OrderBookData currentOrderBook;
        
        // Configuration
        private final BigDecimal tickSize;
        private final int lookbackCandles;
        private final int updateIntervalSeconds;
        
        // Statistics
        private BigDecimal totalBuyVolume;
        private BigDecimal totalSellVolume;
        private BigDecimal maxVolumeAtLevel;
        
        // Real-time update tracking
        private long lastBroadcastTime;  // Unix timestamp in milliseconds
        private CandlestickData lastCandle;  // For real-time updates
        
        public BookmapState(BigDecimal tickSize, int lookbackCandles, int updateIntervalSeconds) {
            this.volumeProfile = new ConcurrentHashMap<>();
            this.tickSize = tickSize;
            this.lookbackCandles = lookbackCandles;
            this.updateIntervalSeconds = updateIntervalSeconds;
            this.totalBuyVolume = BigDecimal.ZERO;
            this.totalSellVolume = BigDecimal.ZERO;
            this.maxVolumeAtLevel = BigDecimal.ZERO;
            this.lastBroadcastTime = 0;
            this.lastCandle = null;
        }
        
        // Getters
        public Map<BigDecimal, PriceLevelData> getVolumeProfile() { return volumeProfile; }
        public OrderBookData getCurrentOrderBook() { return currentOrderBook; }
        public BigDecimal getTickSize() { return tickSize; }
        public BigDecimal getTotalBuyVolume() { return totalBuyVolume; }
        public BigDecimal getTotalSellVolume() { return totalSellVolume; }
        public BigDecimal getMaxVolumeAtLevel() { return maxVolumeAtLevel; }
        public int getUpdateIntervalSeconds() { return updateIntervalSeconds; }
        public long getLastBroadcastTime() { return lastBroadcastTime; }
        public CandlestickData getLastCandle() { return lastCandle; }
        
        // Setters
        public void setCurrentOrderBook(OrderBookData orderBook) { 
            this.currentOrderBook = orderBook; 
        }
        
        public void setLastBroadcastTime(long time) {
            this.lastBroadcastTime = time;
        }
        
        public void setLastCandle(CandlestickData candle) {
            this.lastCandle = candle;
        }
        
        /**
         * Check if enough time has passed for a live update
         * @return true if we should broadcast an update
         */
        public boolean shouldBroadcastLiveUpdate() {
            long now = System.currentTimeMillis();
            long elapsedSeconds = (now - lastBroadcastTime) / 1000;
            return elapsedSeconds >= updateIntervalSeconds;
        }
        
        /**
         * Add trade volume to a price level
         * This is called for every trade that happens
         */
        public void addTrade(BigDecimal price, BigDecimal volume, boolean isBuy) {
            // Round price to tick size for grouping
            BigDecimal roundedPrice = roundToTickSize(price, tickSize);
            
            // Get or create price level data
            PriceLevelData levelData = volumeProfile.computeIfAbsent(
                roundedPrice, 
                p -> new PriceLevelData(p)
            );
            
            // Add volume to the level
            if (isBuy) {
                levelData.addBuyVolume(volume);
                totalBuyVolume = totalBuyVolume.add(volume);
            } else {
                levelData.addSellVolume(volume);
                totalSellVolume = totalSellVolume.add(volume);
            }
            
            // Update max volume for normalization
            BigDecimal levelTotal = levelData.getTotalVolume();
            if (levelTotal.compareTo(maxVolumeAtLevel) > 0) {
                maxVolumeAtLevel = levelTotal;
            }
        }
        
        /**
         * Clear volume profile (called when new candle starts)
         */
        public void clearProfile() {
            volumeProfile.clear();
            totalBuyVolume = BigDecimal.ZERO;
            totalSellVolume = BigDecimal.ZERO;
            maxVolumeAtLevel = BigDecimal.ZERO;
        }
        
        private BigDecimal roundToTickSize(BigDecimal price, BigDecimal tickSize) {
            return price.divide(tickSize, 0, RoundingMode.HALF_UP)
                       .multiply(tickSize);
        }
    }
    
    /**
     * Data for a single price level
     * Tracks both traded volume and current order book
     */
    public static class PriceLevelData {
        private final BigDecimal price;
        private BigDecimal buyVolume;         // Executed buy volume
        private BigDecimal sellVolume;        // Executed sell volume
        private BigDecimal currentBidVolume;  // Current order book bid
        private BigDecimal currentAskVolume;  // Current order book ask
        
        public PriceLevelData(BigDecimal price) {
            this.price = price;
            this.buyVolume = BigDecimal.ZERO;
            this.sellVolume = BigDecimal.ZERO;
            this.currentBidVolume = BigDecimal.ZERO;
            this.currentAskVolume = BigDecimal.ZERO;
        }
        
        public void addBuyVolume(BigDecimal volume) {
            buyVolume = buyVolume.add(volume);
        }
        
        public void addSellVolume(BigDecimal volume) {
            sellVolume = sellVolume.add(volume);
        }
        
        public void setBidVolume(BigDecimal volume) {
            currentBidVolume = volume;
        }
        
        public void setAskVolume(BigDecimal volume) {
            currentAskVolume = volume;
        }
        
        public BigDecimal getTotalVolume() {
            return buyVolume.add(sellVolume);
        }
        
        public BigDecimal getDelta() {
            return buyVolume.subtract(sellVolume);
        }
        
        // Getters
        public BigDecimal getPrice() { return price; }
        public BigDecimal getBuyVolume() { return buyVolume; }
        public BigDecimal getSellVolume() { return sellVolume; }
        public BigDecimal getCurrentBidVolume() { return currentBidVolume; }
        public BigDecimal getCurrentAskVolume() { return currentAskVolume; }
    }
    
    public BookmapIndicator() {
        super(
            "bookmap",
            "Bookmap Heatmap",
            "Visualizes order book depth and trade volume at each price level, similar to Bookmap software",
            IndicatorCategory.ORDER_FLOW
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("tickSize", IndicatorParameter.builder("tickSize")
            .displayName("Tick Size")
            .description("Price level granularity (e.g., 0.01 for $0.01 increments)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("0.01")
            .required(false)
            .build());
        
        params.put("lookback", IndicatorParameter.builder("lookback")
            .displayName("Lookback Candles")
            .description("Number of candles to accumulate volume for")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(50)
            .minValue(10)
            .maxValue(500)
            .required(false)
            .build());
        
        params.put("updateInterval", IndicatorParameter.builder("updateInterval")
            .displayName("Live Update Interval")
            .description("Seconds between real-time updates (0 = only on candle close)")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(5)  // Update every 5 seconds
            .minValue(0)
            .maxValue(60)
            .required(false)
            .build());
        
        params.put("showLevels", IndicatorParameter.builder("showLevels")
            .displayName("Show Significant Levels")
            .description("Highlight high/low volume nodes and liquidity walls")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("true")
            .required(false)
            .build());
        
        return params;
    }
    
    // ============================================================
    // DECLARE REQUIRED DATA TYPES
    // ============================================================
    
    /**
     * This indicator needs BOTH order book and trade data
     * 
     * - ORDER_BOOK: For current market depth visualization
     * - AGGREGATE_TRADE: For executed volume tracking
     * - KLINE: For candle structure and timing
     */
    @Override
    public Set<TradingDataType> getRequiredDataTypes() {
        return Set.of(
            TradingDataType.ORDER_BOOK,       // Current bid/ask depth
            TradingDataType.AGGREGATE_TRADE,  // Executed trades
            TradingDataType.KLINE             // Candle timing
        );
    }
    
    // ============================================================
    // LIFECYCLE METHODS
    // ============================================================
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        System.out.println("📊 BookmapIndicator.onInit - Initializing Bookmap heatmap with live updates");
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        // Parse parameters
        BigDecimal tickSize = new BigDecimal(getStringParameter(params, "tickSize", "0.01"));
        int lookback = getIntParameter(params, "lookback", 50);
        int updateInterval = getIntParameter(params, "updateInterval", 5);
        
        // Create initial state
        BookmapState state = new BookmapState(tickSize, lookback, updateInterval);
        
        System.out.println("✅ Bookmap state initialized (tickSize=" + tickSize + 
                         ", lookback=" + lookback + 
                         ", updateInterval=" + updateInterval + "s)");
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        System.out.println("📊 BookmapIndicator.onNewCandle - Candle closed, outputting volume profile with shapes");
        
        BookmapState bookmapState = (BookmapState) state;
        
        // Build heatmap data from accumulated volume profile
        Map<String, Object> heatmap = buildHeatmap(bookmapState);
        
        // Detect significant levels
        List<Map<String, Object>> levels = detectSignificantLevels(bookmapState);
        
        // ============================================================
        // CREATE CIRCLE SHAPES FOR VISUALIZATION
        // ============================================================
        // We create circles at significant price levels to make the data easier to see
        ShapeCollection shapes = createVolumeCircles(candle, bookmapState, levels);
        
        // Calculate summary values
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("totalVolume", bookmapState.getTotalBuyVolume().add(bookmapState.getTotalSellVolume()));
        values.put("delta", bookmapState.getTotalBuyVolume().subtract(bookmapState.getTotalSellVolume()));
        
        // Find POC (Point of Control) - price with highest volume
        BigDecimal pocPrice = findPOC(bookmapState);
        if (pocPrice != null) {
            values.put("pocPrice", pocPrice);
        }
        
        // Calculate bid/ask ratio from current order book
        if (bookmapState.getCurrentOrderBook() != null) {
            BigDecimal bidVolume = bookmapState.getCurrentOrderBook().getTotalBidVolume(10);
            BigDecimal askVolume = bookmapState.getCurrentOrderBook().getTotalAskVolume(10);
            if (askVolume.compareTo(BigDecimal.ZERO) > 0) {
                values.put("bidAskRatio", bidVolume.divide(askVolume, 4, RoundingMode.HALF_UP));
            }
        }
        
        // Build result
        Map<String, Object> result = new HashMap<>();
//        result.put("values", values);
        result.put("state", bookmapState);
        result.put("heatmap", heatmap);
        result.put("levels", levels);
        
        // Add shapes to result if we have any
        if (!shapes.isEmpty()) {
            result.putAll(shapes.toMap());  // Adds "markers" key with list of circles
        }
        
        return result;
    }
    
    // ============================================================
    // TRADE DATA PROCESSING
    // ============================================================
    
    /**
     * Process each trade and accumulate volume at price levels
     * 
     * This is called VERY frequently (1000+ times per second)
     * We accumulate in state and only output on candle close
     * 
     * NOTE: We DON'T broadcast on every trade (too frequent)
     * All accumulated data will be sent on candle close using the
     * standard "indicatorUpdate" message format (same as other indicators)
     */
    @Override
    public Map<String, Object> onTradeUpdate(TradeData trade, Map<String, Object> params, Object state) {
        BookmapState bookmapState = (BookmapState) state;
        
        // Add trade volume to the price level
        bookmapState.addTrade(
            trade.getPrice(), 
            trade.getQuantity(), 
            trade.isAggressiveBuy()
        );
        
        // Return EMPTY values - don't broadcast trade updates
        // The full data (with heatmap and circles) will be sent on candle close
        return Map.of(
            "values", Map.of(),  // Empty - no broadcast
            "state", bookmapState
        );
    }
    
    // ============================================================
    // ORDER BOOK DATA PROCESSING
    // ============================================================
    
    /**
     * Process order book updates to show current market depth
     * 
     * This is called VERY frequently (100+ times per second)
     * We store the latest order book and update price level data
     * 
     * NOTE: We DON'T broadcast on every order book update (too frequent)
     * Instead, we accumulate the data and output it on candle close
     * This keeps the message format consistent with other indicators
     */
    @Override
    public Map<String, Object> onOrderBookUpdate(OrderBookData orderBook, Map<String, Object> params, Object state) {
        BookmapState bookmapState = (BookmapState) state;
        
        // Store current order book snapshot (for use in onNewCandle)
        bookmapState.setCurrentOrderBook(orderBook);
        
        // Update volume profile with current bid/ask data
        // This shows current liquidity at each price level
        updateProfileWithOrderBook(bookmapState, orderBook);
        
        // Return EMPTY values - don't broadcast order book updates
        // The full data (with heatmap and circles) will be sent on candle close
        // This ensures we use the standard "indicatorUpdate" message format
        return Map.of(
            "values", Map.of(),  // Empty - no broadcast
            "state", bookmapState
        );
    }
    
    // ============================================================
    // HELPER METHODS
    // ============================================================
    
    /**
     * Update volume profile with current order book bid/ask data
     */
    private void updateProfileWithOrderBook(BookmapState state, OrderBookData orderBook) {
        // Update bid volumes
        for (OrderBookData.OrderBookLevel bid : orderBook.getBids()) {
            BigDecimal roundedPrice = roundToTickSize(bid.getPrice(), state.getTickSize());
            PriceLevelData levelData = state.getVolumeProfile().computeIfAbsent(
                roundedPrice, 
                p -> new PriceLevelData(p)
            );
            levelData.setBidVolume(bid.getQuantity());
        }
        
        // Update ask volumes
        for (OrderBookData.OrderBookLevel ask : orderBook.getAsks()) {
            BigDecimal roundedPrice = roundToTickSize(ask.getPrice(), state.getTickSize());
            PriceLevelData levelData = state.getVolumeProfile().computeIfAbsent(
                roundedPrice, 
                p -> new PriceLevelData(p)
            );
            levelData.setAskVolume(ask.getQuantity());
        }
    }
    
    /**
     * Build heatmap data structure for frontend visualization
     */
    private Map<String, Object> buildHeatmap(BookmapState state) {
        Map<String, Object> heatmap = new HashMap<>();
        
        for (Map.Entry<BigDecimal, PriceLevelData> entry : state.getVolumeProfile().entrySet()) {
            BigDecimal price = entry.getKey();
            PriceLevelData data = entry.getValue();
            
            // Calculate intensity (0-1) based on volume
            double intensity = 0.0;
            if (state.getMaxVolumeAtLevel().compareTo(BigDecimal.ZERO) > 0) {
                intensity = data.getTotalVolume()
                    .divide(state.getMaxVolumeAtLevel(), 4, RoundingMode.HALF_UP)
                    .doubleValue();
            }
            
            // Build level data
            Map<String, Object> levelData = new HashMap<>();
            levelData.put("buyVolume", data.getBuyVolume());
            levelData.put("sellVolume", data.getSellVolume());
            levelData.put("bidVolume", data.getCurrentBidVolume());
            levelData.put("askVolume", data.getCurrentAskVolume());
            levelData.put("totalTraded", data.getTotalVolume());
            levelData.put("delta", data.getDelta());
            levelData.put("intensity", intensity);
            
            heatmap.put(price.toPlainString(), levelData);
        }
        
        return heatmap;
    }
    
    /**
     * Find Point of Control (price with highest volume)
     */
    private BigDecimal findPOC(BookmapState state) {
        BigDecimal pocPrice = null;
        BigDecimal maxVolume = BigDecimal.ZERO;
        
        for (Map.Entry<BigDecimal, PriceLevelData> entry : state.getVolumeProfile().entrySet()) {
            BigDecimal totalVolume = entry.getValue().getTotalVolume();
            if (totalVolume.compareTo(maxVolume) > 0) {
                maxVolume = totalVolume;
                pocPrice = entry.getKey();
            }
        }
        
        return pocPrice;
    }
    
    /**
     * Detect significant price levels (HVN, LVN, liquidity walls)
     */
    private List<Map<String, Object>> detectSignificantLevels(BookmapState state) {
        List<Map<String, Object>> levels = new ArrayList<>();
        
        // Find high volume nodes (HVN) - top 10% of volume
        BigDecimal hvnThreshold = state.getMaxVolumeAtLevel()
            .multiply(new BigDecimal("0.7"));
        
        for (Map.Entry<BigDecimal, PriceLevelData> entry : state.getVolumeProfile().entrySet()) {
            PriceLevelData data = entry.getValue();
            BigDecimal totalVolume = data.getTotalVolume();
            
            if (totalVolume.compareTo(hvnThreshold) >= 0) {
                Map<String, Object> level = new HashMap<>();
                level.put("price", entry.getKey());
                level.put("type", "HVN");
                level.put("volume", totalVolume);
                level.put("strength", totalVolume.divide(state.getMaxVolumeAtLevel(), 4, RoundingMode.HALF_UP));
                levels.add(level);
            }
        }
        
        return levels;
    }
    
    /**
     * ============================================================
     * CREATE CIRCLE SHAPES FOR VOLUME VISUALIZATION
     * ============================================================
     * 
     * This method creates circle markers at significant price levels to visualize:
     * 1. POC (Point of Control) - Largest circle at highest volume level
     * 2. HVN (High Volume Nodes) - Medium circles at high volume areas
     * 3. Buy/Sell Pressure - Color coded circles (green for buy dominance, red for sell)
     * 
     * CIRCLE SIZE:
     * - POC: 16-20px (largest)
     * - HVN: 10-14px (medium, scaled by volume)
     * - Regular: 6-10px (small, scaled by volume)
     * 
     * CIRCLE COLOR:
     * - Green (#26a69a): Buy dominance (more buy volume)
     * - Red (#ef5350): Sell dominance (more sell volume)
     * - Yellow (#FFC107): POC marker
     * - Blue (#2196F3): Balanced volume
     * 
     * @param candle The current candle (for timestamp)
     * @param state Bookmap state with volume profile
     * @param levels Detected significant levels
     * @return ShapeCollection with marker circles
     */
    private ShapeCollection createVolumeCircles(CandlestickData candle, 
                                                BookmapState state, 
                                                List<Map<String, Object>> levels) {
        ShapeCollection shapes = new ShapeCollection();
        
        // Get candle timestamp for positioning circles
        long candleTime = candle.getOpenTime().getEpochSecond();
        
        // Find POC price for special highlighting
        BigDecimal pocPrice = findPOC(state);
        
        // Create circles for all significant volume levels
        // We'll show circles for the top 20 price levels by volume
        List<Map.Entry<BigDecimal, PriceLevelData>> sortedLevels = state.getVolumeProfile().entrySet().stream()
            .sorted((a, b) -> b.getValue().getTotalVolume().compareTo(a.getValue().getTotalVolume()))
            .limit(20)  // Show top 20 levels
            .toList();
        
        for (Map.Entry<BigDecimal, PriceLevelData> entry : sortedLevels) {
            BigDecimal price = entry.getKey();
            PriceLevelData data = entry.getValue();
            
            // Skip if no volume
            if (data.getTotalVolume().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            
            // Calculate volume ratio (0-1) for sizing
            double volumeRatio = data.getTotalVolume()
                .divide(state.getMaxVolumeAtLevel(), 4, RoundingMode.HALF_UP)
                .doubleValue();
            
            // Calculate buy/sell delta for coloring
            BigDecimal delta = data.getDelta();
            BigDecimal totalVolume = data.getTotalVolume();
            double deltaRatio = 0.0;
            if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                deltaRatio = delta.divide(totalVolume, 4, RoundingMode.HALF_UP).doubleValue();
            }
            
            // Determine circle size based on volume importance
            int circleSize;
            String circleColor;
            String text = null;
            
            // POC gets special treatment - largest circle, yellow color
            if (price.equals(pocPrice)) {
                circleSize = 20;  // Largest
                circleColor = "#FFC107";  // Yellow - easy to spot
                text = "POC";  // Label it
            }
            // High volume nodes - larger circles
            else if (volumeRatio >= 0.7) {
                circleSize = 10 + (int)(volumeRatio * 6);  // 10-16px
                // Color based on buy/sell pressure
                if (deltaRatio > 0.3) {
                    circleColor = "#26a69a";  // Green - strong buying
                } else if (deltaRatio < -0.3) {
                    circleColor = "#ef5350";  // Red - strong selling
                } else {
                    circleColor = "#2196F3";  // Blue - balanced
                }
            }
            // Medium volume levels
            else if (volumeRatio >= 0.4) {
                circleSize = 8 + (int)(volumeRatio * 4);  // 8-10px
                // Color based on delta
                if (deltaRatio > 0.2) {
                    circleColor = "#4CAF50";  // Light green - buying
                } else if (deltaRatio < -0.2) {
                    circleColor = "#F44336";  // Light red - selling
                } else {
                    circleColor = "#64B5F6";  // Light blue - balanced
                }
            }
            // Lower volume levels - smallest circles
            else {
                circleSize = 6 + (int)(volumeRatio * 2);  // 6-8px
                // Subtle colors
                if (deltaRatio > 0) {
                    circleColor = "#81C784";  // Very light green
                } else {
                    circleColor = "#E57373";  // Very light red
                }
            }
            
            // Create the circle marker
            MarkerShape circle = MarkerShape.builder()
                .time(candleTime)
                .price(price)
                .shape("circle")
                .color(circleColor)
                .size(circleSize)
                .text(text)  // Only POC has text label
                .position("inBar")  // Position at the price level
                .build();
            
            shapes.addMarker(circle);
        }
        
        System.out.println("✅ Created " + shapes.getMarkers().size() + " volume circles for visualization");
        
        return shapes;
    }
    
    private BigDecimal roundToTickSize(BigDecimal price, BigDecimal tickSize) {
        return price.divide(tickSize, 0, RoundingMode.HALF_UP)
                   .multiply(tickSize);
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        // Use custom series type for heatmap visualization
        // The frontend will need to render this as a heatmap
        metadata.put("bookmap", IndicatorMetadata.builder("bookmap")
            .displayName("Bookmap Heatmap")
            .seriesType("heatmap")  // Custom type for heatmap rendering
            .addConfig("bidColor", "#2962FF")   // Blue for bids
            .addConfig("askColor", "#FF6D00")   // Orange for asks
            .addConfig("buyColor", "#26a69a")   // Green for buy volume
            .addConfig("sellColor", "#ef5350")  // Red for sell volume
            .separatePane(false)  // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1; // No warm-up needed, accumulates in real-time
    }
}

