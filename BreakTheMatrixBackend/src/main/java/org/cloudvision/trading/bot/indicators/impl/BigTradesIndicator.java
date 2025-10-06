package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.MarkerShape;
import org.cloudvision.trading.bot.visualization.ShapeCollection;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TradeData;
import org.cloudvision.trading.model.TradingDataType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================
 * BIG TRADES INDICATOR
 * ============================================================
 * 
 * This indicator detects and visualizes unusually large trades that may
 * indicate institutional activity, whale movements, or significant market events.
 * 
 * WHAT THIS INDICATOR DOES:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. DETECTS LARGE TRADES:
 *    - Monitors every trade for size above threshold
 *    - Tracks both buy and sell big trades
 *    - Records exact price, volume, and timing
 *    
 * 2. VISUALIZES ON CHART:
 *    - Green circles for large buys
 *    - Red circles for large sells
 *    - Size scaled by volume magnitude
 *    - Positioned at exact trade price
 *    
 * 3. PROVIDES STATISTICS:
 *    - Count of big trades per candle
 *    - Total big trade volume
 *    - Buy vs sell imbalance
 *    - Average trade size
 *    - Largest trade of the period
 * 
 * USE CASES:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - Spot institutional accumulation/distribution
 * - Identify whale activity
 * - Detect breakout triggers
 * - Find support/resistance from large orders
 * - Track smart money movements
 * 
 * PARAMETERS:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - volumeThreshold: Minimum trade size to be considered "big" (default: 10.0)
 * - showLabels: Show volume labels on markers (default: true)
 * - minMarkerSize: Minimum circle size in pixels (default: 10)
 * - maxMarkerSize: Maximum circle size in pixels (default: 30)
 * 
 * OUTPUT FORMAT:
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * {
 *   "values": {
 *     "bigTradeCount": 5,
 *     "bigBuyCount": 3,
 *     "bigSellCount": 2,
 *     "totalBigVolume": 123.45,
 *     "bigBuyVolume": 80.00,
 *     "bigSellVolume": 43.45,
 *     "imbalance": 0.35,              // (Buy - Sell) / Total
 *     "avgBigTradeSize": 24.69,
 *     "largestTrade": 50.00
 *   },
 *   "markers": [
 *     {
 *       "time": 1696598400,
 *       "price": 50000.00,
 *       "shape": "circle",
 *       "color": "#26a69a",           // Green = big buy
 *       "size": 20,
 *       "text": "50.0",               // Volume label
 *       "position": "inBar"
 *     }
 *   ]
 * }
 */
@Component
public class BigTradesIndicator extends AbstractIndicator {
    
    /**
     * Internal state for tracking big trades
     */
    public static class BigTradesState {
        // Configuration
        private final BigDecimal volumeThreshold;
        private final boolean showLabels;
        private final int minMarkerSize;
        private final int maxMarkerSize;
        private final double opacity;
        
        // Big trades detected this candle
        private final List<BigTradeInfo> bigTrades;
        
        // Statistics
        private int bigTradeCount;
        private int bigBuyCount;
        private int bigSellCount;
        private BigDecimal totalBigVolume;
        private BigDecimal bigBuyVolume;
        private BigDecimal bigSellVolume;
        private BigDecimal largestTradeVolume;
        
        // Current candle reference
        private CandlestickData currentCandle;
        
        public BigTradesState(BigDecimal volumeThreshold, boolean showLabels, 
                             int minMarkerSize, int maxMarkerSize, double opacity) {
            this.volumeThreshold = volumeThreshold;
            this.showLabels = showLabels;
            this.minMarkerSize = minMarkerSize;
            this.maxMarkerSize = maxMarkerSize;
            this.opacity = opacity;
            this.bigTrades = new ArrayList<>();
            reset();
        }
        
        public void reset() {
            bigTrades.clear();
            bigTradeCount = 0;
            bigBuyCount = 0;
            bigSellCount = 0;
            totalBigVolume = BigDecimal.ZERO;
            bigBuyVolume = BigDecimal.ZERO;
            bigSellVolume = BigDecimal.ZERO;
            largestTradeVolume = BigDecimal.ZERO;
        }
        
        public void addBigTrade(TradeData trade) {
            BigTradeInfo info = new BigTradeInfo(
                trade.getPrice(),
                trade.getQuantity(),
                trade.isAggressiveBuy(),
                trade.getTimestamp().toEpochMilli()
            );
            
            bigTrades.add(info);
            bigTradeCount++;
            
            if (trade.isAggressiveBuy()) {
                bigBuyCount++;
                bigBuyVolume = bigBuyVolume.add(trade.getQuantity());
            } else {
                bigSellCount++;
                bigSellVolume = bigSellVolume.add(trade.getQuantity());
            }
            
            totalBigVolume = totalBigVolume.add(trade.getQuantity());
            
            if (trade.getQuantity().compareTo(largestTradeVolume) > 0) {
                largestTradeVolume = trade.getQuantity();
            }
        }
        
