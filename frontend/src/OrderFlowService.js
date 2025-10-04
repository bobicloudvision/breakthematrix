/**
 * OrderFlowService - Aggregates real-time trade data into order flow per candle
 * Manages footprint data for display on charts
 */

export class OrderFlowService {
    constructor(interval = '1m') {
        this.interval = interval;
        this.candles = new Map(); // Map<candleTime, CandleOrderFlow>
        this.listeners = new Set();
        this.intervalMs = this.parseInterval(interval);
    }

    parseInterval(interval) {
        const value = parseInt(interval);
        const unit = interval.slice(-1);
        
        const multipliers = {
            's': 1000,
            'm': 60000,
            'h': 3600000,
            'd': 86400000
        };
        
        return value * (multipliers[unit] || 60000);
    }

    /**
     * Get candle time (start of period) for a given timestamp
     */
    getCandleTime(timestamp) {
        const time = typeof timestamp === 'string' ? new Date(timestamp).getTime() : timestamp;
        return Math.floor(time / this.intervalMs) * this.intervalMs;
    }

    /**
     * Add a trade to the order flow data
     */
    addTrade(trade) {
        const candleTime = this.getCandleTime(trade.timestamp);
        
        if (!this.candles.has(candleTime)) {
            this.candles.set(candleTime, {
                time: Math.floor(candleTime / 1000), // Unix timestamp in seconds
                volumeByPrice: new Map(),
                totalBuyVolume: 0,
                totalSellVolume: 0,
                tradeCount: 0
            });
        }

        const candle = this.candles.get(candleTime);
        
        // Round price to tick size (e.g., 0.01 for most crypto)
        const tickSize = this.determineTickSize(trade.price);
        const priceLevel = Math.round(trade.price / tickSize) * tickSize;
        
        if (!candle.volumeByPrice.has(priceLevel)) {
            candle.volumeByPrice.set(priceLevel, {
                price: priceLevel,
                buyVolume: 0,
                sellVolume: 0,
                trades: 0
            });
        }

        const volumeData = candle.volumeByPrice.get(priceLevel);
        
        // isBuyerMaker = true means sell (market sell, maker is buying)
        // isBuyerMaker = false means buy (market buy, maker is selling)
        if (trade.isBuyerMaker || trade.isAggressiveSell) {
            volumeData.sellVolume += parseFloat(trade.quantity);
            candle.totalSellVolume += parseFloat(trade.quantity);
        } else {
            volumeData.buyVolume += parseFloat(trade.quantity);
            candle.totalBuyVolume += parseFloat(trade.quantity);
        }
        
        volumeData.trades++;
        candle.tradeCount++;

        this.notifyListeners();
    }

    /**
     * Determine appropriate tick size based on price
     */
    determineTickSize(price) {
        if (price < 1) return 0.0001;
        if (price < 10) return 0.001;
        if (price < 100) return 0.01;
        if (price < 1000) return 0.1;
        if (price < 10000) return 1;
        return 10;
    }

    /**
     * Get order flow data for chart display
     */
    getOrderFlowData() {
        const data = [];
        
        this.candles.forEach((candle, time) => {
            const volumeByPrice = Array.from(candle.volumeByPrice.values())
                .sort((a, b) => a.price - b.price);
            
            data.push({
                time: candle.time,
                volumeByPrice,
                totalBuyVolume: candle.totalBuyVolume,
                totalSellVolume: candle.totalSellVolume,
                delta: candle.totalBuyVolume - candle.totalSellVolume,
                tradeCount: candle.tradeCount
            });
        });

        return data.sort((a, b) => a.time - b.time);
    }

    /**
     * Get order flow data for a specific candle
     */
    getCandleOrderFlow(candleTime) {
        const candle = this.candles.get(candleTime);
        if (!candle) return null;

        return {
            time: candle.time,
            volumeByPrice: Array.from(candle.volumeByPrice.values()),
            totalBuyVolume: candle.totalBuyVolume,
            totalSellVolume: candle.totalSellVolume,
            delta: candle.totalBuyVolume - candle.totalSellVolume
        };
    }

    /**
     * Get cumulative delta over time
     */
    getCumulativeDelta() {
        let cumDelta = 0;
        const result = [];

        const sortedCandles = Array.from(this.candles.entries())
            .sort((a, b) => a[0] - b[0]);

        sortedCandles.forEach(([time, candle]) => {
            const delta = candle.totalBuyVolume - candle.totalSellVolume;
            cumDelta += delta;
            result.push({
                time: candle.time,
                value: cumDelta
            });
        });

        return result;
    }

    /**
     * Clear old candles (keep last N)
     */
    cleanup(keepLast = 500) {
        const times = Array.from(this.candles.keys()).sort((a, b) => a - b);
        
        if (times.length > keepLast) {
            const toRemove = times.slice(0, times.length - keepLast);
            toRemove.forEach(time => this.candles.delete(time));
        }
    }

    /**
     * Subscribe to order flow updates
     */
    subscribe(listener) {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Notify all listeners of updates
     */
    notifyListeners() {
        const data = this.getOrderFlowData();
        this.listeners.forEach(listener => listener(data));
    }

    /**
     * Clear all data
     */
    clear() {
        this.candles.clear();
        this.notifyListeners();
    }

    /**
     * Load historical order flow data
     */
    loadHistoricalData(historicalData) {
        historicalData.forEach(candle => {
            if (candle.volumeByPrice) {
                const candleTime = candle.time * 1000; // Convert to ms
                const volumeByPriceMap = new Map();
                
                candle.volumeByPrice.forEach(v => {
                    volumeByPriceMap.set(v.price, v);
                });

                this.candles.set(candleTime, {
                    time: candle.time,
                    volumeByPrice: volumeByPriceMap,
                    totalBuyVolume: candle.totalBuyVolume || 0,
                    totalSellVolume: candle.totalSellVolume || 0,
                    tradeCount: candle.tradeCount || 0
                });
            }
        });

        this.notifyListeners();
    }
}

// Singleton instance
let orderFlowServiceInstance = null;

export function getOrderFlowService(interval = '1m') {
    if (!orderFlowServiceInstance || orderFlowServiceInstance.interval !== interval) {
        orderFlowServiceInstance = new OrderFlowService(interval);
    }
    return orderFlowServiceInstance;
}

