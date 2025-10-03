import { LineSeries, HistogramSeries, AreaSeries, BarSeries, BaselineSeries, CandlestickSeries, LineStyle } from 'lightweight-charts';

/**
 * Centralized manager for adding and managing chart series
 * Handles both indicators and strategy data
 * 
 * Example Usage:
 * 
 * // For Indicators:
 * const response = await fetch('/api/indicators/sma/historical', {...});
 * const data = await response.json();
 * seriesManager.addFromApiResponse('indicator_sma', data, { color: '#2962FF' });
 * 
 * // For Strategies:
 * seriesManager.addSeries('strategy_signals', signalsData, {
 *   seriesType: 'line',
 *   config: { color: '#ff9800', lineWidth: 1 }
 * });
 * 
 * // Remove by prefix:
 * seriesManager.removeSeriesByPrefix('indicator_'); // Remove all indicators
 * seriesManager.removeSeriesByPrefix('strategy_'); // Remove all strategy series
 */
export class ChartSeriesManager {
    constructor(chart) {
        this.chart = chart;
        this.seriesMap = new Map(); // Track all series by key
    }

    /**
     * Add a series to the chart based on metadata
     * @param {string} id - Unique identifier for the series
     * @param {Array} data - Data points array
     * @param {Object} metadata - Series metadata (type, color, config, etc.)
     * @param {Object} params - Additional parameters
     * @returns {boolean} - Success status
     */
    addSeries(id, data, metadata = {}, params = {}) {
        try {
            if (!this.chart) {
                console.error('Chart reference is null');
                return false;
            }

            if (!Array.isArray(data) || data.length === 0) {
                console.warn(`No data provided for series: ${id}`);
                return false;
            }

            // Extract metadata
            const seriesType = metadata.seriesType || 'line';
            const separatePane = metadata.separatePane || false;
            const config = metadata.config || {};
            
            // Get color and other configs
            const color = config.color || params.color || '#2962FF';
            const lineWidth = config.lineWidth || params.lineWidth || 2;
            const title = config.title || metadata.displayName || id.toUpperCase();

            console.log(`Adding ${seriesType} series: ${id}`, { color, separatePane, dataPoints: data.length });

            // Create series based on type
            let series = this._createSeries(seriesType, {
                id,
                color,
                lineWidth,
                title,
                separatePane,
                config
            });

            if (!series) {
                console.error(`Failed to create series for: ${id}`);
                return false;
            }

            // Transform and set data
            const transformedData = this._transformData(data, id, seriesType, color);
            
            if (transformedData.length === 0) {
                console.warn(`No valid data points for series: ${id}`);
                this.chart.removeSeries(series);
                return false;
            }

            series.setData(transformedData);
            
            // Store series reference
            this.seriesMap.set(id, {
                series,
                type: seriesType,
                metadata
            });

            console.log(`✅ Successfully added series: ${id} with ${transformedData.length} points`);
            return true;

        } catch (error) {
            console.error(`❌ Error adding series ${id}:`, error);
            return false;
        }
    }

    /**
     * Create a series based on type
     * @private
     */
    _createSeries(seriesType, options) {
        const { id, color, lineWidth, title, separatePane, config } = options;

        switch (seriesType.toLowerCase()) {
            case 'histogram':
                const histogramOptions = {
                    color: color,
                    title: title,
                    priceFormat: config.priceFormat || { type: 'volume' },
                    ...config
                };

                // Add separate pane with unique scale ID
                if (separatePane) {
                    histogramOptions.priceScaleId = id;
                }

                const histogramSeries = this.chart.addSeries(HistogramSeries, histogramOptions);

                // Configure the price scale after series creation
                if (separatePane) {
                    try {
                        histogramSeries.priceScale().applyOptions({
                            scaleMargins: {
                                top: 0.8,
                                bottom: 0,
                            },
                        });
                    } catch (err) {
                        console.warn(`Could not configure price scale for ${id}:`, err.message);
                    }
                }

                return histogramSeries;

            case 'area':
                return this.chart.addSeries(AreaSeries, {
                    topColor: config.topColor || color + '80',
                    bottomColor: config.bottomColor || color + '00',
                    lineColor: config.lineColor || color,
                    lineWidth: lineWidth,
                    title: title,
                    priceLineVisible: false,
                    lastValueVisible: true,
                    ...config
                });

            case 'bar':
                return this.chart.addSeries(BarSeries, {
                    upColor: config.upColor || color,
                    downColor: config.downColor || color,
                    openVisible: config.openVisible !== false,
                    thinBars: config.thinBars || false,
                    title: title,
                    ...config
                });

            case 'baseline':
                return this.chart.addSeries(BaselineSeries, {
                    baseValue: config.baseValue || { type: 'price', price: 0 },
                    lineColor: config.lineColor || color,
                    topFillColor1: config.topFillColor1 || color + '40',
                    topFillColor2: config.topFillColor2 || color + '20',
                    bottomFillColor1: config.bottomFillColor1 || color + '20',
                    bottomFillColor2: config.bottomFillColor2 || color + '40',
                    lineWidth: lineWidth,
                    title: title,
                    ...config
                });

            case 'candlestick':
                return this.chart.addSeries(CandlestickSeries, {
                    upColor: config.upColor || '#4caf50',
                    downColor: config.downColor || '#e91e63',
                    wickUpColor: config.wickUpColor || config.upColor || '#4caf50',
                    wickDownColor: config.wickDownColor || config.downColor || '#e91e63',
                    borderUpColor: config.borderUpColor || config.upColor || '#4caf50',
                    borderDownColor: config.borderDownColor || config.downColor || '#e91e63',
                    title: title,
                    ...config
                });

            case 'line':
            default:
                const lineOptions = {
                    color: color,
                    lineWidth: lineWidth,
                    title: title,
                    priceLineVisible: false,
                    lastValueVisible: true,
                    ...config
                };

                // Map lineStyle from number to LineStyle enum if provided
                if (config.lineStyle !== undefined) {
                    const lineStyleMap = {
                        0: LineStyle.Solid,
                        1: LineStyle.Dotted,
                        2: LineStyle.Dashed,
                        3: LineStyle.LargeDashed,
                        4: LineStyle.SparseDotted
                    };
                    lineOptions.lineStyle = lineStyleMap[config.lineStyle] || LineStyle.Solid;
                }

                return this.chart.addSeries(LineSeries, lineOptions);
        }
    }

