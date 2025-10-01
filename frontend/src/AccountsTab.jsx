import React, { useState, useEffect } from 'react';

export function AccountsTab() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchAccounts();
  }, []);

  const fetchAccounts = async () => {
    try {
      setLoading(true);
      const response = await fetch('http://localhost:8080/api/accounts');
      if (!response.ok) {
        throw new Error('Failed to fetch accounts');
      }
      const data = await response.json();
      setAccounts(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
  };

  const formatPercentage = (value) => {
    return `${(value * 100).toFixed(1)}%`;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-white/60">Loading accounts...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-red-400">Error: {error}</div>
      </div>
    );
  }

  return (
    <div className="h-full p-4">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-white font-semibold">Trading Accounts</h3>
        <button
          onClick={fetchAccounts}
          className="px-3 py-1.5 text-xs bg-white/10 text-white rounded-md hover:bg-white/20 transition-colors"
        >
          Refresh
        </button>
      </div>

      <div className="space-y-3">
        {accounts.map((account) => (
          <div
            key={account.accountId}
            className={`p-4 rounded-lg border ${
              account.isActive
                ? 'bg-green-500/10 border-green-500/30'
                : 'bg-white/5 border-white/10'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <h4 className="text-white font-medium">{account.accountName}</h4>
                <span className={`px-2 py-1 text-xs rounded-full ${
                  account.isActive
                    ? 'bg-green-500/20 text-green-400'
                    : 'bg-gray-500/20 text-gray-400'
                }`}>
                  {account.isActive ? 'Active' : 'Inactive'}
                </span>
                <span className={`px-2 py-1 text-xs rounded-full ${
                  account.isEnabled
                    ? 'bg-blue-500/20 text-blue-400'
                    : 'bg-red-500/20 text-red-400'
                }`}>
                  {account.isEnabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
              <span className="text-xs text-white/60">{account.accountType}</span>
            </div>

            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <div className="text-white/60 text-xs">Account ID</div>
                <div className="text-white font-mono">{account.accountId}</div>
              </div>
              <div>
                <div className="text-white/60 text-xs">Balance</div>
                <div className={`font-semibold ${
                  account.balance >= 0 ? 'text-green-400' : 'text-red-400'
                }`}>
                  {formatCurrency(account.balance)}
                </div>
              </div>
              <div>
                <div className="text-white/60 text-xs">Total P&L</div>
                <div className={`font-semibold ${
                  account.totalPnL >= 0 ? 'text-green-400' : 'text-red-400'
                }`}>
                  {formatCurrency(account.totalPnL)}
                </div>
              </div>
              <div>
                <div className="text-white/60 text-xs">Win Rate</div>
                <div className="text-white">{formatPercentage(account.winRate)}</div>
              </div>
              <div>
                <div className="text-white/60 text-xs">Total Trades</div>
                <div className="text-white">{account.totalTrades}</div>
              </div>
            </div>
          </div>
        ))}

        {accounts.length === 0 && (
          <div className="text-center text-white/60 py-8">
            No accounts found
          </div>
        )}
      </div>
    </div>
  );
}
