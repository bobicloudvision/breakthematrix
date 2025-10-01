import React, { useState, useEffect } from 'react';

export function PositionsTab() {
  const [activeSubTab, setActiveSubTab] = useState('active');
  const [openPositions, setOpenPositions] = useState([]);
  const [historicalPositions, setHistoricalPositions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [wsConnection, setWsConnection] = useState(null);
  const [wsStatus, setWsStatus] = useState('disconnected'); // disconnected, connecting, connected, error
  const [reconnectAttempts, setReconnectAttempts] = useState(0);

  // Fetch open positions
  const fetchOpenPositions = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch('http://localhost:8080/api/positions/open');
      if (response.ok) {
        const positions = await response.json();
        setOpenPositions(positions);
        console.log('Open positions loaded:', positions);
      } else {
        console.error('Failed to fetch open positions:', response.status);
        setError('Failed to load open positions');
      }
    } catch (error) {
      console.error('Error fetching open positions:', error);
      setError('Error loading open positions');
    } finally {
      setLoading(false);
    }
  };

  // Fetch historical positions
  const fetchHistoricalPositions = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch('http://localhost:8080/api/positions/history');
      if (response.ok) {
        const positions = await response.json();
        setHistoricalPositions(positions);
        console.log('Historical positions loaded:', positions);
      } else {
        console.error('Failed to fetch historical positions:', response.status);
        setError('Failed to load historical positions');
      }
    } catch (error) {
      console.error('Error fetching historical positions:', error);
      setError('Error loading historical positions');
    } finally {
      setLoading(false);
    }
  };

  // Manual WebSocket reconnection
  const reconnectWebSocket = () => {
    if (wsConnection) {
      wsConnection.close();
    }
    setReconnectAttempts(0);
    setWsStatus('connecting');
  };

  // Request positions via WebSocket
  const requestPositionsViaWS = () => {
    if (wsConnection && wsStatus === 'connected') {
      wsConnection.send(JSON.stringify({ 
        action: 'getPositions', 
        accountId: 'paper-main' 
      }));
    }
  };

  // WebSocket connection for real-time position updates
  useEffect(() => {
    const connectWebSocket = () => {
      try {
        setWsStatus('connecting');
        const ws = new WebSocket('ws://localhost:8080/positions-ws');
        
        ws.onopen = () => {
          console.log('WebSocket connected for positions');
          setWsStatus('connected');
          setReconnectAttempts(0);
          // Subscribe to position updates
          ws.send(JSON.stringify({
            action: 'subscribe'
          }));
          // Get current positions
          ws.send(JSON.stringify({ 
            action: 'getPositions', 
            accountId: 'paper-main' 
          }));
        };

        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            console.log('Received position update:', data);
            
            // Handle getPositions response
            if (data.action === 'getPositions' && data.positions) {
              console.log('Received positions data:', data.positions);
              setOpenPositions(data.positions);
            }
            // Handle different types of position updates
            else if (data.type === 'position_update' || data.type === 'position_opened' || data.type === 'position_closed') {
              // Update open positions list
              fetchOpenPositions();
            }
            // Handle general position updates
            else if (data.positions) {
              console.log('Received positions update:', data.positions);
              setOpenPositions(data.positions);
            }
          } catch (error) {
            console.error('Error parsing WebSocket message:', error);
          }
        };

        ws.onclose = () => {
          console.log('WebSocket disconnected for positions');
          setWsStatus('disconnected');
          // Attempt to reconnect with exponential backoff
          if (reconnectAttempts < 5) {
            const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
            setTimeout(() => {
              setReconnectAttempts(prev => prev + 1);
              connectWebSocket();
            }, delay);
          }
        };

        ws.onerror = (error) => {
          console.error('WebSocket error:', error);
          setWsStatus('error');
        };

        setWsConnection(ws);
      } catch (error) {
        console.error('Failed to create WebSocket connection:', error);
        setWsStatus('error');
      }
    };

    // Only connect WebSocket when component mounts
    connectWebSocket();

    // Cleanup on unmount
    return () => {
      if (wsConnection) {
        wsConnection.close();
      }
    };
  }, [reconnectAttempts]);

  // Load data when component mounts or sub-tab changes
  useEffect(() => {
    if (activeSubTab === 'active') {
      if (wsStatus === 'connected') {
        requestPositionsViaWS();
      } else {
        fetchOpenPositions();
      }
    } else if (activeSubTab === 'history') {
      fetchHistoricalPositions();
    }
  }, [activeSubTab, wsStatus]);

  // Format currency values
  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  };

  // Format percentage
  const formatPercentage = (value) => {
    if (value === 0 || value === null || value === undefined) {
      return '0.00%';
    }
    return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
  };

  // Format duration
  const formatDuration = (duration) => {
    if (!duration) return 'N/A';
    // Parse ISO 8601 duration (PT28.157093S)
    const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?/);
    if (!match) return duration;
    
    const hours = parseInt(match[1] || 0);
    const minutes = parseInt(match[2] || 0);
    const seconds = parseFloat(match[3] || 0);
    
    if (hours > 0) {
      return `${hours}h ${minutes}m ${Math.round(seconds)}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${Math.round(seconds)}s`;
    } else {
      return `${Math.round(seconds)}s`;
    }
  };

  // Format date/time
  const formatDateTime = (dateString) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleString();
  };

  // Get PnL color class
  const getPnLColorClass = (pnl) => {
    if (pnl > 0) return 'text-green-400';
    if (pnl < 0) return 'text-red-400';
    return 'text-slate-400';
  };

  const renderPosition = (position) => (
    <div key={position.positionId} className="bg-slate-800/50 rounded-lg p-4 border border-slate-600/30 hover:border-cyan-500/30 transition-all duration-200">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3">
          <span className="text-cyan-300 font-semibold text-lg">{position.symbol}</span>
          <span className={`px-2 py-1 rounded text-xs font-medium ${
            position.side === 'LONG' 
              ? 'bg-green-500/20 text-green-400 border border-green-500/30' 
              : 'bg-red-500/20 text-red-400 border border-red-500/30'
          }`}>
            {position.side}
          </span>
          {position.open ? (
            <span className="px-2 py-1 rounded text-xs font-medium bg-blue-500/20 text-blue-400 border border-blue-500/30">
              OPEN
            </span>
          ) : (
            <span className="px-2 py-1 rounded text-xs font-medium bg-slate-500/20 text-slate-400 border border-slate-500/30">
              CLOSED
            </span>
          )}
        </div>
        <div className={`text-right ${getPnLColorClass(position.totalPnL)}`}>
          <div className="text-lg font-semibold">{formatCurrency(position.totalPnL)}</div>
          <div className="text-sm">{formatPercentage(position.pnlPercentage)}</div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 text-sm">
        <div>
          <div className="text-slate-400 mb-1">Entry Price</div>
          <div className="text-cyan-200 font-medium">{formatCurrency(position.entryPrice)}</div>
        </div>
        <div>
          <div className="text-slate-400 mb-1">Quantity</div>
          <div className="text-cyan-200 font-medium">
            {position.quantity > 0 ? position.quantity.toFixed(8) : 'N/A'}
          </div>
        </div>
        <div>
          <div className="text-slate-400 mb-1">Entry Value</div>
          <div className="text-cyan-200 font-medium">
            {position.entryValue > 0 ? formatCurrency(position.entryValue) : 'N/A'}
          </div>
        </div>
        <div>
          <div className="text-slate-400 mb-1">Duration</div>
          <div className="text-cyan-200 font-medium">{formatDuration(position.duration)}</div>
        </div>
        <div>
          <div className="text-slate-400 mb-1">Entry Time</div>
          <div className="text-cyan-200 font-medium text-xs">{formatDateTime(position.entryTime)}</div>
        </div>
        {position.exitTime && (
          <div>
            <div className="text-slate-400 mb-1">Exit Time</div>
            <div className="text-cyan-200 font-medium text-xs">{formatDateTime(position.exitTime)}</div>
          </div>
        )}
        {position.exitPrice && (
          <div>
            <div className="text-slate-400 mb-1">Exit Price</div>
            <div className="text-cyan-200 font-medium">{formatCurrency(position.exitPrice)}</div>
          </div>
        )}
        {position.stopLoss && (
          <div>
            <div className="text-slate-400 mb-1">Stop Loss</div>
            <div className="text-red-400 font-medium">{formatCurrency(position.stopLoss)}</div>
          </div>
        )}
        {position.takeProfit && (
          <div>
            <div className="text-slate-400 mb-1">Take Profit</div>
            <div className="text-green-400 font-medium">{formatCurrency(position.takeProfit)}</div>
          </div>
        )}
      </div>

      {position.strategyId && (
        <div className="mt-3 pt-3 border-t border-slate-600/30">
          <div className="text-slate-400 text-sm">Strategy ID</div>
          <div className="text-cyan-200 font-mono text-xs">{position.strategyId}</div>
        </div>
      )}
    </div>
  );

  return (
    <div className="h-full flex flex-col">
      {/* Sub-tabs */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-cyan-500/20">
        <button
          onClick={() => setActiveSubTab('active')}
          className={`px-4 py-2 text-sm font-medium rounded-lg transition-all duration-200 ${
            activeSubTab === 'active'
              ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 shadow-lg shadow-cyan-500/20'
              : 'bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10'
          }`}
        >
          Active ({openPositions.length})
        </button>
        <button
          onClick={() => setActiveSubTab('history')}
          className={`px-4 py-2 text-sm font-medium rounded-lg transition-all duration-200 ${
            activeSubTab === 'history'
              ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 shadow-lg shadow-cyan-500/20'
              : 'bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10'
          }`}
        >
          History ({historicalPositions.length})
        </button>
        <div className="ml-auto flex items-center gap-3">
          {/* WebSocket Status Indicator */}
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${
              wsStatus === 'connected' ? 'bg-green-400' :
              wsStatus === 'connecting' ? 'bg-yellow-400' :
              wsStatus === 'error' ? 'bg-red-400' :
              'bg-slate-400'
            }`}></div>
            <span className="text-xs text-slate-400">
              {wsStatus === 'connected' ? 'Live' :
               wsStatus === 'connecting' ? 'Connecting...' :
               wsStatus === 'error' ? 'Error' :
               'Disconnected'}
            </span>
          </div>
          
          {wsStatus !== 'connected' && (
            <button
              onClick={reconnectWebSocket}
              className="px-3 py-2 text-sm font-medium rounded-lg bg-gradient-to-r from-cyan-500/20 to-blue-500/20 text-cyan-300 border border-cyan-500/30 hover:from-cyan-500/30 hover:to-blue-500/30 hover:text-cyan-200 hover:border-cyan-400/50 hover:shadow-md hover:shadow-cyan-500/10 transition-all duration-200"
            >
              🔌 Reconnect
            </button>
          )}
          
          <button
            onClick={() => {
              if (activeSubTab === 'active') {
                if (wsStatus === 'connected') {
                  requestPositionsViaWS();
                } else {
                  fetchOpenPositions();
                }
              } else {
                fetchHistoricalPositions();
              }
            }}
            className="px-3 py-2 text-sm font-medium rounded-lg bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10 transition-all duration-200"
          >
            🔄 Refresh {wsStatus === 'connected' && activeSubTab === 'active' ? '(WS)' : ''}
          </button>
        </div>
      </div>

      {/* Summary Stats */}
      {!loading && !error && (
        <div className="px-4 py-3 border-b border-cyan-500/20 bg-gradient-to-r from-slate-800/30 to-slate-700/30">
          <div className="flex items-center justify-between text-sm">
            <div className="text-slate-400">
              {activeSubTab === 'active' ? 'Open Positions' : 'Historical Positions'}: {activeSubTab === 'active' ? openPositions.length : historicalPositions.length}
            </div>
            <div className={`font-semibold ${getPnLColorClass(
              activeSubTab === 'active' 
                ? openPositions.reduce((sum, pos) => sum + pos.totalPnL, 0)
                : historicalPositions.reduce((sum, pos) => sum + pos.totalPnL, 0)
            )}`}>
              Total P&L: {formatCurrency(
                activeSubTab === 'active' 
                  ? openPositions.reduce((sum, pos) => sum + pos.totalPnL, 0)
                  : historicalPositions.reduce((sum, pos) => sum + pos.totalPnL, 0)
              )}
            </div>
          </div>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {loading && (
          <div className="flex items-center justify-center h-32">
            <div className="text-cyan-400 text-lg">Loading positions...</div>
          </div>
        )}

        {error && (
          <div className="flex items-center justify-center h-32">
            <div className="text-red-400 text-lg">{error}</div>
          </div>
        )}

        {!loading && !error && (
          <>
            {activeSubTab === 'active' && (
              <div className="space-y-4">
                {openPositions.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-4xl mb-4">📊</div>
                    <div className="text-slate-400 text-lg">No open positions</div>
                    <div className="text-slate-500 text-sm">Active trading positions will appear here</div>
                  </div>
                ) : (
                  openPositions.map(renderPosition)
                )}
              </div>
            )}

            {activeSubTab === 'history' && (
              <div className="space-y-4">
                {historicalPositions.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-4xl mb-4">📈</div>
                    <div className="text-slate-400 text-lg">No historical positions</div>
                    <div className="text-slate-500 text-sm">Completed trades will appear here</div>
                  </div>
                ) : (
                  historicalPositions.map(renderPosition)
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
