import { 
    createChart, 
    ColorType, 
    LineStyle,
    HistogramSeries,
    CandlestickSeries,
} from 'lightweight-charts';
import React, { useEffect, useRef, useState } from 'react';
import { FootprintPrimitive } from './FootprintPrimitive';

export const OrderFlowChart = ({ provider, symbol, interval }) => {
    const chartContainerRef = useRef();
    const chartRef = useRef();
    const bidSeriesRef = useRef();
    const askSeriesRef = useRef();
    const candleSeriesRef = useRef();
    const footprintRef = useRef();
    const [orderBookData, setOrderBookData] = useState({ 
        bids: [], 
        asks: [],
        bestBid: null,
        bestAsk: null,
        spread: null
    });
    const [trades, setTrades] = useState([]);
    const [orderFlowCandles, setOrderFlowCandles] = useState([]);
    const [wsConnected, setWsConnected] = useState(false);
    const wsRef = useRef(null);

    // Initialize chart
    useEffect(() => {
        if (!chartContainerRef.current) return;

        const handleResize = () => {
            if (chartContainerRef.current && chartRef.current) {
                chartRef.current.applyOptions({ 
                    width: chartContainerRef.current.clientWidth,
                    height: chartContainerRef.current.clientHeight 
                });
            }
        };

        const chart = createChart(chartContainerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: 'transparent' },
                textColor: '#d1d5db',
            },
            width: chartContainerRef.current.clientWidth,
            height: chartContainerRef.current.clientHeight,
            grid: { 
                vertLines: { color: 'rgba(255, 255, 255, 0.1)' },
                horzLines: { color: 'rgba(255, 255, 255, 0.1)' },
            },
            leftPriceScale: {
                visible: false,
            },
            rightPriceScale: {
                borderColor: 'rgba(197, 203, 206, 0.8)',
                visible: true,
                scaleMargins: {
                    top: 0.1,
                    bottom: 0.2, // More space at bottom for volume bars
                },
            },
            timeScale: {
                visible: true,
                timeVisible: true,
                secondsVisible: true,
                borderColor: 'rgba(197, 203, 206, 0.8)',
            },
            crosshair: {
                mode: 0,
            },
        });

        // Create candlestick series with footprint side boxes
        const candleSeries = chart.addSeries(CandlestickSeries, {
            upColor: 'rgba(16, 185, 129, 0.3)', // Light green fill
            downColor: 'rgba(239, 68, 68, 0.3)', // Light red fill
            borderUpColor: 'rgba(16, 185, 129, 1)', // Solid green border
            borderDownColor: 'rgba(239, 68, 68, 1)', // Solid red border
            wickUpColor: 'rgba(16, 185, 129, 0.8)', // Green wick
            wickDownColor: 'rgba(239, 68, 68, 0.8)', // Red wick
            priceScaleId: 'right',
        });

        // Create and attach footprint primitive (pass chart and series)
        const footprint = new FootprintPrimitive(chart, candleSeries);
        footprint._minVolumeToShow = 0.01; // Lower threshold for crypto (small volumes)
        footprint._fontSize = 11; // Increased from 9 to 11 for larger, more readable text
        candleSeries.attachPrimitive(footprint);
        footprintRef.current = footprint;
        console.log('[OrderFlow] Footprint GRID LAYOUT - structured table view with cell backgrounds, borders, and centered text');

        chartRef.current = chart;
        candleSeriesRef.current = candleSeries;
        bidSeriesRef.current = null;
        askSeriesRef.current = null;

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            if (chartRef.current) {
                chartRef.current.remove();
            }
        };
    }, []);

    // Subscribe to order flow data via REST API
    useEffect(() => {
        if (!provider || !symbol) return;

        const subscribeToOrderFlow = async () => {
            try {
                // Subscribe to all order flow data types
                await fetch(
                    `http://localhost:8080/api/orderflow/subscribe/all?provider=${provider}&symbol=${symbol}`,
                    { method: 'POST' }
                );
                console.log(`Subscribed to order flow for ${symbol}`);
            } catch (error) {
                console.error('Error subscribing to order flow:', error);
            }
        };

        subscribeToOrderFlow();
    }, [provider, symbol]);

    // Fetch footprint candles with volumeProfile data
    useEffect(() => {
        if (!symbol || !interval) return;

        const fetchFootprintCandles = async () => {
            try {
                // Fetch historical candles
                const historicalResponse = await fetch(
                    `http://localhost:8080/api/footprint/historical?symbol=${symbol}&interval=${interval}&limit=200`
                );
                
                if (!historicalResponse.ok) {
                    console.warn('Failed to fetch historical footprint:', historicalResponse.status);
                    return;
                }
                
                const historicalData = await historicalResponse.json();
                console.log('Historical footprint response:', historicalData);
                
                let candlesArray = [];
                
                // Parse historical candles
                if (historicalData.success && historicalData.candles) {
                    candlesArray = historicalData.candles;
                } else if (historicalData.candles) {
                    candlesArray = historicalData.candles;
                } else if (Array.isArray(historicalData)) {
                    candlesArray = historicalData;
                }
                
                // Fetch current (incomplete) candle
                try {
                    const currentResponse = await fetch(
                        `http://localhost:8080/api/footprint/current?symbol=${symbol}&interval=${interval}`
                    );
                    
                    if (currentResponse.ok) {
                        const currentData = await currentResponse.json();
                        console.log('Current footprint response:', currentData);
                        
                        if (currentData.candle) {
                            // Add or update the current candle
                            const currentTime = Math.floor(Date.now() / 1000);
                            const intervalSeconds = interval === '1m' ? 60 : interval === '5m' ? 300 : interval === '15m' ? 900 : 60;
                            const currentCandleTime = Math.floor(currentTime / intervalSeconds) * intervalSeconds;
                            
                            // Check if last candle is the current candle (same time period)
                            const lastCandle = candlesArray[candlesArray.length - 1];
                            const lastTime = lastCandle?.openTime ? Math.floor(new Date(lastCandle.openTime).getTime() / 1000) : 0;
                            
                            if (lastTime >= currentCandleTime - intervalSeconds) {
                                // Update the last candle with current data
                                candlesArray[candlesArray.length - 1] = currentData.candle;
                            } else {
                                // Append as new candle
                                candlesArray.push(currentData.candle);
                            }
                            
                            console.log('Updated with current candle');
                        }
                    }
                } catch (currentError) {
                    console.log('Could not fetch current candle (optional):', currentError.message);
                }
                
                if (candlesArray.length > 0) {
                    console.log('Processing', candlesArray.length, 'footprint candles');
                    console.log('Sample candle:', candlesArray[0]);
                    
                    // Transform API response to FootprintPrimitive format
                    const transformedCandles = candlesArray.map((candle, index) => {
                        // Try to get timestamp from multiple possible fields
                        let time;
                        if (candle.openTime) {
                            time = Math.floor(new Date(candle.openTime).getTime() / 1000);
                        } else if (candle.time) {
                            time = typeof candle.time === 'number' ? candle.time : Math.floor(new Date(candle.time).getTime() / 1000);
                        } else {
                            // Fallback: use current time minus interval for each candle
                            const now = Math.floor(Date.now() / 1000);
                            const intervalSeconds = interval === '1m' ? 60 : interval === '5m' ? 300 : interval === '15m' ? 900 : 60;
                            time = now - ((candlesArray.length - index - 1) * intervalSeconds);
                        }
                        
                        // Map volumeProfile to volumeByPrice format expected by FootprintPrimitive
                        const volumeByPrice = candle.volumeProfile?.map(level => ({
                            price: level.price,
                            buyVolume: level.buyVolume || 0,
                            sellVolume: level.sellVolume || 0,
                            trades: level.tradeCount || 0,
                        })) || [];
                        
                        return {
                            time,
                            open: candle.open || candle.low || candle.close || 0,
                            high: candle.high || 0,
                            low: candle.low || 0,
                            close: candle.close || candle.high || candle.open || 0,
                            volume: candle.totalVolume || 0,
                            volumeByPrice,
                            // Additional footprint metrics
                            delta: candle.delta || 0,
                            cumulativeDelta: candle.cumulativeDelta || 0,
                            pointOfControl: candle.pointOfControl,
                            valueAreaHigh: candle.valueAreaHigh,
                            valueAreaLow: candle.valueAreaLow,
                        };
                    });
                    
                    console.log('Transformed candles:', transformedCandles.length);
                    console.log('Sample transformed candle:', transformedCandles[0]);
                    console.log('Sample volumeByPrice:', transformedCandles[0]?.volumeByPrice);
                    console.log('volumeByPrice entries:', transformedCandles[0]?.volumeByPrice?.length);
                    
                    // Verify data structure
                    if (transformedCandles[0]?.volumeByPrice?.length > 0) {
                        const firstLevel = transformedCandles[0].volumeByPrice[0];
                        console.log('First price level:', {
                            price: firstLevel.price,
                            buyVolume: firstLevel.buyVolume,
                            sellVolume: firstLevel.sellVolume,
                            delta: firstLevel.buyVolume - firstLevel.sellVolume
                        });
                    }
                    
                    setOrderFlowCandles(transformedCandles);
                    
                    // Update candlestick series with OHLC data
                    if (candleSeriesRef.current && transformedCandles.length > 0) {
                        const chartData = transformedCandles.map(candle => ({
                            time: candle.time,
                            open: candle.open,
                            high: candle.high,
                            low: candle.low,
                            close: candle.close,
                        }));
                        console.log('Setting chart data:', chartData.length, 'candles');
                        candleSeriesRef.current.setData(chartData);
                    }
                } else {
                    console.warn('No candles found in response');
                }
            } catch (error) {
                console.error('Error fetching footprint candles:', error);
            }
        };

        fetchFootprintCandles();
        const intervalId = setInterval(fetchFootprintCandles, 2000); // Refresh every 2 seconds for real-time
        
        return () => clearInterval(intervalId);
    }, [symbol, interval]);

    // Update footprint primitive when order flow candles change
    useEffect(() => {
        if (!footprintRef.current || !orderFlowCandles.length) return;
        
        console.log('[OrderFlow] Updating footprint with', orderFlowCandles.length, 'candles');
        console.log('[OrderFlow] First candle volumeByPrice count:', orderFlowCandles[0]?.volumeByPrice?.length);
        
        footprintRef.current.updateData(orderFlowCandles);
        
        // Force chart redraw using requestAnimationFrame (like BoxPrimitive/LinePrimitive)
        if (chartRef.current) {
            requestAnimationFrame(() => {
                footprintRef.current.updateAllViews();
                // Force chart redraw
                chartRef.current.timeScale().applyOptions({});
                console.log('[OrderFlow] Chart redraw triggered via requestAnimationFrame');
            });
        }
    }, [orderFlowCandles]);

    // WebSocket for real-time order flow data
    useEffect(() => {
        if (!provider || !symbol) return;

        const ws = new WebSocket('ws://localhost:8080/orderflow-ws');
        
        ws.onopen = () => {
            console.log('WebSocket connected');
            setWsConnected(true);
            // Subscribe to order flow data
            ws.send(JSON.stringify({
                action: 'subscribe',
                symbol: symbol,
                types: ['AGGREGATE_TRADE', 'ORDER_BOOK', 'BOOK_TICKER']
            }));
        };
        
        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                
                // Handle different data types based on dataType field
                if (data.dataType === 'ORDER_BOOK') {
                    // Order book update
                    const orderBook = data.orderBook || data;
                    setOrderBookData({
                        bids: orderBook.bids || [],
                        asks: orderBook.asks || [],
                        bestBid: orderBook.bestBid,
                        bestAsk: orderBook.bestAsk,
                        spread: orderBook.spread,
                        bidVolume5: orderBook.bidVolume5,
                        bidVolume10: orderBook.bidVolume10,
                        askVolume5: orderBook.askVolume5,
                        askVolume10: orderBook.askVolume10,
                    });
                } else if (data.dataType === 'AGGREGATE_TRADE') {
                    // Trade update - data is nested in trade object
                    const tradeData = data.trade || {};
                    const trade = {
                        price: tradeData.price,
                        quantity: tradeData.quantity,
                        timestamp: data.timestamp ? new Date(data.timestamp).getTime() : Date.now(),
                        isBuyerMaker: tradeData.isBuyerMaker || false,
                        isAggressiveBuy: tradeData.isAggressiveBuy || false,
                        isAggressiveSell: tradeData.isAggressiveSell || false,
                    };
                    setTrades(prev => [...prev.slice(-99), trade]); // Keep last 100 trades
                } else if (data.dataType === 'BOOK_TICKER') {
                    // Best bid/ask update
                    console.log('Book ticker:', data);
                }
            } catch (error) {
                console.error('Error parsing WebSocket data:', error);
            }
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
            setWsConnected(false);
        };

        ws.onclose = () => {
            console.log('WebSocket disconnected');
            setWsConnected(false);
        };

        wsRef.current = ws;

        return () => {
            if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
                // Unsubscribe before closing
                wsRef.current.send(JSON.stringify({
                    action: 'unsubscribe',
                    symbol: symbol
                }));
                wsRef.current.close();
            }
        };
    }, [provider, symbol]);

    // Order book data updates (not displayed on chart anymore - only in header)
    // Data is still collected for the header metrics

    return (
        <div className="w-full h-full flex flex-col bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40">
            {/* Header */}
            <div className="px-6 py-4 border-b border-cyan-500/20 bg-slate-800/30">
                <div className="flex items-center justify-between">
                    <div>
                        <div className="flex items-center gap-3">
                            <h2 className="text-xl font-bold text-cyan-300">Order Flow & Footprint Analysis</h2>
                            <div className="flex items-center gap-2">
                                <div className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`} />
                                <span className={`text-xs ${wsConnected ? 'text-green-400' : 'text-red-400'}`}>
                                    {wsConnected ? 'Connected' : 'Disconnected'}
                                </span>
                            </div>
                        </div>
                        <p className="text-sm text-slate-400">
                            {symbol} - Footprint chart with buy/sell volume by price
                        </p>
                    </div>
                    <div className="flex gap-6">
                        <div className="text-right">
                            <div className="text-xs text-slate-400">Best Bid</div>
                            <div className="text-lg font-semibold text-green-400">
                                {orderBookData.bestBid ? parseFloat(orderBookData.bestBid).toFixed(2) : '---'}
                            </div>
                        </div>
                        <div className="text-right">
                            <div className="text-xs text-slate-400">Spread</div>
                            <div className="text-lg font-semibold text-yellow-400">
                                {orderBookData.spread ? parseFloat(orderBookData.spread).toFixed(2) : '---'}
                            </div>
                        </div>
                        <div className="text-right">
                            <div className="text-xs text-slate-400">Best Ask</div>
                            <div className="text-lg font-semibold text-red-400">
                                {orderBookData.bestAsk ? parseFloat(orderBookData.bestAsk).toFixed(2) : '---'}
                            </div>
                        </div>
                        <div className="text-right">
                            <div className="text-xs text-slate-400">Bid Vol (5)</div>
                            <div className="text-sm font-semibold text-green-400">
                                {orderBookData.bidVolume5 ? parseFloat(orderBookData.bidVolume5).toFixed(4) : '---'}
                            </div>
                        </div>
                        <div className="text-right">
                            <div className="text-xs text-slate-400">Ask Vol (5)</div>
                            <div className="text-sm font-semibold text-red-400">
                                {orderBookData.askVolume5 ? parseFloat(orderBookData.askVolume5).toFixed(4) : '---'}
                            </div>
                        </div>
                    </div>
                </div>
                {/* Footprint Metrics */}
                {orderFlowCandles.length > 0 && (
                    <div className="px-6 py-3 border-b border-cyan-500/20 bg-slate-800/20">
                        <div className="flex items-center gap-6 text-xs">
                            <div className="flex items-center gap-2">
                                <span className="text-slate-400">Last Candle Delta:</span>
                                <span className={`font-semibold ${
                                    orderFlowCandles[orderFlowCandles.length - 1].delta > 0 
                                        ? 'text-green-400' 
                                        : 'text-red-400'
                                }`}>
                                    {orderFlowCandles[orderFlowCandles.length - 1].delta?.toFixed(2) || '0.00'}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-slate-400">Cumulative Delta:</span>
                                <span className={`font-semibold ${
                                    orderFlowCandles[orderFlowCandles.length - 1].cumulativeDelta > 0 
                                        ? 'text-green-400' 
                                        : 'text-red-400'
                                }`}>
                                    {orderFlowCandles[orderFlowCandles.length - 1].cumulativeDelta?.toFixed(2) || '0.00'}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-slate-400">POC:</span>
                                <span className="font-semibold text-yellow-400">
                                    {orderFlowCandles[orderFlowCandles.length - 1].pointOfControl?.toFixed(2) || '---'}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-slate-400">Value Area:</span>
                                <span className="font-semibold text-cyan-400">
                                    {orderFlowCandles[orderFlowCandles.length - 1].valueAreaLow?.toFixed(2) || '---'}
                                    {' - '}
                                    {orderFlowCandles[orderFlowCandles.length - 1].valueAreaHigh?.toFixed(2) || '---'}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-slate-400">Total Candles:</span>
                                <span className="font-semibold text-slate-300">
                                    {orderFlowCandles.length}
                                </span>
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* Footprint Chart - Full Width */}
            <div className="flex-1 flex flex-col min-h-0">
                <div className="px-4 py-2 bg-slate-800/30 border-b border-cyan-500/20">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-4">
                            <h3 className="text-sm font-semibold text-cyan-300">Footprint Chart - Volume by Price</h3>
                            <div className="flex items-center gap-4 text-xs">
                                <div className="flex items-center gap-1">
                                    <div className="w-3 h-3 bg-green-500/70 rounded"></div>
                                    <span className="text-slate-400">Buy Volume (Green = More Buyers)</span>
                                </div>
                                <div className="flex items-center gap-1">
                                    <div className="w-3 h-3 bg-red-500/70 rounded"></div>
                                    <span className="text-slate-400">Sell Volume (Red = More Sellers)</span>
                                </div>
                                <div className="flex items-center gap-1">
                                    <div className="w-3 h-3 bg-yellow-500/70 rounded"></div>
                                    <span className="text-slate-400">VAH/VAL (Value Area)</span>
                                </div>
                                <div className="flex items-center gap-1">
                                    <div className="w-3 h-3 bg-cyan-500/70 rounded"></div>
                                    <span className="text-slate-400">POC (Point of Control)</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div ref={chartContainerRef} className="flex-1" />
            </div>
        </div>
    );
};

// Wrapper component with data fetching
export const OrderFlow = ({ provider, symbol, interval }) => {
    if (!provider) {
        return (
            <div className="flex items-center justify-center h-full w-full">
                <div className="text-center">
                    <p className="text-cyan-300 text-xl mb-2">Select a provider to view order flow</p>
                </div>
            </div>
        );
    }

    return (
        <OrderFlowChart 
            provider={provider}
            symbol={symbol}
            interval={interval}
        />
    );
};

