import React, { useEffect, useState } from 'react';

// Custom elegant blinking animation styles
const elegantBlinkStyle = `
  @keyframes elegant-blink {
    0%, 100% { 
      opacity: 0.3; 
      transform: scale(1);
    }
    50% { 
      opacity: 0.6; 
      transform: scale(1.02);
    }
  }
  
  .elegant-blink {
    animation: elegant-blink 3s ease-in-out infinite;
  }
`;

export function StrategiesTab() {
  const [strategies, setStrategies] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState({});
  const [message, setMessage] = useState(null);

  const fetchStrategies = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetch('http://localhost:8080/api/bot/strategies', {
        method: 'GET',
        headers: { accept: '*/*' },
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      setStrategies(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStrategies();
    const id = setInterval(fetchStrategies, 10000); // Refresh every 10 seconds
    return () => clearInterval(id);
  }, []);

  const formatDate = (iso) => {
    if (!iso) return '-';
    try {
      const d = new Date(iso);
      return d.toLocaleString();
    } catch {
      return iso;
    }
  };

  const formatNumber = (num) => {
    if (num === null || num === undefined) return '-';
    return Number(num).toFixed(2);
  };

  const toggleStrategy = async (strategyId, enabled) => {
    try {
      setActionLoading(prev => ({ ...prev, [strategyId]: true }));
      setError(null); // Clear any previous errors
      
      const action = enabled ? 'disable' : 'enable';
      const url = `http://localhost:8080/api/bot/strategies/${strategyId}/${action}`;
      
      const response = await fetch(url, {
        method: 'POST',
        headers: { accept: '*/*' },
        body: '',
      });
      
      if (!response.ok) {
        // Try to parse error response
        try {
          const errorData = await response.json();
          if (errorData.error) {
            throw new Error(errorData.error);
          }
        } catch (parseError) {
          // If parsing fails, use generic error
          throw new Error(`HTTP ${response.status}`);
        }
        throw new Error(`HTTP ${response.status}`);
      }
      
      // Refresh strategies after successful toggle
      await fetchStrategies();
      
      // Show success message
      const strategyName = strategies.find(s => s.id === strategyId)?.name || 'Strategy';
      setMessage(`${strategyName} ${enabled ? 'disabled' : 'enabled'} successfully!`);
      setTimeout(() => setMessage(null), 3000); // Auto-hide after 3 seconds
    } catch (e) {
      console.error('Failed to toggle strategy:', e);
      setError(e.message);
    } finally {
      setActionLoading(prev => ({ ...prev, [strategyId]: false }));
    }
  };

  return (
    <div className="h-full w-full flex flex-col">
      <style>{elegantBlinkStyle}</style>
      <div className="flex items-center justify-between px-3 py-2 border-b border-white/10 bg-black/30 flex-shrink-0">
        <div className="text-white/80 text-sm font-medium">Trading Strategies</div>
        <div className="flex items-center gap-2">
          {loading && (
            <div className="text-xs text-white/50">Refreshing…</div>
          )}
          <button
            onClick={fetchStrategies}
            className="px-2 py-1 text-xs rounded border border-white/10 text-white/80 hover:bg-white/10"
          >
            Refresh
          </button>
        </div>
      </div>
      
      {/* Success Message */}
      {message && (
        <div className="p-3 flex-shrink-0">
          <div className="bg-green-500/10 border border-green-500/30 rounded-lg p-3">
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 rounded-full bg-green-500/20 flex items-center justify-center flex-shrink-0">
                <svg className="w-3 h-3 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div className="flex-1">
                <div className="text-green-300 font-medium text-sm">Success</div>
                <div className="text-green-400 text-sm">{message}</div>
              </div>
              <button
                onClick={() => setMessage(null)}
                className="text-green-400 hover:text-green-300 transition-colors flex-shrink-0"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      )}
      
      {error ? (
        <div className="p-3 flex-shrink-0">
          <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-3">
            <div className="flex items-start gap-2">
              <div className="w-5 h-5 rounded-full bg-red-500/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                <div className="w-2 h-2 bg-red-400 rounded-full"></div>
              </div>
              <div className="flex-1">
                <div className="text-red-300 font-medium text-sm mb-1">Strategy Error</div>
                <div className="text-red-400 text-sm leading-relaxed">{error}</div>
              </div>
              <button
                onClick={() => setError(null)}
                className="text-red-400 hover:text-red-300 transition-colors flex-shrink-0"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto overflow-x-auto min-h-0">
          <div className="p-3">
            {strategies.length === 0 ? (
              <div className="text-center text-white/50 py-6">No strategies found</div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                {strategies.map((strategy) => (
                  <div key={strategy.id} className={`bg-black/30 rounded-lg border border-white/10 p-3 group relative overflow-hidden transition-all duration-300 hover:bg-black/40 ${
                    strategy.enabled ? 'shadow-green-500/20' : ''
                  }`}>
                    {/* Elegant Blinking Shadow for Enabled Strategies */}
                    {strategy.enabled && (
                      <div className="absolute inset-0 rounded-lg bg-gradient-to-r from-green-500/8 to-emerald-500/8 elegant-blink shadow-lg shadow-green-500/15"></div>
                    )}
                    {/* Strategy Header */}
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-1.5 min-w-0 flex-1">
                        <div className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${strategy.enabled ? 'bg-green-500' : 'bg-gray-500'}`}></div>
                        <h3 className="text-white font-medium text-md truncate">{strategy.name}</h3>
                      </div>
                      
                      {strategy.enabled && (
                        <div className="flex items-center gap-1 text-green-300 text-xs font-medium bg-gradient-to-r from-green-500/15 to-emerald-500/15 border border-green-400/30 rounded-full px-2 py-0.5 backdrop-blur-sm flex-shrink-0">
                          <div className="w-1 h-1 bg-green-400 rounded-full animate-pulse"></div>
                          <span className="tracking-wide">ACTIVE</span>
                        </div>
                      )}
                    </div>

                    {/* Elegant Hover Button */}
                    <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-black/60 via-black/40 to-black/60 backdrop-blur-sm opacity-0 group-hover:opacity-100 transition-all duration-500 ease-out">
                      <div className="relative">
                        <button
                          onClick={() => toggleStrategy(strategy.id, strategy.enabled)}
                          disabled={actionLoading[strategy.id]}
                          className={`uppercase relative px-4 py-2 text-md font-semibold rounded-xl transition-all duration-300 transform scale-90 group-hover:scale-100 shadow-2xl backdrop-blur-md border ${
                            strategy.enabled
                              ? 'bg-gradient-to-r from-red-500/20 to-red-600/20 text-red-100 border-red-400/40 hover:from-red-500/30 hover:to-red-600/30 hover:border-red-400/60 hover:shadow-red-500/25'
                              : 'bg-gradient-to-r from-green-500/20 to-green-600/20 text-green-100 border-green-400/40 hover:from-green-500/30 hover:to-green-600/30 hover:border-green-400/60 hover:shadow-green-500/25'
                          } ${actionLoading[strategy.id] ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:shadow-2xl'}`}
                        >
                          {/* Button Glow Effect */}
                          <div className={`absolute inset-0 rounded-2xl blur-md opacity-0 group-hover:opacity-30 transition-opacity duration-300 ${
                            strategy.enabled ? 'bg-red-500' : 'bg-green-500'
                          }`}></div>
                          
                          {/* Button Content */}
                          <div className="relative z-10 flex items-center gap-3">
                            {actionLoading[strategy.id] ? (
                              <>
                                <div className="w-6 h-6 border-2 border-current border-t-transparent rounded-full animate-spin"></div>
                                <span className="tracking-wide">Processing...</span>
                              </>
                            ) : (
                              <>
                                <div className={`w-3 h-3 rounded-full ${
                                  strategy.enabled ? 'bg-red-300' : 'bg-green-300'
                                }`}></div>
                                <span className="tracking-wide">
                                  {strategy.enabled ? 'Disable Strategy' : 'Enable Strategy'}
                                </span>
                              </>
                            )}
                          </div>
                        </button>
                      </div>
                    </div>

                    {/* Symbols */}
                    <div className="mb-2">
                      <div className="text-xs text-white/60 mb-1">Symbols</div>
                      <div className="flex flex-wrap gap-1">
                        {strategy.symbols.slice(0, 3).map((symbol) => (
                          <span key={symbol} className="px-1.5 py-0.5 text-xs bg-white/10 text-white/80 rounded">
                            {symbol}
                          </span>
                        ))}
                        {strategy.symbols.length > 3 && (
                          <span className="px-1.5 py-0.5 text-xs bg-white/5 text-white/60 rounded">
                            +{strategy.symbols.length - 3}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Key Stats */}
                    <div className="space-y-1.5 text-xs">
                      <div className="flex justify-between">
                        <span className="text-white/70">Trades</span>
                        <span className="text-white font-mono">{strategy.stats.totalTrades}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Win Rate</span>
                        <span className="text-white font-mono">
                          {strategy.stats.winRate ? `${(strategy.stats.winRate * 100).toFixed(1)}%` : '-'}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Net P&L</span>
                        <span className={`font-mono ${strategy.stats.netProfit >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                          ${formatNumber(strategy.stats.netProfit)}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Drawdown</span>
                        <span className="text-red-400 font-mono">${formatNumber(strategy.stats.maxDrawdown)}</span>
                      </div>
                    </div>

                    {/* Last Trade Time */}
                    <div className="mt-2 pt-2 border-t border-white/10 text-xs">
                      <div className="flex justify-between text-white/60">
                        <span>Last Trade</span>
                        <span className="truncate ml-2">{formatDate(strategy.stats.lastTradeTime)}</span>
                      </div>
                    </div>

                    {/* Running Indicator for Enabled Strategies */}
                    {strategy.enabled && (
                      <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-r from-green-500 via-emerald-400 to-green-500 rounded-b-lg overflow-hidden">
                        <div className="h-full bg-gradient-to-r from-transparent via-white/30 to-transparent animate-pulse"></div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
