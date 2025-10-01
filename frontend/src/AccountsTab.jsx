import React, { useState, useEffect } from 'react';

export function AccountsTab() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activatingAccount, setActivatingAccount] = useState(null);
  const [deactivatingAccount, setDeactivatingAccount] = useState(null);
  const [activationMessage, setActivationMessage] = useState(null);
  const [warningMessage, setWarningMessage] = useState(null);

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

  const activateAccount = async (accountId) => {
    try {
      setActivatingAccount(accountId);
      setError(null);
      setActivationMessage(null);
      setWarningMessage(null);

      const response = await fetch(`http://localhost:8080/api/accounts/${accountId}/activate`, {
        method: 'POST',
        headers: {
          'accept': '*/*',
          'Content-Type': 'application/json',
        },
        body: '',
      });

      if (!response.ok) {
        throw new Error('Failed to activate account');
      }

      const result = await response.json();
      setActivationMessage(result.message);
      
      // Refresh accounts to update the active status
      await fetchAccounts();
    } catch (err) {
      setError(err.message);
    } finally {
      setActivatingAccount(null);
    }
  };

  const deactivateAccount = async (accountId) => {
    try {
      setDeactivatingAccount(accountId);
      setError(null);
      setActivationMessage(null);
      setWarningMessage(null);

      const response = await fetch(`http://localhost:8080/api/accounts/${accountId}/deactivate`, {
        method: 'POST',
        headers: {
          'accept': '*/*',
          'Content-Type': 'application/json',
        },
        body: '',
      });

      if (!response.ok) {
        throw new Error('Failed to deactivate account');
      }

      const result = await response.json();
      setActivationMessage(result.message);
      
      // Show warning if this was the active account
      if (result.warning) {
        setWarningMessage(result.warning);
      }
      
      // Refresh accounts to update the active status
      await fetchAccounts();
    } catch (err) {
      setError(err.message);
    } finally {
      setDeactivatingAccount(null);
    }
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

      {/* Success/Error/Warning Messages */}
      {activationMessage && (
        <div className="mb-4 p-3 bg-green-500/10 border border-green-500/20 rounded text-green-400 text-sm">
          {activationMessage}
        </div>
      )}

      {warningMessage && (
        <div className="mb-4 p-3 bg-yellow-500/10 border border-yellow-500/20 rounded text-yellow-400 text-sm">
          {warningMessage}
        </div>
      )}

      {error && (
        <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded text-red-400 text-sm">
          Error: {error}
        </div>
      )}

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

            <div className="grid grid-cols-2 gap-4 text-sm mb-4">
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

            {/* Action Buttons */}
            <div className="flex justify-end gap-2">
              {account.isActive ? (
                <button
                  onClick={() => deactivateAccount(account.accountId)}
                  disabled={deactivatingAccount === account.accountId}
                  className={`px-4 py-2 text-xs font-medium rounded-md transition-all ${
                    deactivatingAccount === account.accountId
                      ? 'bg-orange-500/20 text-orange-400 border border-orange-500/30 cursor-not-allowed'
                      : 'bg-orange-500/20 text-orange-300 border border-orange-500/30 hover:bg-orange-500/30'
                  }`}
                >
                  {deactivatingAccount === account.accountId ? (
                    <div className="flex items-center gap-2">
                      <div className="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin"></div>
                      <span>Deactivating...</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                      </svg>
                      <span>Deactivate</span>
                    </div>
                  )}
                </button>
              ) : (
                <button
                  onClick={() => activateAccount(account.accountId)}
                  disabled={activatingAccount === account.accountId}
                  className={`px-4 py-2 text-xs font-medium rounded-md transition-all ${
                    activatingAccount === account.accountId
                      ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30 cursor-not-allowed'
                      : 'bg-blue-500/20 text-blue-300 border border-blue-500/30 hover:bg-blue-500/30'
                  }`}
                >
                  {activatingAccount === account.accountId ? (
                    <div className="flex items-center gap-2">
                      <div className="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin"></div>
                      <span>Activating...</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                      </svg>
                      <span>Activate</span>
                    </div>
                  )}
                </button>
              )}
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
