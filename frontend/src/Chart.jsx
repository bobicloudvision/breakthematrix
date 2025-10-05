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
import { ArrowPrimitive } from './ArrowPrimitive';
import { MarkerPrimitive } from './MarkerPrimitive';
import { FillBetweenPrimitive } from './FillBetweenPrimitive';
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
        provider,
        symbol,
        interval,
        seriesRef: externalSeriesRef,
        seriesManagerRef: externalSeriesManagerRef,
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
    // Use external refs if provided, otherwise create local refs (for backwards compatibility)
    const seriesRef = externalSeriesRef || useRef();
    const seriesManagerRef = externalSeriesManagerRef || useRef(null);
    const markerPluginRef = useRef(null); // v5 marker plugin instance
    const isAddingIndicatorsRef = useRef(false);
    const isMountedRef = useRef(true);
    const isInitialDataLoadRef = useRef(true); // Track if this is the first data load
    const indicatorsLoadedRef = useRef(false); // Track if indicators have been loaded for current data
    
    // Track when data is loaded (boolean state that changes only when data availability changes)
    const [dataLoaded, setDataLoaded] = useState(false);

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

            window.addEventListener('resize', handleResize);

            // Add ResizeObserver to detect container size changes (e.g., when bottom bar is resized)
            const resizeObserver = new ResizeObserver(() => {
                if (!chartContainerRef.current || !chartRef.current) return;
                chartRef.current.applyOptions({ 
                    width: chartContainerRef.current.clientWidth,
                    height: chartContainerRef.current.clientHeight || 400,
                });
            });

            if (chartContainerRef.current) {
                resizeObserver.observe(chartContainerRef.current);
            }

            return () => {
                isMountedRef.current = false;
                window.removeEventListener('resize', handleResize);
                resizeObserver.disconnect();
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

    // Reset initial load flag when provider/symbol/interval changes
    useEffect(() => {
        isInitialDataLoadRef.current = true;
        indicatorsLoadedRef.current = false; // Reset indicators loaded flag
        setDataLoaded(false); // Reset data loaded state
    }, [provider, symbol, interval]);
    
    // Track when data becomes available (changes from empty to loaded)
    useEffect(() => {
        const hasData = data && data.length > 0;
        if (hasData && !dataLoaded) {
            console.log('Data loaded, triggering indicators load');
            setDataLoaded(true);
        } else if (!hasData && dataLoaded) {
            setDataLoaded(false);
        }
    }, [data && data.length > 0, dataLoaded]); // Only track boolean change, not actual length

    // Update data when it changes (only for initial load)
    useEffect(() => {
        if (seriesRef.current && data && data.length > 0 && isInitialDataLoadRef.current) {
            try {
                console.log('Setting initial chart data:', data.length, 'candles');
                seriesRef.current.setData(data);
                
                // Update series manager's chart data reference
                if (seriesManagerRef.current) {
                    seriesManagerRef.current.setChartData(data);
                }
                
                // Apply default zoom on initial load
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
                
                // Mark initial load as complete
                isInitialDataLoadRef.current = false;
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
    }, [strategyData]); // Removed 'data' to prevent re-adding on WebSocket updates

    // Handle enabled indicators from IndicatorsTab
    useEffect(() => {
        // Wait for series manager and data to be ready
        if (!seriesManagerRef.current) {
            console.log('Series manager not ready yet');
            return;
        }
        
        if (!data || data.length === 0) {
            console.log('Data not loaded yet, waiting...');
            return;
        }

        console.log('Enabled indicators changed:', enabledIndicators, 'Data loaded:', data.length);

        // Remove all existing indicator series and shapes (boxes, lines, markers)
        seriesManagerRef.current.removeSeriesByPrefix('indicator_');
        seriesManagerRef.current.clearAllShapes();
        
        // Mark that we're loading indicators
        indicatorsLoadedRef.current = true;

        // Fetch and add new indicator series
        const fetchAndAddIndicators = async () => {
            const allBoxes = []; // Collect all boxes from all indicators
            const allArrows = []; // Collect all arrows from all indicators
            const allMarkerShapes = []; // Collect all marker shapes from all indicators
            
            console.log(`Processing ${enabledIndicators.length} active indicators`);
            
            if (enabledIndicators.length === 0) {
                console.log('No active indicators to fetch');
                return;
            }
            
            if (!provider || !symbol || !interval) {
                console.error('Missing required parameters for fetching indicators:', { provider, symbol, interval });
                return;
            }
            
            try {
                // Fetch all indicators at once with the new endpoint
                const providerName = typeof provider === 'string' ? provider : (provider.name || provider);
                const requestBody = {
                    provider: providerName,
                    symbol: symbol,
                    interval: interval,
                    count: 1000 // Fetch same amount as chart data
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
                    console.error(`Failed to fetch indicators: HTTP ${res.status}`, errorText);
                    return;
                }
                
                const response = await res.json();
                console.log('Indicators response received:', {
                    indicatorCount: response.indicatorCount,
                    fromActiveInstances: response.fromActiveInstances,
                    indicators: response.indicators?.length || 0
                });
                
                if (!response.indicators || !Array.isArray(response.indicators)) {
                    console.warn('No indicators returned from API');
                    return;
                }
                
                // Process each indicator from the response
                for (const indicatorData of response.indicators) {
                    try {
                        const indicatorId = indicatorData.indicatorId;
                        const instanceKey = indicatorData.instanceKey;
                        
                        console.log(`Processing indicator: ${indicatorId} (${instanceKey})`);
                        console.log('Raw indicatorData structure:', {
                            hasMetadata: !!indicatorData.metadata,
                            metadataKeys: indicatorData.metadata ? Object.keys(indicatorData.metadata) : [],
                            hasSeries: !!indicatorData.series,
                            seriesKeys: indicatorData.series ? Object.keys(indicatorData.series) : [],
                            hasShapes: !!indicatorData.shapes,
                            shapesKeys: indicatorData.shapes ? Object.keys(indicatorData.shapes) : []
                        });
                        
                        // Extract metadata - it might be nested under indicator ID or at top level
                        let metadata = indicatorData.metadata ? indicatorData.metadata[indicatorId] : null;
                        
                        // If not found under indicator ID, use the whole metadata object
                        if (!metadata && indicatorData.metadata) {
                            console.log('Metadata not nested under indicator ID, using top-level metadata');
                            metadata = indicatorData.metadata;
                        }
                        
                        if (!metadata) {
                            console.warn(`No metadata found for indicator ${indicatorId}`);
                            continue;
                        }
                        
                        // Create API response structure that matches the old format
                        // Note: shapes can be at top level (indicatorData.shapes) or in metadata
                        const shapes = indicatorData.shapes || metadata.shapes || {};
                        const apiResponse = {
                            metadata: {
                                [indicatorId]: metadata
                            },
                            series: indicatorData.series || {},
                            shapes: shapes
                        };
                        
                        // Ensure each series metadata has a displayName
                        Object.keys(apiResponse.metadata).forEach(seriesKey => {
                            const seriesMeta = apiResponse.metadata[seriesKey];
                            if (seriesMeta && !seriesMeta.displayName) {
                                // Generate human-readable display name from key
                                // Convert camelCase/PascalCase to Title Case with spaces
                                const displayName = seriesKey
                                    .replace(/([A-Z])/g, ' $1') // Add space before capitals
                                    .replace(/^./, str => str.toUpperCase()) // Capitalize first letter
                                    .trim();
                                seriesMeta.displayName = displayName;
                                console.log(`Generated displayName for ${seriesKey}: "${displayName}"`);
                            }
                        });
                        
                        console.log(`Indicator ${indicatorId} data:`, {
                            hasMetadata: !!metadata,
                            hasShapes: !!shapes,
                            shapesLocation: indicatorData.shapes ? 'top-level' : (metadata.shapes ? 'metadata' : 'none'),
                            boxCount: shapes?.boxes?.length || 0,
                            lineCount: shapes?.lines?.length || 0,
                            markerCount: shapes?.markers?.length || 0,
                            arrowCount: shapes?.arrows?.length || 0,
                            markerShapeCount: shapes?.markerShapes?.length || 0,
                            seriesKeys: Object.keys(apiResponse.series),
                            seriesCount: Object.values(apiResponse.series).filter(s => Array.isArray(s)).length,
                            displayNames: Object.keys(apiResponse.metadata).map(k => 
                                `${k}: ${apiResponse.metadata[k]?.displayName || 'none'}`
                            )
                        });
                    
                        // Add series data and shapes (lines, markers) if present
                        // Note: addFromApiResponse automatically handles lines and markers via LinePrimitive
                        if (apiResponse.metadata || apiResponse.series) {
                            const indicatorKey = `indicator_${instanceKey}`;
                            const params = indicatorData.params || {};
                            
                            seriesManagerRef.current.addFromApiResponse(
                                indicatorKey,
                                apiResponse,
                                params,
                                LinePrimitive
                            );
                        
                            // Add fills (support both single fill and multiple fills array)
                            if (apiResponse.shapes && apiResponse.series) {
                                const seriesKeys = Object.keys(apiResponse.series);
                                
                                // Support both shapes.fill (single) and shapes.fills (array)
                                let fillConfigs = [];
                                if (apiResponse.shapes.fill?.enabled) {
                                    fillConfigs.push(apiResponse.shapes.fill);
                                }
                                if (apiResponse.shapes.fills && Array.isArray(apiResponse.shapes.fills)) {
                                    fillConfigs.push(...apiResponse.shapes.fills.filter(f => f.enabled !== false));
                                }
                                
                                if (fillConfigs.length > 0 && seriesKeys.length > 0) {
                                    console.log(`Processing ${fillConfigs.length} fill(s) for indicator ${instanceKey}`);
                                    
                                    fillConfigs.forEach((fillConfig, fillIndex) => {
                                        // Resolve source1 to actual data if it's a series name
                                        let source1Data = fillConfig.source1 || 'close';
                                        if (typeof source1Data === 'string' && apiResponse.series[source1Data]) {
                                            source1Data = apiResponse.series[source1Data];
                                        }
                                        
                                        // Resolve source2 to actual data if it's a series name
                                        let source2Data = fillConfig.source2;
                                        if (typeof source2Data === 'string' && apiResponse.series[source2Data]) {
                                            // source2 is a series name (e.g., "trailingStop"), resolve to actual data
                                            source2Data = apiResponse.series[source2Data];
                                            console.log(`Resolved source2 "${fillConfig.source2}" to data array with ${source2Data.length} points`);
                                        } else if (!source2Data) {
                                            // No source2 specified, use first series by default
                                            source2Data = apiResponse.series[seriesKeys[0]];
                                            console.log(`Using default series for fill: ${seriesKeys[0]}`);
                                        }
                                        
                                        // Build fill options based on backend configuration
                                        const fillOptions = {
                                            mode: fillConfig.mode || 'series',
                                            source1: source1Data,
                                            source2: source2Data,
                                            
                                            // For hline mode
                                            hline1: fillConfig.hline1,
                                            hline2: fillConfig.hline2,
                                            
                                            // Colors
                                            color: fillConfig.color || 'rgba(41, 98, 255, 0.1)',
                                            topColor: fillConfig.topColor,
                                            bottomColor: fillConfig.bottomColor,
                                            
                                            // Dynamic coloring
                                            colorMode: fillConfig.colorMode || 'static',
                                            upFillColor: fillConfig.upFillColor || 'rgba(76, 175, 80, 0.15)',
                                            downFillColor: fillConfig.downFillColor || 'rgba(239, 83, 80, 0.15)',
                                            neutralFillColor: fillConfig.neutralFillColor || 'rgba(158, 158, 158, 0.1)',
                                            
                                            // Additional options
                                            fillGaps: fillConfig.fillGaps !== false,
                                            display: fillConfig.display !== false
                                        };
                                        
                                        console.log(`Adding fill #${fillIndex} between "${fillConfig.source1 || 'close'}" and "${fillConfig.source2 || seriesKeys[0]}"`, fillOptions);
                                        
                                        setTimeout(() => {
                                            if (seriesManagerRef.current && data && data.length > 0) {
                                                seriesManagerRef.current.addFillBetween(
                                                    `fill_${instanceKey}_${fillIndex}`,
                                                    fillOptions,
                                                    FillBetweenPrimitive
                                                );
                                            }
                                        }, 300 + (fillIndex * 50)); // Stagger fills slightly
                                    });
                                }
                            }
                        }
                        
                        // Collect boxes from shapes if present (boxes are batched for better performance)
                        if (apiResponse.shapes?.boxes && Array.isArray(apiResponse.shapes.boxes)) {
                            console.log(`Adding ${apiResponse.shapes.boxes.length} boxes from indicator ${instanceKey}`);
                            
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
                        
                        // Collect arrows from shapes if present
                        if (apiResponse.shapes?.arrows && Array.isArray(apiResponse.shapes.arrows)) {
                            console.log(`Adding ${apiResponse.shapes.arrows.length} arrows from indicator ${instanceKey}`);
                            
                            // Transform API arrow format to ArrowPrimitive format
                            const transformedArrows = apiResponse.shapes.arrows.map(arrow => ({
                                time: arrow.time,
                                price: arrow.price,
                                direction: arrow.direction || 'up',
                                color: arrow.color || '#2196F3',
                                size: arrow.size || 8,
                                borderColor: arrow.borderColor,
                                borderWidth: arrow.borderWidth,
                                text: arrow.text || arrow.label,
                                textColor: arrow.textColor
                            }));
                            
                            allArrows.push(...transformedArrows);
                        }
                        
                        // Collect markers from shapes if present (support both 'markers' and 'markerShapes')
                        const markersArray = apiResponse.shapes?.markers || apiResponse.shapes?.markerShapes;
                        if (markersArray && Array.isArray(markersArray)) {
                            console.log(`Adding ${markersArray.length} markers from indicator ${instanceKey}`);
                            
                            // Transform API marker format to MarkerPrimitive format
                            const transformedMarkerShapes = markersArray.map(marker => ({
                                time: marker.time,
                                price: marker.price,
                                shape: marker.shape || marker.position || 'circle',
                                color: marker.color || '#2196F3',
                                size: marker.size || 6,
                                borderColor: marker.borderColor,
                                borderWidth: marker.borderWidth,
                                text: marker.text || marker.label,
                                textColor: marker.textColor
                            }));
                            
                            allMarkerShapes.push(...transformedMarkerShapes);
                        }

                    } catch (error) {
                        console.error(`❌ Error processing indicator ${indicatorId}:`, error);
                    }
                }
            } catch (error) {
                console.error(`❌ Error fetching indicators:`, error);
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
            
            // Add all collected arrows at once
            if (allArrows.length > 0) {
                console.log(`Adding ${allArrows.length} total arrows from all indicators`);
                setTimeout(() => {
                    if (seriesManagerRef.current && data && data.length > 0) {
                        seriesManagerRef.current.addArrows(allArrows, ArrowPrimitive);
                    }
                }, 250); // Slightly staggered after boxes
            }
            
            // Add all collected marker shapes at once
            if (allMarkerShapes.length > 0) {
                console.log(`Adding ${allMarkerShapes.length} total marker shapes from all indicators`);
                setTimeout(() => {
                    if (seriesManagerRef.current && data && data.length > 0) {
                        seriesManagerRef.current.addMarkerShapes(allMarkerShapes, MarkerPrimitive);
                    }
                }, 300); // Slightly staggered after arrows
            }
        };

        fetchAndAddIndicators();
    }, [enabledIndicators, provider, symbol, interval, dataLoaded]); // Trigger when data first loads or indicators change

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
            <div className="w-full lex items-center justify-center h-full w-full rounded-lg">
                <div className="max-w-md mx-auto text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
                    <p className="text-gray-400">Loading chart data...</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="w-full flex items-center justify-center h-full w-full rounded-lg">
                <div className="max-w-md mx-auto text-center">
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
        time: candle.timestamp, // Use timestamp field directly (already in seconds)
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
    const [wsStatus, setWsStatus] = useState('disconnected'); // 'connecting', 'connected', 'disconnected', 'error'
    const [wsMessage, setWsMessage] = useState('');
    const [wsReconnectTrigger, setWsReconnectTrigger] = useState(0);
    const wsRef = useRef(null);
    
    // Refs shared between Chart and ChartComponent for WebSocket updates
    const seriesRef = useRef(null);
    const seriesManagerRef = useRef(null);

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
                const url = `http://localhost:8080/api/trading/historical/${providerName}/${symbol}/${interval}?limit=5000`;
                
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

    // WebSocket connection for real-time indicator updates
    useEffect(() => {
        if (!provider || !symbol || !interval) {
            setWsStatus('disconnected');
            setWsMessage('No connection');
            return;
        }

        // Close existing connection if any
        if (wsRef.current) {
            console.log('Closing existing WebSocket connection');
            wsRef.current.close();
            wsRef.current = null;
        }

        // Set connecting state
        setWsStatus('connecting');
        setWsMessage('Connecting...');

        // Create new WebSocket connection
        const ws = new WebSocket('ws://localhost:8080/indicator-ws');
        wsRef.current = ws;

        ws.onopen = () => {
            console.log('✅ Connected to indicator WebSocket');
            setWsStatus('connected');
            setWsMessage('Connected');
            
            // Subscribe to the current context
            const providerName = typeof provider === 'string' ? provider : (provider.name || provider);
            ws.send(JSON.stringify({
                action: 'subscribeContext',
                provider: providerName,
                symbol: symbol,
                interval: interval
            }));
        };

        ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                
                switch (message.type) {
                    case 'connected':
                        console.log('📡', message.message);
                        setWsStatus('connected');
                        setWsMessage(message.message || 'Connected');
                        break;
                        
                    case 'contextSubscribed':
                        console.log('✅ Subscribed to indicator context:', message.context);
                        console.log('   Active instances:', message.activeInstances);
                        setWsStatus('connected');
                        setWsMessage(`Subscribed: ${message.activeInstances || 0} indicators`);
                        break;
                        
                    case 'candleUpdate': 
                        // Handle real-time candlestick updates
                        // Extract candle data from message (could be message.candle, message.data.candle, or message.data)
                        const candleData = message.candle || message.data?.candle || message.data;
                        if (candleData && seriesRef.current) {
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
                                setData(prevCandles => {
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
                                if (candleData.closed) {
                                    setRealCount(prev => prev + 1);
                                }
                            } catch (error) {
                                console.error('Error updating candle:', error);
                            }
                        }
                        break;
                    
                    case 'indicatorUpdate':
                        // Extract indicator data from message.data
                        const indicatorData = message.data || message;
                        
                        console.log('📊 Indicator Update:', {
                            indicator: indicatorData.indicatorId,
                            instanceKey: indicatorData.instanceKey,
                            time: new Date(indicatorData.timestamp),
                            values: indicatorData.values
                        });
                        
                        // Update status message with last update time
                        const updateTime = new Date(indicatorData.timestamp).toLocaleTimeString();
                        setWsMessage(`Last update: ${updateTime}`);
                        
                        // Update indicator series in real-time without re-fetching
                        if (seriesManagerRef.current && indicatorData.values && indicatorData.timestamp) {
                            const instanceKey = indicatorData.instanceKey;
                            const indicatorPrefix = `indicator_${instanceKey}`;
                            
                            // Convert ISO timestamp to Unix timestamp (seconds)
                            const timeValue = Math.floor(new Date(indicatorData.timestamp).getTime() / 1000);
                            
                            console.log(`Attempting to update indicator ${instanceKey} at time ${timeValue}`, indicatorData.values);
                            
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
                        }
                        break;
                        
                    case 'error':
                        console.error('❌ WebSocket error:', message.error);
                        setWsStatus('error');
                        setWsMessage(`Error: ${message.error}`);
                        break;
                        
                    default:
                        console.log('📨 WebSocket message:', message);
                }
            } catch (error) {
                console.error('Error parsing WebSocket message:', error);
                setWsStatus('error');
                setWsMessage('Parse error');
            }
        };

        ws.onerror = (error) => {
            console.error('❌ WebSocket error:', error);
            setWsStatus('error');
            setWsMessage('Connection error');
        };

        ws.onclose = () => {
            console.log('❌ Disconnected from indicator WebSocket');
            setWsStatus('disconnected');
            setWsMessage('Disconnected');
        };

        // Cleanup on unmount or when dependencies change
        return () => {
            if (ws.readyState === WebSocket.OPEN) {
                console.log('Closing WebSocket connection');
                ws.close();
            }
            wsRef.current = null;
            setWsStatus('disconnected');
            setWsMessage('Disconnected');
        };
    }, [provider, symbol, interval, wsReconnectTrigger]);

    // Get status badge colors
    const getStatusColors = () => {
        switch (wsStatus) {
            case 'connected':
                return {
                    bg: 'bg-green-500/20',
                    border: 'border-green-400/50',
                    text: 'text-green-300',
                    dot: 'bg-green-400',
                    icon: '✓'
                };
            case 'connecting':
                return {
                    bg: 'bg-yellow-500/20',
                    border: 'border-yellow-400/50',
                    text: 'text-yellow-300',
                    dot: 'bg-yellow-400 animate-pulse',
                    icon: '⟳'
                };
            case 'error':
                return {
                    bg: 'bg-red-500/20',
                    border: 'border-red-400/50',
                    text: 'text-red-300',
                    dot: 'bg-red-400',
                    icon: '✕'
                };
            case 'disconnected':
            default:
                return {
                    bg: 'bg-gray-500/20',
                    border: 'border-gray-400/50',
                    text: 'text-gray-300',
                    dot: 'bg-gray-400',
                    icon: '○'
                };
        }
    };

    const statusColors = getStatusColors();

    // Reconnect function
    const handleReconnect = () => {
        if (wsRef.current) {
            wsRef.current.close();
            wsRef.current = null;
        }
        // Force re-run of the WebSocket effect by incrementing trigger
        setWsReconnectTrigger(prev => prev + 1);
    };

    return (
        <div className="relative w-full h-full">
            {/* WebSocket Status Indicator */}
            <div className="absolute top-3 right-3 z-10">
                <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border backdrop-blur-md ${statusColors.bg} ${statusColors.border} ${statusColors.text} shadow-lg`}>
                    <div className="flex items-center gap-2">
                        <div className={`w-2 h-2 rounded-full ${statusColors.dot}`}></div>
                        <span className="text-xs font-medium">{statusColors.icon} Indicators</span>
                    </div>
                    <div className="text-xs opacity-80 border-l border-white/20 pl-2">
                        {wsMessage}
                    </div>
                    {(wsStatus === 'disconnected' || wsStatus === 'error') && (
                        <button
                            onClick={handleReconnect}
                            className="ml-2 px-2 py-0.5 text-xs rounded bg-white/10 hover:bg-white/20 transition-colors border border-white/20"
                            title="Reconnect"
                        >
                            ⟳
                        </button>
                    )}
                </div>
            </div>

            <ChartComponent 
                data={data} 
                loading={loading} 
                error={error}
                realCount={realCount}
                activeStrategies={activeStrategies}
                strategyData={strategyData}
                enabledIndicators={enabledIndicators}
                provider={provider}
                symbol={symbol}
                interval={interval}
                seriesRef={seriesRef}
                seriesManagerRef={seriesManagerRef}
            />
        </div>
    );
}