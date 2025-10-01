import { createChart, ColorType } from 'lightweight-charts';
import React, { useEffect, useRef, useState } from 'react';

export const ChartComponent = props => {
    const {
        data,
        loading = false,
        error = null,
        colors: {
            backgroundColor = 'transparent',
            textColor = 'black',
            upColor = '#26a69a',
            downColor = '#ef5350',
            wickUpColor = '#26a69a',
            wickDownColor = '#ef5350',
            borderUpColor = '#26a69a',
            borderDownColor = '#ef5350',
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
                height: 400,
                grid: { 
                    vertLines: { color: '#2a2e39' },
                    horzLines: { color: '#2a2e39' },
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
                chart.timeScale().fitContent();
            }

            window.addEventListener('resize', handleResize);

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
                chartRef.current.timeScale().fitContent();
            }
        }
    }, [data]);

    if (loading) {
        return (
            <div className="flex items-center justify-center h-80 bg-gray-800 rounded-lg">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
                    <p className="text-gray-400">Loading chart data...</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-80 bg-gray-800 rounded-lg">
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

export function Chart({ provider, symbol, interval }) {
    const [data, setData] = useState([]);
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
                console.log('Transformed chart data:', chartData);
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

    return (
        <ChartComponent 
            data={data} 
            loading={loading} 
            error={error}
        />
    );
}