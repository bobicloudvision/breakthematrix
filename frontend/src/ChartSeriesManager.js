import { LineSeries, HistogramSeries, AreaSeries, BarSeries, BaselineSeries, CandlestickSeries, LineStyle } from 'lightweight-charts';

/**
 * Centralized manager for adding and managing chart series, boxes, lines, arrows, markers, and fills
 * Handles indicators, strategy data, and shape primitives
 * 
 * Example Usage:
 * 
 * // For Indicators (with automatic shapes support):
 * // API returns: { metadata: {...}, series: { sma: [...] }, shapes: {...} }
 * const response = await fetch('/api/indicators/sma/historical', {...});
 * const apiResponse = await response.json();
 * seriesManager.addFromApiResponse('indicator_sma', apiResponse, { color: '#2962FF' }, LinePrimitive);
 * 
 * // For Strategies:
 * seriesManager.addSeries('strategy_signals', signalsData, {
 *   seriesType: 'line',
 *   config: { color: '#ff9800', lineWidth: 1 }
 * });
 * 
 * // For Markers (from indicator data with conditions):
 * seriesManager.addSeries('bullish_signal', data, {
 *   seriesType: 'marker',
 *   displayName: 'Bullish Signal',
 *   config: {
 *     shape: 'circle',
 *     color: '#26a69a',
 *     size: 4,
 *     position: 'below',
 *     priceField: 'trailingStop',
 *     conditionField: 'signal',
 *     conditionValue: 1
 *   }
 * });
 * 
 * // For Boxes:
 * seriesManager.addBoxes(boxes, BoxPrimitive);
 * seriesManager.removeAllBoxes();
 * 
 * // For Lines/Trendlines:
 * seriesManager.addLines(lines, LinePrimitive);
 * seriesManager.removeAllLines();
 * 
 * // For Arrows:
 * seriesManager.addArrows(arrows, ArrowPrimitive);
 * seriesManager.removeAllArrows();
 * 
 * // For Marker Shapes:
 * seriesManager.addMarkerShapes(markerShapes, MarkerPrimitive);
 * seriesManager.removeAllMarkerShapes();
 * 
 * // For Series Markers (v5 API):
 * seriesManager.addShapeMarkers('myMarkers', markers);
 * seriesManager.removeShapeMarkers('myMarkers');
 * 
 * // For Fill Between (price and indicator line):
 * seriesManager.addFillBetween('fill_trailing_stop', trailingStopData, {
 *   colorMode: 'dynamic',
 *   upFillColor: 'rgba(76, 175, 80, 0.15)',
 *   downFillColor: 'rgba(239, 83, 80, 0.15)'
 * }, FillBetweenPrimitive);
 * seriesManager.removeFillBetween('fill_trailing_stop');
 * 
 * // For Shapes (markers, lines, arrows, marker shapes together):
 * seriesManager.addShapesFromApiResponse('indicator_srbreaks', shapes, LinePrimitive, ArrowPrimitive, MarkerPrimitive);
 * seriesManager.clearAllShapes();
 * 
 * // Remove by prefix:
 * seriesManager.removeSeriesByPrefix('indicator_'); // Remove all indicators
 * seriesManager.removeSeriesByPrefix('strategy_'); // Remove all strategy series
 */
export class ChartSeriesManager {
    constructor(chart, mainSeries = null, chartData = null) {
        this.chart = chart;
        this.mainSeries = mainSeries;
        this.chartData = chartData;
        this.seriesMap = new Map(); // Track all series by key
        this.boxPrimitives = []; // Track box primitives
        this.linePrimitives = []; // Track line primitives
        this.arrowPrimitives = []; // Track arrow primitives
        this.markerPrimitives = []; // Track marker primitives (different from markerSets)
        this.fillBetweenPrimitives = []; // Track fill-between primitives
        this.markerSets = new Map(); // Track marker sets by ID
        this.markerPlugin = null; // Track marker plugin for v5 API
    }

    /**
     * Update main series reference (e.g., when candlestick series is created)
     */
    setMainSeries(series) {
        this.mainSeries = series;
    }

