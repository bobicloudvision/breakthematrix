import React, { useState, useEffect } from "react";
import { Chart } from "./Chart";
import { TradingProviderDropdown } from "./TradingProviderDropdown";
import { BotControl } from "./BotControl";
import { OrdersTab } from "./OrdersTab";
import { StrategiesTab } from "./StrategiesTab";

export default function App() {
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [symbol, setSymbol] = useState('ETHUSDT');
  const [interval, setInterval] = useState('1m');
  const [isSymbolOpen, setIsSymbolOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('orders');

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

  const handleProviderSelect = (provider) => {
    setSelectedProvider(provider);
    console.log('Selected provider:', provider);
  };

  const handleSymbolSelect = (newSymbol) => {
    setSymbol(newSymbol);
    setIsSymbolOpen(false);
    console.log('Selected symbol:', newSymbol);
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
    <div className="min-h-screen w-full" style={{ backgroundColor: '#0f0f0f' }}>
      <div className="flex h-screen">
        {/* Main Content Area */}
        <div className="flex-1 flex flex-col">
          {/* Top Bar */}
          <header className="sticky top-0 z-20 w-full border-b border-white/10 bg-black/40 backdrop-blur-md">
            <div className="mx-auto max-w-7xl px-4 py-2">
              <div className="flex items-center gap-3">
                <div className="text-white/90 font-semibold tracking-tight">BreakTheMatrix</div>

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
                      className="px-3 py-2 h-9 bg-white/5 text-white rounded-md border border-white/10 focus:outline-none focus:ring-2 focus:ring-blue-500 hover:bg-white/10 flex items-center justify-between min-w-[100px]"
                    >
                      <span className="text-white/90">{symbol}</span>
                      <svg 
                        className={`w-4 h-4 text-white/70 transition-transform ${isSymbolOpen ? 'rotate-180' : ''}`} 
                        fill="none" 
                        stroke="currentColor" 
                        viewBox="0 0 24 24"
                      >
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>

                    {isSymbolOpen && (
                      <div className="absolute z-50 w-full mt-1 bg-black/80 backdrop-blur-md border border-white/10 rounded-md shadow-xl max-h-60 overflow-auto">
                        {symbolOptions.map((option) => (
                          <button
                            key={option}
                            onClick={() => handleSymbolSelect(option)}
                            className={`w-full px-3 py-2 text-left text-white hover:bg-white/10 focus:outline-none focus:bg-white/10 first:rounded-t-md last:rounded-b-md ${
                              symbol === option ? 'bg-white/10' : ''
                            }`}
                          >
                            {option}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Interval */}
                  <div className="flex gap-1">
                    {intervalOptions.map((option) => (
                      <button
                        key={option.value}
                        onClick={() => setInterval(option.value)}
                        className={`px-3 py-1.5 text-xs rounded-md border transition-all ${
                          interval === option.value
                            ? 'bg-white/20 text-white border-white/30 shadow-sm'
                            : 'bg-white/5 text-white/60 border-white/10 hover:bg-white/10 hover:text-white/90 hover:border-white/20'
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
              <Chart 
                provider={selectedProvider}
                symbol={symbol}
                interval={interval}
              />
            </div>
            {/* Bottom Tabs */}
            <div className="h-52 border-t border-white/10 bg-black/30 backdrop-blur-sm">
              <div className="h-10 flex items-center gap-2 px-3 border-b border-white/10">
                <button 
                  onClick={() => setActiveTab('orders')}
                  className={`px-3 h-8 text-sm rounded-md transition-all ${
                    activeTab === 'orders' 
                      ? 'bg-white/20 text-white border border-white/30' 
                      : 'bg-white/5 text-white/70 hover:bg-white/10'
                  }`}
                >
                  Orders
                </button>
                <button 
                  onClick={() => setActiveTab('strategies')}
                  className={`px-3 h-8 text-sm rounded-md transition-all ${
                    activeTab === 'strategies' 
                      ? 'bg-white/20 text-white border border-white/30' 
                      : 'bg-white/5 text-white/70 hover:bg-white/10'
                  }`}
                >
                  Strategies
                </button>
                {/* Future tabs: Positions, Alerts, Console, etc. */}
              </div>
              <div className="h-[calc(100%-2.5rem)]">
                {activeTab === 'orders' && <OrdersTab />}
                {activeTab === 'strategies' && <StrategiesTab />}
              </div>
            </div>
          </main>
        </div>

        {/* Right Sidebar - Bot Dashboard */}
        <div className="w-80 border-l border-white/10 bg-black/20 backdrop-blur-sm">
          <BotControl interval={interval} historicalLimit={1000} />
        </div>
      </div>
    </div>
  );
}
