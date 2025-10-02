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
    const indicatorSeriesRef = useRef({});
    const markerPluginRef = useRef(null); // v5 marker plugin instance
    const isAddingIndicatorsRef = useRef(false);
    const isMountedRef = useRef(true);
    const boxPrimitivesRef = useRef([]);
    const boxesContainerRef = useRef(null); // Backup for HTML overlay

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
                indicatorSeriesRef.current = {};
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

    // Draw boxes/rectangles on chart using plugin system (lightweight-charts v4+)
    const drawBoxes = (chart, series, boxes) => {
        console.log('drawBoxes called (plugin version v5):', { boxesCount: boxes.length });
        
        if (!chart || !series || !boxes || boxes.length === 0) {
            console.log('Skipping boxes: missing chart, series, or boxes');
            return;
        }
        
        // Remove existing box primitives
        boxPrimitivesRef.current.forEach(primitive => {
            try {
                series.detachPrimitive(primitive);
            } catch (e) {
                console.warn('Error detaching box primitive:', e);
            }
        });
        boxPrimitivesRef.current = [];
        
        // Create new box primitive with all boxes (pass data for logical coordinate fallback)
        try {
            const boxPrimitive = new BoxPrimitive(chart, series, boxes, data);
            series.attachPrimitive(boxPrimitive);
            boxPrimitivesRef.current.push(boxPrimitive);
            console.log(`✅ Box primitive attached with ${boxes.length} boxes`);
            
            // Request animation frame to ensure chart is fully rendered
            requestAnimationFrame(() => {
                boxPrimitive.updateAllViews();
                console.log('Box primitive updateAllViews() called after RAF');
                
                // Force chart redraw
                chart.timeScale().applyOptions({});
            });
        } catch (e) {
            console.error('Error creating box primitive:', e);
        }
    };
    
    // Old HTML-based drawBoxes (keeping as backup)
    const drawBoxesHTML = (chart, series, boxes) => {
        // Get visible time range
        const timeRange = chart.timeScale().getVisibleRange();
        console.log('Chart visible time range:', {
            from: timeRange?.from,
            to: timeRange?.to,
            fromDate: timeRange?.from ? new Date(timeRange.from * 1000).toISOString() : 'null',
            toDate: timeRange?.to ? new Date(timeRange.to * 1000).toISOString() : 'null'
        });
        
        // Get logical range (all loaded data)
        const logicalRange = chart.timeScale().getVisibleLogicalRange();
        console.log('Chart logical range:', logicalRange);
        
        console.log('drawBoxes called:', {
            hasContainer: !!chartContainerRef.current,
            hasChart: !!chart,
            hasSeries: !!series,
            boxesCount: boxes.length,
            chartDataLength: data?.length
        });
        
        if (!chartContainerRef.current || !chart || !series) {
            console.error('drawBoxes: Missing required refs');
            return;
        }
        
        // Create or clear boxes container
        if (!boxesContainerRef.current) {
            const container = document.createElement('div');
            container.style.position = 'absolute';
            container.style.top = '0';
            container.style.left = '0';
            container.style.right = '0';
            container.style.bottom = '0';
            container.style.pointerEvents = 'none';
            container.style.zIndex = '10';
            container.style.overflow = 'hidden';
            chartContainerRef.current.style.position = 'relative';
            chartContainerRef.current.appendChild(container);
            boxesContainerRef.current = container;
            console.log('Created boxes container');

        } else {
            // Clear existing boxes
            boxesContainerRef.current.innerHTML = '';
        }
        
        // Log the first and last data points in the chart
        if (data && data.length > 0) {
            console.log('Chart data range:', {
                first: data[0].time,
                last: data[data.length - 1].time,
                firstDate: new Date(data[0].time * 1000).toISOString(),
                lastDate: new Date(data[data.length - 1].time * 1000).toISOString(),
                totalPoints: data.length
            });
        }
        
        console.log('Starting to process', boxes.length, 'boxes');
        
        boxes.forEach((box, idx) => {
            console.log(`\n=== Processing box ${idx + 1} ===`);
            try {
                console.log(`Box ${idx + 1} data:`, box);
                
                // Try to get time scale first
                const timeScale = chart.timeScale();
                if (!timeScale) {
                    console.error('Time scale not available');
                    return;
                }
                
                // IMPORTANT: We need to find the logical indices for our times
                // timeToCoordinate might not work if times don't exactly match data points
                // Let's try a different approach: find closest data points
                let logical1 = null;
                let logical2 = null;
                
                if (data && data.length > 0) {
                    // Find logical indices for the box times
                    for (let i = 0; i < data.length; i++) {
                        if (data[i].time >= box.time1 && logical1 === null) {
                            logical1 = i;
                        }
                        if (data[i].time >= box.time2) {
                            logical2 = i;
                            break;
                        }
                    }
                    
                    // If time2 is beyond the last data point, use the last index
                    if (logical2 === null && box.time2 > data[data.length - 1].time) {
                        logical2 = data.length - 1;
                    }
                    
                    // If time1 is before the first data point, use the first index
                    if (logical1 === null && box.time1 < data[0].time) {
                        logical1 = 0;
                    }
                    
                    console.log(`Logical indices for box ${idx + 1}:`, { logical1, logical2, time1: box.time1, time2: box.time2, dataLast: data[data.length - 1].time });
                }
                
                // Convert time to pixel coordinates
                let x1, x2;
                if (logical1 !== null && logical2 !== null) {
                    x1 = timeScale.logicalToCoordinate(logical1);
                    x2 = timeScale.logicalToCoordinate(logical2);
                    console.log(`Using logical coordinates: ${logical1} -> ${x1}, ${logical2} -> ${x2}`);
                } else {
                    x1 = timeScale.timeToCoordinate(box.time1);
                    x2 = timeScale.timeToCoordinate(box.time2);
                    console.log(`Using time coordinates directly`);
                }
                
                console.log(`Time coordinates for box ${idx + 1}:`, { 
                    time1: box.time1, 
                    time2: box.time2,
                    x1, 
                    x2,
                    time1Date: new Date(box.time1 * 1000).toISOString(),
                    time2Date: new Date(box.time2 * 1000).toISOString()
                });
                
                const y1 = series.priceToCoordinate(box.price1);
                const y2 = series.priceToCoordinate(box.price2);
                
                console.log(`Box ${idx + 1} coordinates:`, { x1, x2, y1, y2 });
                
                if (x1 === null || x2 === null || y1 === null || y2 === null) {
                    console.warn(`Could not calculate coordinates for box ${idx + 1}:`, box, { x1, x2, y1, y2 });
                    
                    // Try to understand why
                    const visibleRange = timeScale.getVisibleRange();
                    console.warn('Visible range:', visibleRange, {
                        from: visibleRange?.from,
                        to: visibleRange?.to,
                        boxWithinRange: box.time1 >= visibleRange?.from && box.time2 <= visibleRange?.to
                    });
                    return;
                }
                
                // Create box element
                const boxElement = document.createElement('div');
                boxElement.style.position = 'absolute';
                boxElement.style.left = Math.min(x1, x2) + 'px';
                boxElement.style.top = Math.min(y1, y2) + 'px';
                boxElement.style.width = Math.abs(x2 - x1) + 'px';
                boxElement.style.height = Math.abs(y2 - y1) + 'px';
                boxElement.style.backgroundColor = box.backgroundColor || 'rgba(33, 150, 243, 0.1)';
                boxElement.style.border = `${box.borderWidth || 1}px ${box.borderStyle || 'solid'} ${box.borderColor || '#2196F3'}`;
                boxElement.style.boxSizing = 'border-box';
                
                // Add text label if provided
                if (box.text) {
                    const label = document.createElement('div');
                    label.textContent = box.text;
                    label.style.position = 'absolute';
                    label.style.top = '4px';
                    label.style.left = '4px';
                    label.style.color = box.textColor || '#ffffff';
                    label.style.fontSize = '11px';
                    label.style.fontFamily = 'Arial, sans-serif';
                    label.style.fontWeight = 'bold';
                    label.style.textShadow = '1px 1px 2px rgba(0,0,0,0.8)';
                    boxElement.appendChild(label);
                }
                
                boxesContainerRef.current.appendChild(boxElement);
                console.log(`✅ Box ${idx + 1} created successfully at position:`, {
                    left: boxElement.style.left,
                    top: boxElement.style.top,
                    width: boxElement.style.width,
                    height: boxElement.style.height,
                    backgroundColor: boxElement.style.backgroundColor,
                    border: boxElement.style.border
                });
            } catch (e) {
                console.error(`❌ Error drawing box ${idx + 1}:`, e, box);
            }
        });
        
        console.log('Total boxes created:', boxesContainerRef.current.children.length);
        
        // Store box data with logical indices for updating
        const boxesData = boxes.map(box => {
            let logical1 = null;
            let logical2 = null;
            
            if (data && data.length > 0) {
                for (let i = 0; i < data.length; i++) {
                    if (data[i].time >= box.time1 && logical1 === null) {
                        logical1 = i;
                    }
                    if (data[i].time >= box.time2) {
                        logical2 = i;
                        break;
                    }
                }
                
                // If time2 is beyond the last data point, use the last index
                if (logical2 === null && box.time2 > data[data.length - 1].time) {
                    logical2 = data.length - 1;
                }
                
                // If time1 is before the first data point, use the first index
                if (logical1 === null && box.time1 < data[0].time) {
                    logical1 = 0;
                }
            }
            
            return { ...box, logical1, logical2 };
        });
        
        // Update box positions on chart scroll/zoom
        const updateBoxes = () => {
            if (!boxesContainerRef.current || !chart || !series) return;
            const boxElements = boxesContainerRef.current.children;
            const timeScale = chart.timeScale();
            
            boxesData.forEach((box, index) => {
                if (index >= boxElements.length) return;
                
                let x1, x2;
                if (box.logical1 !== null && box.logical2 !== null) {
                    x1 = timeScale.logicalToCoordinate(box.logical1);
                    x2 = timeScale.logicalToCoordinate(box.logical2);
                } else {
                    x1 = timeScale.timeToCoordinate(box.time1);
                    x2 = timeScale.timeToCoordinate(box.time2);
                }
                
                const y1 = series.priceToCoordinate(box.price1);
                const y2 = series.priceToCoordinate(box.price2);
                
                if (x1 !== null && x2 !== null && y1 !== null && y2 !== null) {
                    const boxElement = boxElements[index];
                    boxElement.style.left = Math.min(x1, x2) + 'px';
                    boxElement.style.top = Math.min(y1, y2) + 'px';
                    boxElement.style.width = Math.abs(x2 - x1) + 'px';
                    boxElement.style.height = Math.abs(y2 - y1) + 'px';
                }
            });
        };
        
        chart.timeScale().subscribeVisibleTimeRangeChange(updateBoxes);
    };

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
            
            // Clear existing indicator series
            console.log('Clearing existing indicator series...');
            Object.values(indicatorSeriesRef.current).forEach(series => {
                if (series && chart) {
                    try {
                        chart.removeSeries(series);
                    } catch (e) {
                        console.warn('Error removing series:', e);
                    }
                }
            });
            indicatorSeriesRef.current = {};

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
                
                // Validate data format based on series type
                let validData;
                
                switch (indicator.type.toLowerCase()) {
                    case 'candlestick':
                        validData = indicator.data.filter(item => 
                            item && 
                            typeof item.time === 'number' && 
                            typeof item.open === 'number' && 
                            typeof item.high === 'number' && 
                            typeof item.low === 'number' && 
                            typeof item.close === 'number' &&
                            !isNaN(item.time) && 
                            !isNaN(item.open) && 
                            !isNaN(item.high) && 
                            !isNaN(item.low) && 
                            !isNaN(item.close)
                        );
                        break;
                        
                    case 'bar':
                        validData = indicator.data.filter(item => 
                            item && 
                            typeof item.time === 'number' && 
                            typeof item.open === 'number' && 
                            typeof item.high === 'number' && 
                            typeof item.low === 'number' && 
                            typeof item.close === 'number' &&
                            !isNaN(item.time) && 
                            !isNaN(item.open) && 
                            !isNaN(item.high) && 
                            !isNaN(item.low) && 
                            !isNaN(item.close)
                        );
                        break;
                        
                    case 'histogram':
                        validData = indicator.data.filter(item => 
                            item && 
                            typeof item.time === 'number' && 
                            typeof item.value === 'number' &&
                            !isNaN(item.time) && 
                            !isNaN(item.value)
                        ).map(item => {
                            // Preserve the individual bar color if provided
                            const dataPoint = { time: item.time, value: item.value };
                            if (item.color) {
                                dataPoint.color = item.color;
                            }
                            return dataPoint;
                        });
                        break;
                        
                    default: // line, area, baseline
                        validData = indicator.data.filter(item => 
                            item && 
                            typeof item.time === 'number' && 
                            typeof item.value === 'number' &&
                            !isNaN(item.time) && 
                            !isNaN(item.value)
                        );
                        break;
                }
                
                // Remove duplicates and sort by time (CRITICAL for lightweight-charts)
                const originalLength = validData.length;
                const timeMap = new Map();
                validData.forEach(item => {
                    // Keep the last occurrence of each timestamp
                    timeMap.set(item.time, item);
                });
                validData = Array.from(timeMap.values()).sort((a, b) => a.time - b.time);
                
                if (originalLength !== validData.length) {
                    console.warn(`${indicator.name}: Removed ${originalLength - validData.length} duplicate timestamps`);
                }
                console.log(`Validated data for ${indicator.name}: ${validData.length} unique points (sorted)`);
                
                // Debug: Check timestamp alignment with main chart data
                if (validData.length > 0 && data && data.length > 0) {
                    const indicatorFirst = validData[0].time;
                    const indicatorLast = validData[validData.length - 1].time;
                    const chartFirst = data[0].time;
                    const chartLast = data[data.length - 1].time;
                    
                    console.log(`${indicator.name} time range:`, {
                        first: indicatorFirst,
                        last: indicatorLast,
                        firstDate: new Date(indicatorFirst * 1000).toISOString(),
                        lastDate: new Date(indicatorLast * 1000).toISOString(),
                        duration: `${((indicatorLast - indicatorFirst) / 60).toFixed(1)} minutes`
                    });
                    console.log(`Main chart time range:`, {
                        first: chartFirst,
                        last: chartLast,
                        firstDate: new Date(chartFirst * 1000).toISOString(),
                        lastDate: new Date(chartLast * 1000).toISOString(),
                        duration: `${((chartLast - chartFirst) / 3600).toFixed(1)} hours`
                    });
                    
                    // Warn if time ranges don't overlap properly
                    if (indicatorFirst > chartLast || indicatorLast < chartFirst) {
                        console.error(`⚠️ ${indicator.name}: Time range does NOT overlap with chart data!`);
                    } else if (indicatorLast - indicatorFirst < (chartLast - chartFirst) * 0.5) {
                        console.warn(`⚠️ ${indicator.name}: Indicator covers only ${(((indicatorLast - indicatorFirst) / (chartLast - chartFirst)) * 100).toFixed(1)}% of chart time range. Backend should calculate indicators for the full range!`);
                    }
                }
                
                if (validData.length === 0) {
                    console.warn(`No valid data points found for indicator: ${indicator.name} (type: ${indicator.type})`);
                    return;
                }

                try {
                    // Check if chart is still valid before creating series
                    if (!chart || !chartRef.current) {
                        console.error('Chart is no longer valid, skipping indicator');
                        return;
                    }
                    
                    console.log(`Creating series for ${indicator.name} with type ${indicator.type}`);
                    let series;
                    
                    switch (indicator.type.toLowerCase()) {
                        case 'line':
                            const lineOptions = {
                                color: indicator.config.color,
                                lineWidth: indicator.config.lineWidth || 2,
                                title: indicator.config.title
                            };
                            
                            // Map lineStyle from number to LineStyle enum
                            if (indicator.config.lineStyle !== undefined) {
                                const lineStyleMap = {
                                    0: LineStyle.Solid,
                                    1: LineStyle.Dotted,
                                    2: LineStyle.Dashed,
                                    3: LineStyle.LargeDashed,
                                    4: LineStyle.SparseDotted
                                };
                                lineOptions.lineStyle = lineStyleMap[indicator.config.lineStyle] || LineStyle.Solid;
                            }
                            
                            series = chart.addSeries(LineSeries, lineOptions);
                            console.log(`Line series created for ${indicator.name}:`, !!series, 'with options:', lineOptions);
                            break;
                            
                        case 'area':
                            series = chart.addSeries(AreaSeries, {
                                lineColor: indicator.config.color,
                                topColor: indicator.config.topColor || indicator.config.color + '40', // 25% opacity
                                bottomColor: indicator.config.bottomColor || indicator.config.color + '10', // 6% opacity
                                lineWidth: indicator.config.lineWidth || 2,
                                title: indicator.config.title
                            });
                            break;
                            
                        case 'bar':
                            series = chart.addSeries(BarSeries, {
                                upColor: indicator.config.upColor || indicator.config.color,
                                downColor: indicator.config.downColor || indicator.config.color,
                                openVisible: indicator.config.openVisible !== false,
                                thinBars: indicator.config.thinBars || false,
                                title: indicator.config.title
                            });
                            break;
                            
                        case 'baseline':
                            series = chart.addSeries(BaselineSeries, {
                                baseValue: indicator.config.baseValue || { type: 'price', price: 0 },
                                lineColor: indicator.config.color,
                                topFillColor1: indicator.config.topFillColor1 || indicator.config.color + '40',
                                topFillColor2: indicator.config.topFillColor2 || indicator.config.color + '20',
                                bottomFillColor1: indicator.config.bottomFillColor1 || indicator.config.color + '20',
                                bottomFillColor2: indicator.config.bottomFillColor2 || indicator.config.color + '40',
                                lineWidth: indicator.config.lineWidth || 2,
                                title: indicator.config.title
                            });
                            break;
                            
                        case 'candlestick':
                            series = chart.addSeries(CandlestickSeries, {
                                upColor: indicator.config.upColor || '#4caf50',
                                downColor: indicator.config.downColor || '#e91e63',
                                wickUpColor: indicator.config.wickUpColor || indicator.config.upColor || '#4caf50',
                                wickDownColor: indicator.config.wickDownColor || indicator.config.downColor || '#e91e63',
                                borderUpColor: indicator.config.borderUpColor || indicator.config.upColor || '#4caf50',
                                borderDownColor: indicator.config.borderDownColor || indicator.config.downColor || '#e91e63',
                                title: indicator.config.title
                            });
                            break;
                            
                        case 'histogram':
                            const histogramOptions = {
                                color: indicator.config.color,
                                priceFormat: indicator.config.priceFormat || {
                                    type: 'volume',
                                },
                                title: indicator.config.title
                            };
                            
                            // Handle separate pane for indicators like volume
                            if (indicator.separatePane) {
                                // Create a unique price scale ID for this indicator
                                const priceScaleId = indicator.config.priceScaleId || `${indicator.name}_scale`;
                                histogramOptions.priceScaleId = priceScaleId;
                                
                                // Configure the separate price scale
                                histogramOptions.priceScaleOptions = {
                                    scaleMargins: {
                                        top: 0.8,    // 80% of the chart height is for the main chart
                                        bottom: 0,   // Volume starts at the bottom
                                    },
                                };
                            } else {
                                histogramOptions.priceScaleId = indicator.config.priceScaleId || '';
                            }
                            
                            series = chart.addSeries(HistogramSeries, histogramOptions);
                            
                            // Apply price scale options if separate pane
                            if (indicator.separatePane && series) {
                                const priceScaleId = indicator.config.priceScaleId || `${indicator.name}_scale`;
                                series.priceScale().applyOptions({
                                    scaleMargins: {
                                        top: 0.8,
                                        bottom: 0,
                                    },
                                });
                            }
                            break;
                            
                        default:
                            console.warn(`Unsupported indicator type: ${indicator.type}`);
                            return;
                    }

                    // Check if series was created successfully
                    if (!series) {
                        console.error(`Failed to create series for ${indicator.name}`, {
                            indicatorType: indicator.type,
                            indicatorConfig: indicator.config,
                            chartValid: !!chart,
                            chartRefValid: !!chartRef.current
                        });
                        return;
                    }
                    
                    console.log(`${indicator.name} series created, setting data...`);
                    
                    // Add a small delay before setting data to prevent timing issues
                    setTimeout(() => {
                        try {
                            // Check if series is still valid
                            if (!series || typeof series.setData !== 'function') {
                                console.error(`${indicator.name} series is invalid or destroyed`);
                                return;
                            }
                            
                            // Check if chart is still valid
                            if (!chart || !chartRef.current) {
                                console.error('Chart is no longer valid');
                                return;
                            }
                            
                            console.log(`Setting data for ${indicator.name} with ${validData.length} points`);
                            series.setData(validData);
                            indicatorSeriesRef.current[indicator.name] = series;
                            console.log(`${indicator.name} series added successfully`);
                        } catch (e) {
                            console.error(`Error setting data for ${indicator.name}:`, e);
                        }
                    }, 100 + (50 * index)); // Increased base delay and stagger
                    
                } catch (e) {
                    console.error(`Error adding ${indicator.name} series:`, e);
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
                    if (chartRef.current && seriesRef.current && data && data.length > 0) {
                        console.log('Attempting to draw boxes after delay...');
                        drawBoxes(chartRef.current, seriesRef.current, strategyData.boxes);
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
            <div className="flex items-center justify-center h-80 rounded-lg">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
                    <p className="text-gray-400">Loading chart data...</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-80 rounded-lg">
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

export function Chart({ provider, symbol, interval, activeStrategies = [] }) {
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
        />
    );
}