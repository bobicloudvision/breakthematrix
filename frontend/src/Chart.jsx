import { createChart, ColorType } from 'lightweight-charts';
import React, { useEffect, useRef, useState } from 'react';

// Global variable for default zoom level (last N candles)
if (typeof window !== 'undefined' && !window.BTM_DEFAULT_ZOOM_CANDLES) {
    window.BTM_DEFAULT_ZOOM_CANDLES = 250;
}

export const ChartComponent = props => {
    const {
        data,
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

            const candleSeries = chart.addCandlestickSeries({
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
                window.removeEventListener('resize', handleResize);
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
        }
    }, [data, realCount]);

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
const generateFutureData = (lastCandle, interval) => {
    if (!lastCandle) return [];
    
    const futureData = [];
    const intervalSec = getIntervalSeconds(interval);
    let currentTime = lastCandle.time;
    const lastPrice = lastCandle.close;
    
    // Get the end of the current day (23:59:59)
    const lastCandleDate = new Date(lastCandle.time * 1000);
    const endOfDay = new Date(lastCandleDate);
    endOfDay.setHours(23, 59, 59, 999);
    const endOfDayTimestamp = Math.floor(endOfDay.getTime() / 1000);
    
    console.log('Last candle time:', new Date(lastCandle.time * 1000).toISOString());
    console.log('End of day:', endOfDay.toISOString());
    console.log('Interval sec:', intervalSec);
    
    // Generate candles until end of day
    while (currentTime < endOfDayTimestamp) {
        currentTime += intervalSec;
        if (currentTime <= endOfDayTimestamp) {
            futureData.push({
                time: currentTime,
                open: lastPrice,
                high: lastPrice,
                low: lastPrice,
                close: lastPrice,
            });
        }
    }
    
    console.log('Generated future data:', futureData.length, 'candles');
    return futureData;
};

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

export function Chart({ provider, symbol, interval }) {
    const [data, setData] = useState([]);
    const [realCount, setRealCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

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
                const futureData = generateFutureData(chartData[chartData.length - 1], interval);
                
                // Fallback: if no future data generated, create at least 10 future candles
                const finalFutureData = futureData.length > 0 ? futureData : (() => {
                    const fallbackData = [];
                    const intervalSec = getIntervalSeconds(interval);
                    let currentTime = chartData[chartData.length - 1].time;
                    const lastPrice = chartData[chartData.length - 1].close;
                    
                    for (let i = 1; i <= 10; i++) {
                        currentTime += intervalSec;
                        fallbackData.push({
                            time: currentTime,
                            open: lastPrice,
                            high: lastPrice,
                            low: lastPrice,
                            close: lastPrice,
                        });
                    }
                    return fallbackData;
                })();
                
                const combinedData = [...chartData, ...finalFutureData];
                
                console.log('Transformed chart data:', chartData.length, 'candles +', futureData.length, 'future candles');
                setData(combinedData);
            } catch (err) {
                setError(err.message);
                console.error('Failed to fetch historical data:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchHistoricalData();
    }, [provider, symbol, interval]);

    return (
        <ChartComponent 
            data={data} 
            loading={loading} 
            error={error}
            realCount={realCount}
        />
    );
}