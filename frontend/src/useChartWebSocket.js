/**
 * useChartWebSocket.js
 * 
 * Custom hook for managing WebSocket connection for real-time chart updates.
 * Handles:
 * - Connection lifecycle
 * - Real-time candle updates
 * - Real-time indicator updates
 * - Connection status
 * 
 * Can be easily disabled for replay mode or testing.
 */

import { useEffect, useRef, useState } from 'react';
import { 
    handleCandleUpdate, 
    handleIndicatorUpdate,
    extractCandleFromMessage,
    extractIndicatorFromMessage
} from './chartUpdateHandlers';

/**
 * Custom hook for chart WebSocket connection
 * 
 * @param {Object} params
 * @param {string|object} params.provider - Provider name or provider object
 * @param {string} params.symbol - Trading symbol
 * @param {string} params.interval - Time interval
 * @param {Object} params.seriesRef - Ref to main candlestick series
 * @param {Object} params.seriesManagerRef - Ref to series manager
 * @param {Function} params.onDataUpdate - Callback for updating candle data
 * @param {Function} params.onRealCountUpdate - Callback for updating real count
 * @param {boolean} params.enabled - Whether WebSocket is enabled (default: true)
 * @param {number} params.reconnectTrigger - Trigger for forcing reconnection
 * 
 * @returns {Object} { status, message, reconnect }
 */
export const useChartWebSocket = ({
    provider,
    symbol,
    interval,
    seriesRef,
    seriesManagerRef,
    onDataUpdate,
    onRealCountUpdate,
    enabled = true,
    reconnectTrigger = 0
}) => {
    const [wsStatus, setWsStatus] = useState('disconnected');
    const [wsMessage, setWsMessage] = useState('');
    const wsRef = useRef(null);

    useEffect(() => {
        // Don't connect if disabled (e.g., replay mode)
        if (!enabled) {
            setWsStatus('disconnected');
            setWsMessage('Disabled');
            return;
        }

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
                        const candleData = extractCandleFromMessage(message);
                        if (candleData) {
                            handleCandleUpdate(candleData, seriesRef, seriesManagerRef, onDataUpdate, onRealCountUpdate);
                        }
                        break;
                    
                    case 'indicatorUpdate':
                        const indicatorData = extractIndicatorFromMessage(message);
                        if (indicatorData) {
                            handleIndicatorUpdate(indicatorData, seriesManagerRef, setWsMessage);
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
    }, [provider, symbol, interval, enabled, reconnectTrigger]);

    // Reconnect function
    const reconnect = () => {
        if (wsRef.current) {
            wsRef.current.close();
            wsRef.current = null;
        }
    };

    return {
        status: wsStatus,
        message: wsMessage,
        reconnect
    };
};


/**
 * Get status colors for UI display
 */
export const getWebSocketStatusColors = (status) => {
    switch (status) {
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