    /**
     * Update chart data reference (needed for box logical coordinates)
     */
    setChartData(data) {
        this.chartData = data;
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
            
            // Handle marker series type differently
            if (seriesType.toLowerCase() === 'marker') {
                return this._addMarkerSeries(id, data, metadata, params);
            }
            
            // Get color and other configs
            const color = config.color || params.color || '#2962FF';
            const lineWidth = config.lineWidth || params.lineWidth || 2;
            const title = config.title || metadata.displayName || id.toUpperCase();

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
                    priceLineVisible: true,
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
                    // Value validation - skip zero values and NaN
                    return !isNaN(point.value) && point.value !== 0;
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
     * Merge two series data arrays by time, combining their values
     * Used for markers that need data from multiple series (e.g., signal + trailingStop)
     * @private
     */
    _mergeSeriesDataByTime(array1, array2, field1Name, field2Name) {
        // Create a map of time -> merged data point
        const timeMap = new Map();
        
        // Add data from first array
        array1.forEach(point => {
            const time = point.time || point.timestamp || point.t;
            if (time !== undefined) {
                const value = point.value !== undefined ? point.value : point.val;
                timeMap.set(time, {
                    time: time,
                    [field1Name]: value
                });
            }
        });
        
        // Merge data from second array
        array2.forEach(point => {
            const time = point.time || point.timestamp || point.t;
            if (time !== undefined) {
                const value = point.value !== undefined ? point.value : point.val;
                const existing = timeMap.get(time);
                if (existing) {
                    existing[field2Name] = value;
                } else {
                    timeMap.set(time, {
                        time: time,
                        [field2Name]: value
                    });
                }
            }
        });
        
        // Convert to array and filter out incomplete points
        const merged = Array.from(timeMap.values())
            .filter(point => point[field1Name] !== undefined && point[field2Name] !== undefined)
            .sort((a, b) => a.time - b.time);
        
        return merged;
    }

    /**
     * Add a marker series (converts data to markers based on conditions)
     * @private
     */
    _addMarkerSeries(id, data, metadata, params) {
        const config = metadata.config || {};
        
        console.log(`🎯 _addMarkerSeries called for ${id}:`, {
            dataLength: data.length,
            config: config,
            samplePoint: data[0]
        });
        
        // Show detailed data structure
        if (data[0]) {
            const keys = Object.keys(data[0]);
            console.log('✨ Sample merged data point:', data[0]);
            console.log('Keys:', keys);
            console.log('Looking for conditionField:', config.conditionField, '→', data[0][config.conditionField]);
            console.log('Looking for priceField:', config.priceField, '→', data[0][config.priceField]);
        }
        
        // Extract marker configuration
        const shape = config.shape || 'circle';
        const color = config.color || params.color || '#2196F3';
        const size = config.size || 1;
        const position = config.position || 'inBar';
        const priceField = config.priceField || 'value'; // Field to use for marker price
        const conditionField = config.conditionField; // Field to check for condition
        const conditionValue = config.conditionValue; // Value to match
        const text = config.text || '';
        
        console.log(`Marker config: shape=${shape}, color=${color}, size=${size}, position=${position}`);
        console.log(`Data filtering: priceField=${priceField}, conditionField=${conditionField}, conditionValue=${conditionValue}`);
        
        // Filter and transform data to markers
        let debugCount = 0;
        const markers = data
            .map((point, index) => {
                if (typeof point !== 'object') return null;
                
                // Extract time
                const time = point.time || point.timestamp || point.t;
                if (time === undefined) return null;
                
                // Check condition if specified
                if (conditionField !== undefined && conditionValue !== undefined) {
                    let conditionFieldValue;
                    
                    // Check multiple possible locations for the condition field
                    if (point[conditionField] !== undefined) {
                        // Direct property (from merged data)
                        conditionFieldValue = point[conditionField];
                    } else if (point.values && typeof point.values === 'object') {
                        conditionFieldValue = point.values[conditionField];
                    } else if (point.fills && typeof point.fills === 'object' && !Array.isArray(point.fills)) {
                        conditionFieldValue = point.fills[conditionField];
                    }
                    
                    // Debug first few points
                    if (index < 3) {
                        console.log(`Point ${index} condition check:`, {
                            conditionField,
                            conditionFieldValue,
                            conditionValue,
                            matches: conditionFieldValue === conditionValue,
                            hasValues: !!point.values,
                            hasFills: !!point.fills,
                            fillsKeys: point.fills ? Object.keys(point.fills) : 'none',
                            valuesKeys: point.values ? Object.keys(point.values) : 'none'
                        });
                    }
                    
                    // Skip if condition not met
                    if (conditionFieldValue !== conditionValue) {
                        return null;
                    }
                    
                    debugCount++;
                }
                
                // Extract price for marker placement
                let price;
                if (point[priceField] !== undefined) {
                    // Direct property (from merged data)
                    price = point[priceField];
                } else if (point.values && typeof point.values === 'object') {
                    price = point.values[priceField];
                } else if (point.fills && typeof point.fills === 'object' && !Array.isArray(point.fills)) {
                    price = point.fills[priceField];
                } else {
                    price = point.value || point.close;
                }
                
                if (price === undefined || price === null) {
                    if (index < 3) {
                        console.log(`Point ${index} price extraction failed:`, {
                            priceField,
                            price,
                            hasFills: !!point.fills,
                            fillsKeys: point.fills ? Object.keys(point.fills) : 'none'
                        });
                    }
                    return null;
                }
                
                return {
                    time: time,
                    price: parseFloat(price),
                    shape: shape,
                    color: color,
                    size: size,
                    position: position,
                    text: text
                };
            })
            .filter(marker => marker !== null);
        
        console.log(`Debug: ${debugCount} points matched the condition out of ${data.length} total points`);
        
        console.log(`Filtered markers result: ${markers.length} markers generated from ${data.length} data points`);
        if (markers.length > 0) {
            console.log('Sample marker:', markers[0]);
        }
        
        if (markers.length === 0) {
            console.warn(`❌ No markers generated for series: ${id} (no data matched conditions)`);
            return false;
        }
        
        // Use addShapeMarkers to add the markers
        const success = this.addShapeMarkers(id, markers);
        
        if (success) {
            // Store in seriesMap for tracking (even though it's not a real series)
            this.seriesMap.set(id, {
                series: null, // Markers don't have a series object
                type: 'marker',
                metadata: metadata
            });
            
            console.log(`✅ Added ${markers.length} markers for series: ${id}`);
        }
        
        return success;
    }

    /**
     * Update existing series data
     */
    updateSeries(id, data) {
        const seriesInfo = this.seriesMap.get(id);
        
        // Check if this is a marker set (not a regular series)
        if (!seriesInfo && this.markerSets.has(id)) {
            // This is a marker series, re-add it
            const metadata = { seriesType: 'marker' };
            // Try to preserve original metadata if available
            return this._addMarkerSeries(id, data, metadata, {});
        }
        
        if (!seriesInfo) {
            console.warn(`Series not found for update: ${id}`);
            return false;
        }

        try {
            // Handle marker series type
            if (seriesInfo.type === 'marker') {
                return this._addMarkerSeries(id, data, seriesInfo.metadata, {});
            }
            
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
            // Handle marker series
            if (seriesInfo.type === 'marker') {
                this.removeShapeMarkers(id);
                this.seriesMap.delete(id);
                console.log(`Removed marker series: ${id}`);
                return true;
            }
            
            // Regular series
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
                if (seriesInfo.type === 'marker') {
                    this.removeShapeMarkers(id);
                } else {
                    this.chart.removeSeries(seriesInfo.series);
                }
            } catch (error) {
                console.error(`Error removing series ${id}:`, error);
            }
        });
        this.seriesMap.clear();
        console.log('Cleared all series');
    }

    /**
     * Add series from API response format
     * Handles the standard API response with metadata, series, and shapes
     * For indicators with multiple series, adds all series from metadata
     * Also handles shapes (markers and lines) if present
     * @param {string} id - Unique identifier for the indicator
     * @param {Object} apiResponse - API response with metadata, series, and optionally shapes
     * @param {Object} additionalParams - Additional parameters
     * @param {Object} LinePrimitiveClass - Optional LinePrimitive class for rendering lines
     * @returns {boolean|Object} - Success status or object with series/shapes success status
     */
    addFromApiResponse(id, apiResponse, additionalParams = {}, LinePrimitiveClass = null) {
        let seriesAdded = false;
        let shapesAdded = { markers: false, lines: false };
        
        console.log(`📊 addFromApiResponse for ${id}:`, {
            metadataKeys: apiResponse.metadata ? Object.keys(apiResponse.metadata) : [],
            seriesKeys: apiResponse.series ? Object.keys(apiResponse.series) : [],
            hasShapes: !!apiResponse.shapes
        });
        
        // Check if this is a multi-series indicator (has multiple entries in metadata)
        if (apiResponse.metadata && typeof apiResponse.metadata === 'object') {
            const metadataKeys = Object.keys(apiResponse.metadata);
            
            if (metadataKeys.length > 1) {
                // Multi-series indicator - add each series separately
                let successCount = 0;
                
                // Get all available series data keys
                const seriesDataKeys = apiResponse.series ? Object.keys(apiResponse.series) : [];
                console.log(`Available series data keys: [${seriesDataKeys.join(', ')}]`);
                
                metadataKeys.forEach(seriesKey => {
                    const seriesMetadata = apiResponse.metadata[seriesKey];
                    
                    // For marker series, try to use the source data specified in metadata
                    // or fall back to the first available series data
                    let seriesData = [];
                    
                    if (seriesMetadata?.seriesType === 'marker') {
                        // For markers, we need data with ALL fields (e.g., both signal and trailingStop)
                        // Backend often sends separate arrays, so we need to merge them
                        const priceField = seriesMetadata.config?.priceField;
                        const conditionField = seriesMetadata.config?.conditionField;
                        
                        // Try to get arrays for both fields
                        const priceData = priceField ? apiResponse.series?.[priceField] : null;
                        const conditionData = conditionField ? apiResponse.series?.[conditionField] : null;
                        
                        if (priceData && conditionData) {
                            // Merge the two arrays by time
                            console.log(`🔗 Merging ${priceField} and ${conditionField} arrays for marker ${seriesKey}`);
                            seriesData = this._mergeSeriesDataByTime(priceData, conditionData, priceField, conditionField);
                            console.log(`✅ Merged ${seriesData.length} data points for marker ${seriesKey}`);
                        } else {
                            console.warn(`⚠️ Could not find both ${priceField} and ${conditionField} arrays for marker ${seriesKey}`);
                            console.log(`Available keys:`, seriesDataKeys);
                        }
                    } else {
                        // For regular series, try to match by series key or use first available
                        seriesData = apiResponse.series?.[seriesKey] || [];
                        if (seriesData.length === 0 && seriesDataKeys.length > 0) {
                            seriesData = apiResponse.series[seriesDataKeys[0]];
                        }
                    }
                    
                    const seriesId = `${id}_${seriesKey}`;
                    
                    console.log(`Adding series ${seriesId}:`, {
                        type: seriesMetadata?.seriesType,
                        dataLength: seriesData.length,
                        hasCondition: !!(seriesMetadata?.config?.conditionField)
                    });
                    
                    const success = this.addSeries(seriesId, seriesData, seriesMetadata, additionalParams);
                    if (success) successCount++;
                });
                
                seriesAdded = successCount > 0;
            } else {
                // Single series indicator
                // Get data from series object
                let data = [];
                if (apiResponse.series && typeof apiResponse.series === 'object') {
                    // Get the first series data from the series object
                    const seriesKeys = Object.keys(apiResponse.series);
                    if (seriesKeys.length > 0) {
                        data = apiResponse.series[seriesKeys[0]];
                    }
                }
                
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
                
                seriesAdded = this.addSeries(id, data, metadata, additionalParams);
            }
        } else {
            // No metadata, get data from series object
            let data = [];
            if (apiResponse.series && typeof apiResponse.series === 'object') {
                const seriesKeys = Object.keys(apiResponse.series);
                if (seriesKeys.length > 0) {
                    data = apiResponse.series[seriesKeys[0]];
                }
            }
            seriesAdded = this.addSeries(id, data, {}, additionalParams);
        }
        
        // Handle shapes if present
        if (apiResponse.shapes && LinePrimitiveClass) {
            shapesAdded = this.addShapesFromApiResponse(id, apiResponse.shapes, LinePrimitiveClass);
        } else if (apiResponse.shapes && !LinePrimitiveClass) {
            console.warn('Shapes found in API response but LinePrimitiveClass not provided');
        }
        
        // Return combined result if shapes were found, otherwise just series status
        if (apiResponse.shapes) {
            return {
                series: seriesAdded,
                shapes: shapesAdded
            };
        }
        
        return seriesAdded;
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

    /**
     * Add boxes/rectangles to the chart using primitives
     * Requires BoxPrimitive class to be imported
     * @param {Array} boxes - Array of box objects with {time1, time2, price1, price2, ...styling}
     * @param {Object} BoxPrimitiveClass - The BoxPrimitive class constructor
     * @returns {boolean} - Success status
     */
    addBoxes(boxes, BoxPrimitiveClass) {
        if (!this.mainSeries) {
            console.error('Cannot add boxes: mainSeries not set');
            return false;
        }
        
        if (!boxes || !Array.isArray(boxes) || boxes.length === 0) {
            return false;
        }

        try {
            // Create new box primitive with all boxes
            const boxPrimitive = new BoxPrimitiveClass(
                this.chart, 
                this.mainSeries, 
                boxes, 
                this.chartData
            );
            this.mainSeries.attachPrimitive(boxPrimitive);
            this.boxPrimitives.push(boxPrimitive);
            
            console.log(`✅ Box primitive attached with ${boxes.length} boxes`);
            
            // Request animation frame to ensure chart is fully rendered
            requestAnimationFrame(() => {
                boxPrimitive.updateAllViews();
                // Force chart redraw
                this.chart.timeScale().applyOptions({});
            });

            return true;
        } catch (error) {
            console.error('Error creating box primitive:', error);
            return false;
        }
    }

    /**
     * Remove all boxes from the chart
     */
    removeAllBoxes() {
        if (!this.mainSeries) {
            return;
        }

        this.boxPrimitives.forEach(primitive => {
            try {
                this.mainSeries.detachPrimitive(primitive);
            } catch (e) {
                console.warn('Error detaching box primitive:', e);
            }
        });
        
        this.boxPrimitives = [];
        console.log('All box primitives removed');
    }

    /**
     * Update boxes with new data
     * @param {Array} boxes - New array of boxes
     * @param {Object} BoxPrimitiveClass - The BoxPrimitive class constructor
     * @returns {boolean} - Success status
     */
    updateBoxes(boxes, BoxPrimitiveClass) {
        this.removeAllBoxes();
        return this.addBoxes(boxes, BoxPrimitiveClass);
    }

    /**
     * Add lines/trendlines to the chart using primitives
     * Requires LinePrimitive class to be imported
     * @param {Array} lines - Array of line objects with {time1, time2, price1, price2, color, lineWidth, lineStyle, label}
     * @param {Object} LinePrimitiveClass - The LinePrimitive class constructor
     * @returns {boolean} - Success status
     */
    addLines(lines, LinePrimitiveClass) {
        if (!this.mainSeries) {
            console.error('Cannot add lines: mainSeries not set');
            return false;
        }
        
        if (!lines || !Array.isArray(lines) || lines.length === 0) {
            return false;
        }

        try {
            // Create new line primitive with all lines
            const linePrimitive = new LinePrimitiveClass(
                this.chart, 
                this.mainSeries, 
                lines, 
                this.chartData
            );
            this.mainSeries.attachPrimitive(linePrimitive);
            this.linePrimitives.push(linePrimitive);
            
            console.log(`✅ Line primitive attached with ${lines.length} lines`);
            
            // Request animation frame to ensure chart is fully rendered
            requestAnimationFrame(() => {
                linePrimitive.updateAllViews();
                // Force chart redraw
                this.chart.timeScale().applyOptions({});
            });

            return true;
        } catch (error) {
            console.error('Error creating line primitive:', error);
            return false;
        }
    }

    /**
     * Remove all lines from the chart
     */
    removeAllLines() {
        if (!this.mainSeries) {
            return;
        }

        this.linePrimitives.forEach(primitive => {
            try {
                this.mainSeries.detachPrimitive(primitive);
            } catch (e) {
                console.warn('Error detaching line primitive:', e);
            }
        });
        
        this.linePrimitives = [];
        console.log('All line primitives removed');
    }

    /**
     * Update lines with new data
     * @param {Array} lines - New array of lines
     * @param {Object} LinePrimitiveClass - The LinePrimitive class constructor
     * @returns {boolean} - Success status
     */
    updateLines(lines, LinePrimitiveClass) {
        this.removeAllLines();
        return this.addLines(lines, LinePrimitiveClass);
    }

    /**
     * Add arrows to the chart using primitives
     * Requires ArrowPrimitive class to be imported
     * @param {Array} arrows - Array of arrow objects with {time, price, direction, color, size, text}
     * @param {Object} ArrowPrimitiveClass - The ArrowPrimitive class constructor
     * @returns {boolean} - Success status
     */
    addArrows(arrows, ArrowPrimitiveClass) {
        if (!this.mainSeries) {
            console.error('Cannot add arrows: mainSeries not set');
            return false;
        }
        
        if (!arrows || !Array.isArray(arrows) || arrows.length === 0) {
            return false;
        }

        try {
            // Create new arrow primitive with all arrows
            const arrowPrimitive = new ArrowPrimitiveClass(
                this.chart, 
                this.mainSeries, 
                arrows, 
                this.chartData
            );
            
            // Attach to main series
            this.mainSeries.attachPrimitive(arrowPrimitive);
            
            // Track primitive for cleanup
            this.arrowPrimitives.push(arrowPrimitive);
            
            console.log(`Added ${arrows.length} arrows to chart`);
            return true;
        } catch (e) {
            console.error('Error adding arrows:', e);
            return false;
        }
    }

    /**
     * Remove all arrow primitives
     */
    removeAllArrows() {
        if (!this.mainSeries) {
            return;
        }

        this.arrowPrimitives.forEach(primitive => {
            try {
                this.mainSeries.detachPrimitive(primitive);
            } catch (e) {
                console.warn('Error detaching arrow primitive:', e);
            }
        });
        
        this.arrowPrimitives = [];
        console.log('All arrow primitives removed');
    }

    /**
     * Update arrows with new data
     * @param {Array} arrows - New array of arrows
     * @param {Object} ArrowPrimitiveClass - The ArrowPrimitive class constructor
     * @returns {boolean} - Success status
     */
    updateArrows(arrows, ArrowPrimitiveClass) {
        this.removeAllArrows();
        return this.addArrows(arrows, ArrowPrimitiveClass);
    }

    /**
     * Add custom marker shapes to the chart using primitives
     * These are rendered marker shapes (different from series markers)
     * Requires MarkerPrimitive class to be imported
     * @param {Array} markers - Array of marker objects with {time, price, shape, color, size, text}
     * @param {Object} MarkerPrimitiveClass - The MarkerPrimitive class constructor
     * @returns {boolean} - Success status
     */
    addMarkerShapes(markers, MarkerPrimitiveClass) {
        if (!this.mainSeries) {
            console.error('Cannot add marker shapes: mainSeries not set');
            return false;
        }
        
        if (!markers || !Array.isArray(markers) || markers.length === 0) {
            return false;
        }

        try {
            // Create new marker primitive with all markers
            const markerPrimitive = new MarkerPrimitiveClass(
                this.chart, 
                this.mainSeries, 
                markers, 
                this.chartData
            );
            
            // Attach to main series
            this.mainSeries.attachPrimitive(markerPrimitive);
            
            // Track primitive for cleanup
            this.markerPrimitives.push(markerPrimitive);
            
            console.log(`Added ${markers.length} marker shapes to chart`);
            return true;
        } catch (e) {
            console.error('Error adding marker shapes:', e);
            return false;
        }
    }

    /**
     * Remove all marker shape primitives
     */
    removeAllMarkerShapes() {
        if (!this.mainSeries) {
            return;
        }

        this.markerPrimitives.forEach(primitive => {
            try {
                this.mainSeries.detachPrimitive(primitive);
            } catch (e) {
                console.warn('Error detaching marker primitive:', e);
            }
        });
        
        this.markerPrimitives = [];
        console.log('All marker shape primitives removed');
    }

    /**
     * Update marker shapes with new data
     * @param {Array} markers - New array of markers
     * @param {Object} MarkerPrimitiveClass - The MarkerPrimitive class constructor
     * @returns {boolean} - Success status
     */
    updateMarkerShapes(markers, MarkerPrimitiveClass) {
        this.removeAllMarkerShapes();
        return this.addMarkerShapes(markers, MarkerPrimitiveClass);
    }

    /**
     * Add markers from shapes array
     * These are different from series markers - they're placed at specific price/time coordinates
     * @param {string} id - Unique identifier for this marker set
     * @param {Array} markers - Array of marker objects with {shape, color, size, price, time, position, text}
     * @returns {boolean} - Success status
     */
    addShapeMarkers(id, markers) {
        if (!this.mainSeries) {
            console.error('Cannot add markers: mainSeries not set');
            return false;
        }
        
        if (!markers || !Array.isArray(markers) || markers.length === 0) {
            return false;
        }

        try {
            // Transform markers to lightweight-charts format
            const chartMarkers = markers.map(marker => {
                // Map shape to lightweight-charts marker shape
                let shape = 'circle';
                if (marker.shape === 'square') {
                    shape = 'square';
                } else if (marker.shape === 'triangle' || marker.shape === 'arrow') {
                    // Convert position to determine arrow direction
                    if (marker.position === 'above' || marker.position === 'aboveBar') {
                        shape = 'arrowDown';
                    } else {
                        shape = 'arrowUp';
                    }
                }

                // Map position
                let position = 'inBar';
                if (marker.position === 'above' || marker.position === 'aboveBar') {
                    position = 'aboveBar';
                } else if (marker.position === 'below' || marker.position === 'belowBar') {
                    position = 'belowBar';
                }

                return {
                    time: marker.time,
                    position: position,
                    color: marker.color || '#2196F3',
                    shape: shape,
                    text: marker.text || '',
                    size: marker.size || 1
                };
            }).sort((a, b) => a.time - b.time);

            // Store markers for this ID
            this.markerSets.set(id, chartMarkers);

            // Merge all marker sets and apply to series
            this._applyAllMarkers();

            console.log(`✅ Added ${chartMarkers.length} markers with ID: ${id}`);
            return true;
        } catch (error) {
            console.error('Error adding shape markers:', error);
            return false;
        }
    }

    /**
     * Remove markers by ID
     */
    removeShapeMarkers(id) {
        if (!this.markerSets.has(id)) {
            return false;
        }

        this.markerSets.delete(id);
        this._applyAllMarkers();
        console.log(`Removed marker set: ${id}`);
        return true;
    }

    /**
     * Remove all shape markers
     */
    removeAllShapeMarkers() {
        this.markerSets.clear();
        if (this.markerPlugin) {
            this.markerPlugin.setMarkers([]);
        }
        console.log('All shape markers removed');
    }

    /**
     * Apply all marker sets to the main series
     * Uses createSeriesMarkers plugin for lightweight-charts v5
     * @private
     */
    _applyAllMarkers() {
        if (!this.mainSeries) {
            console.warn('⚠️ Cannot apply markers: mainSeries not set');
            return;
        }

        // Merge all marker sets
        const allMarkers = [];
        this.markerSets.forEach((markers, id) => {
            console.log(`Merging marker set ${id}: ${markers.length} markers`);
            allMarkers.push(...markers);
        });

        // Sort by time
        allMarkers.sort((a, b) => a.time - b.time);
        
        console.log(`📍 Applying ${allMarkers.length} total markers to chart`);
        if (allMarkers.length > 0) {
            console.log('First marker:', allMarkers[0]);
            console.log('Last marker:', allMarkers[allMarkers.length - 1]);
        }

        // Apply to series using v5 plugin API
        // Note: createSeriesMarkers needs to be imported in Chart.jsx and passed to the manager
        if (typeof window !== 'undefined' && window.createSeriesMarkers) {
            console.log('✅ createSeriesMarkers is available');
            if (!this.markerPlugin && allMarkers.length > 0) {
                // Create marker plugin if it doesn't exist
                console.log('Creating new marker plugin...');
                this.markerPlugin = window.createSeriesMarkers(this.mainSeries, allMarkers);
                console.log('Marker plugin created:', !!this.markerPlugin);
            } else if (this.markerPlugin) {
                // Update existing plugin
                console.log('Updating existing marker plugin...');
                this.markerPlugin.setMarkers(allMarkers);
                console.log('Marker plugin updated');
            }
        } else {
            console.error('❌ createSeriesMarkers not available. Markers will not be displayed. Please ensure lightweight-charts v5+ is installed.');
            console.log('window.createSeriesMarkers:', typeof window !== 'undefined' ? window.createSeriesMarkers : 'window undefined');
        }
    }

    /**
     * Add shapes from API response (markers and lines)
     * @param {string} id - Unique identifier for these shapes
     * @param {Object} shapes - Shapes object with markers and lines arrays
     * @param {Object} LinePrimitiveClass - The LinePrimitive class constructor
     * @returns {Object} - Success status for markers and lines
     */
    addShapesFromApiResponse(id, shapes, LinePrimitiveClass, ArrowPrimitiveClass = null, MarkerPrimitiveClass = null) {
        const result = {
            markers: false,
            lines: false,
            arrows: false,
            markerShapes: false
        };

        // Add markers if present
        if (shapes.markers && Array.isArray(shapes.markers) && shapes.markers.length > 0) {
            result.markers = this.addShapeMarkers(`${id}_markers`, shapes.markers);
        }

        // Add lines if present
        if (shapes.lines && Array.isArray(shapes.lines) && shapes.lines.length > 0) {
            result.lines = this.addLines(shapes.lines, LinePrimitiveClass);
        }

        // Add arrows if present (requires ArrowPrimitiveClass)
        if (shapes.arrows && Array.isArray(shapes.arrows) && shapes.arrows.length > 0) {
            if (ArrowPrimitiveClass) {
                result.arrows = this.addArrows(shapes.arrows, ArrowPrimitiveClass);
            } else {
                console.warn('Arrows found in API response but ArrowPrimitiveClass not provided');
            }
        }

        // Add marker shapes if present (requires MarkerPrimitiveClass)
        if (shapes.markerShapes && Array.isArray(shapes.markerShapes) && shapes.markerShapes.length > 0) {
            if (MarkerPrimitiveClass) {
                result.markerShapes = this.addMarkerShapes(shapes.markerShapes, MarkerPrimitiveClass);
            } else {
                console.warn('Marker shapes found in API response but MarkerPrimitiveClass not provided');
            }
        }

        return result;
    }

    /**
     * Remove shapes by ID (both markers and lines with that prefix)
     */
    removeShapes(id) {
        this.removeShapeMarkers(`${id}_markers`);
        // Note: Lines are currently all removed together via removeAllLines()
        // In the future, we could track lines by ID for granular removal
    }

    /**
     * Clear all shapes (markers, lines, arrows, marker shapes, boxes, and fill-between)
     */
    clearAllShapes() {
        this.removeAllShapeMarkers();
        this.removeAllLines();
        this.removeAllArrows();
        this.removeAllMarkerShapes();
        this.removeAllBoxes();
        this.removeAllFillBetween();
        console.log('Cleared all shapes');
    }

    /**
     * Set the createSeriesMarkers function for v5 API
     * This should be called after initialization with the imported function from lightweight-charts
     * @param {Function} createSeriesMarkersFunc - The createSeriesMarkers function from lightweight-charts
     */
    setCreateSeriesMarkers(createSeriesMarkersFunc) {
        if (typeof window !== 'undefined') {
            window.createSeriesMarkers = createSeriesMarkersFunc;
        }
    }

    /**
     * Add fill between two series or horizontal lines
     * Requires FillBetweenPrimitive class to be imported
     * 
     * Supports three modes:
     * 1. Fill between two series: { mode: 'series', source1: 'close', source2: indicatorData, color: '...' }
     * 2. Fill between hlines: { mode: 'hline', hline1: 50000, hline2: 51000, color: '...' }
     * 3. Gradient fill: { mode: 'series', source1: '...', source2: '...', colorMode: 'gradient', topColor: '...', bottomColor: '...' }
     * 
     * @param {string} id - Unique identifier for this fill
     * @param {Object} options - Fill options
     * @param {Object} FillBetweenPrimitiveClass - The FillBetweenPrimitive class constructor
     * @returns {boolean} - Success status
     */
    addFillBetween(id, options, FillBetweenPrimitiveClass) {
        if (!this.mainSeries) {
            console.error('Cannot add fill between: mainSeries not set');
            return false;
        }
        
        if (!options) {
            console.warn('No options provided for fill between');
            return false;
        }

        try {
            // Create new fill-between primitive
            const fillPrimitive = new FillBetweenPrimitiveClass(
                this.chart, 
                this.mainSeries, 
                options
            );
            this.mainSeries.attachPrimitive(fillPrimitive);
            this.fillBetweenPrimitives.push({ id, primitive: fillPrimitive });
            
            const mode = options.mode || 'series';
            console.log(`✅ Fill-between primitive attached for ${id} (mode: ${mode})`);
            
            // Request animation frame to ensure chart is fully rendered
            requestAnimationFrame(() => {
                fillPrimitive.updateAllViews();
                // Force chart redraw
                this.chart.timeScale().applyOptions({});
            });

            return true;
        } catch (error) {
            console.error('Error creating fill-between primitive:', error);
            return false;
        }
    }

    /**
     * Remove a specific fill-between primitive by ID
     */
    removeFillBetween(id) {
        if (!this.mainSeries) {
            return false;
        }

        const index = this.fillBetweenPrimitives.findIndex(item => item.id === id);
        if (index === -1) {
            return false;
        }

        try {
            const { primitive } = this.fillBetweenPrimitives[index];
            this.mainSeries.detachPrimitive(primitive);
            this.fillBetweenPrimitives.splice(index, 1);
            console.log(`Removed fill-between primitive: ${id}`);
            return true;
        } catch (e) {
            console.warn('Error detaching fill-between primitive:', e);
            return false;
        }
    }

    /**
     * Remove all fill-between primitives
     */
    removeAllFillBetween() {
        if (!this.mainSeries) {
            return;
        }

        this.fillBetweenPrimitives.forEach(({ id, primitive }) => {
            try {
                this.mainSeries.detachPrimitive(primitive);
            } catch (e) {
                console.warn(`Error detaching fill-between primitive ${id}:`, e);
            }
        });
        
        this.fillBetweenPrimitives = [];
        console.log('All fill-between primitives removed');
    }

    /**
     * Update a fill-between primitive with new data or options
     */
    updateFillBetween(id, options) {
        const item = this.fillBetweenPrimitives.find(item => item.id === id);
        if (!item) {
            console.warn(`Fill-between primitive not found: ${id}`);
            return false;
        }

        try {
            // Update source data if provided
            if (options.source1 && Array.isArray(options.source1) || 
                options.source2 && Array.isArray(options.source2)) {
                item.primitive.updateSourceData(options.source1, options.source2);
            }
            
            // Update all options
            item.primitive.updateOptions(options);
            return true;
        } catch (error) {
            console.error(`Error updating fill-between primitive ${id}:`, error);
            return false;
        }
    }
}

export default ChartSeriesManager;

