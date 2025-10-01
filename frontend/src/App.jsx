import React, { useState, useEffect } from "react";
import { Chart } from "./Chart";
import { TradingProviderDropdown } from "./TradingProviderDropdown";

export default function App() {
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [symbol, setSymbol] = useState('ETHUSDT');
  const [interval, setInterval] = useState('1m');

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

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      <div className="flex flex-col h-screen">
        {/* Header */}
        <div className="p-4 border-b border-gray-800 bg-gray-900">
          <div className="max-w-6xl mx-auto">
            <h1 className="text-2xl font-bold">BreakTheMatrix Trading</h1>
          </div>
        </div>

        {/* Controls */}
        <div className="p-4 bg-gray-900 border-b border-gray-800">
          <div className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2">Trading Provider</label>
              <TradingProviderDropdown 
                onProviderSelect={handleProviderSelect}
                selectedProvider={selectedProvider}
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium mb-2">Symbol</label>
              <input
                type="text"
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
                className="w-full px-3 py-2 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g., ETHUSDT"
              />
            </div>
            
            <div>
              <label className="block text-sm font-medium mb-2">Interval</label>
              <select
                value={interval}
                onChange={(e) => setInterval(e.target.value)}
                className="w-full px-3 py-2 bg-gray-700 text-white rounded-lg border border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="1m">1 Minute</option>
                <option value="5m">5 Minutes</option>
                <option value="15m">15 Minutes</option>
                <option value="1h">1 Hour</option>
                <option value="4h">4 Hours</option>
                <option value="1d">1 Day</option>
              </select>
            </div>
          </div>
        </div>

        {/* Chart Area */}
        <div className="flex-1 bg-gray-800">
          <Chart 
            provider={selectedProvider}
            symbol={symbol}
            interval={interval}
          />
        </div>
      </div>
    </div>
  );
}
