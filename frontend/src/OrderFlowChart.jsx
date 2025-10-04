import { 
    createChart, 
    ColorType, 
    LineStyle,
    HistogramSeries,
} from 'lightweight-charts';
import React, { useEffect, useRef, useState } from 'react';

export const OrderFlowChart = ({ provider, symbol, interval }) => {
    const chartContainerRef = useRef();
    const chartRef = useRef();
    const bidSeriesRef = useRef();
    const askSeriesRef = useRef();
    const [orderBookData, setOrderBookData] = useState({ 
        bids: [], 
        asks: [],
        bestBid: null,
        bestAsk: null,
        spread: null
    });
    const [trades, setTrades] = useState([]);
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
            rightPriceScale: {
                borderColor: 'rgba(197, 203, 206, 0.8)',
                scaleMargins: {
                    top: 0.05,
                    bottom: 0.05,
                },
                title: 'Volume',
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

        // Create bid series (green)
        const bidSeries = chart.addSeries(HistogramSeries, {
            color: 'rgba(34, 197, 94, 0.5)',
            priceFormat: {
                type: 'price',
                precision: 2,
                minMove: 0.01,
            },
            priceScaleId: 'right',
        });

        // Create ask series (red)
        const askSeries = chart.addSeries(HistogramSeries, {
            color: 'rgba(239, 68, 68, 0.5)',
            priceFormat: {
                type: 'price',
                precision: 2,
                minMove: 0.01,
            },
            priceScaleId: 'right',
        });

        chartRef.current = chart;
        bidSeriesRef.current = bidSeries;
        askSeriesRef.current = askSeries;

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

    // Update chart with order book data - using cumulative depth over time
    useEffect(() => {
        if (!bidSeriesRef.current || !askSeriesRef.current) return;
        if (!orderBookData.bids?.length || !orderBookData.asks?.length) return;

        const now = Math.floor(Date.now() / 1000);

        // Calculate total bid and ask volumes for this timestamp
        const totalBidVolume = orderBookData.bids
            .slice(0, 10)
            .reduce((sum, bid) => sum + parseFloat(bid.quantity || 0), 0);
        
        const totalAskVolume = orderBookData.asks
            .slice(0, 10)
            .reduce((sum, ask) => sum + parseFloat(ask.quantity || 0), 0);

        // Update with single data point per update (time-series of volume changes)
        bidSeriesRef.current.update({
            time: now,
            value: totalBidVolume,
            color: 'rgba(34, 197, 94, 0.7)',
        });

        askSeriesRef.current.update({
            time: now,
            value: totalAskVolume,
            color: 'rgba(239, 68, 68, 0.7)',
        });
    }, [orderBookData]);

    return (
        <div className="w-full h-full flex flex-col bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40">
            {/* Header */}
            <div className="px-6 py-4 border-b border-cyan-500/20 bg-slate-800/30">
                <div className="flex items-center justify-between">
                    <div>
                        <div className="flex items-center gap-3">
                            <h2 className="text-xl font-bold text-cyan-300">Order Flow Analysis</h2>
                            <div className="flex items-center gap-2">
                                <div className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`} />
                                <span className={`text-xs ${wsConnected ? 'text-green-400' : 'text-red-400'}`}>
                                    {wsConnected ? 'Connected' : 'Disconnected'}
                                </span>
                            </div>
                        </div>
                        <p className="text-sm text-slate-400">
                            {symbol} - Real-time order book depth
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
            </div>

            <div className="flex flex-1 min-h-0">
                {/* Order Book Table */}
                <div className="w-80 border-r border-cyan-500/20 bg-slate-900/40 overflow-hidden flex flex-col">
                    <div className="px-4 py-3 border-b border-cyan-500/20 bg-slate-800/50">
                        <h3 className="text-sm font-semibold text-cyan-300">Order Book</h3>
                    </div>
                    <div className="flex-1 overflow-y-auto">
                        {/* Asks (reversed order - lowest price at bottom) */}
                        <div className="border-b border-cyan-500/20">
                            {orderBookData.asks?.slice(0, 15).reverse().map((ask, idx) => (
                                <div 
                                    key={`ask-${idx}`} 
                                    className="px-4 py-1 flex justify-between text-xs hover:bg-red-500/10 relative"
                                >
                                    <div 
                                        className="absolute left-0 top-0 h-full bg-red-500/20"
                                        style={{ 
                                            width: `${(parseFloat(ask.quantity) / Math.max(...orderBookData.asks.slice(0, 15).map(a => parseFloat(a.quantity)))) * 100}%` 
                                        }}
                                    />
                                    <span className="text-red-400 relative z-10">{parseFloat(ask.price).toFixed(2)}</span>
                                    <span className="text-slate-300 relative z-10">{parseFloat(ask.quantity).toFixed(4)}</span>
                                </div>
                            ))}
                        </div>
                        
                        {/* Spread */}
                        <div className="px-4 py-2 bg-slate-800/50 text-center border-y border-cyan-500/30">
                            <div className="text-xs text-slate-400">Spread</div>
                            <div className="text-sm font-semibold text-yellow-400">
                                {orderBookData.spread 
                                    ? parseFloat(orderBookData.spread).toFixed(2)
                                    : orderBookData.asks?.[0] && orderBookData.bids?.[0] 
                                        ? (parseFloat(orderBookData.asks[0].price) - parseFloat(orderBookData.bids[0].price)).toFixed(2)
                                        : '---'
                                }
                            </div>
                            {orderBookData.bestBid && orderBookData.bestAsk && (
                                <div className="text-xs text-slate-500 mt-1">
                                    {parseFloat(orderBookData.bestBid).toFixed(2)} / {parseFloat(orderBookData.bestAsk).toFixed(2)}
                                </div>
                            )}
                        </div>

                        {/* Bids */}
                        <div>
                            {orderBookData.bids?.slice(0, 15).map((bid, idx) => (
                                <div 
                                    key={`bid-${idx}`} 
                                    className="px-4 py-1 flex justify-between text-xs hover:bg-green-500/10 relative"
                                >
                                    <div 
                                        className="absolute left-0 top-0 h-full bg-green-500/20"
                                        style={{ 
                                            width: `${(parseFloat(bid.quantity) / Math.max(...orderBookData.bids.slice(0, 15).map(b => parseFloat(b.quantity)))) * 100}%` 
                                        }}
                                    />
                                    <span className="text-green-400 relative z-10">{parseFloat(bid.price).toFixed(2)}</span>
                                    <span className="text-slate-300 relative z-10">{parseFloat(bid.quantity).toFixed(4)}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Chart Area */}
                <div className="flex-1 flex flex-col min-h-0">
                    <div className="px-4 py-2 bg-slate-800/30 border-b border-cyan-500/20">
                        <div className="flex items-center gap-4">
                            <h3 className="text-sm font-semibold text-cyan-300">Order Book Depth Over Time</h3>
                            <div className="flex items-center gap-4 text-xs">
                                <div className="flex items-center gap-1">
                                    <div className="w-3 h-3 bg-green-500/70 rounded"></div>
                                    <span className="text-slate-400">Bid Depth (Top 10)</span>
                                </div>
                                <div className="flex items-center gap-1">
                                    <div className="w-3 h-3 bg-red-500/70 rounded"></div>
                                    <span className="text-slate-400">Ask Depth (Top 10)</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div ref={chartContainerRef} className="flex-1" />
                </div>

                {/* Recent Trades */}
                <div className="w-64 border-l border-cyan-500/20 bg-slate-900/40 overflow-hidden flex flex-col">
                    <div className="px-4 py-3 border-b border-cyan-500/20 bg-slate-800/50">
                        <h3 className="text-sm font-semibold text-cyan-300">Recent Trades</h3>
                    </div>
                    <div className="flex-1 overflow-y-auto">
                        {trades.slice().reverse().map((trade, idx) => {
                            // isBuyerMaker = true means sell (maker is selling, taker is buying)
                            // isBuyerMaker = false means buy (maker is buying, taker is selling)
                            const isSell = trade.isBuyerMaker || trade.isAggressiveSell;
                            const isBuy = !trade.isBuyerMaker || trade.isAggressiveBuy;
                            
                            return (
                                <div 
                                    key={`trade-${idx}-${trade.timestamp}`} 
                                    className="px-4 py-2 border-b border-slate-700/30 hover:bg-slate-800/50"
                                >
                                    <div className="flex justify-between items-center">
                                        <span className={`text-sm font-semibold ${
                                            isSell ? 'text-red-400' : 'text-green-400'
                                        }`}>
                                            {parseFloat(trade.price).toFixed(2)}
                                        </span>
                                        <span className="text-xs text-slate-400">
                                            {new Date(trade.timestamp).toLocaleTimeString()}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center mt-1">
                                        <span className="text-xs text-slate-300">
                                            {parseFloat(trade.quantity).toFixed(6)}
                                        </span>
                                        <span className={`text-xs font-medium ${
                                            isSell ? 'text-red-400' : 'text-green-400'
                                        }`}>
                                            {isSell ? 'SELL' : 'BUY'}
                                        </span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
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

