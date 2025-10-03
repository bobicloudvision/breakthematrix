import { 
    createChart, 
    ColorType, 
    LineStyle,
    CandlestickSeries,
    LineSeries,
    AreaSeries,
    BarSeries,
    BaselineSeries,
    HistogramSeries,
    createSeriesMarkers
} from 'lightweight-charts';
import React, { useEffect, useRef, useState } from 'react';
import { BoxPrimitive } from './BoxPrimitive';
import { LinePrimitive } from './LinePrimitive';
import { ChartSeriesManager } from './ChartSeriesManager';

// Global variable for default zoom level (last N candles)
if (typeof window !== 'undefined' && !window.BTM_DEFAULT_ZOOM_CANDLES) {
    window.BTM_DEFAULT_ZOOM_CANDLES = 250;
}

export const ChartComponent = props => {
    const {
        data,
        activeStrategies = [],
        strategyData = {},
        loading = false,
        error = null,
        realCount = 0,
        enabledIndicators = [],
        colors: {
            backgroundColor = 'transparent',
            textColor = '#fff',
            upColor = '#4caf50',
            downColor = '#e91e63',
            wickUpColor = '#4caf50',
            wickDownColor = '#e91e63',
            borderUpColor = '#4caf50',
            borderDownColor = '#e91e63',
        } = {},
    } = props;

    const chartContainerRef = useRef();
    const chartRef = useRef();
    const seriesRef = useRef();
    const seriesManagerRef = useRef(null); // Centralized series and box manager
    const markerPluginRef = useRef(null); // v5 marker plugin instance
    const isAddingIndicatorsRef = useRef(false);
    const isMountedRef = useRef(true);

    useEffect(
        () => {
            if (!chartContainerRef.current) return;

            const handleResize = () => {
                if (chartContainerRef.current && chartRef.current) {
                    chartRef.current.applyOptions({ width: chartContainerRef.current.clientWidth });
                }
            };

            const chart = createChart(chartContainerRef.current, {
                layout: {
                    background: { type: ColorType.Solid, color: backgroundColor },
                    textColor: textColor || '#d1d5db',
                },
                width: chartContainerRef.current.clientWidth,
                height: chartContainerRef.current.clientHeight || 200,
                grid: { 
                    vertLines: { color: 'rgba(255, 255, 255, 0.1)' },
                    horzLines: { color: 'rgba(255, 255, 255, 0.1)' },
                },
                timeScale: {
                    rightOffset: 12,
                    barSpacing: 3,
                    fixLeftEdge: false,
                    fixRightEdge: false,
                    lockVisibleTimeRangeOnResize: true,
                    rightBarStaysOnScroll: true,
                    borderVisible: false,
                    borderColor: '#fff000',
                    visible: true,
                    timeVisible: true,
                    secondsVisible: false,
                },
            });

            const candleSeries = chart.addSeries(CandlestickSeries, {
                upColor,
                downColor,
                wickUpColor,
                wickDownColor,
                borderUpColor,
                borderDownColor,
            });

            // Store references
            chartRef.current = chart;
            seriesRef.current = candleSeries;
            
            // Initialize series manager with chart, main series, and data
            seriesManagerRef.current = new ChartSeriesManager(chart, candleSeries, data);
            
            // Set createSeriesMarkers function for v5 API marker support
            seriesManagerRef.current.setCreateSeriesMarkers(createSeriesMarkers);

            // Set initial data if available
            if (data && data.length > 0) {
                candleSeries.setData(data);
                // Default zoom: show last N REAL candles (exclude future placeholders)
                const defaultZoomCandles = window.BTM_DEFAULT_ZOOM_CANDLES || 100;
                const endIndex = realCount > 0 ? realCount - 1 : data.length - 1;
                const startIndex = Math.max(0, endIndex - defaultZoomCandles + 1);
                if (endIndex > 0) {
                    chart.timeScale().setVisibleLogicalRange({
                        from: startIndex,
                        to: endIndex,
                    });
                } else {
                    chart.timeScale().fitContent();
                }
            }

            window.addEventListener('resize', () => {
                if (!chartContainerRef.current || !chartRef.current) return;
                chartRef.current.applyOptions({ 
                    width: chartContainerRef.current.clientWidth,
                    height: chartContainerRef.current.clientHeight || 400,
                });
            });

            return () => {
                isMountedRef.current = false;
                window.removeEventListener('resize', handleResize);
                if (markerPluginRef.current) {
                    markerPluginRef.current.detach();
                    markerPluginRef.current = null;
                }
                chart.remove();
                chartRef.current = null;
                seriesRef.current = null;
            };
        },
        [backgroundColor, textColor, upColor, downColor, wickUpColor, wickDownColor, borderUpColor, borderDownColor]
    );

    // Update data when it changes
    useEffect(() => {
        if (seriesRef.current && data && data.length > 0) {
            try {
                console.log('Updating chart with new data:', data.length, 'candles');
                seriesRef.current.setData(data);
                
                // Update series manager's chart data reference
                if (seriesManagerRef.current) {
                    seriesManagerRef.current.setChartData(data);
                }
                
                if (chartRef.current) {
                    // Default zoom: show last N REAL candles (exclude future placeholders)
                    const defaultZoomCandles = window.BTM_DEFAULT_ZOOM_CANDLES || 100;
                    const endIndex = realCount > 0 ? realCount - 1 : data.length - 1;
                    const startIndex = Math.max(0, endIndex - defaultZoomCandles + 1);
                    if (endIndex > 0) {
                        chartRef.current.timeScale().setVisibleLogicalRange({
                            from: startIndex,
                            to: endIndex,
                        });
                    } else {
                        chartRef.current.timeScale().fitContent();
                    }
                }
            } catch (error) {
                console.error('Error updating chart data:', error);
            }
        }
    }, [data, realCount]);

    // Log active strategies when they change
    useEffect(() => {
        if (activeStrategies && activeStrategies.length > 0) {
            console.log('Active strategies received in chart:', activeStrategies);
        }
    }, [activeStrategies]);

    // Update indicators when strategy data changes
    useEffect(() => {
        if (chartRef.current && seriesRef.current && data && data.length > 0 && Object.keys(strategyData).length > 0) {
            console.log('Strategy data received in chart:', strategyData);
            console.log('Chart data available:', data.length, 'candles');
            
            // Add a longer delay to ensure chart is fully processed
            const timeoutId = setTimeout(() => {
                console.log('Attempting to add indicators after delay...');
                // Add indicators for each strategy
                Object.values(strategyData).forEach(data => {
                    if (data && data.series && data.series.indicators) {
                        addIndicators(chartRef.current, data);
                    }
                });
            }, 1000); // Increased delay to 1 second

            return () => clearTimeout(timeoutId);
        }
    }, [strategyData, data]);

    // Handle enabled indicators from IndicatorsTab
    useEffect(() => {
        if (!seriesManagerRef.current || !data || data.length === 0) {
            return;
        }

        console.log('Enabled indicators changed:', enabledIndicators);

        // Remove all existing indicator series and boxes
        seriesManagerRef.current.removeSeriesByPrefix('indicator_');
        seriesManagerRef.current.removeAllBoxes();

        // Fetch and add new indicator series (only visible ones)
        const fetchAndAddIndicators = async () => {
            const allBoxes = []; // Collect all boxes from all indicators
            
            // Filter to only visible indicators (visible !== false means default true or explicitly true)
            const visibleIndicators = enabledIndicators.filter(ind => ind.visible !== false);
            console.log(`Processing ${visibleIndicators.length} visible indicators out of ${enabledIndicators.length} total`);
            
            for (const indicator of visibleIndicators) {
                try {
                    const instanceId = indicator.instanceId || `${indicator.id}_${Date.now()}`;
                    console.log(`Fetching data for indicator instance: ${instanceId} (type: ${indicator.id})`, indicator.params);
                    
                    // Fetch historical data for this indicator
                    const res = await fetch(`http://localhost:8080/api/indicators/${indicator.id}/historical`, {
                        method: 'POST',
                        headers: {
                            'accept': 'application/json',
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(indicator.params),
                    });
                    
                    if (!res.ok) {
                        const errorText = await res.text();
                        console.error(`Failed to fetch indicator ${instanceId}: HTTP ${res.status}`, errorText);
                        continue;
                    }
                    
                    const apiResponse = await res.json();
                    console.log(`Indicator ${instanceId} response received:`, {
                        hasMetadata: !!apiResponse.metadata,
                        hasShapes: !!apiResponse.shapes,
                        boxCount: apiResponse.shapes?.boxes?.length || 0
                    });
                    
                    // Add series data if present
                    if (apiResponse.metadata || apiResponse.data) {
                        const indicatorKey = `indicator_${instanceId}`;
                        const params = indicator.params?.params || {};
                        
                        seriesManagerRef.current.addFromApiResponse(
                            indicatorKey,
                            apiResponse,
                            params,
                            LinePrimitive
                        );
                    }
                    
                    // Collect boxes from shapes if present
                    if (apiResponse.shapes?.boxes && Array.isArray(apiResponse.shapes.boxes)) {
                        console.log(`Adding ${apiResponse.shapes.boxes.length} boxes from indicator ${instanceId}`);
                        
                        // Transform API box format to BoxPrimitive format
                        const transformedBoxes = apiResponse.shapes.boxes.map(box => ({
                            time1: box.time1,
                            time2: box.time2,
                            price1: box.price1,
                            price2: box.price2,
                            backgroundColor: box.color || box.backgroundColor || 'rgba(33, 150, 243, 0.1)',
                            text: box.label || box.text,
                            textColor: box.textColor || box.borderColor || '#ffffff',
                            borderColor: box.borderColor,
                            borderWidth: box.borderWidth,
                            borderStyle: box.borderStyle
                        }));
                        
                        allBoxes.push(...transformedBoxes);
                    }

                } catch (error) {
                    console.error(`❌ Error adding indicator ${indicator.instanceId || indicator.id}:`, error);
                }
            }
            
            // Add all collected boxes at once
            if (allBoxes.length > 0) {
                console.log(`Adding ${allBoxes.length} total boxes from all indicators`);
                setTimeout(() => {
                    if (seriesManagerRef.current && data && data.length > 0) {
                        seriesManagerRef.current.addBoxes(allBoxes, BoxPrimitive);
                    }
                }, 200); // Small delay to ensure series are rendered first
            }
        };

        fetchAndAddIndicators();
    }, [enabledIndicators, data]);

    // Add indicators to chart
    const addIndicators = (chart, strategyData) => {
        // Prevent concurrent indicator addition
        if (isAddingIndicatorsRef.current) {
            console.log('Already adding indicators, skipping...');
            return;
        }

        console.log('addIndicators called with:', {
            chart: !!chart,
            strategyData: !!strategyData,
            series: !!(strategyData && strategyData.series),
            indicators: !!(strategyData && strategyData.series && strategyData.series.indicators),
            mainSeries: !!seriesRef.current,
            data: !!data,
            dataLength: data ? data.length : 0,
            hasBoxes: !!(strategyData && strategyData.boxes),
            boxesLength: strategyData?.boxes?.length
        });
        
        console.log('Full strategyData structure:', strategyData);

        if (!chart) {
            console.log('Cannot add indicators: chart is null');
            return;
        }
        if (!strategyData) {
            console.log('Cannot add indicators: strategyData is null');
            return;
        }
        if (!strategyData.series || !strategyData.series.indicators) {
            console.log('Cannot add indicators: strategyData.series.indicators is null');
            return;
        }
        if (!seriesRef.current) {
            console.log('Cannot add indicators: seriesRef.current is null');
            return;
        }
        if (!data || data.length === 0) {
            console.log('Cannot add indicators: data is null or empty');
            return;
        }

        // Check if chart is in a stable state
        try {
            const timeScale = chart.timeScale();
            if (!timeScale) {
                console.log('Cannot add indicators: chart timeScale is not ready');
                return;
            }
            console.log('Chart is in stable state, proceeding with indicators...');
        } catch (e) {
            console.log('Cannot add indicators: chart is not ready:', e.message);
            return;
        }

        // Set flag to prevent concurrent additions
        isAddingIndicatorsRef.current = true;

        try {
            console.log('Starting to add indicators...');
            console.log('Strategy data structure:', strategyData);
            
            // Clear existing strategy indicator series
            console.log('Clearing existing strategy indicator series...');
            if (seriesManagerRef.current) {
                seriesManagerRef.current.removeSeriesByPrefix('strategy_');
            }

            // Process indicators from the new structure
            const indicators = strategyData.series.indicators;
            console.log('Processing indicators:', indicators.length);

            // Process indicators one by one to avoid overwhelming the chart
            const processIndicator = (index) => {
                try {
                    // Check if component is still mounted
                    if (!isMountedRef.current) {
                        console.log('Component unmounted, stopping indicator processing');
                        return;
                    }
                    
                    // Check if chart is still valid before processing
                    if (!chartRef.current || !chart) {
                        console.log('Chart is no longer valid, stopping indicator processing');
                        return;
                    }
                    
                    if (index >= indicators.length) {
                        console.log('All indicators processed');
                        return;
                    }

                const indicator = indicators[index];
                console.log(`Processing indicator ${index + 1}/${indicators.length}:`, {
                    name: indicator.name,
                    type: indicator.type,
                    config: indicator.config,
                    dataLength: indicator.data ? indicator.data.length : 0
                });
                
                // Basic validation - ChartSeriesManager will handle detailed validation
                if (!indicator.data || !Array.isArray(indicator.data) || indicator.data.length === 0) {
                    console.warn(`No data for indicator: ${indicator.name}`);
                    processIndicator(index + 1);
                    return;
                }

                try {
                    // Check if chart and series manager are still valid
                    if (!seriesManagerRef.current || !chart || !chartRef.current) {
                        console.error('Chart or series manager is no longer valid, skipping indicator');
                        return;
                    }
                    
                    console.log(`Creating series for ${indicator.name} with type ${indicator.type}`);
                    
                    // Use ChartSeriesManager to add the series
                    const seriesId = `strategy_${indicator.name}`;
                    const metadata = {
                        seriesType: indicator.type,
                        separatePane: indicator.separatePane || false,
                        config: indicator.config
                    };
                    
                    // Add a small delay before adding to prevent timing issues
                    setTimeout(() => {
                        try {
                            // Check if still valid
                            if (!seriesManagerRef.current || !chartRef.current) {
                                console.error('Chart is no longer valid');
                                return;
                            }
                            
                            console.log(`Adding series ${seriesId} with ${indicator.data.length} points`);
                            const success = seriesManagerRef.current.addSeries(
                                seriesId,
                                indicator.data,
                                metadata,
                                indicator.config
                            );
                            
                            if (success) {
                                console.log(`✅ ${indicator.name} series added successfully`);
                            } else {
                                console.warn(`⚠️ Failed to add series for ${indicator.name}`);
                            }
                        } catch (e) {
                            console.error(`Error adding ${indicator.name} series:`, e);
                        }
                    }, 100 + (50 * index)); // Increased base delay and stagger
                    
                } catch (e) {
                    console.error(`Error processing ${indicator.name} series:`, e);
                }
                
                // Process next indicator after a delay
                setTimeout(() => {
                    processIndicator(index + 1);
                }, 100);
                } catch (e) {
                    console.error(`Error processing indicator ${index}:`, e);
                    // Continue with next indicator even if this one fails
                    setTimeout(() => {
                        processIndicator(index + 1);
                    }, 100);
                }
            };

            // Start processing indicators
            processIndicator(0);

            // Add markers for trading signals using v5 plugin API
            // In v5, markers are now managed through a plugin, not directly on the series
            setTimeout(() => {
                if (!seriesRef.current || !isMountedRef.current) {
                    console.warn('Cannot add markers: series ref is not available');
                    return;
                }
                
                try {
                    if (strategyData.markers && Array.isArray(strategyData.markers) && strategyData.markers.length > 0) {
                        console.log('Adding markers using v5 plugin API:', strategyData.markers.length, 'markers');
                        
                        // Create or update marker plugin
                        if (!markerPluginRef.current) {
                            // Create new marker plugin
                            markerPluginRef.current = createSeriesMarkers(seriesRef.current, strategyData.markers);
                            console.log('✅ Marker plugin created with', strategyData.markers.length, 'markers');
                        } else {
                            // Update existing plugin with new markers
                            markerPluginRef.current.setMarkers(strategyData.markers);
                            console.log('✅ Marker plugin updated with', strategyData.markers.length, 'markers');
                        }
                    } else {
                        // Clear markers if no markers in strategy data
                        if (markerPluginRef.current) {
                            console.log('Clearing markers...');
                            markerPluginRef.current.setMarkers([]);
                        }
                    }
                } catch (e) {
                    console.error('❌ Error setting markers:', e);
                    console.error('Error stack:', e.stack);
                }
            }, 200); // Small delay to ensure indicators are processed
            
            // Add boxes/rectangles if provided (with delay to ensure chart is ready)
            if (strategyData.boxes && Array.isArray(strategyData.boxes) && strategyData.boxes.length > 0) {
                console.log('Adding boxes:', strategyData.boxes.length, 'boxes', strategyData.boxes);
                setTimeout(() => {
                    if (seriesManagerRef.current && data && data.length > 0) {
                        console.log('Attempting to add boxes after delay...');
                        seriesManagerRef.current.addBoxes(strategyData.boxes, BoxPrimitive);
                    }
                }, 500); // Increased delay
            } else {
                console.log('No boxes to add:', {
                    hasBoxes: !!strategyData.boxes,
                    isArray: Array.isArray(strategyData.boxes),
                    length: strategyData.boxes?.length
                });
            }
            
            console.log('All indicators added successfully');
            
            // Add a small delay to let the chart process the changes
            setTimeout(() => {
                console.log('Chart processing complete');
                isAddingIndicatorsRef.current = false;
            }, 100);
        } catch (error) {
            console.error('Error adding indicators:', error);
            isAddingIndicatorsRef.current = false;
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-full w-full rounded-lg">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
                    <p className="text-gray-400">Loading chart data...</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-full w-full rounded-lg">
                <div className="text-center">
                    <div className="text-red-500 text-6xl mb-4">⚠️</div>
                    <p className="text-red-400 mb-2">Failed to load chart data</p>
                    <p className="text-gray-400 text-sm">{error}</p>
                </div>
            </div>
        );
    }

    return (
        <div
            ref={chartContainerRef}
            className="w-full h-full"
        />
    );
};

// Transform API data to chart format
const transformApiData = (apiData) => {
    return apiData.map(candle => ({
        time: new Date(candle.openTime).getTime() / 1000, // Convert to Unix timestamp
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
    }));
};

// Generate future empty data to maintain grid visibility until end of day
// const generateFutureData = (lastCandle, interval) => {
//     if (!lastCandle) return [];
    
//     const futureData = [];
//     const intervalSec = getIntervalSeconds(interval);
//     let currentTime = lastCandle.time;
//     const lastPrice = lastCandle.close;
    
//     // Get the end of the current day (23:59:59)
//     const lastCandleDate = new Date(lastCandle.time * 1000);
//     const endOfDay = new Date(lastCandleDate);
//     endOfDay.setHours(23, 59, 59, 999);
//     const endOfDayTimestamp = Math.floor(endOfDay.getTime() / 1000);
    
//     console.log('Last candle time:', new Date(lastCandle.time * 1000).toISOString());
//     console.log('End of day:', endOfDay.toISOString());
//     console.log('Interval sec:', intervalSec);
    
//     // Generate candles until end of day
//     while (currentTime < endOfDayTimestamp) {
//         currentTime += intervalSec;
//         if (currentTime <= endOfDayTimestamp) {
//             futureData.push({
//                 time: currentTime,
//                 open: lastPrice,
//                 high: lastPrice,
//                 low: lastPrice,
//                 close: lastPrice,
//             });
//         }
//     }
    
//     console.log('Generated future data:', futureData.length, 'candles');
//     return futureData;
// };

// Get interval in seconds
const getIntervalSeconds = (interval) => {
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

export function Chart({ provider, symbol, interval, activeStrategies = [], enabledIndicators = [] }) {
    const [data, setData] = useState([]);
    const [realCount, setRealCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [strategyData, setStrategyData] = useState({});

    // Fetch strategy visualization data
    const fetchStrategyData = async (strategyId, symbol) => {
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

    useEffect(() => {

        const fetchHistoricalData = async () => {

            console.log('Chart useEffect triggered:', { provider, symbol, interval });
            
            if (!provider || !symbol || !interval) {
                console.log('Missing required props, clearing chart data');
                setData([]);
                return;
            }

            try {
                setLoading(true);
                setError(null);
                
                const providerName = typeof provider === 'string' ? provider : (provider.name || provider);
                const url = `http://localhost:8080/api/trading/historical/${providerName}/${symbol}/${interval}?limit=1000`;
                
                console.log('Fetching data from:', url);
                const response = await fetch(url);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                
                const apiData = await response.json();
                console.log('API response:', apiData);
                const chartData = transformApiData(apiData);
                setRealCount(chartData.length);
                
                // Generate future empty data to maintain grid visibility until end of day
                // const futureData = generateFutureData(chartData[chartData.length - 1], interval);
                
                // // Fallback: if no future data generated, create at least 10 future candles
                // const finalFutureData = futureData.length > 0 ? futureData : (() => {
                //     const fallbackData = [];
                //     const intervalSec = getIntervalSeconds(interval);
                //     let currentTime = chartData[chartData.length - 1].time;
                //     const lastPrice = chartData[chartData.length - 1].close;
                    
                //     for (let i = 1; i <= 10; i++) {
                //         currentTime += intervalSec;
                //         fallbackData.push({
                //             time: currentTime,
                //             open: lastPrice,
                //             high: lastPrice,
                //             low: lastPrice,
                //             close: lastPrice,
                //         });
                //     }
                //     return fallbackData;
                // })();
                
                // const combinedData = [...chartData, ...finalFutureData];
                
                // console.log('Transformed chart data:', chartData.length, 'candles +', futureData.length, 'future candles');
                setData(chartData);
            } catch (err) {
                setError(err.message);
                console.error('Failed to fetch historical data:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchHistoricalData();
    }, [provider, symbol, interval]);

    // Fetch strategy data when active strategies or symbol changes
    useEffect(() => {
        const fetchAllStrategyData = async () => {
            console.log('Fetching strategy data for:', { activeStrategies: activeStrategies.length, symbol });
            
            if (activeStrategies.length > 0 && symbol) {
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
                setStrategyData(strategyDataMap);
            } else {
                console.log('No active strategies or symbol, clearing strategy data');
                setStrategyData({});
            }
        };

        fetchAllStrategyData();
    }, [activeStrategies, symbol]);

    return (
        <ChartComponent 
            data={data} 
            loading={loading} 
            error={error}
            realCount={realCount}
            activeStrategies={activeStrategies}
            strategyData={strategyData}
            enabledIndicators={enabledIndicators}
        />
    );
}