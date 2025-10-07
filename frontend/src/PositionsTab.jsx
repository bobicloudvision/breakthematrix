import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';

export function PositionsTab() {
  const [activeSubTab, setActiveSubTab] = useState('active');
  const [openPositions, setOpenPositions] = useState([]);
  const [historicalPositions, setHistoricalPositions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [wsConnection, setWsConnection] = useState(null);
  const [wsStatus, setWsStatus] = useState('disconnected'); // disconnected, connecting, connected, error
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  const [closingPositions, setClosingPositions] = useState(new Set());
  const [editingPosition, setEditingPosition] = useState(null);
  const [editStopLoss, setEditStopLoss] = useState('');
  const [editTakeProfit, setEditTakeProfit] = useState('');
  const [savingLevels, setSavingLevels] = useState(false);

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

  // Close position by symbol
  const closePosition = async (symbol, positionId) => {
    try {
      setClosingPositions(prev => new Set([...prev, positionId]));
      
      const response = await fetch('http://localhost:8080/api/positions/close', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'accept': '*/*'
        },
        body: JSON.stringify({ symbol })
      });

      if (response.ok) {
        const result = await response.json();
        console.log('Position closed:', result);
        
        // Refresh positions
        if (wsStatus === 'connected') {
          requestPositionsViaWS();
        } else {
          fetchOpenPositions();
        }
        
        // Show success message (you could add a toast notification here)
        alert(`Successfully closed ${result.closedPositions} position(s) for ${symbol}\nOrder ID: ${result.orderId}\nQuantity: ${result.totalQuantity}`);
      } else {
        const errorText = await response.text();
        console.error('Failed to close position:', response.status, errorText);
        alert(`Failed to close position: ${errorText || response.statusText}`);
      }
    } catch (error) {
      console.error('Error closing position:', error);
      alert(`Error closing position: ${error.message}`);
    } finally {
      setClosingPositions(prev => {
        const newSet = new Set(prev);
        newSet.delete(positionId);
        return newSet;
      });
    }
  };

  // Open edit modal for stop-loss and take-profit
  const openEditModal = (position) => {
    setEditingPosition(position);
    setEditStopLoss(position.stopLoss || '');
    setEditTakeProfit(position.takeProfit || '');
  };

  // Close edit modal
  const closeEditModal = () => {
    setEditingPosition(null);
    setEditStopLoss('');
    setEditTakeProfit('');
  };

  // Update stop-loss
  const updateStopLoss = async (positionId, stopLoss) => {
    try {
      const response = await fetch(`http://localhost:8080/api/positions/${positionId}/stop-loss?stopLoss=${stopLoss}`, {
        method: 'POST',
        headers: {
          'accept': '*/*'
        }
      });

      if (response.ok) {
        console.log('Stop-loss updated successfully');
        return true;
      } else {
        const errorText = await response.text();
        console.error('Failed to update stop-loss:', response.status, errorText);
        throw new Error(errorText || response.statusText);
      }
    } catch (error) {
      console.error('Error updating stop-loss:', error);
      throw error;
    }
  };

  // Update take-profit
  const updateTakeProfit = async (positionId, takeProfit) => {
    try {
      const response = await fetch(`http://localhost:8080/api/positions/${positionId}/take-profit?takeProfit=${takeProfit}`, {
        method: 'POST',
        headers: {
          'accept': '*/*'
        }
      });

      if (response.ok) {
        console.log('Take-profit updated successfully');
        return true;
      } else {
        const errorText = await response.text();
        console.error('Failed to update take-profit:', response.status, errorText);
        throw new Error(errorText || response.statusText);
      }
    } catch (error) {
      console.error('Error updating take-profit:', error);
      throw error;
    }
  };

  // Save stop-loss and take-profit levels
  const saveLevels = async () => {
    if (!editingPosition) return;

    try {
      setSavingLevels(true);
      const promises = [];

      if (editStopLoss && editStopLoss !== editingPosition.stopLoss) {
        promises.push(updateStopLoss(editingPosition.positionId, parseFloat(editStopLoss)));
      }

      if (editTakeProfit && editTakeProfit !== editingPosition.takeProfit) {
        promises.push(updateTakeProfit(editingPosition.positionId, parseFloat(editTakeProfit)));
      }

      if (promises.length > 0) {
        await Promise.all(promises);
        
        // Refresh positions
        if (wsStatus === 'connected') {
          requestPositionsViaWS();
        } else {
          fetchOpenPositions();
        }

        alert('Stop-loss and take-profit updated successfully!');
        closeEditModal();
      } else {
        closeEditModal();
      }
    } catch (error) {
      alert(`Error updating levels: ${error.message}`);
    } finally {
      setSavingLevels(false);
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

  // Sort positions by entry time (newest first)
  const sortPositionsByDate = (positions) => {
    return [...positions].sort((a, b) => {
      const dateA = new Date(a.entryTime);
      const dateB = new Date(b.entryTime);
      return dateB - dateA; // Descending order (newest first)
    });
  };

  const renderPositionRow = (position) => (
    <tr key={position.positionId} className="border-b border-slate-600/30 hover:bg-slate-700/30 transition-all duration-200">
      <td className="px-3 py-3">
        <div className="flex flex-col gap-1">
          <span className="text-cyan-300 font-semibold">{position.symbol}</span>
          {position.strategyId && (
            <span className="text-slate-500 font-mono text-xs truncate max-w-[100px]" title={position.strategyId}>
              {position.strategyId}
            </span>
          )}
        </div>
      </td>
      <td className="px-3 py-3 text-center">
        <span className={`px-2 py-1 rounded text-xs font-medium inline-block ${
          position.side === 'LONG' 
            ? 'bg-green-500/20 text-green-400 border border-green-500/30' 
            : 'bg-red-500/20 text-red-400 border border-red-500/30'
        }`}>
          {position.side}
        </span>
      </td>
      <td className="px-3 py-3 text-center">
        {position.isOpen ? (
          <span className="px-2 py-1 rounded text-xs font-medium bg-blue-500/20 text-blue-400 border border-blue-500/30">
            OPEN
          </span>
        ) : (
          <span className="px-2 py-1 rounded text-xs font-medium bg-slate-500/20 text-slate-400 border border-slate-500/30">
            CLOSED
          </span>
        )}
      </td>
      <td className="px-3 py-3 text-right text-cyan-200 font-medium">
        {position.quantity > 0 ? position.quantity.toFixed(8) : 'N/A'}
      </td>
      <td className="px-3 py-3 text-right text-cyan-200 font-medium">
        {formatCurrency(position.entryPrice)}
      </td>
      <td className="px-3 py-3 text-right text-cyan-200 font-medium">
        {position.exitPrice ? formatCurrency(position.exitPrice) : '-'}
      </td>
      <td className="px-3 py-3 text-right text-cyan-200 font-medium">
        {position.entryValue > 0 ? formatCurrency(position.entryValue) : 'N/A'}
      </td>
      <td className="px-3 py-3 text-right">
        <div className={`font-semibold ${getPnLColorClass(position.totalPnL)}`}>
          {formatCurrency(position.totalPnL)}
        </div>
        <div className={`text-xs ${getPnLColorClass(position.totalPnL)}`}>
          {formatPercentage(position.pnLPercentage)}
        </div>
      </td>
      <td className="px-3 py-3 text-right">
        {position.stopLoss ? (
          <span className="text-red-400 text-sm">{formatCurrency(position.stopLoss)}</span>
        ) : (
          <span className="text-slate-500 text-sm">-</span>
        )}
      </td>
      <td className="px-3 py-3 text-right">
        {position.takeProfit ? (
          <span className="text-green-400 text-sm">{formatCurrency(position.takeProfit)}</span>
        ) : (
          <span className="text-slate-500 text-sm">-</span>
        )}
      </td>
      <td className="px-3 py-3 text-center text-cyan-200 text-sm">
        {formatDuration(position.duration)}
      </td>
      <td className="px-3 py-3 text-cyan-200 text-xs">
        <div>{formatDateTime(position.entryTime)}</div>
        {position.exitTime && (
          <div className="text-slate-400 mt-1">{formatDateTime(position.exitTime)}</div>
        )}
      </td>
      {activeSubTab === 'active' && (
        <td className="px-3 py-3 text-center">
          {position.isOpen && (
            <div className="flex items-center gap-2 justify-center">
              <button
                onClick={() => openEditModal(position)}
                className="px-3 py-1.5 text-xs font-medium rounded transition-all duration-200 bg-cyan-500/20 text-cyan-300 border border-cyan-500/30 hover:bg-cyan-500/30 hover:text-cyan-200"
                title="Edit SL/TP"
              >
                ⚙️ Edit
              </button>
              <button
                onClick={() => {
                  if (window.confirm(`Are you sure you want to close the ${position.symbol} ${position.side} position?\n\nEntry Price: ${formatCurrency(position.entryPrice)}\nQuantity: ${position.quantity}\nCurrent P&L: ${formatCurrency(position.totalPnL)}`)) {
                    closePosition(position.symbol, position.positionId);
                  }
                }}
                disabled={closingPositions.has(position.positionId)}
                className={`px-3 py-1.5 text-xs font-medium rounded transition-all duration-200 ${
                  closingPositions.has(position.positionId)
                    ? 'bg-slate-700/50 text-slate-500 border border-slate-600/30 cursor-not-allowed'
                    : 'bg-red-500/20 text-red-300 border border-red-500/30 hover:bg-red-500/30 hover:text-red-200'
                }`}
              >
                {closingPositions.has(position.positionId) ? '⏳' : '🔴 Close'}
              </button>
            </div>
          )}
        </td>
      )}
    </tr>
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
      <div className="flex-1 overflow-auto">
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
              <>
                {openPositions.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-4xl mb-4">📊</div>
                    <div className="text-slate-400 text-lg">No open positions</div>
                    <div className="text-slate-500 text-sm">Active trading positions will appear here</div>
                  </div>
                ) : (
                  <table className="w-full border-collapse text-sm">
                    <thead className="bg-slate-800/50 sticky top-0 z-10">
                      <tr className="border-b border-cyan-500/30">
                        <th className="px-3 py-3 text-left text-slate-300 font-semibold">Symbol</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Side</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Status</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Quantity</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Entry Price</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Exit Price</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Amount</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">P&L</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Stop Loss</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Take Profit</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Duration</th>
                        <th className="px-3 py-3 text-left text-slate-300 font-semibold">Time</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sortPositionsByDate(openPositions).map(renderPositionRow)}
                    </tbody>
                  </table>
                )}
              </>
            )}

            {activeSubTab === 'history' && (
              <>
                {historicalPositions.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-4xl mb-4">📈</div>
                    <div className="text-slate-400 text-lg">No historical positions</div>
                    <div className="text-slate-500 text-sm">Completed trades will appear here</div>
                  </div>
                ) : (
                  <table className="w-full border-collapse text-sm">
                    <thead className="bg-slate-800/50 sticky top-0 z-10">
                      <tr className="border-b border-cyan-500/30">
                        <th className="px-3 py-3 text-left text-slate-300 font-semibold">Symbol</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Side</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Status</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Quantity</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Entry Price</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Exit Price</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Amount</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">P&L</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Stop Loss</th>
                        <th className="px-3 py-3 text-right text-slate-300 font-semibold">Take Profit</th>
                        <th className="px-3 py-3 text-center text-slate-300 font-semibold">Duration</th>
                        <th className="px-3 py-3 text-left text-slate-300 font-semibold">Time</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sortPositionsByDate(historicalPositions).map(renderPositionRow)}
                    </tbody>
                  </table>
                )}
              </>
            )}
          </>
        )}
      </div>

      {/* Edit SL/TP Modal */}
      {editingPosition && createPortal(
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50" onClick={closeEditModal}>
          <div className="bg-slate-800 rounded-lg border border-cyan-500/30 shadow-2xl shadow-cyan-500/20 p-6 max-w-md w-full mx-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-xl font-semibold text-cyan-300">Edit Stop-Loss & Take-Profit</h3>
              <button
                onClick={closeEditModal}
                className="text-slate-400 hover:text-cyan-300 text-2xl leading-none"
              >
                ×
              </button>
            </div>

            <div className="space-y-4 mb-6">
              <div>
                <div className="text-sm text-slate-400 mb-2">Position</div>
                <div className="flex items-center gap-2">
                  <span className="text-cyan-300 font-semibold text-lg">{editingPosition.symbol}</span>
                  <span className={`px-2 py-1 rounded text-xs font-medium ${
                    editingPosition.side === 'LONG' 
                      ? 'bg-green-500/20 text-green-400 border border-green-500/30' 
                      : 'bg-red-500/20 text-red-400 border border-red-500/30'
                  }`}>
                    {editingPosition.side}
                  </span>
                </div>
              </div>

              <div>
                <div className="text-sm text-slate-400 mb-2">Entry Price</div>
                <div className="text-cyan-200 font-medium">{formatCurrency(editingPosition.entryPrice)}</div>
              </div>

              <div>
                <label className="block text-sm text-slate-400 mb-2">Stop Loss</label>
                <input
                  type="number"
                  step="0.01"
                  value={editStopLoss}
                  onChange={(e) => setEditStopLoss(e.target.value)}
                  placeholder="Enter stop loss price"
                  className="w-full px-4 py-2 bg-slate-900/50 border border-slate-600/50 rounded-lg text-cyan-200 placeholder-slate-500 focus:border-red-500/50 focus:outline-none focus:ring-2 focus:ring-red-500/20"
                />
              </div>

              <div>
                <label className="block text-sm text-slate-400 mb-2">Take Profit</label>
                <input
                  type="number"
                  step="0.01"
                  value={editTakeProfit}
                  onChange={(e) => setEditTakeProfit(e.target.value)}
                  placeholder="Enter take profit price"
                  className="w-full px-4 py-2 bg-slate-900/50 border border-slate-600/50 rounded-lg text-cyan-200 placeholder-slate-500 focus:border-green-500/50 focus:outline-none focus:ring-2 focus:ring-green-500/20"
                />
              </div>
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={closeEditModal}
                className="flex-1 px-4 py-2 text-sm font-medium rounded-lg bg-slate-700/50 text-slate-300 border border-slate-600/50 hover:bg-slate-700 hover:text-cyan-200 transition-all duration-200"
              >
                Cancel
              </button>
              <button
                onClick={saveLevels}
                disabled={savingLevels}
                className={`flex-1 px-4 py-2 text-sm font-medium rounded-lg transition-all duration-200 ${
                  savingLevels
                    ? 'bg-slate-700/50 text-slate-500 border border-slate-600/30 cursor-not-allowed'
                    : 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 hover:from-cyan-500/40 hover:to-blue-500/40 hover:shadow-lg hover:shadow-cyan-500/20'
                }`}
              >
                {savingLevels ? '⏳ Saving...' : '💾 Save'}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
