import React, { useState } from "react";
import { Chart } from "./Chart";
import { TradingProviderDropdown } from "./TradingProviderDropdown";

export default function App() {
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [symbol, setSymbol] = useState('ETHUSDT');
  const [interval, setInterval] = useState('1m');

  const handleProviderSelect = (provider) => {
    setSelectedProvider(provider);
    console.log('Selected provider:', provider);
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-6">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-8 text-center">BreakTheMatrix Trading</h1>
        
        <div className="mb-8 grid grid-cols-1 md:grid-cols-3 gap-4">
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

        <div className="bg-gray-800 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">
            Price Chart - {symbol} ({interval})
          </h2>
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
