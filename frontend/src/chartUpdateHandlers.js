/**
 * chartUpdateHandlers.js
 * 
 * Shared handler functions for updating chart data.
 * Used by:
 * - WebSocket for real-time updates
 * - Replay mode for manual updates
 * - Any other data source that needs to update the chart
 */

/**
 * Handle candle updates (real-time or replay)
 * 
 * @param {Object} candleData - Candle data to update
 * @param {Object} seriesRef - Ref to main candlestick series
 * @param {Object} seriesManagerRef - Ref to series manager
 * @param {Function} onDataUpdate - Callback to update data array
 * @param {Function} onRealCountUpdate - Optional callback to update real count
 */
export const handleCandleUpdate = (candleData, seriesRef, seriesManagerRef, onDataUpdate, onRealCountUpdate) => {
    if (!candleData || !seriesRef.current) {
        console.warn('Cannot update candle: missing data or series ref');
        return;
    }

    const candleTime = candleData.timestamp;
    
    const newCandle = {
        time: candleTime,
        open: candleData.open,
        high: candleData.high,
        low: candleData.low,
        close: candleData.close
    };
    
    // Use update() method to avoid redrawing the entire chart
    try {
        seriesRef.current.update(newCandle);
        
        // Also update the data array in memory for consistency
        onDataUpdate(prevCandles => {
            const existingCandleIndex = prevCandles.findIndex(c => c.time === candleTime);
            
            let updatedCandles;
            if (existingCandleIndex >= 0) {
                // UPDATE existing candle in memory
                updatedCandles = [...prevCandles];
                updatedCandles[existingCandleIndex] = newCandle;
            } else {
                // ADD new candle to memory
                updatedCandles = [...prevCandles, newCandle];
            }
            
            // Update series manager's chart data reference
            if (seriesManagerRef.current) {
                seriesManagerRef.current.setChartData(updatedCandles);
            }
            
            return updatedCandles;
        });
        
        // Update real count if this is a closed candle
        if (candleData.closed && onRealCountUpdate) {
            onRealCountUpdate(prev => prev + 1);
        }
    } catch (error) {
        console.error('Error updating candle:', error);
    }
};

/**
 * Handle indicator updates (real-time or replay)
 * 
 * @param {Object} indicatorData - Indicator data to update
 * @param {Object} seriesManagerRef - Ref to series manager
 * @param {Function} onStatusUpdate - Optional callback for status messages
 */
export const handleIndicatorUpdate = (indicatorData, seriesManagerRef, onStatusUpdate) => {
    if (!seriesManagerRef.current || !indicatorData.values || !indicatorData.timestamp) {
        console.warn('Cannot update indicator: missing data or series manager');
        return;
    }

    const instanceKey = indicatorData.instanceKey;
    const indicatorPrefix = `indicator_${instanceKey}`;
    
    // Convert ISO timestamp to Unix timestamp (seconds)
    const timeValue = Math.floor(new Date(indicatorData.timestamp).getTime() / 1000);
    
    console.log(`Updating indicator ${instanceKey} at time ${timeValue}`, indicatorData.values);
    
    // Update status message if callback provided
    if (onStatusUpdate) {
        const updateTime = new Date(indicatorData.timestamp).toLocaleTimeString();
        onStatusUpdate(`Last update: ${updateTime}`);
    }
    
    // Update each series in the values object
    Object.entries(indicatorData.values).forEach(([seriesKey, value]) => {
        // Try to find and update the series
        // Series could be named like: indicator_Binance:ETHUSDT:1m:sma:9998834d_sma
        const possibleSeriesIds = [
            `${indicatorPrefix}_${seriesKey}`,  // e.g., indicator_Binance:ETHUSDT:1m:sma:9998834d_sma
            `${indicatorPrefix}_${indicatorData.indicatorId}`, // e.g., indicator_Binance:ETHUSDT:1m:sma:9998834d_sma (using indicatorId)
            indicatorPrefix // Just the prefix (for single-series indicators)
        ];
        
        console.log(`Trying to find series for ${seriesKey}, checking:`, possibleSeriesIds);
        
        let updated = false;
        for (const seriesId of possibleSeriesIds) {
            const series = seriesManagerRef.current.getSeries(seriesId);
            if (series) {
                try {
                    series.update({ time: timeValue, value: value });
                    console.log(`✅ Updated ${seriesId} with value ${value} at ${timeValue}`);
                    updated = true;
                    break;
                } catch (error) {
                    console.error(`Error updating series ${seriesId}:`, error);
                }
            }
        }
        
        if (!updated) {
            console.warn(`⚠️ Could not find series to update for ${seriesKey}`);
            console.warn(`   Tried:`, possibleSeriesIds);
            console.warn(`   Available series:`, seriesManagerRef.current.getAllSeriesIds());
        }
    });
};

/**
 * Handle batch candle updates (useful for replay)
 * 
 * @param {Array} candles - Array of candle data
 * @param {Object} seriesRef - Ref to main candlestick series
 * @param {Object} seriesManagerRef - Ref to series manager
 * @param {Function} onDataUpdate - Callback to update data array
 */
export const handleBatchCandleUpdate = (candles, seriesRef, seriesManagerRef, onDataUpdate) => {
    if (!candles || candles.length === 0 || !seriesRef.current) {
        console.warn('Cannot batch update candles: missing data or series ref');
        return;
    }

    try {
        // Set all candles at once (more efficient for batch updates)
        seriesRef.current.setData(candles);
        
        // Update data in state
        onDataUpdate(candles);
        
        // Update series manager's chart data reference
        if (seriesManagerRef.current) {
            seriesManagerRef.current.setChartData(candles);
        }
    } catch (error) {
        console.error('Error batch updating candles:', error);
    }
};

/**
 * Parse WebSocket message and extract candle data
 * Handles different message formats from the backend
 * 
 * @param {Object} message - WebSocket message
 * @returns {Object|null} - Extracted candle data or null
 */
export const extractCandleFromMessage = (message) => {
    // Extract candle data from message (could be message.candle, message.data.candle, or message.data)
    return message.candle || message.data?.candle || message.data || null;
};

/**
 * Parse WebSocket message and extract indicator data
 * Handles different message formats from the backend
 * 
 * @param {Object} message - WebSocket message
 * @returns {Object|null} - Extracted indicator data or null
 */
export const extractIndicatorFromMessage = (message) => {
    // Extract indicator data from message.data
    return message.data || message || null;
};

