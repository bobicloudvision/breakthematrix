import React, { useState, useEffect } from "react";
import { Chart } from "./Chart";
import { OrderFlow } from "./OrderFlowChart";
import { TradingProviderDropdown } from "./TradingProviderDropdown";
import { BotControl } from "./BotControl";
import { OrdersTab } from "./OrdersTab";
import { StrategiesTab } from "./StrategiesTab";
import { AccountsTab } from "./AccountsTab";
import { PositionsTab } from "./PositionsTab";
import { IndicatorsTab } from "./IndicatorsTab";
import { IndicatorConfigModal } from "./IndicatorConfigModal";
import { ReplayTab } from "./ReplayTab";
import { ManualTradingTab } from "./ManualTradingTab";
import { Button } from "./Button";
import SVGLogo from "./logo.jsx";

export default function App() {
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [symbol, setSymbol] = useState('BTCUSDT');
  const [interval, setInterval] = useState('1m');
  const [isSymbolOpen, setIsSymbolOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('orders');
  const [mainView, setMainView] = useState('chart'); // 'chart' or 'orderflow'
  const [activeStrategies, setActiveStrategies] = useState([]);
  const [enabledIndicators, setEnabledIndicators] = useState([]);
  const [bottomBarHeight, setBottomBarHeight] = useState(400);
  const [isResizing, setIsResizing] = useState(false);
  const [rightSidebarWidth, setRightSidebarWidth] = useState(380);
  const [isResizingRightSidebar, setIsResizingRightSidebar] = useState(false);
  const [resizeStartX, setResizeStartX] = useState(0);
  const [resizeStartWidth, setResizeStartWidth] = useState(0);
  
  // Indicator config in sidebar state
  const [sidebarIndicatorConfig, setSidebarIndicatorConfig] = useState(null);
  
  // Right sidebar view state: 'bot' or 'manual'
  const [rightSidebarView, setRightSidebarView] = useState('bot');
  
  // Current price from chart for manual trading
  const [currentPrice, setCurrentPrice] = useState(null);

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

  // Load and save bottom bar height
  useEffect(() => {
    const savedHeight = localStorage.getItem('bottomBarHeight');
    if (savedHeight) {
      setBottomBarHeight(parseInt(savedHeight, 10));
    }
  }, []);

  useEffect(() => {
    localStorage.setItem('bottomBarHeight', bottomBarHeight.toString());
  }, [bottomBarHeight]);

  // Load and save right sidebar width
  useEffect(() => {
    const savedWidth = localStorage.getItem('rightSidebarWidth');
    if (savedWidth) {
      setRightSidebarWidth(parseInt(savedWidth, 10));
    }
  }, []);

  useEffect(() => {
    localStorage.setItem('rightSidebarWidth', rightSidebarWidth.toString());
  }, [rightSidebarWidth]);

  // Fetch active strategies on component mount
  useEffect(() => {
    fetchActiveStrategies();
  }, []);

  // Fetch enabled indicators from backend
  const fetchEnabledIndicators = async () => {
    if (!selectedProvider || !symbol || !interval) {
      return;
    }

    try {
      const res = await fetch(
        `http://localhost:8080/api/indicators/instances?provider=${selectedProvider}&symbol=${symbol}&interval=${interval}`,
        {
          method: 'GET',
          headers: { accept: 'application/json' },
        }
      );

      if (!res.ok) {
        console.error('Failed to fetch enabled indicators:', res.status);
        return;
      }

      const data = await res.json();
      if (data.success && Array.isArray(data.instances)) {
        setEnabledIndicators(data.instances);
      }
    } catch (error) {
      console.error('Error fetching enabled indicators:', error);
    }
  };

  // Load enabled indicators when provider/symbol/interval changes
  useEffect(() => {
    fetchEnabledIndicators();
  }, [selectedProvider, symbol, interval]);

  // Listen for indicator changes
  useEffect(() => {
    const handleIndicatorChange = () => {
      fetchEnabledIndicators();
    };
    
    window.addEventListener('indicatorsChanged', handleIndicatorChange);

    return () => {
      window.removeEventListener('indicatorsChanged', handleIndicatorChange);
    };
  }, [selectedProvider, symbol, interval]);

  // Handle resize mouse events for bottom bar
  useEffect(() => {
    const handleMouseMove = (e) => {
      if (!isResizing) return;
      
      const windowHeight = window.innerHeight;
      const newHeight = windowHeight - e.clientY;
      
      // Set min and max height constraints
      const minHeight = 200;
      const maxHeight = windowHeight - 200; // Leave at least 200px for chart
      
      if (newHeight >= minHeight && newHeight <= maxHeight) {
        setBottomBarHeight(newHeight);
      }
    };

    const handleMouseUp = () => {
      setIsResizing(false);
    };

    if (isResizing) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'ns-resize';
      document.body.style.userSelect = 'none';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isResizing]);

  // Handle resize mouse events for right sidebar
  useEffect(() => {
    const handleMouseMove = (e) => {
      if (!isResizingRightSidebar) return;
      
      // Calculate the delta from the starting position
      const deltaX = resizeStartX - e.clientX;
      const newWidth = resizeStartWidth + deltaX;
      
      // Set min and max width constraints
      const minWidth = 320;
      const maxWidth = 900;
      
      if (newWidth >= minWidth && newWidth <= maxWidth) {
        setRightSidebarWidth(newWidth);
      }
    };

    const handleMouseUp = () => {
      setIsResizingRightSidebar(false);
    };

    if (isResizingRightSidebar) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'ew-resize';
      document.body.style.userSelect = 'none';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isResizingRightSidebar, resizeStartX, resizeStartWidth]);

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

  // Handle opening indicator config in sidebar
  const handleOpenIndicatorConfig = (indicator, initialParams, isEditing) => {
    setSidebarIndicatorConfig({ indicator, initialParams, isEditing });
  };

  // Handle closing indicator config in sidebar
  const handleCloseIndicatorConfig = () => {
    setSidebarIndicatorConfig(null);
  };

  // Handle applying indicator from sidebar
  const handleApplyIndicatorFromSidebar = async (indicatorId, params, shouldClose = true) => {
    try {
      // If editing, use PATCH to update the existing instance
      if (sidebarIndicatorConfig?.isEditing && sidebarIndicatorConfig?.initialParams?.instanceKey) {
        const patchPayload = {
          params: params.params || {}
        };
        
        const res = await fetch(
          `http://localhost:8080/api/indicators/instances/${encodeURIComponent(sidebarIndicatorConfig.initialParams.instanceKey)}`,
          {
            method: 'PATCH',
            headers: {
              accept: 'application/json',
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(patchPayload),
          }
        );
        
        if (!res.ok) {
          throw new Error(`Failed to update indicator: HTTP ${res.status}`);
        }
        
        const data = await res.json();
        console.log(`Updated indicator instance:`, data);
      } else {
        // Activate a new instance
        const activatePayload = {
          indicatorId: indicatorId,
          provider: params.provider,
          symbol: params.symbol,
          interval: params.interval,
          params: params.params || {},
          historyCount: params.count || 5000
        };
        
        const res = await fetch('http://localhost:8080/api/indicators/instances/activate', {
          method: 'POST',
          headers: {
            accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(activatePayload),
        });
        
        if (!res.ok) {
          throw new Error(`Failed to activate indicator: HTTP ${res.status}`);
        }
        
        const data = await res.json();
        console.log(`Activated indicator instance:`, data);
      }
      
      // Refresh enabled indicators (this will trigger the Chart effect to reload)
      await fetchEnabledIndicators();
      
      // Note: We don't dispatch 'indicatorsChanged' event here because
      // fetchEnabledIndicators() already updates the state which triggers the Chart effect.
      // Dispatching the event would cause a double-fetch and duplicate indicators.
      
      // Only close sidebar config if explicitly requested (not for auto-save)
      if (shouldClose) {
        setSidebarIndicatorConfig(null);
      }
    } catch (e) {
      console.error('Error applying indicator:', e);
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
    <div className="min-h-screen w-full overflow-hidden">


      <div className="flex h-screen overflow-hidden">
        {/* Main Content Area */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Top Bar */}
          <header className="sticky top-0 z-20 w-full border-b border-zinc-500/20 backdrop-blur-xl shadow-2xl shadow-zinc-500/10">
            <div className="px-4 py-3">
              <div className="flex items-center gap-4">
                <div className="text-2xl font-bold bg-gradient-to-r from-cyan-400 via-blue-400 to-purple-400 bg-clip-text text-transparent tracking-tight">
                   <SVGLogo /> 
                </div>

                {/* Main View Tabs */}
                <div className="flex gap-2 ml-6">
                  <Button
                    variant={mainView === 'chart' ? 'primary' : 'secondary'}
                    size="md"
                    onClick={() => setMainView('chart')}
                  >
                    Chart
                  </Button>
                  <Button
                    variant={mainView === 'orderflow' ? 'primary' : 'secondary'}
                    size="md"
                    onClick={() => setMainView('orderflow')}
                  >
                    OrderFlow
                  </Button>
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
                      <Button
                        key={option.value}
                        variant={interval === option.value ? 'primary' : 'secondary'}
                        size="md"
                        onClick={() => setInterval(option.value)}
                      >
                        {option.label}
                      </Button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </header>

          {/* Chart Area */}
          <main className="flex-1 flex flex-col min-h-0">
            <div className="flex-1 w-full min-h-0 flex items-center justify-center">
              {selectedProvider ? (
                <div className="w-full h-full">
                  {mainView === 'chart' ? (
                    <Chart 
                      provider={selectedProvider}
                      symbol={symbol}
                      interval={interval}
                      activeStrategies={activeStrategies}
                      enabledIndicators={enabledIndicators}
                      onPriceUpdate={setCurrentPrice}
                    />
                  ) : (
                    <OrderFlow 
                      provider={selectedProvider}
                      symbol={symbol}
                      interval={interval}
                    />
                  )}
                </div>
              ) : (
                <div className="flex items-center justify-center h-full w-full bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40 backdrop-blur-xl">
                  <div className="text-center max-w-md mx-auto px-4">
                    <div className="text-6xl mb-4">📊</div>
                    <p className="text-cyan-300 text-xl font-medium mb-2">Select a Trading Provider</p>
                    <p className="text-slate-400 mb-4">Choose a provider from the dropdown above to start trading</p>
                    <div className="bg-slate-800/50 rounded-lg p-4 border border-slate-600/30">
                      <p className="text-slate-300 text-sm mb-2">Available providers:</p>
                      <ul className="text-slate-400 text-xs text-left space-y-1">
                        <li>• Binance (Live trading)</li>
                        <li>• Mock Provider (Testing)</li>
                        <li>• More providers coming soon...</li>
                      </ul>
                    </div>
                  </div>
                </div>
              )}
            </div>
            {/* Bottom Tabs */}
            <div 
              style={{ height: `${bottomBarHeight}px` }}
              className="border-t border-cyan-500/20 bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40 backdrop-blur-xl shadow-2xl shadow-cyan-500/5"
            >
              {/* Resize Handle */}
              <div
                onMouseDown={() => setIsResizing(true)}
                className="h-1 w-full cursor-ns-resize hover:bg-cyan-500/50 active:bg-cyan-500/70 transition-colors duration-150 group"
              >
                <div className="h-0.5 w-20 mx-auto mt-0.5 bg-slate-600/50 group-hover:bg-cyan-500/70 rounded-full transition-colors duration-150"></div>
              </div>
              
              <div className="h-12 flex items-center gap-3 px-4 border-b border-cyan-500/20 bg-gradient-to-r from-slate-800/30 to-slate-700/30">
                <Button
                  variant={activeTab === 'orders' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setActiveTab('orders')}
                >
                  Orders
                </Button>
                <Button
                  variant={activeTab === 'strategies' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setActiveTab('strategies')}
                >
                  Strategies
                </Button>
                <Button
                  variant={activeTab === 'accounts' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setActiveTab('accounts')}
                >
                  Accounts
                </Button>
                <Button
                  variant={activeTab === 'positions' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setActiveTab('positions')}
                >
                  Positions
                </Button>
                <Button
                  variant={activeTab === 'indicators' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setActiveTab('indicators')}
                >
                  Indicators
                </Button>
                <Button
                  variant={activeTab === 'replay' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setActiveTab('replay')}
                >
                  Replay
                </Button>
                {/* Future tabs: Alerts, Console, etc. */}
              </div>
              <div className="h-[calc(100%-3.25rem)]">
                {activeTab === 'orders' && <OrdersTab />}
                {activeTab === 'strategies' && <StrategiesTab />}
                {activeTab === 'accounts' && <AccountsTab />}
                {activeTab === 'positions' && <PositionsTab />}
                {activeTab === 'indicators' && (
                  <IndicatorsTab 
                    onOpenConfigInSidebar={handleOpenIndicatorConfig}
                  />
                )}
                {activeTab === 'replay' && (
                  <ReplayTab 
                    provider={selectedProvider}
                    symbol={symbol}
                    interval={interval}
                  />
                )}
              </div>
            </div>
          </main>
        </div>

        {/* Right Sidebar - Bot Dashboard or Indicator Config */}
        <div 
          style={{ width: `${rightSidebarWidth}px` }}
          className="relative border-l border-zinc-500/20 bg-gradient-to-br from-zinc-900/40 via-gray-900/30 to-zinc-900/40 backdrop-blur-xl shadow-2xl shadow-zinc-500/5 overflow-y-auto flex-shrink-0"
        >
          {/* Resize Handle */}
          <div
            onMouseDown={(e) => {
              setResizeStartX(e.clientX);
              setResizeStartWidth(rightSidebarWidth);
              setIsResizingRightSidebar(true);
            }}
            className="absolute left-0 top-0 w-1 h-full cursor-ew-resize hover:bg-cyan-500/50 active:bg-cyan-500/70 transition-colors duration-150 z-10 group"
          >
            <div className="absolute left-0.5 top-1/2 -translate-y-1/2 h-20 w-0.5 bg-slate-600/50 group-hover:bg-cyan-500/70 rounded-full transition-colors duration-150"></div>
          </div>
          
          {sidebarIndicatorConfig ? (
            <IndicatorConfigModal
              indicator={sidebarIndicatorConfig.indicator}
              initialParams={sidebarIndicatorConfig.initialParams}
              isEditing={sidebarIndicatorConfig.isEditing}
              onClose={handleCloseIndicatorConfig}
              onApply={handleApplyIndicatorFromSidebar}
              isSidebar={true}
            />
          ) : (
            <div className="flex flex-col h-full">
              {/* Right Sidebar Tabs */}
              <div className="flex gap-2 border-b border-zinc-500/20 bg-gradient-to-r from-zinc-800/30 to-zinc-700/30 px-4 py-3">
                <Button
                  variant={rightSidebarView === 'bot' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setRightSidebarView('bot')}
                  className="flex-1"
                >
                  Bot Control
                </Button>
                <Button
                  variant={rightSidebarView === 'manual' ? 'primary' : 'secondary'}
                  size="md"
                  onClick={() => setRightSidebarView('manual')}
                  className="flex-1"
                >
                  Manual Trading
                </Button>
              </div>

              {/* Right Sidebar Content */}
              <div className="flex-1 overflow-hidden">
                {rightSidebarView === 'bot' ? (
                  <BotControl interval={interval} historicalLimit={1000} />
                ) : (
                  <ManualTradingTab symbol={symbol} currentPrice={currentPrice} />
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