    /**
     * Transform data to chart format
     * @private
     */
    _transformData(data, seriesId, seriesType, color) {
        // Get base ID without prefix (e.g., 'sma' from 'indicator_sma')
        const baseId = seriesId.replace('indicator_', '').replace('strategy_', '');
        
        const transformedData = data
            .map(point => {
                if (typeof point !== 'object') return null;

                // Extract time
                const time = point.time || point.timestamp || point.t;
                if (time === undefined) return null;

                // Handle different data formats based on series type
                switch (seriesType.toLowerCase()) {
                    case 'candlestick':
                    case 'bar':
                        // OHLC data
                        if (point.open === undefined || point.high === undefined || 
                            point.low === undefined || point.close === undefined) {
                            return null;
                        }
                        return {
                            time: time,
                            open: parseFloat(point.open),
                            high: parseFloat(point.high),
                            low: parseFloat(point.low),
                            close: parseFloat(point.close)
                        };

                    case 'histogram':
                    case 'line':
                    case 'area':
                    case 'baseline':
                    default:
                        // Value-based data
                        let value;
                        
                        // Check if value is nested in a values object
                        if (point.values && typeof point.values === 'object') {
                            // Try to find value by base ID, series ID, or common property names
                            value = point.values[baseId] || 
                                   point.values[seriesId] || 
                                   point.values.value ||
                                   point.values.val ||
                                   Object.values(point.values)[0];
                        } else {
                            // Direct value properties
                            value = point.value !== undefined ? point.value : 
                                   point.val !== undefined ? point.val :
                                   point.v !== undefined ? point.v : undefined;
                        }

                        if (value === undefined || value === null) return null;

                        // Build data point
                        const dataPoint = {
                            time: time,
                            value: parseFloat(value)
                        };

                        // Preserve individual bar color if provided (for histogram)
                        if (seriesType === 'histogram' && point.color) {
                            dataPoint.color = point.color;
                        } else if (seriesType === 'histogram') {
                            // Default coloring based on value
                            dataPoint.color = value >= 0 ? color : '#ef5350';
                        }

                        return dataPoint;
                }
            })
            .filter(point => {
                if (point === null) return false;
                
                // Validate based on series type
                if (point.open !== undefined) {
                    // OHLC validation
                    return !isNaN(point.open) && !isNaN(point.high) && 
                           !isNaN(point.low) && !isNaN(point.close);
                } else {
                    // Value validation
                    return !isNaN(point.value);
                }
            });

        // Remove duplicates and sort by time (CRITICAL for lightweight-charts)
        if (transformedData.length === 0) {
            return transformedData;
        }

        const originalLength = transformedData.length;
        const timeMap = new Map();
        
        // Keep the last occurrence of each timestamp
        transformedData.forEach(item => {
            timeMap.set(item.time, item);
        });
        
        // Sort by time
        const dedupedData = Array.from(timeMap.values()).sort((a, b) => a.time - b.time);
        
        if (originalLength !== dedupedData.length) {
            console.log(`${seriesId}: Removed ${originalLength - dedupedData.length} duplicate timestamps`);
        }
        
        return dedupedData;
    }

    /**
     * Update existing series data
     */
    updateSeries(id, data) {
        const seriesInfo = this.seriesMap.get(id);
        if (!seriesInfo) {
            console.warn(`Series not found for update: ${id}`);
            return false;
        }

        try {
            const transformedData = this._transformData(
                data, 
                id, 
                seriesInfo.type, 
                seriesInfo.metadata.config?.color || '#2962FF'
            );
            
            if (transformedData.length > 0) {
                seriesInfo.series.setData(transformedData);
                console.log(`Updated series: ${id} with ${transformedData.length} points`);
                return true;
            }
            return false;
        } catch (error) {
            console.error(`Error updating series ${id}:`, error);
            return false;
        }
    }