        public boolean isBigTrade(BigDecimal volume) {
            return volume.compareTo(volumeThreshold) >= 0;
        }
        
        // Getters
        public List<BigTradeInfo> getBigTrades() { return bigTrades; }
        public int getBigTradeCount() { return bigTradeCount; }
        public int getBigBuyCount() { return bigBuyCount; }
        public int getBigSellCount() { return bigSellCount; }
        public BigDecimal getTotalBigVolume() { return totalBigVolume; }
        public BigDecimal getBigBuyVolume() { return bigBuyVolume; }
        public BigDecimal getBigSellVolume() { return bigSellVolume; }
        public BigDecimal getLargestTradeVolume() { return largestTradeVolume; }
        public BigDecimal getVolumeThreshold() { return volumeThreshold; }
        public boolean isShowLabels() { return showLabels; }
        public int getMinMarkerSize() { return minMarkerSize; }
        public int getMaxMarkerSize() { return maxMarkerSize; }
        public double getOpacity() { return opacity; }
        public CandlestickData getCurrentCandle() { return currentCandle; }
        public void setCurrentCandle(CandlestickData candle) { this.currentCandle = candle; }
    }
    
    /**
     * Information about a single big trade
     */
    public static class BigTradeInfo {
        private final BigDecimal price;
        private final BigDecimal volume;
        private final boolean isBuy;
        private final long timestamp;
        
        public BigTradeInfo(BigDecimal price, BigDecimal volume, boolean isBuy, long timestamp) {
            this.price = price;
            this.volume = volume;
            this.isBuy = isBuy;
            this.timestamp = timestamp;
        }
        
        public BigDecimal getPrice() { return price; }
        public BigDecimal getVolume() { return volume; }
        public boolean isBuy() { return isBuy; }
        public long getTimestamp() { return timestamp; }
    }
    
