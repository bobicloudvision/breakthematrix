/**
 * Chart.jsx
 * 
 * Main chart orchestrator that:
 * - Fetches data via ChartDataService
 * - Manages WebSocket connections for real-time updates
 * - Passes data to ChartRenderer for visualization
 * 
 * This component can be adapted to use different data sources (live, replay, etc.)
 */

import React, { useEffect, useRef, useState } from 'react';
import { ChartRenderer } from './ChartRenderer';
import { fetchHistoricalData, fetchAllStrategyData, fetchIndicators } from './ChartDataService';
import { useChartWebSocket, getWebSocketStatusColors } from './useChartWebSocket';


export function Chart({ provider, symbol, interval, activeStrategies = [], enabledIndicators = [], onPriceUpdate }) {
    const [data, setData] = useState([]);
    const [realCount, setRealCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [strategyData, setStrategyData] = useState({});
    const [wsReconnectTrigger, setWsReconnectTrigger] = useState(0);
    
    // Refs shared between Chart and ChartRenderer for WebSocket updates
    const seriesRef = useRef(null);
    const seriesManagerRef = useRef(null);

    // WebSocket connection for real-time updates
    const { status: wsStatus, message: wsMessage, reconnect: wsReconnect } = useChartWebSocket({
        provider,
        symbol,
        interval,
        seriesRef,
        seriesManagerRef,
        onDataUpdate: setData,
        onRealCountUpdate: setRealCount,
        enabled: true, // Set to false for replay mode
        reconnectTrigger: wsReconnectTrigger
    });

    // Report latest price to parent component
    useEffect(() => {
        if (data.length > 0 && onPriceUpdate) {
            const latestCandle = data[data.length - 1];
            onPriceUpdate(latestCandle.close);
        }
    }, [data, onPriceUpdate]);

    // Fetch historical data when provider/symbol/interval changes
    useEffect(() => {
        const loadHistoricalData = async () => {
            console.log('Chart useEffect triggered:', { provider, symbol, interval });
            
            if (!provider || !symbol || !interval) {
                console.log('Missing required props, clearing chart data');
                setData([]);
                return;
            }

            try {
                setLoading(true);
                setError(null);
                
                const result = await fetchHistoricalData(provider, symbol, interval, 5000);
                setData(result.data);
                setRealCount(result.realCount);
            } catch (err) {
                setError(err.message);
                console.error('Failed to fetch historical data:', err);
            } finally {
                setLoading(false);
            }
        };

        loadHistoricalData();
    }, [provider, symbol, interval]);

    // Fetch strategy data when active strategies or symbol changes
    useEffect(() => {
        const loadStrategyData = async () => {
            const strategyDataMap = await fetchAllStrategyData(activeStrategies, symbol);
                setStrategyData(strategyDataMap);
        };

        loadStrategyData();
    }, [activeStrategies, symbol]);

    // Get WebSocket status colors for UI
    const statusColors = getWebSocketStatusColors(wsStatus);

    // Reconnect function
    const handleReconnect = () => {
        wsReconnect();
        // Force re-run of the WebSocket hook by incrementing trigger
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

            <ChartRenderer 
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
                onFetchIndicators={fetchIndicators} // Pass indicator fetching function
            />
        </div>
    );
}