    /**
     * Remove a series from the chart
     */
    removeSeries(id) {
        const seriesInfo = this.seriesMap.get(id);
        if (!seriesInfo) {
            return false;
        }

        try {
            this.chart.removeSeries(seriesInfo.series);
            this.seriesMap.delete(id);
            console.log(`Removed series: ${id}`);
            return true;
        } catch (error) {
            console.error(`Error removing series ${id}:`, error);
            return false;
        }
    }

    /**
     * Remove series by prefix (e.g., all indicators)
     */
    removeSeriesByPrefix(prefix) {
        const keysToRemove = Array.from(this.seriesMap.keys())
            .filter(key => key.startsWith(prefix));
        
        keysToRemove.forEach(key => this.removeSeries(key));
        console.log(`Removed ${keysToRemove.length} series with prefix: ${prefix}`);
    }

    /**
     * Check if a series exists
     */
    hasSeries(id) {
        return this.seriesMap.has(id);
    }

    /**
     * Get all series IDs
     */
    getAllSeriesIds() {
        return Array.from(this.seriesMap.keys());
    }

    /**
     * Clear all managed series
     */
    clearAll() {
        this.seriesMap.forEach((seriesInfo, id) => {
            try {
                this.chart.removeSeries(seriesInfo.series);
            } catch (error) {
                console.error(`Error removing series ${id}:`, error);
            }
        });
        this.seriesMap.clear();
        console.log('Cleared all series');
    }

    /**
     * Add series from API response format
     * Handles the standard API response with metadata and data
     */
    addFromApiResponse(id, apiResponse, additionalParams = {}) {
        const data = apiResponse.data || apiResponse;
        
        // For indicators, the id might be 'indicator_sma' but metadata key is 'sma'
        // Try to find the metadata by looking for the base id
        let metadata = apiResponse.metadata?.[id] || {};
        
        // If not found, try to find by removing 'indicator_' prefix
        if (Object.keys(metadata).length === 0 && id.startsWith('indicator_')) {
            const baseId = id.replace('indicator_', '');
            metadata = apiResponse.metadata?.[baseId] || {};
        }
        
        // If still not found, try to get the first metadata entry
        if (Object.keys(metadata).length === 0 && apiResponse.metadata) {
            const firstKey = Object.keys(apiResponse.metadata)[0];
            if (firstKey) {
                metadata = apiResponse.metadata[firstKey];
            }
        }
        
        return this.addSeries(id, data, metadata, additionalParams);
    }

    /**
     * Helper method to add strategy-related series
     * @param {string} strategyName - Name of the strategy
     * @param {Object} seriesData - Object containing series data { signals: [...], trendline: [...], etc. }
     * @param {Object} configs - Configuration for each series type
     * @returns {number} - Number of successfully added series
     */
    addStrategySeries(strategyName, seriesData, configs = {}) {
        let addedCount = 0;
        
        // Add signals as markers or line
        if (seriesData.signals && Array.isArray(seriesData.signals)) {
            const success = this.addSeries(
                `strategy_${strategyName}_signals`,
                seriesData.signals,
                {
                    seriesType: configs.signals?.type || 'line',
                    config: configs.signals || { color: '#ff9800', lineWidth: 1 }
                }
            );
            if (success) addedCount++;
        }

        // Add trend line
        if (seriesData.trendline && Array.isArray(seriesData.trendline)) {
            const success = this.addSeries(
                `strategy_${strategyName}_trend`,
                seriesData.trendline,
                {
                    seriesType: 'line',
                    config: configs.trendline || { color: '#9c27b0', lineWidth: 2, lineStyle: 2 }
                }
            );
            if (success) addedCount++;
        }

        // Add support/resistance levels
        if (seriesData.levels && Array.isArray(seriesData.levels)) {
            const success = this.addSeries(
                `strategy_${strategyName}_levels`,
                seriesData.levels,
                {
                    seriesType: 'line',
                    config: configs.levels || { color: '#2196f3', lineWidth: 1, lineStyle: 2 }
                }
            );
            if (success) addedCount++;
        }

        // Add any custom series
        Object.keys(seriesData).forEach(key => {
            if (!['signals', 'trendline', 'levels'].includes(key)) {
                const customData = seriesData[key];
                if (Array.isArray(customData) && customData.length > 0) {
                    const success = this.addSeries(
                        `strategy_${strategyName}_${key}`,
                        customData,
                        {
                            seriesType: configs[key]?.type || 'line',
                            config: configs[key] || { color: '#607d8b', lineWidth: 1 }
                        }
                    );
                    if (success) addedCount++;
                }
            }
        });

        console.log(`Added ${addedCount} series for strategy: ${strategyName}`);
        return addedCount;
    }

    /**
     * Remove all series for a specific strategy
     */
    removeStrategy(strategyName) {
        this.removeSeriesByPrefix(`strategy_${strategyName}_`);
    }
}

export default ChartSeriesManager;

