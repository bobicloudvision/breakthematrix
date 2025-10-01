import React, { useState, useEffect } from "react";
import { Chart } from "./Chart";
import { TradingProviderDropdown } from "./TradingProviderDropdown";
import { BotControl } from "./BotControl";
import { OrdersTab } from "./OrdersTab";
import { StrategiesTab } from "./StrategiesTab";
import { AccountsTab } from "./AccountsTab";

export default function App() {
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [symbol, setSymbol] = useState('ETHUSDT');
  const [interval, setInterval] = useState('1m');
  const [isSymbolOpen, setIsSymbolOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('orders');
  const [activeStrategies, setActiveStrategies] = useState([]);

  // Load from localStorage on component mount
  useEffect(() => {
    const savedProvider = localStorage.getItem('tradingProvider');
    const savedSymbol = localStorage.getItem('tradingSymbol');
    const savedInterval = localStorage.getItem('tradingInterval');

    if (savedProvider) {
      setSelectedProvider(savedProvider);
    }
    if (savedSymbol) {
      setSymbol(savedSymbol);
    }
    if (savedInterval) {
      setInterval(savedInterval);
    }
  }, []);

  // Save to localStorage when values change
  useEffect(() => {
    if (selectedProvider) {
      localStorage.setItem('tradingProvider', selectedProvider);
    }
  }, [selectedProvider]);

  useEffect(() => {
    localStorage.setItem('tradingSymbol', symbol);
  }, [symbol]);

  useEffect(() => {
    localStorage.setItem('tradingInterval', interval);
  }, [interval]);

  // Fetch active strategies on component mount
  useEffect(() => {
    fetchActiveStrategies();
  }, []);

  const handleProviderSelect = (provider) => {
    setSelectedProvider(provider);
    console.log('Selected provider:', provider);
  };

  const handleSymbolSelect = (newSymbol) => {
    setSymbol(newSymbol);
    setIsSymbolOpen(false);
    console.log('Selected symbol:', newSymbol);
  };

  // Fetch active strategies
  const fetchActiveStrategies = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/bot/strategies/active');
      if (response.ok) {
        const strategies = await response.json();
        setActiveStrategies(strategies);
        console.log('Active strategies loaded:', strategies);
      } else {
        console.error('Failed to fetch active strategies:', response.status);
      }
    } catch (error) {
      console.error('Error fetching active strategies:', error);
    }
  };

  const intervalOptions = [
    { value: '1m', label: '1m' },
    { value: '5m', label: '5m' },
    { value: '15m', label: '15m' },
    { value: '1h', label: '1h' },
    { value: '4h', label: '4h' },
    { value: '1d', label: '1d' },
  ];

  const symbolOptions = [
    'BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'XRPUSDT', 
    'SOLUSDT', 'DOTUSDT', 'DOGEUSDT', 'AVAXUSDT', 'MATICUSDT'
  ];

  return (
    <div className="min-h-screen w-full">


      <div className="flex h-screen">
        {/* Main Content Area */}
        <div className="flex-1 flex flex-col">
          {/* Top Bar */}
          <header className="sticky top-0 z-20 w-full border-b border-cyan-500/20 bg-gradient-to-r from-slate-900/90 via-gray-900/90 to-slate-900/90 backdrop-blur-xl shadow-2xl shadow-cyan-500/10">
            <div className="mx-auto max-w-7xl px-4 py-3">
              <div className="flex items-center gap-4">
                <div className="text-2xl font-bold bg-gradient-to-r from-cyan-400 via-blue-400 to-purple-400 bg-clip-text text-transparent tracking-tight">
                  BreakTheMatrix 
                </div>

                {/* Controls - compact inline */}
                <div className="ml-auto flex items-center gap-4">
                  {/* Provider */}
                  <div className="min-w-[180px]">
                    <TradingProviderDropdown 
                      onProviderSelect={handleProviderSelect}
                      selectedProvider={selectedProvider}
                    />
                  </div>

                  {/* Symbol */}
                  <div className="relative">
                    <button
                      onClick={() => setIsSymbolOpen(!isSymbolOpen)}
                      className="px-4 py-2 h-10 bg-gradient-to-r from-slate-800/50 to-slate-700/50 text-cyan-300 rounded-lg border border-cyan-500/30 focus:outline-none focus:ring-2 focus:ring-cyan-500/50 hover:from-slate-700/60 hover:to-slate-600/60 hover:border-cyan-400/50 flex items-center justify-between min-w-[120px] transition-all duration-200 shadow-lg shadow-cyan-500/10"
                    >
                      <span className="text-cyan-200 font-medium">{symbol}</span>
                      <svg 
                        className={`w-4 h-4 text-cyan-400 transition-transform duration-200 ${isSymbolOpen ? 'rotate-180' : ''}`} 
                        fill="none" 
                        stroke="currentColor" 
                        viewBox="0 0 24 24"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>

                    {isSymbolOpen && (
                      <div className="absolute z-50 w-full mt-2 bg-gradient-to-b from-slate-800/95 to-slate-900/95 backdrop-blur-xl border border-cyan-500/30 rounded-lg shadow-2xl shadow-cyan-500/20 max-h-60 overflow-auto">
                        {symbolOptions.map((option) => (
                          <button
                            key={option}
                            onClick={() => handleSymbolSelect(option)}
                            className={`w-full px-4 py-3 text-left text-cyan-200 hover:bg-gradient-to-r hover:from-cyan-500/20 hover:to-blue-500/20 focus:outline-none focus:bg-gradient-to-r focus:from-cyan-500/20 focus:to-blue-500/20 first:rounded-t-lg last:rounded-b-lg transition-all duration-150 ${
                              symbol === option ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100' : ''
                            }`}
                          >
                            {option}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Interval */}
                  <div className="flex gap-2">
                    {intervalOptions.map((option) => (
                      <button
                        key={option.value}
                        onClick={() => setInterval(option.value)}
                        className={`px-4 py-2 text-sm font-medium rounded-lg border transition-all duration-200 ${
                          interval === option.value
                            ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border-cyan-400/50 shadow-lg shadow-cyan-500/20'
                            : 'bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10'
                        }`}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </header>

          {/* Chart Area */}
          <main className="flex-1 flex flex-col">
            <div className="flex-1 w-full">
              {selectedProvider ? (
                <Chart 
                  provider={selectedProvider}
                  symbol={symbol}
                  interval={interval}
                  activeStrategies={activeStrategies}
                />
              ) : (
                <div className="flex items-center justify-center h-full bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40 backdrop-blur-xl">
                  <div className="text-center">
                    <div className="text-6xl mb-4">📊</div>
                    <p className="text-cyan-300 text-xl font-medium mb-2">Select a Trading Provider</p>
                    <p className="text-slate-400">Choose a provider from the dropdown above to start trading</p>
                  </div>
                </div>
              )}
            </div>
            {/* Bottom Tabs */}
            <div className="h-[400px] border-t border-cyan-500/20 bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40 backdrop-blur-xl shadow-2xl shadow-cyan-500/5">
              <div className="h-12 flex items-center gap-3 px-4 border-b border-cyan-500/20 bg-gradient-to-r from-slate-800/30 to-slate-700/30">
                <button  
                  onClick={() => setActiveTab('orders')}
                  className={`px-4 h-9 text-sm font-medium rounded-lg transition-all duration-200 ${
                    activeTab === 'orders' 
                      ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 shadow-lg shadow-cyan-500/20' 
                      : 'bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10'
                  }`}
                >
                  Orders
                </button>
                <button 
                  onClick={() => setActiveTab('strategies')}
                  className={`px-4 h-9 text-sm font-medium rounded-lg transition-all duration-200 ${
                    activeTab === 'strategies' 
                      ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 shadow-lg shadow-cyan-500/20' 
                      : 'bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10'
                  }`}
                >
                  Strategies
                </button>
                <button 
                  onClick={() => setActiveTab('accounts')}
                  className={`px-4 h-9 text-sm font-medium rounded-lg transition-all duration-200 ${
                    activeTab === 'accounts' 
                      ? 'bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 shadow-lg shadow-cyan-500/20' 
                      : 'bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 hover:shadow-md hover:shadow-cyan-500/10'
                  }`}
                >
                  Accounts
                </button>
                {/* Future tabs: Positions, Alerts, Console, etc. */}
              </div>
              <div className="h-[calc(100%-3.5rem)]">
                {activeTab === 'orders' && <OrdersTab />}
                {activeTab === 'strategies' && <StrategiesTab />}
                {activeTab === 'accounts' && <AccountsTab />}
              </div>
            </div>
          </main>
        </div>

        {/* Right Sidebar - Bot Dashboard */}
        <div className="w-80 border-l border-cyan-500/20 bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40 backdrop-blur-xl shadow-2xl shadow-cyan-500/5 overflow-y-auto">
          <BotControl interval={interval} historicalLimit={1000} />
        </div>
      </div>
    </div>
  );
}
