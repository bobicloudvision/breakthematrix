import React, { useState, useEffect } from 'react';
import { Button } from './Button';

export function ManualTradingTab({ symbol = 'BTCUSDT', currentPrice = null }) {
  const [formData, setFormData] = useState({
    positionSide: 'LONG',
    orderType: 'LIMIT',
    price: '',
    quantity: '',
    stopLoss: '',
    takeProfit: ''
  });

  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [error, setError] = useState(null);
  const [isManualPriceEdit, setIsManualPriceEdit] = useState(false);
  const [isManualStopLossEdit, setIsManualStopLossEdit] = useState(false);
  const [isManualTakeProfitEdit, setIsManualTakeProfitEdit] = useState(false);

  // Configuration for default stop loss and take profit percentages
  const STOP_LOSS_PERCENTAGE = 1; // 1% stop loss
  const TAKE_PROFIT_PERCENTAGE = 2; // 2% take profit

  // Update symbol when it changes
  useEffect(() => {
    // Reset form when symbol changes (optional)
    setResponse(null);
    setError(null);
    // Reset manual edit flags when symbol changes - allow auto-fill for new symbol
    setIsManualPriceEdit(false);
    setIsManualStopLossEdit(false);
    setIsManualTakeProfitEdit(false);
  }, [symbol]);

  // Auto-fill price when currentPrice changes (for LIMIT orders) - only if not manually edited
  useEffect(() => {
    if (currentPrice && !isManualPriceEdit) {
      setFormData(prev => {
        // Only update if it's a LIMIT order
        if (prev.orderType === 'LIMIT') {
          return {
            ...prev,
            price: currentPrice.toString()
          };
        }
        return prev;
      });
    }
  }, [currentPrice, isManualPriceEdit]);

  // Update price when switching to LIMIT order type - only if not manually edited
  useEffect(() => {
    setFormData(prev => {
      // When switching to LIMIT and we have a current price but no price set
      if (prev.orderType === 'LIMIT' && currentPrice && !prev.price && !isManualPriceEdit) {
        return {
          ...prev,
          price: currentPrice.toString()
        };
      }
      return prev;
    });
  }, [formData.orderType, currentPrice, isManualPriceEdit]);

  // Auto-calculate stop loss and take profit based on price and position side
  useEffect(() => {
    const price = parseFloat(formData.price);
    if (!price || isNaN(price)) return;

    setFormData(prev => {
      const updates = {};
      
      // Calculate stop loss if not manually edited
      if (!isManualStopLossEdit) {
        if (prev.positionSide === 'LONG') {
          // For LONG: stop loss below entry price
          const stopLoss = price * (1 - STOP_LOSS_PERCENTAGE / 100);
          updates.stopLoss = stopLoss.toFixed(2);
        } else {
          // For SHORT: stop loss above entry price
          const stopLoss = price * (1 + STOP_LOSS_PERCENTAGE / 100);
          updates.stopLoss = stopLoss.toFixed(2);
        }
      }
      
      // Calculate take profit if not manually edited
      if (!isManualTakeProfitEdit) {
        if (prev.positionSide === 'LONG') {
          // For LONG: take profit above entry price
          const takeProfit = price * (1 + TAKE_PROFIT_PERCENTAGE / 100);
          updates.takeProfit = takeProfit.toFixed(2);
        } else {
          // For SHORT: take profit below entry price
          const takeProfit = price * (1 - TAKE_PROFIT_PERCENTAGE / 100);
          updates.takeProfit = takeProfit.toFixed(2);
        }
      }
      
      return { ...prev, ...updates };
    });
  }, [formData.price, formData.positionSide, isManualStopLossEdit, isManualTakeProfitEdit, STOP_LOSS_PERCENTAGE, TAKE_PROFIT_PERCENTAGE]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    
    // Track manual edits
    if (name === 'price') {
      setIsManualPriceEdit(true);
    } else if (name === 'stopLoss') {
      setIsManualStopLossEdit(true);
    } else if (name === 'takeProfit') {
      setIsManualTakeProfitEdit(true);
    }
    
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
        symbol: symbol,
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
      positionSide: 'LONG',
      orderType: 'LIMIT',
      price: '',
      quantity: '',
      stopLoss: '',
      takeProfit: ''
    });
    setResponse(null);
    setError(null);
    // Reset manual edit flags to allow auto-fill again
    setIsManualPriceEdit(false);
    setIsManualStopLossEdit(false);
    setIsManualTakeProfitEdit(false);
  };

  const handleOrderTypeChange = (newOrderType) => {
    setFormData(prev => ({ ...prev, orderType: newOrderType }));
    // Reset manual edit flags when switching order types to allow auto-fill
    setIsManualPriceEdit(false);
    setIsManualStopLossEdit(false);
    setIsManualTakeProfitEdit(false);
  };

  const handlePositionSideChange = (newSide) => {
    setFormData(prev => ({ ...prev, positionSide: newSide }));
    // Reset stop loss and take profit flags when changing position side
    // so they recalculate based on the new direction
    setIsManualStopLossEdit(false);
    setIsManualTakeProfitEdit(false);
  };

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  };

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
            {/* Symbol Display */}
            <div className="bg-slate-800/50 rounded-lg p-3 border border-slate-600/30 hidden">
              <label className="block text-slate-300 text-sm font-medium mb-2">Symbol</label>
              <div className="w-full px-3 py-2 text-sm bg-slate-900/50 text-cyan-300 rounded-lg border border-cyan-500/30 font-semibold">
                {symbol}
              </div>
            </div>

            {/* Position Side */}
            <div>
              <label className="block text-slate-300 text-sm font-medium mb-2">Position Side</label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  onClick={() => handlePositionSideChange('LONG')}
                  variant={formData.positionSide === 'LONG' ? 'success' : 'secondary'}
                  size="sm"
                  fullWidth
                >
                  LONG
                </Button>
                <Button
                  type="button"
                  onClick={() => handlePositionSideChange('SHORT')}
                  variant={formData.positionSide === 'SHORT' ? 'danger' : 'secondary'}
                  size="sm"
                  fullWidth
                >
                  SHORT
                </Button>
              </div>
            </div>

            {/* Order Type */}
            <div>
              <label className="block text-slate-300 text-sm font-medium mb-2">Order Type</label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  onClick={() => handleOrderTypeChange('LIMIT')}
                  variant={formData.orderType === 'LIMIT' ? 'primary' : 'secondary'}
                  size="sm"
                  fullWidth
                >
                  LIMIT
                </Button>
                <Button
                  type="button"
                  onClick={() => handleOrderTypeChange('MARKET')}
                  variant={formData.orderType === 'MARKET' ? 'primary' : 'secondary'}
                  size="sm"
                  fullWidth
                >
                  MARKET
                </Button>
              </div>
            </div>

            {/* Price */}
            <div>
              <label className="block text-slate-300 text-sm font-medium mb-2">
                Price (USD)
                {formData.orderType === 'MARKET' && <span className="text-slate-500 text-xs ml-1">(Market)</span>}
                {formData.orderType === 'LIMIT' && currentPrice && !isManualPriceEdit && (
                  <span className="text-cyan-400 text-xs ml-1">🔄 Live</span>
                )}
                {formData.orderType === 'LIMIT' && isManualPriceEdit && (
                  <span className="text-green-400 text-xs ml-1">✏️ Manual</span>
                )}
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
            <div>
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
            <div>
              <label className="block text-slate-300 text-sm font-medium mb-2">
                Stop Loss (USD)
                <span className="text-slate-500 text-xs ml-1">(Optional)</span>
                {!isManualStopLossEdit && formData.stopLoss && (
                  <span className="text-cyan-400 text-xs ml-1">🔄 Auto {STOP_LOSS_PERCENTAGE}%</span>
                )}
                {isManualStopLossEdit && (
                  <span className="text-green-400 text-xs ml-1">✏️ Manual</span>
                )}
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
            <div>
              <label className="block text-slate-300 text-sm font-medium mb-2">
                Take Profit (USD)
                <span className="text-slate-500 text-xs ml-1">(Optional)</span>
                {!isManualTakeProfitEdit && formData.takeProfit && (
                  <span className="text-cyan-400 text-xs ml-1">🔄 Auto {TAKE_PROFIT_PERCENTAGE}%</span>
                )}
                {isManualTakeProfitEdit && (
                  <span className="text-green-400 text-xs ml-1">✏️ Manual</span>
                )}
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
              <Button
                type="submit"
                disabled={loading}
                loading={loading}
                variant="primary"
                size="md"
                fullWidth
              >
                Open Position
              </Button>
              <Button
                type="button"
                onClick={handleReset}
                disabled={loading}
                variant="secondary"
                size="md"
              >
                Reset
              </Button>
            </div>
          </form>
        </div>
      </div>

      {/* Response Section */}
      <div className="border-t border-zinc-500/20 backdrop-blur-xl p-4">
        <h3 className="text-sm font-semibold text-zinc-300 mb-3">Response</h3>

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

