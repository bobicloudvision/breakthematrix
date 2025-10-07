import React, { useState, useEffect } from 'react';

export function ManualTradingTab({ symbol = 'BTCUSDT' }) {
  const [formData, setFormData] = useState({
    positionSide: 'LONG',
    orderType: 'LIMIT',
    price: '',
    quantity: '',
    stopLoss: '',
    takeProfit: ''
  });

  // Update symbol when it changes
  useEffect(() => {
    // Reset form when symbol changes (optional)
    setResponse(null);
    setError(null);
  }, [symbol]);

  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [error, setError] = useState(null);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResponse(null);

    try {
      // Prepare request body
      const requestBody = {
        symbol: formData.symbol,
        positionSide: formData.positionSide,
        orderType: formData.orderType,
        price: parseFloat(formData.price),
        quantity: parseFloat(formData.quantity)
      };

      // Add optional fields if provided
      if (formData.stopLoss) {
        requestBody.stopLoss = parseFloat(formData.stopLoss);
      }
      if (formData.takeProfit) {
        requestBody.takeProfit = parseFloat(formData.takeProfit);
      }

      const res = await fetch('http://localhost:8080/api/positions/open', {
        method: 'POST',
        headers: {
          'accept': '*/*',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
      });

      const data = await res.json();

      if (res.ok) {
        setResponse(data);
        console.log('Position opened successfully:', data);
        // Dispatch event to refresh positions
        window.dispatchEvent(new Event('positionsChanged'));
      } else {
        setError(data.message || 'Failed to open position');
      }
    } catch (err) {
      console.error('Error opening position:', err);
      setError(err.message || 'Failed to open position');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setFormData({
      symbol: 'BTCUSDT',
      positionSide: 'LONG',
      orderType: 'LIMIT',
      price: '',
      quantity: '',
      stopLoss: '',
      takeProfit: ''
    });
    setResponse(null);
    setError(null);
  };

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  };

  const symbolOptions = [
    'BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'ADAUSDT', 'XRPUSDT', 
    'SOLUSDT', 'DOTUSDT', 'DOGEUSDT', 'AVAXUSDT', 'MATICUSDT'
  ];

  return (
    <div className="h-full flex flex-col overflow-y-auto">
      {/* Form Section */}
      <div className="p-4">
        <div>
          <div className="mb-4">
            <h2 className="text-lg font-bold bg-gradient-to-r from-cyan-400 to-blue-400 bg-clip-text text-transparent mb-1">
              Manual Trading
            </h2>
            <p className="text-slate-400 text-xs">
              Open trading position manually
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3">
            {/* Symbol Selection */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">Symbol</label>
              <select
                name="symbol"
                value={formData.symbol}
                onChange={handleInputChange}
                className="w-full px-3 py-2 text-sm bg-slate-900/50 text-cyan-200 rounded-lg border border-cyan-500/30 focus:outline-none focus:ring-2 focus:ring-cyan-500/50 hover:border-cyan-400/50 transition-all"
                required
              >
                {symbolOptions.map(sym => (
                  <option key={sym} value={sym}>{sym}</option>
                ))}
              </select>
            </div>

            {/* Position Side */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">Position Side</label>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setFormData(prev => ({ ...prev, positionSide: 'LONG' }))}
                  className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg transition-all duration-200 ${
                    formData.positionSide === 'LONG'
                      ? 'bg-green-500/30 text-green-100 border border-green-400/50 shadow-lg shadow-green-500/20'
                      : 'bg-slate-700/40 text-slate-300 border border-slate-600/40 hover:border-green-500/40'
                  }`}
                >
                  LONG
                </button>
                <button
                  type="button"
                  onClick={() => setFormData(prev => ({ ...prev, positionSide: 'SHORT' }))}
                  className={`flex-1 px-3 py-2 text-xs font-medium rounded-lg transition-all duration-200 ${
                    formData.positionSide === 'SHORT'
                      ? 'bg-red-500/30 text-red-100 border border-red-400/50 shadow-lg shadow-red-500/20'
                      : 'bg-slate-700/40 text-slate-300 border border-slate-600/40 hover:border-red-500/40'
                  }`}
                >
                  SHORT
                </button>
              </div>
            </div>

            {/* Order Type */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">Order Type</label>
              <select
                name="orderType"
                value={formData.orderType}
                onChange={handleInputChange}
                className="w-full px-3 py-2 text-sm bg-slate-900/50 text-cyan-200 rounded-lg border border-cyan-500/30 focus:outline-none focus:ring-2 focus:ring-cyan-500/50 hover:border-cyan-400/50 transition-all"
                required
              >
                <option value="LIMIT">LIMIT</option>
                <option value="MARKET">MARKET</option>
              </select>
            </div>

            {/* Price */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">
                Price (USD)
                {formData.orderType === 'MARKET' && <span className="text-slate-500 text-xs ml-1">(Market)</span>}
              </label>
              <input
                type="number"
                name="price"
                value={formData.price}
                onChange={handleInputChange}
                placeholder="45000"
                step="0.01"
                className="w-full px-3 py-2 text-sm bg-slate-900/50 text-cyan-200 rounded-lg border border-cyan-500/30 focus:outline-none focus:ring-2 focus:ring-cyan-500/50 hover:border-cyan-400/50 transition-all placeholder-slate-500"
                required={formData.orderType === 'LIMIT'}
                disabled={formData.orderType === 'MARKET'}
              />
            </div>

            {/* Quantity */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">Quantity</label>
              <input
                type="number"
                name="quantity"
                value={formData.quantity}
                onChange={handleInputChange}
                placeholder="0.001"
                step="0.00000001"
                className="w-full px-3 py-2 text-sm bg-slate-900/50 text-cyan-200 rounded-lg border border-cyan-500/30 focus:outline-none focus:ring-2 focus:ring-cyan-500/50 hover:border-cyan-400/50 transition-all placeholder-slate-500"
                required
              />
            </div>

            {/* Stop Loss */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">
                Stop Loss (USD)
                <span className="text-slate-500 text-xs ml-1">(Optional)</span>
              </label>
              <input
                type="number"
                name="stopLoss"
                value={formData.stopLoss}
                onChange={handleInputChange}
                placeholder="44000"
                step="0.01"
                className="w-full px-3 py-2 text-sm bg-slate-900/50 text-red-300 rounded-lg border border-red-500/30 focus:outline-none focus:ring-2 focus:ring-red-500/50 hover:border-red-400/50 transition-all placeholder-slate-500"
              />
            </div>

            {/* Take Profit */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30">
              <label className="block text-slate-300 text-sm font-medium mb-2">
                Take Profit (USD)
                <span className="text-slate-500 text-xs ml-1">(Optional)</span>
              </label>
              <input
                type="number"
                name="takeProfit"
                value={formData.takeProfit}
                onChange={handleInputChange}
                placeholder="47000"
                step="0.01"
                className="w-full px-3 py-2 text-sm bg-slate-900/50 text-green-300 rounded-lg border border-green-500/30 focus:outline-none focus:ring-2 focus:ring-green-500/50 hover:border-green-400/50 transition-all placeholder-slate-500"
              />
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={loading}
                className="flex-1 px-4 py-2.5 text-sm bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 rounded-lg border border-cyan-400/50 shadow-lg shadow-cyan-500/20 hover:from-cyan-500/40 hover:to-blue-500/40 hover:shadow-cyan-500/30 transition-all duration-200 font-medium disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? 'Opening...' : 'Open Position'}
              </button>
              <button
                type="button"
                onClick={handleReset}
                disabled={loading}
                className="px-4 py-2.5 text-sm bg-gradient-to-r from-slate-800/40 to-slate-700/40 text-slate-300 rounded-lg border border-slate-600/40 hover:from-slate-700/60 hover:to-slate-600/60 hover:text-cyan-200 hover:border-cyan-500/40 transition-all duration-200 font-medium disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Reset
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* Response Section */}
      <div className="border-t border-cyan-500/20 bg-gradient-to-br from-slate-900/40 via-gray-900/30 to-slate-900/40 backdrop-blur-xl p-4">
        <h3 className="text-sm font-semibold text-cyan-300 mb-3">Response</h3>

        {!response && !error && (
          <div className="text-center py-8">
            <div className="text-3xl mb-2">📋</div>
            <p className="text-slate-400 text-xs">
              Response will appear here
            </p>
          </div>
        )}

        {error && (
          <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-3">
            <div className="flex items-start gap-2">
              <div className="text-xl">❌</div>
              <div>
                <div className="text-red-400 font-semibold text-sm mb-1">Error</div>
                <div className="text-red-300 text-xs">{error}</div>
              </div>
            </div>
          </div>
        )}

        {response && (
          <div className="space-y-3">
            {/* Status Badge */}
            <div className={`rounded-lg p-3 border ${
              response.status === 'SUBMITTED' || response.status === 'FILLED'
                ? 'bg-green-500/10 border-green-500/30'
                : 'bg-yellow-500/10 border-yellow-500/30'
            }`}>
              <div className="flex items-center gap-2 mb-2">
                <div className="text-lg">✅</div>
                <div>
                  <div className="text-green-400 font-semibold text-sm">Success</div>
                  <div className="text-green-300 text-xs">{response.message}</div>
                </div>
              </div>
              <div className="mt-2 pt-2 border-t border-green-500/20">
                <div className="text-xs text-slate-400 mb-0.5">Status</div>
                <div className="text-green-300 font-medium text-sm">{response.status}</div>
              </div>
            </div>

            {/* Position Details */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30 space-y-2">
              <div className="text-cyan-300 font-semibold text-sm mb-2">Position Details</div>
              
              <div>
                <div className="text-xs text-slate-400 mb-0.5">Position ID</div>
                <div className="text-cyan-200 font-mono text-xs break-all">{response.positionId}</div>
              </div>
              
              <div>
                <div className="text-xs text-slate-400 mb-0.5">Order ID</div>
                <div className="text-cyan-200 font-mono text-xs break-all">{response.orderId}</div>
              </div>
              
              {response.executionPrice && (
                <div>
                  <div className="text-xs text-slate-400 mb-0.5">Execution Price</div>
                  <div className="text-cyan-200 font-medium text-sm">{formatCurrency(response.executionPrice)}</div>
                </div>
              )}
              
              <div>
                <div className="text-xs text-slate-400 mb-0.5">Order Type</div>
                <div className="text-cyan-200 text-sm">{response.orderType}</div>
              </div>
            </div>

            {/* Order Details */}
            {response.order && (
              <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30 space-y-2">
                <div className="text-cyan-300 font-semibold text-sm mb-2">Order Details</div>
                
                <div className="space-y-2">
                  <div className="flex justify-between items-center">
                    <div className="text-xs text-slate-400">Symbol</div>
                    <div className="text-cyan-200 font-medium text-sm">{response.order.symbol}</div>
                  </div>
                  
                  <div className="flex justify-between items-center">
                    <div className="text-xs text-slate-400">Side</div>
                    <div className={`font-medium text-sm ${
                      response.order.side === 'BUY' ? 'text-green-400' : 'text-red-400'
                    }`}>
                      {response.order.side}
                    </div>
                  </div>
                  
                  <div className="flex justify-between items-center">
                    <div className="text-xs text-slate-400">Quantity</div>
                    <div className="text-cyan-200 text-sm">{response.order.quantity}</div>
                  </div>
                  
                  {response.order.price && (
                    <div className="flex justify-between items-center">
                      <div className="text-xs text-slate-400">Price</div>
                      <div className="text-cyan-200 text-sm">{formatCurrency(response.order.price)}</div>
                    </div>
                  )}
                  
                  {response.order.suggestedStopLoss && (
                    <div className="flex justify-between items-center">
                      <div className="text-xs text-slate-400">Stop Loss</div>
                      <div className="text-red-400 text-sm">{formatCurrency(response.order.suggestedStopLoss)}</div>
                    </div>
                  )}
                  
                  {response.order.suggestedTakeProfit && (
                    <div className="flex justify-between items-center">
                      <div className="text-xs text-slate-400">Take Profit</div>
                      <div className="text-green-400 text-sm">{formatCurrency(response.order.suggestedTakeProfit)}</div>
                    </div>
                  )}
                </div>
                
                {response.order.createdAt && (
                  <div className="pt-2 border-t border-slate-600/30">
                    <div className="text-xs text-slate-400 mb-0.5">Created At</div>
                    <div className="text-cyan-200 text-xs">
                      {new Date(response.order.createdAt).toLocaleString()}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Raw Response (Collapsible) */}
            <details className="bg-slate-800/30 rounded-lg border border-slate-600/30">
              <summary className="px-3 py-2 cursor-pointer text-slate-300 hover:text-cyan-300 transition-colors text-xs font-medium">
                View Raw Response
              </summary>
              <div className="px-3 pb-3">
                <pre className="text-xs text-slate-400 overflow-auto max-h-48 bg-slate-900/50 rounded p-2 border border-slate-700/30">
                  {JSON.stringify(response, null, 2)}
                </pre>
              </div>
            </details>
          </div>
        )}
      </div>
    </div>
  );
}