    public BigTradesIndicator() {
        super(
            "bigtrades",
            "Big Trades",
            "Detects and visualizes unusually large trades that may indicate whale activity or institutional orders",
            IndicatorCategory.ORDER_FLOW
        );
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("volumeThreshold", IndicatorParameter.builder("volumeThreshold")
            .displayName("Volume Threshold")
            .description("Minimum trade size to be considered 'big' (in base asset units)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("10.0")
            .required(false)
            .build());
        
        params.put("showLabels", IndicatorParameter.builder("showLabels")
            .displayName("Show Volume Labels")
            .description("Display trade volume on markers")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("true")
            .required(false)
            .build());
        
        params.put("minMarkerSize", IndicatorParameter.builder("minMarkerSize")
            .displayName("Min Marker Size")
            .description("Minimum circle size in pixels")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(10)
            .minValue(5)
            .maxValue(20)
            .required(false)
            .build());
        
        params.put("maxMarkerSize", IndicatorParameter.builder("maxMarkerSize")
            .displayName("Max Marker Size")
            .description("Maximum circle size in pixels")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(30)
            .minValue(20)
            .maxValue(50)
            .required(false)
            .build());
        
        params.put("opacity", IndicatorParameter.builder("opacity")
            .displayName("Marker Opacity")
            .description("Opacity of markers (0.0 = transparent, 1.0 = opaque)")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("0.7")
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Set<TradingDataType> getRequiredDataTypes() {
        return Set.of(
            TradingDataType.AGGREGATE_TRADE,  // For detecting big trades
            TradingDataType.KLINE             // For candle timing
        );
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        System.out.println("🐋 BigTradesIndicator.onInit - Initializing big trades detector");
        
        // Merge with defaults
        params = mergeWithDefaults(params);
        
        // Parse parameters
        BigDecimal volumeThreshold = new BigDecimal(getStringParameter(params, "volumeThreshold", "10.0"));
        boolean showLabels = Boolean.parseBoolean(getStringParameter(params, "showLabels", "true"));
        int minMarkerSize = getIntParameter(params, "minMarkerSize", 10);
        int maxMarkerSize = getIntParameter(params, "maxMarkerSize", 30);
        double opacity = Double.parseDouble(getStringParameter(params, "opacity", "0.7"));
        
        BigTradesState state = new BigTradesState(volumeThreshold, showLabels, minMarkerSize, maxMarkerSize, opacity);
        
        System.out.println("✅ Big trades detector initialized (threshold=" + volumeThreshold + 
                         ", labels=" + showLabels + ", opacity=" + opacity + ")");
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object state) {
        BigTradesState bigTradesState = (BigTradesState) state;
        
        // Save candle reference
        bigTradesState.setCurrentCandle(candle);
        
        // Build result with statistics and markers
        Map<String, Object> result = buildResult(candle, bigTradesState);
        
        // Reset for next candle
        bigTradesState.reset();
        
        return result;
    }
    
    @Override
    public Map<String, Object> onTradeUpdate(TradeData trade, Map<String, Object> params, Object state) {
        BigTradesState bigTradesState = (BigTradesState) state;
        
        // Check if this is a big trade
        if (bigTradesState.isBigTrade(trade.getQuantity())) {
            bigTradesState.addBigTrade(trade);
            
            System.out.println("🐋 BIG TRADE: " + 
                             (trade.isAggressiveBuy() ? "BUY" : "SELL") + 
                             " " + trade.getQuantity() + " @ " + trade.getPrice());
        }
        
        // Don't broadcast on every trade - only on candle close
        return Map.of(
            "values", Map.of(),
            "state", bigTradesState
        );
    }
    
    /**
     * Build result with statistics and visualization markers
     */
    private Map<String, Object> buildResult(CandlestickData candle, BigTradesState state) {
        Map<String, Object> result = new HashMap<>();
        
        // Calculate statistics - ALL values must be BigDecimal
        Map<String, Object> values = new HashMap<>();
        values.put("bigTradeCount", new BigDecimal(state.getBigTradeCount()));
        values.put("bigBuyCount", new BigDecimal(state.getBigBuyCount()));
        values.put("bigSellCount", new BigDecimal(state.getBigSellCount()));
        values.put("totalBigVolume", state.getTotalBigVolume());
        values.put("bigBuyVolume", state.getBigBuyVolume());
        values.put("bigSellVolume", state.getBigSellVolume());
        
        // Calculate imbalance: (Buy - Sell) / Total
        if (state.getTotalBigVolume().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal imbalance = state.getBigBuyVolume()
                .subtract(state.getBigSellVolume())
                .divide(state.getTotalBigVolume(), 4, RoundingMode.HALF_UP);
            values.put("imbalance", imbalance);
        } else {
            values.put("imbalance", BigDecimal.ZERO);
        }
        
        // Average trade size
        if (state.getBigTradeCount() > 0) {
            BigDecimal avgSize = state.getTotalBigVolume()
                .divide(new BigDecimal(state.getBigTradeCount()), 4, RoundingMode.HALF_UP);
            values.put("avgBigTradeSize", avgSize);
        } else {
            values.put("avgBigTradeSize", BigDecimal.ZERO);
        }
        
        values.put("largestTrade", state.getLargestTradeVolume());
        
        result.put("values", values);
        result.put("state", state);
        
        // Create visualization markers
        ShapeCollection shapes = createBigTradeMarkers(candle, state);
        if (!shapes.isEmpty()) {
            result.putAll(shapes.toMap());
        }
        
        return result;
    }
    
    /**
     * Create circle markers for each big trade
     */
    private ShapeCollection createBigTradeMarkers(CandlestickData candle, BigTradesState state) {
        ShapeCollection shapes = new ShapeCollection();
        
        if (state.getBigTrades().isEmpty()) {
            return shapes;
        }
        
        long candleTime = candle.getOpenTime().getEpochSecond();
        
        for (BigTradeInfo trade : state.getBigTrades()) {
            // Calculate marker size based on volume
            // Scale from min to max based on volume relative to threshold
            double volumeRatio = trade.getVolume()
                .divide(state.getVolumeThreshold(), 4, RoundingMode.HALF_UP)
                .doubleValue();
            
            // Cap the ratio for extreme outliers
            volumeRatio = Math.min(volumeRatio, 5.0);
            
            // Linear interpolation between min and max size
            int size = state.getMinMarkerSize() + 
                      (int)((state.getMaxMarkerSize() - state.getMinMarkerSize()) * 
                            (volumeRatio - 1.0) / 4.0);
            
            // Ensure size is within bounds
            size = Math.max(state.getMinMarkerSize(), Math.min(state.getMaxMarkerSize(), size));
            
            // Color based on buy/sell
            String color = trade.isBuy() ? "#26a69a" : "#ef5350";  // Green for buy, red for sell
            
            // Optional label showing volume
            String text = null;
            if (state.isShowLabels()) {
                text = trade.getVolume().setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
            
            // Create marker with opacity
            MarkerShape marker = MarkerShape.builder()
                .time(candleTime)
                .price(trade.getPrice())
                .shape("circle")
                .color(color)
                .size(size)
                .text(text)
                .position("inBar")
                .opacity(state.getOpacity())
                .build();
            
            shapes.addMarker(marker);
        }
        
        if (!shapes.isEmpty()) {
            System.out.println("✅ Created " + shapes.getMarkers().size() + 
                             " big trade markers (Buy: " + state.getBigBuyCount() + 
                             ", Sell: " + state.getBigSellCount() + ")");
        }
        
        return shapes;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        
        Map<String, IndicatorMetadata> metadata = new HashMap<>();
        
        metadata.put("bigtrades", IndicatorMetadata.builder("bigtrades")
            .displayName("Big Trades")
            .seriesType("scatter")  // Scatter plot for discrete trade markers
            .addConfig("buyColor", "#26a69a")   // Green for big buys
            .addConfig("sellColor", "#ef5350")  // Red for big sells
            .separatePane(false)  // Overlay on main chart
            .paneOrder(0)
            .build());
        
        return metadata;
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        return 1; // No warm-up needed
    }
}

