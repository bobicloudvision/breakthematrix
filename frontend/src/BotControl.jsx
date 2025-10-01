import React, { useState, useEffect } from 'react';

export const BotControl = ({ interval, historicalLimit = 100 }) => {
    const [isEnabled, setIsEnabled] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const [lastAction, setLastAction] = useState(null);
    const [botStatus, setBotStatus] = useState({
        enabled: false,
        mode: 'IDLE',
        activeStrategies: 0,
        strategies: 0,
        tradingStarted: false
    });
    const [expandedSections, setExpandedSections] = useState({
        status: true,
        settings: false,
        performance: false,
        logs: false
    });

    // Fetch bot status from API
    const fetchBotStatus = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/bot/status', {
                method: 'GET',
                headers: {
                    'accept': '*/*',
                },
            });
            
            if (response.ok) {
                const status = await response.json();
                setBotStatus(status);
                setIsEnabled(status.enabled);
                console.log('Bot status fetched:', status);
            }
        } catch (err) {
            console.error('Failed to fetch bot status:', err);
        }
    };

    // Poll bot status every 5 seconds
    useEffect(() => {
        fetchBotStatus(); // Initial fetch
        const interval = setInterval(fetchBotStatus, 5000);
        return () => clearInterval(interval);
    }, []);

    const enableBot = async () => {
        try {
            setIsLoading(true);
            setError(null);
            
            const url = `http://localhost:8080/api/bot/enable?bootstrap=true&interval=${interval}&historicalLimit=${historicalLimit}`;
            
            console.log('Enabling bot:', url);
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'accept': '*/*',
                    'Content-Type': 'application/json',
                },
                body: '',
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            setLastAction('enabled');
            console.log('Bot enabled successfully');
            // Refresh status after enabling
            setTimeout(fetchBotStatus, 1000);
        } catch (err) {
            setError(err.message);
            console.error('Failed to enable bot:', err);
        } finally {
            setIsLoading(false);
        }
    };

    const disableBot = async () => {
        try {
            setIsLoading(true);
            setError(null);
            
            const url = `http://localhost:8080/api/bot/disable`;
            
            console.log('Disabling bot:', url);
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'accept': '*/*',
                    'Content-Type': 'application/json',
                },
                body: '',
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            setLastAction('disabled');
            console.log('Bot disabled successfully');
            // Refresh status after disabling
            setTimeout(fetchBotStatus, 1000);
        } catch (err) {
            setError(err.message);
            console.error('Failed to disable bot:', err);
        } finally {
            setIsLoading(false);
        }
    };

    const handleToggle = () => {
        if (isEnabled) {
            disableBot();
        } else {
            enableBot();
        }
    };

    const toggleSection = (section) => {
        setExpandedSections(prev => ({
            ...prev,
            [section]: !prev[section]
        }));
    };

    const SectionHeader = ({ title, section, icon }) => (
        <button
            onClick={() => toggleSection(section)}
            className="w-full flex items-center justify-between p-3 text-left hover:bg-white/5 transition-colors"
        >
            <div className="flex items-center gap-2">
                {icon}
                <span className="text-white font-medium">{title}</span>
            </div>
            <svg 
                className={`w-4 h-4 text-white/50 transition-transform ${
                    expandedSections[section] ? 'rotate-180' : ''
                }`} 
                fill="none" 
                stroke="currentColor" 
                viewBox="0 0 24 24"
            >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
        </button>
    );

    return (
        <div className="h-full flex flex-col bg-black/20">
            {/* Header */}
            <div className="p-4 border-b border-white/10">
                <h2 className="text-lg font-semibold text-white">Bot Dashboard</h2>
                <p className="text-sm text-white/60">Trading Bot Control Panel</p>
            </div>

            {/* Status Section */}
            <div className="border-b border-white/10">
                <SectionHeader
                    title="Status"
                    section="status"
                    icon={
                        <div className={`w-2 h-2 rounded-full ${isEnabled ? 'bg-green-500' : 'bg-gray-500'}`}></div>
                    }
                />
                {expandedSections.status && (
                    <div className="p-4 space-y-4">
                        <div className="flex items-center justify-between">
                            <span className="text-white/70">Bot Status</span>
                            <span className={`text-sm font-medium ${botStatus.enabled ? 'text-green-400' : 'text-gray-400'}`}>
                                {botStatus.enabled ? 'Running' : 'Stopped'}
                            </span>
                        </div>

                        <div className="flex items-center justify-between">
                            <span className="text-white/70">Mode</span>
                            <span className={`text-sm font-medium ${
                                botStatus.mode === 'TRADING' ? 'text-green-400' : 
                                botStatus.mode === 'IDLE' ? 'text-yellow-400' : 'text-gray-400'
                            }`}>
                                {botStatus.mode}
                            </span>
                        </div>

                        <div className="flex items-center justify-between">
                            <span className="text-white/70">Trading Started</span>
                            <span className={`text-sm font-medium ${botStatus.tradingStarted ? 'text-green-400' : 'text-gray-400'}`}>
                                {botStatus.tradingStarted ? 'Yes' : 'No'}
                            </span>
                        </div>

                        <div className="flex items-center justify-between">
                            <span className="text-white/70">Active Strategies</span>
                            <span className="text-white text-sm">{botStatus.activeStrategies}/{botStatus.strategies}</span>
                        </div>
                        
                        <div className="flex items-center justify-between">
                            <span className="text-white/70">Interval</span>
                            <span className="text-white text-sm">{interval}</span>
                        </div>

                        <div className="flex items-center justify-between">
                            <span className="text-white/70">Historical Limit</span>
                            <span className="text-white text-sm">{historicalLimit}</span>
                        </div>

                        {/* Toggle Button */}
                        <button
                            onClick={handleToggle}
                            disabled={isLoading}
                            className={`w-full px-4 py-2 rounded-md text-sm font-medium transition-all ${
                                isEnabled
                                    ? 'bg-red-500/20 text-red-300 border border-red-500/30 hover:bg-red-500/30'
                                    : 'bg-green-500/20 text-green-300 border border-green-500/30 hover:bg-green-500/30'
                            } ${isLoading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                        >
                            {isLoading ? (
                                <div className="flex items-center justify-center gap-2">
                                    <div className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin"></div>
                                    <span>Processing...</span>
                                </div>
                            ) : (
                                <div className="flex items-center justify-center gap-2">
                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        {isEnabled ? (
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                        ) : (
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h1m4 0h1m-6 4h8m-5-8a3 3 0 110 6 3 3 0 010-6z" />
                                        )}
                                    </svg>
                                    <span>{isEnabled ? 'Stop Bot' : 'Start Bot'}</span>
                                </div>
                            )}
                        </button>

                        {/* Error Display */}
                        {error && (
                            <div className="p-2 bg-red-500/10 border border-red-500/20 rounded text-red-400 text-sm">
                                Error: {error}
                            </div>
                        )}

                        {/* Success Message */}
                        {lastAction && !isLoading && (
                            <div className="p-2 bg-green-500/10 border border-green-500/20 rounded text-green-400 text-sm">
                                Bot {lastAction} successfully
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Settings Section */}
            <div className="border-b border-white/10">
                <SectionHeader
                    title="Settings"
                    section="settings"
                    icon={
                        <svg className="w-4 h-4 text-white/70" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        </svg>
                    }
                />
                {expandedSections.settings && (
                    <div className="p-4 space-y-3">
                        <div className="text-white/60 text-sm">
                            Bot configuration settings will be available here.
                        </div>
                        <div className="space-y-2">
                            <div className="text-white/50 text-xs">• Risk Management</div>
                            <div className="text-white/50 text-xs">• Trading Strategies</div>
                            <div className="text-white/50 text-xs">• Notification Settings</div>
                        </div>
                    </div>
                )}
            </div>

            {/* Performance Section */}
            <div className="border-b border-white/10">
                <SectionHeader
                    title="Performance"
                    section="performance"
                    icon={
                        <svg className="w-4 h-4 text-white/70" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                        </svg>
                    }
                />
                {expandedSections.performance && (
                    <div className="p-4 space-y-3">
                        <div className="text-white/60 text-sm">
                            Trading performance metrics will be displayed here.
                        </div>
                        <div className="space-y-2">
                            <div className="text-white/50 text-xs">• P&L Tracking</div>
                            <div className="text-white/50 text-xs">• Win/Loss Ratio</div>
                            <div className="text-white/50 text-xs">• Trade History</div>
                        </div>
                    </div>
                )}
            </div>

            {/* Logs Section */}
            <div className="flex-1">
                <SectionHeader
                    title="Logs"
                    section="logs"
                    icon={
                        <svg className="w-4 h-4 text-white/70" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                    }
                />
                {expandedSections.logs && (
                    <div className="p-4 space-y-3">
                        <div className="text-white/60 text-sm">
                            Bot activity logs will be shown here.
                        </div>
                        <div className="space-y-2">
                            <div className="text-white/50 text-xs">• Real-time Events</div>
                            <div className="text-white/50 text-xs">• Error Messages</div>
                            <div className="text-white/50 text-xs">• Trade Executions</div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};
