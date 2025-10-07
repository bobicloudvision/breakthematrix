import React, { useState, useEffect } from 'react';

export function ReplayTab({ provider, symbol, interval }) {
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [speed, setSpeed] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [error, setError] = useState(null);
  const [availableData, setAvailableData] = useState(null);
  const [loadingAvailableData, setLoadingAvailableData] = useState(false);
  const [activeSession, setActiveSession] = useState(null);
  const [sessionStatus, setSessionStatus] = useState(null);
  const [controlLoading, setControlLoading] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);
  const wsRef = React.useRef(null);

  // Fetch available data on mount and when provider/symbol/interval changes
  useEffect(() => {
    const fetchAvailableData = async () => {
      setLoadingAvailableData(true);
      try {
        const res = await fetch('http://localhost:8080/api/replay/available-data', {
          method: 'GET',
          headers: {
            'accept': '*/*',
          },
        });

        if (res.ok) {
          const data = await res.json();
          setAvailableData(data);
          
          // Auto-fill times if there's matching data
          if (provider && symbol && interval && data.data) {
            const matchingDataset = data.data.find(
              d => d.provider === provider && d.symbol === symbol && d.interval === interval
            );
            
            if (matchingDataset) {
              // Convert ISO string to datetime-local format
              const start = new Date(matchingDataset.startTime).toISOString().slice(0, 16);
              const end = new Date(matchingDataset.endTime).toISOString().slice(0, 16);
              setStartTime(start);
              setEndTime(end);
            }
          }
        }
      } catch (err) {
        console.error('Error fetching available data:', err);
      } finally {
        setLoadingAvailableData(false);
      }
    };

    fetchAvailableData();
  }, [provider, symbol, interval]);

  const handleCreateReplay = async () => {
    if (!provider || !symbol || !interval) {
      setError('Please select provider, symbol, and interval first');
      return;
    }

    if (!startTime || !endTime) {
      setError('Please select both start and end times');
      return;
    }

    setIsLoading(true);
    setError(null);
    setResponse(null);

    try {
      // Convert datetime-local format to ISO 8601 format with milliseconds
      const startTimeISO = new Date(startTime).toISOString();
      const endTimeISO = new Date(endTime).toISOString();

      const payload = {
        provider,
        symbol,
        interval,
        startTime: startTimeISO,
        endTime: endTimeISO,
        speed: parseFloat(speed)
      };

      const res = await fetch('http://localhost:8080/api/replay/create', {
        method: 'POST',
        headers: {
          'accept': '*/*',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`Failed to create replay: ${res.status} - ${errorText}`);
      }

      const data = await res.json();
      setResponse(data);
      setActiveSession(data.sessionId);
      setSessionStatus(data.status);
      console.log('Replay created:', data);
    } catch (err) {
      console.error('Error creating replay:', err);
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const fetchSessionStatus = async (sessionId) => {
    try {
      const res = await fetch(`http://localhost:8080/api/replay/${sessionId}/status`, {
        method: 'GET',
        headers: {
          'accept': '*/*',
        },
      });

      if (res.ok) {
        const status = await res.json();
        setSessionStatus(status);
        return status;
      }
    } catch (err) {
      console.error('Error fetching session status:', err);
    }
    return null;
  };

  // WebSocket connection for real-time updates
  useEffect(() => {
    if (!activeSession) {
      // Clean up WebSocket if session ends
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
        setWsConnected(false);
      }
      return;
    }

    // Connect to WebSocket
    const ws = new WebSocket('ws://localhost:8080/replay-ws');
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('WebSocket connected');
      setWsConnected(true);
      
      // Subscribe to the session
      ws.send(JSON.stringify({
        action: 'subscribe',
        sessionId: activeSession
      }));
    };

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        
        if (message.type === 'subscribed') {
          console.log('Subscribed to session:', message.sessionId);
          if (message.status) {
            setSessionStatus(message.status);
          }
        } else if (message.type === 'replayUpdate') {
          // Real-time replay updates with candle and status
          console.log('Replay update - Candle:', message.currentIndex, '/', message.totalCandles, 
                      `(${message.progress.toFixed(1)}%)`, message.candle?.symbol, 
                      '@', message.candle?.close);
          
          // Update status with the full message (it contains all status fields)
          setSessionStatus({
            state: message.state,
            currentIndex: message.currentIndex,
            totalCandles: message.totalCandles,
            progress: message.progress,
            speed: message.speed,
            currentTime: message.candle?.openTime,
            symbol: message.candle?.symbol,
            interval: message.candle?.interval,
            sessionId: message.sessionId,
            indicatorCount: Object.keys(message.indicators || {}).length
          });

          // Candle data is available for chart updates if needed
          // message.candle contains: open, high, low, close, volume, time, etc.
        } else if (message.type === 'complete') {
          console.log('Replay complete');
          // Optionally show a completion notification
        } else if (message.type === 'error') {
          console.error('WebSocket error:', message.message);
          setError(message.message);
        }
      } catch (err) {
        console.error('Error parsing WebSocket message:', err);
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

    // Cleanup on unmount or session change
    return () => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.close();
      }
    };
  }, [activeSession]);

  const handlePlayPause = async () => {
    if (!activeSession) return;

    setControlLoading(true);
    try {
      const action = sessionStatus?.state === 'PLAYING' ? 'pause' : 'play';
      const res = await fetch(`http://localhost:8080/api/replay/${activeSession}/${action}`, {
        method: 'POST',
        headers: {
          'accept': '*/*',
        },
      });

      if (res.ok) {
        const data = await res.json();
        console.log(`Replay ${action}:`, data.message);
        // Fetch updated status immediately for responsive UI
        await fetchSessionStatus(activeSession);
      }
    } catch (err) {
      console.error(`Error toggling replay:`, err);
      setError(err.message);
    } finally {
      setControlLoading(false);
    }
  };

  const handleStop = async () => {
    if (!activeSession) return;

    setControlLoading(true);
    try {
      const res = await fetch(`http://localhost:8080/api/replay/${activeSession}`, {
        method: 'DELETE',
        headers: {
          'accept': '*/*',
        },
      });

      if (res.ok) {
        const data = await res.json();
        console.log('Replay stopped:', data.message);
        setActiveSession(null);
        setSessionStatus(null);
        setResponse(null);
      }
    } catch (err) {
      console.error('Error stopping replay:', err);
      setError(err.message);
    } finally {
      setControlLoading(false);
    }
  };

  const handleSpeedChange = async (newSpeed) => {
    if (!activeSession) return;

    try {
      const res = await fetch(`http://localhost:8080/api/replay/${activeSession}/speed`, {
        method: 'POST',
        headers: {
          'accept': '*/*',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ speed: parseFloat(newSpeed) }),
      });

      if (res.ok) {
        const data = await res.json();
        console.log('Speed changed:', data.message);
        setSpeed(newSpeed);
        // Fetch updated status immediately for responsive UI
        await fetchSessionStatus(activeSession);
      }
    } catch (err) {
      console.error('Error changing speed:', err);
    }
  };

  const setQuickTimeRange = (hours) => {
    const now = new Date();
    const start = new Date(now.getTime() - hours * 60 * 60 * 1000);
    
    setEndTime(now.toISOString().slice(0, 16));
    setStartTime(start.toISOString().slice(0, 16));
  };

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="max-w-4xl mx-auto">
        <div className="mb-6">
          <h2 className="text-2xl font-bold bg-gradient-to-r from-zinc-400 to-zinc-500 bg-clip-text text-transparent mb-2">
            Replay Session
          </h2>
          <p className="text-zinc-400 text-sm">
            Create a replay session to simulate historical market data
          </p>
        </div>

        {/* Active Session Controls */}
        {activeSession && sessionStatus && (
          <div className="mb-6 p-6 bg-gradient-to-r from-purple-900/30 to-blue-900/30 rounded-lg border border-purple-500/50 shadow-lg shadow-purple-500/20">
            <div className="flex items-center justify-between mb-4">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="text-lg font-bold text-purple-300">Active Replay Session</h3>
                  {wsConnected && (
                    <div className="flex items-center gap-1 px-2 py-0.5 bg-green-500/20 rounded-full border border-green-500/30">
                      <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse"></div>
                      <span className="text-xs text-green-300 font-medium">Live</span>
                    </div>
                  )}
                </div>
                <p className="text-xs text-purple-400 font-mono">{activeSession}</p>
              </div>
              <div className={`px-3 py-1 rounded-full text-xs font-medium ${
                sessionStatus.state === 'PLAYING' 
                  ? 'bg-green-500/20 text-green-300 border border-green-500/50'
                  : sessionStatus.state === 'PAUSED'
                  ? 'bg-yellow-500/20 text-yellow-300 border border-yellow-500/50'
                  : 'bg-zinc-500/20 text-zinc-300 border border-zinc-500/50'
              }`}>
                {sessionStatus.state}
              </div>
            </div>

            {/* Progress Bar */}
            <div className="mb-4">
              <div className="flex justify-between text-xs text-slate-400 mb-2">
                <span>Progress: {sessionStatus.currentIndex} / {sessionStatus.totalCandles} candles</span>
                <span>{sessionStatus.progress.toFixed(1)}%</span>
              </div>
              <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-gradient-to-r from-purple-500 to-blue-500 transition-all duration-300"
                  style={{ width: `${sessionStatus.progress}%` }}
                />
              </div>
            </div>

            {/* Session Info Grid */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4 text-xs">
              <div className="bg-slate-900/50 p-2 rounded">
                <span className="text-slate-400 block">Current Time</span>
                <span className="text-purple-200 font-medium">
                  {new Date(sessionStatus.currentTime).toLocaleString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit'
                  })}
                </span>
              </div>
              <div className="bg-slate-900/50 p-2 rounded">
                <span className="text-slate-400 block">Speed</span>
                <span className="text-purple-200 font-medium">{sessionStatus.speed}x</span>
              </div>
              <div className="bg-slate-900/50 p-2 rounded">
                <span className="text-slate-400 block">Symbol</span>
                <span className="text-purple-200 font-medium">{sessionStatus.symbol}</span>
              </div>
              <div className="bg-slate-900/50 p-2 rounded">
                <span className="text-slate-400 block">Interval</span>
                <span className="text-purple-200 font-medium">{sessionStatus.interval}</span>
              </div>
            </div>

            {/* Playback Controls */}
            <div className="flex gap-3">
              <button
                onClick={handlePlayPause}
                disabled={controlLoading}
                className="flex-1 px-4 py-2 bg-gradient-to-r from-purple-500/30 to-blue-500/30 text-purple-100 rounded-lg border border-purple-400/50 hover:from-purple-500/40 hover:to-blue-500/40 transition-all duration-200 font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                {sessionStatus.state === 'PLAYING' ? (
                  <>
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                    Pause
                  </>
                ) : (
                  <>
                    <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                    </svg>
                    Play
                  </>
                )}
              </button>
              
              <button
                onClick={handleStop}
                disabled={controlLoading}
                className="px-4 py-2 bg-gradient-to-r from-red-500/30 to-red-600/30 text-red-100 rounded-lg border border-red-400/50 hover:from-red-500/40 hover:to-red-600/40 transition-all duration-200 font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8 7a1 1 0 00-1 1v4a1 1 0 001 1h4a1 1 0 001-1V8a1 1 0 00-1-1H8z" clipRule="evenodd" />
                </svg>
                Stop
              </button>
            </div>

            {/* Speed Control for Active Session */}
            <div className="mt-4 p-3 bg-slate-900/30 rounded-lg">
              <label className="text-xs text-slate-400 mb-2 block">Playback Speed</label>
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min="0"
                  max="10"
                  step="0.5"
                  value={sessionStatus.speed}
                  onChange={(e) => handleSpeedChange(parseFloat(e.target.value))}
                  className="flex-1 h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-purple-500"
                />
                <div className="min-w-[60px] px-2 py-1 bg-slate-900/50 text-purple-200 rounded text-center font-mono text-sm">
                  {sessionStatus.speed}x
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Current Settings Display - Only show if no active session */}
        {!activeSession && (
          <div className="mb-6 p-4 bg-gradient-to-r from-zinc-800/50 to-zinc-700/50 rounded-lg border border-zinc-500/30">
            <h3 className="text-sm font-medium text-zinc-300 mb-3">Current Settings</h3>
            <div className="grid grid-cols-3 gap-4 text-sm">
              <div>
                <span className="text-slate-400">Provider:</span>
                <span className="ml-2 text-zinc-200 font-medium">{provider || 'Not selected'}</span>
              </div>
              <div>
                <span className="text-slate-400">Symbol:</span>
                <span className="ml-2 text-zinc-200 font-medium">{symbol || 'Not selected'}</span>
              </div>
              <div>
                <span className="text-slate-400">Interval:</span>
                <span className="ml-2 text-zinc-200 font-medium">{interval || 'Not selected'}</span>
              </div>
            </div>
          </div>
        )}

        {/* Session Creation Form - Only show if no active session */}
        {!activeSession && (
          <>
            {/* Available Data Display */}
            {loadingAvailableData ? (
          <div className="mb-6 p-4 bg-gradient-to-r from-zinc-800/50 to-zinc-700/50 rounded-lg border border-zinc-500/30">
            <div className="flex items-center justify-center gap-2 text-zinc-300">
              <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
              <span className="text-sm">Loading available data...</span>
            </div>
          </div>
        ) : availableData && availableData.data && availableData.data.length > 0 ? (
          <div className="mb-6 p-4 bg-gradient-to-r from-zinc-800/50 to-zinc-700/50 rounded-lg border border-zinc-500/30">
            <h3 className="text-sm font-medium text-zinc-300 mb-3">Available Data ({availableData.totalDataSets} dataset{availableData.totalDataSets !== 1 ? 's' : ''})</h3>
            <div className="space-y-3">
              {availableData.data.map((dataset) => {
                const isMatchingDataset = dataset.provider === provider && dataset.symbol === symbol && dataset.interval === interval;
                return (
                  <div
                    key={dataset.key}
                    className={`p-3 rounded-lg border ${
                      isMatchingDataset
                        ? 'bg-zinc-500/10 border-zinc-400/50'
                        : 'bg-zinc-900/30 border-zinc-600/30'
                    }`}
                  >
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                      <div>
                        <span className="text-slate-400 block">Provider / Symbol</span>
                        <span className={`font-medium ${isMatchingDataset ? 'text-zinc-200' : 'text-zinc-300'}`}>
                          {dataset.provider} / {dataset.symbol}
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Interval</span>
                        <span className={`font-medium ${isMatchingDataset ? 'text-zinc-200' : 'text-zinc-300'}`}>
                          {dataset.interval}
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Duration / Candles</span>
                        <span className={`font-medium ${isMatchingDataset ? 'text-zinc-200' : 'text-zinc-300'}`}>
                          {dataset.duration} / {dataset.candleCount}
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Time Range</span>
                        <span className={`font-medium ${isMatchingDataset ? 'text-zinc-200' : 'text-zinc-300'}`}>
                          {new Date(dataset.startTime).toLocaleString('en-US', { 
                            month: 'short', 
                            day: 'numeric', 
                            hour: '2-digit', 
                            minute: '2-digit' 
                          })} - {new Date(dataset.endTime).toLocaleString('en-US', { 
                            month: 'short', 
                            day: 'numeric', 
                            hour: '2-digit', 
                            minute: '2-digit' 
                          })}
                        </span>
                      </div>
                    </div>
                    {isMatchingDataset && (
                      <div className="mt-2 text-xs text-zinc-300 flex items-center gap-1">
                        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                        </svg>
                        Matching current settings - time range pre-filled below
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        ) : null}

        {/* Time Range Selection */}
        <div className="mb-6 p-4 bg-gradient-to-r from-zinc-800/50 to-zinc-700/50 rounded-lg border border-zinc-500/30">
          <h3 className="text-sm font-medium text-zinc-300 mb-4">Time Range</h3>
          
          {/* Quick Time Range Buttons */}
          <div className="mb-4">
            <label className="text-xs text-slate-400 mb-2 block">Quick Select</label>
            <div className="flex gap-2 flex-wrap">
              {[1, 6, 12, 24, 48, 72].map((hours) => (
                <button
                  key={hours}
                  onClick={() => setQuickTimeRange(hours)}
                  className="px-3 py-1.5 text-xs font-medium rounded-md bg-gradient-to-r from-zinc-700/60 to-zinc-600/60 text-zinc-300 border border-zinc-600/40 hover:from-zinc-500/20 hover:to-zinc-600/20 hover:text-zinc-200 hover:border-zinc-500/40 transition-all duration-200"
                >
                  Last {hours}h
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs text-slate-400 mb-2 block">Start Time</label>
              <input
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                className="w-full px-3 py-2 bg-zinc-900/50 text-zinc-200 rounded-lg border border-zinc-500/30 focus:outline-none focus:ring-2 focus:ring-zinc-500/50 focus:border-zinc-400/50 transition-all duration-200"
              />
            </div>
            <div>
              <label className="text-xs text-slate-400 mb-2 block">End Time</label>
              <input
                type="datetime-local"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
                className="w-full px-3 py-2 bg-zinc-900/50 text-zinc-200 rounded-lg border border-zinc-500/30 focus:outline-none focus:ring-2 focus:ring-zinc-500/50 focus:border-zinc-400/50 transition-all duration-200"
              />
            </div>
          </div>
        </div>

        {/* Speed Control */}
        <div className="mb-6 p-4 bg-gradient-to-r from-zinc-800/50 to-zinc-700/50 rounded-lg border border-zinc-500/30">
          <h3 className="text-sm font-medium text-zinc-300 mb-4">Playback Speed</h3>
          <div className="flex items-center gap-4">
            <input
              type="range"
              min="0"
              max="10"
              step="0.5"
              value={speed}
              onChange={(e) => setSpeed(e.target.value)}
              className="flex-1 h-2 bg-zinc-700 rounded-lg appearance-none cursor-pointer accent-zinc-500"
            />
            <div className="min-w-[80px] px-3 py-2 bg-zinc-900/50 text-zinc-200 rounded-lg border border-zinc-500/30 text-center font-mono">
              {speed}x
            </div>
          </div>
          <div className="flex justify-between text-xs text-zinc-500 mt-2">
            <span>Paused (0x)</span>
            <span>Normal (1x)</span>
            <span>Fast (10x)</span>
          </div>
        </div>

        {/* Create Button */}
        <div className="mb-6">
          <button
            onClick={handleCreateReplay}
            disabled={isLoading || !provider || !symbol || !interval}
            className={`w-full px-6 py-3 text-base font-medium rounded-lg transition-all duration-200 ${
              isLoading || !provider || !symbol || !interval
                ? 'bg-zinc-700/50 text-zinc-500 border border-zinc-600/30 cursor-not-allowed'
                : 'bg-gradient-to-r from-zinc-500/30 to-zinc-600/30 text-zinc-100 border border-zinc-400/50 shadow-lg shadow-zinc-500/20 hover:from-zinc-500/40 hover:to-zinc-600/40 hover:shadow-xl hover:shadow-zinc-500/30'
            }`}
          >
            {isLoading ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                Creating Replay...
              </span>
            ) : (
              'Create Replay Session'
            )}
          </button>
        </div>

        {/* Error Display */}
        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/30 rounded-lg">
            <div className="flex items-start gap-3">
              <svg className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
              </svg>
              <div>
                <h4 className="text-red-300 font-medium mb-1">Error</h4>
                <p className="text-red-200 text-sm">{error}</p>
              </div>
            </div>
          </div>
        )}

            {/* Info Box */}
            <div className="p-4 bg-blue-500/10 border border-blue-500/30 rounded-lg">
              <div className="flex items-start gap-3">
                <svg className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                </svg>
                <div>
                  <h4 className="text-blue-300 font-medium mb-2">About Replay Mode</h4>
                  <ul className="text-blue-200 text-sm space-y-1">
                    <li>• Replay historical market data at your chosen speed</li>
                    <li>• Speed 0 = paused, 1 = real-time, higher = faster playback</li>
                    <li>• Use quick select buttons for common time ranges</li>
                    <li>• Provider, symbol, and interval are taken from main settings</li>
                    <li>• Real-time updates via WebSocket when session is active</li>
                  </ul>
                </div>
              </div>
            </div>
          </>
        )}

        {/* Error Display */}
        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/30 rounded-lg">
            <div className="flex items-start gap-3">
              <svg className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
              </svg>
              <div>
                <h4 className="text-red-300 font-medium mb-1">Error</h4>
                <p className="text-red-200 text-sm">{error}</p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

