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
    <div className="h-full p-6 bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-bold text-white mb-1">Trading Accounts</h2>
          <p className="text-white/60 text-sm">Manage your trading accounts and monitor performance</p>
        </div>
        <button
          onClick={fetchAccounts}
          className="group flex items-center gap-2 px-4 py-2.5 bg-white/10 hover:bg-white/20 text-white rounded-lg transition-all duration-200 border border-white/10 hover:border-white/20"
        >
          <svg className="w-4 h-4 group-hover:rotate-180 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          <span className="text-sm font-medium">Refresh</span>
        </button>
      </div>

      {/* Messages */}
      {activationMessage && (
        <div className="mb-6 p-4 bg-gradient-to-r from-green-500/10 to-emerald-500/10 border border-green-500/30 rounded-xl backdrop-blur-sm">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-green-500/20 rounded-full flex items-center justify-center">
              <svg className="w-4 h-4 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <span className="text-green-400 font-medium">{activationMessage}</span>
          </div>
        </div>
      )}

      {warningMessage && (
        <div className="mb-6 p-4 bg-gradient-to-r from-yellow-500/10 to-amber-500/10 border border-yellow-500/30 rounded-xl backdrop-blur-sm">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-yellow-500/20 rounded-full flex items-center justify-center">
              <svg className="w-4 h-4 text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z" />
              </svg>
            </div>
            <span className="text-yellow-400 font-medium">{warningMessage}</span>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-6 p-4 bg-gradient-to-r from-red-500/10 to-rose-500/10 border border-red-500/30 rounded-xl backdrop-blur-sm">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-red-500/20 rounded-full flex items-center justify-center">
              <svg className="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <span className="text-red-400 font-medium">Error: {error}</span>
          </div>
        </div>
      )}

      {/* Accounts Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {accounts.map((account) => (
          <div
            key={account.accountId}
            className={`group relative overflow-hidden rounded-xl border transition-all duration-300 hover:scale-[1.02] ${
              account.isActive
                ? 'bg-gradient-to-br from-green-500/10 via-emerald-500/5 to-green-500/10 border-green-500/30 shadow-sm shadow-green-500/5'
                : 'bg-gradient-to-br from-white/5 via-white/3 to-white/5 border-white/10 hover:border-white/20'
            }`}
          >
            {/* Background Pattern */}
            <div className="absolute inset-0 opacity-5">
              <div className="absolute inset-0 bg-gradient-to-br from-transparent via-white/10 to-transparent"></div>
            </div>

            <div className="relative p-4">
              {/* Header */}
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                    account.isActive 
                      ? 'bg-gradient-to-br from-green-500 to-emerald-600 shadow-sm shadow-green-500/10' 
                      : 'bg-gradient-to-br from-slate-600 to-slate-700'
                  }`}>
                    <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                    </svg>
                  </div>
                  <div className="min-w-0 flex-1">
                    <h3 className="text-lg font-bold text-white mb-1 truncate">{account.accountName}</h3>
                    <p className="text-white/60 text-xs font-mono truncate">{account.accountId}</p>
                  </div>
                </div>
                
                <div className="flex flex-col items-end gap-1">
                  <span className="px-2 py-1 text-xs font-semibold rounded-full bg-white/10 text-white/80 border border-white/20">
                    {account.accountType}
                  </span>
                  <div className="flex gap-1 mt-2">
                    <span className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      account.isActive
                        ? 'bg-green-500/20 text-green-400 border border-green-500/30'
                        : 'bg-slate-500/20 text-slate-400 border border-slate-500/30'
                    }`}>
                      {account.isActive ? 'Active' : 'Inactive'}
                    </span>
                    <span className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      account.isEnabled
                        ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                        : 'bg-red-500/20 text-red-400 border border-red-500/30'
                    }`}>
                      {account.isEnabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Stats Grid */}
              <div className="grid grid-cols-2 gap-3 mb-4">
                <div className="space-y-1">
                  <div className="text-white/60 text-xs font-medium uppercase tracking-wide">Balance</div>
                  <div className={`text-lg font-bold ${
                    account.balance >= 0 ? 'text-green-400' : 'text-red-400'
                  }`}>
                    {formatCurrency(account.balance)}
                  </div>
                </div>
                <div className="space-y-1">
                  <div className="text-white/60 text-xs font-medium uppercase tracking-wide">Total P&L</div>
                  <div className={`text-lg font-bold ${
                    account.totalPnL >= 0 ? 'text-green-400' : 'text-red-400'
                  }`}>
                    {formatCurrency(account.totalPnL)}
                  </div>
                </div>
                <div className="space-y-1">
                  <div className="text-white/60 text-xs font-medium uppercase tracking-wide">Win Rate</div>
                  <div className="text-lg font-bold text-white">{formatPercentage(account.winRate)}</div>
                </div>
                <div className="space-y-1">
                  <div className="text-white/60 text-xs font-medium uppercase tracking-wide">Total Trades</div>
                  <div className="text-lg font-bold text-white">{account.totalTrades}</div>
                </div>
              </div>

              {/* Action Button */}
              <div className="flex justify-end">
                {account.isActive ? (
                  <button
                    onClick={() => deactivateAccount(account.accountId)}
                    disabled={deactivatingAccount === account.accountId}
                    className={`group/btn flex items-center gap-2 px-4 py-2 rounded-lg font-semibold text-xs transition-all duration-200 ${
                      deactivatingAccount === account.accountId
                        ? 'bg-orange-500/20 text-orange-400 border border-orange-500/30 cursor-not-allowed'
                        : 'bg-gradient-to-r from-orange-500/20 to-red-500/20 text-orange-300 border border-orange-500/30 hover:from-orange-500/30 hover:to-red-500/30 hover:border-orange-500/50 hover:shadow-sm hover:shadow-orange-500/10'
                    }`}
                  >
                    {deactivatingAccount === account.accountId ? (
                      <>
                        <div className="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin"></div>
                        <span>Deactivating...</span>
                      </>
                    ) : (
                      <>
                        <svg className="w-3 h-3 group-hover/btn:rotate-90 transition-transform duration-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                        <span>Deactivate</span>
                      </>
                    )}
                  </button>
                ) : (
                  <button
                    onClick={() => activateAccount(account.accountId)}
                    disabled={activatingAccount === account.accountId}
                    className={`group/btn flex items-center gap-2 px-4 py-2 rounded-lg font-semibold text-xs transition-all duration-200 ${
                      activatingAccount === account.accountId
                        ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30 cursor-not-allowed'
                        : 'bg-gradient-to-r from-blue-500/20 to-cyan-500/20 text-blue-300 border border-blue-500/30 hover:from-blue-500/30 hover:to-cyan-500/30 hover:border-blue-500/50 hover:shadow-sm hover:shadow-blue-500/10'
                    }`}
                  >
                    {activatingAccount === account.accountId ? (
                      <>
                        <div className="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin"></div>
                        <span>Activating...</span>
                      </>
                    ) : (
                      <>
                        <svg className="w-3 h-3 group-hover/btn:scale-110 transition-transform duration-200" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <span>Activate</span>
                      </>
                    )}
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}

        {accounts.length === 0 && (
          <div className="text-center py-16">
            <div className="w-24 h-24 mx-auto mb-6 bg-white/5 rounded-full flex items-center justify-center">
              <svg className="w-12 h-12 text-white/40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <h3 className="text-xl font-semibold text-white mb-2">No accounts found</h3>
            <p className="text-white/60">Add your first trading account to get started</p>
          </div>
        )}
      </div>
    </div>
  );
}
