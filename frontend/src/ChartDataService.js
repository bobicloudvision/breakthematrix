/**
 * ChartDataService.js
 * 
 * Handles all data fetching for the chart:
 * - Historical candle data
 * - Strategy visualization data
 * - Indicator data
 * 
 * This service can be swapped out with a replay data service in the future.
 */

// Transform API data to chart format
export const transformApiData = (apiData) => {
    return apiData.map(candle => ({
        time: candle.timestamp, // Use timestamp field directly (already in seconds)
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
    }));
};

// Get interval in seconds
export const getIntervalSeconds = (interval) => {
    const intervalMap = {
        '1m': 60,
        '5m': 5 * 60,
        '15m': 15 * 60,
        '1h': 60 * 60,
        '4h': 4 * 60 * 60,
        '1d': 24 * 60 * 60,
    };
    return intervalMap[interval] || 60;
};

/**
 * Fetch historical candle data
 * @param {string|object} provider - Provider name or provider object
 * @param {string} symbol - Trading symbol
 * @param {string} interval - Time interval
 * @param {number} limit - Number of candles to fetch
 * @returns {Promise<{data: Array, realCount: number}>}
 */
export const fetchHistoricalData = async (provider, symbol, interval, limit = 5000) => {
    if (!provider || !symbol || !interval) {
        throw new Error('Missing required parameters: provider, symbol, or interval');
    }

    const providerName = typeof provider === 'string' ? provider : (provider.name || provider);
    const url = `http://localhost:8080/api/trading/historical/${providerName}/${symbol}/${interval}?limit=${limit}`;
    
    console.log('Fetching historical data from:', url);
    const response = await fetch(url);
    
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    const apiData = await response.json();
    console.log('Historical API response:', apiData.length, 'candles');
    
    const chartData = transformApiData(apiData);
    
    return {
        data: chartData,
        realCount: chartData.length
    };
};

/**
 * Fetch strategy visualization data
 * @param {string} strategyId - Strategy ID
 * @param {string} symbol - Trading symbol
 * @returns {Promise<object|null>}
 */
export const fetchStrategyData = async (strategyId, symbol) => {
    try {
        const response = await fetch(`http://localhost:8080/api/visualization/strategies/${strategyId}/symbols/${symbol}/tradingview`);
        
        if (response.ok) {
            const data = await response.json();
            console.log('Strategy visualization data loaded:', data);
            return data;
        } else {
            console.error('Failed to fetch strategy data:', response.status);
            return null;
        }
    } catch (error) {
        console.error('Error fetching strategy data:', error);
        return null;
    }
};

/**
 * Fetch all strategy data for active strategies
 * @param {Array} activeStrategies - Array of active strategy objects
 * @param {string} symbol - Trading symbol
 * @returns {Promise<object>} - Map of strategy ID to strategy data
 */
export const fetchAllStrategyData = async (activeStrategies, symbol) => {
    console.log('Fetching strategy data for:', { activeStrategies: activeStrategies.length, symbol });
    
    if (!activeStrategies || activeStrategies.length === 0 || !symbol) {
        console.log('No active strategies or symbol, returning empty data');
        return {};
    }

    const strategyDataMap = {};
    
    for (const strategy of activeStrategies) {
        console.log('Checking strategy:', strategy.id, 'symbols:', strategy.symbols);
        if (strategy.symbols.includes(symbol)) {
            console.log('Fetching data for strategy:', strategy.id);
            const data = await fetchStrategyData(strategy.id, symbol);
            if (data) {
                console.log('Strategy data fetched successfully:', strategy.id, {
                    hasBoxes: !!data.boxes,
                    boxesCount: data.boxes?.length,
                    hasMarkers: !!data.markers,
                    markersCount: data.markers?.length
                });
                console.log('Boxes in fetched data:', data.boxes);
                strategyDataMap[strategy.id] = data;
            } else {
                console.log('Failed to fetch data for strategy:', strategy.id);
            }
        } else {
            console.log('Strategy', strategy.id, 'does not support symbol', symbol);
        }
    }
    
    console.log('Final strategy data map:', Object.keys(strategyDataMap));
    return strategyDataMap;
};

/**
 * Fetch indicator historical data
 * @param {string|object} provider - Provider name or provider object
 * @param {string} symbol - Trading symbol
 * @param {string} interval - Time interval
 * @param {number} count - Number of data points to fetch
 * @returns {Promise<object>} - Indicator response data
 */
export const fetchIndicators = async (provider, symbol, interval, count = 1000) => {
    const providerName = typeof provider === 'string' ? provider : (provider.name || provider);
    const requestBody = {
        provider: providerName,
        symbol: symbol,
        interval: interval,
        count: count
    };
    
    console.log('Fetching indicators with request:', requestBody);
    
    const res = await fetch('http://localhost:8080/api/indicators/historical', {
        method: 'POST',
        headers: {
            'accept': 'application/json',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody),
    });
    
    if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`Failed to fetch indicators: HTTP ${res.status} - ${errorText}`);
    }
    
    const response = await res.json();
    console.log('Indicators response received:', {
        indicatorCount: response.indicatorCount,
        fromActiveInstances: response.fromActiveInstances,
        indicators: response.indicators?.length || 0
    });
    
    return response;
};

