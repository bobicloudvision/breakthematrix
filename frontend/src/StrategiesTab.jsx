import React, { useEffect, useState } from 'react';

export function StrategiesTab() {
  const [strategies, setStrategies] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState({});

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

  return (
    <div className="h-full w-full flex flex-col">
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
      {error ? (
        <div className="p-3 text-red-400 text-sm flex-shrink-0">Failed to load strategies: {error}</div>
      ) : (
        <div className="flex-1 overflow-y-auto overflow-x-auto min-h-0">
          <div className="p-3 space-y-3">
            {strategies.length === 0 ? (
              <div className="text-center text-white/50 py-6">No strategies found</div>
            ) : (
              strategies.map((strategy) => (
                <div key={strategy.id} className="bg-black/30 rounded-lg border border-white/10 p-4">
                  {/* Strategy Header */}
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <div className={`w-2 h-2 rounded-full ${strategy.enabled ? 'bg-green-500' : 'bg-gray-500'}`}></div>
                      <h3 className="text-white font-medium">{strategy.name}</h3>
                    </div>
                    <div className="text-xs text-white/60">{strategy.id}</div>
                  </div>

                  {/* Symbols */}
                  <div className="mb-3">
                    <div className="text-xs text-white/60 mb-1">Symbols</div>
                    <div className="flex flex-wrap gap-1">
                      {strategy.symbols.map((symbol) => (
                        <span key={symbol} className="px-2 py-1 text-xs bg-white/10 text-white/80 rounded">
                          {symbol}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Stats Grid */}
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span className="text-white/70">Total Trades</span>
                        <span className="text-white font-mono">{strategy.stats.totalTrades}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Winning Trades</span>
                        <span className="text-green-400 font-mono">{strategy.stats.winningTrades}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Losing Trades</span>
                        <span className="text-red-400 font-mono">{strategy.stats.losingTrades}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Win Rate</span>
                        <span className="text-white font-mono">
                          {strategy.stats.winRate ? `${(strategy.stats.winRate * 100).toFixed(1)}%` : '-'}
                        </span>
                      </div>
                    </div>
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span className="text-white/70">Net Profit</span>
                        <span className={`font-mono ${strategy.stats.netProfit >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                          ${formatNumber(strategy.stats.netProfit)}
                        </span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Total Profit</span>
                        <span className="text-green-400 font-mono">${formatNumber(strategy.stats.totalProfit)}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Total Loss</span>
                        <span className="text-red-400 font-mono">${formatNumber(strategy.stats.totalLoss)}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-white/70">Max Drawdown</span>
                        <span className="text-red-400 font-mono">${formatNumber(strategy.stats.maxDrawdown)}</span>
                      </div>
                    </div>
                  </div>

                  {/* Additional Info */}
                  <div className="mt-3 pt-3 border-t border-white/10 text-xs">
                    <div className="flex justify-between text-white/60">
                      <span>Started</span>
                      <span>{formatDate(strategy.stats.startTime)}</span>
                    </div>
                    <div className="flex justify-between text-white/60 mt-1">
                      <span>Last Trade</span>
                      <span>{formatDate(strategy.stats.lastTradeTime)}</span>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
