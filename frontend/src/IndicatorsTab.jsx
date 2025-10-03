import React, { useEffect, useState } from 'react';
import { IndicatorConfigModal } from './IndicatorConfigModal';

export function IndicatorsTab() {
  const [indicators, setIndicators] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedIndicator, setSelectedIndicator] = useState(null);
  const [editingInstance, setEditingInstance] = useState(null); // Track which instance is being edited
  const [enabledIndicators, setEnabledIndicators] = useState(() => {
    // Load enabled indicators from localStorage
    const stored = localStorage.getItem('enabledIndicators');
    return stored ? JSON.parse(stored) : [];
  });

  const fetchIndicators = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetch('http://localhost:8080/api/indicators', {
        method: 'GET',
        headers: { accept: 'application/json' },
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      setIndicators(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIndicators();
  }, []);

  const getInitialParams = (indicatorId) => {
    // If editing an existing instance, return its params
    if (editingInstance) {
      return editingInstance.params;
    }
    
    // Otherwise return fresh default params
    return {
      provider: localStorage.getItem('tradingProvider') || 'Binance',
      symbol: localStorage.getItem('tradingSymbol') || 'BTCUSDT',
      interval: localStorage.getItem('tradingInterval') || '5m',
      count: 200,
      params: {}
    };
  };

  const handleOpenConfig = (indicator, instanceToEdit = null) => {
    setSelectedIndicator(indicator);
    setEditingInstance(instanceToEdit);
  };

  const handleCloseModal = () => {
    setSelectedIndicator(null);
    setEditingInstance(null);
  };

  const handleApplyIndicator = (indicatorId, params) => {
    let newEnabled;
    
    if (editingInstance) {
      // Update existing instance
      newEnabled = enabledIndicators.map(ind => 
        ind.instanceId === editingInstance.instanceId
          ? { ...ind, params: params }
          : ind
      );
      console.log(`Updated indicator instance: ${editingInstance.instanceId}`);
    } else {
      // Create new instance
      const instanceId = `${indicatorId}_${Date.now()}`;
      const newIndicator = {
        id: indicatorId, // Base indicator type (e.g., 'sma')
        instanceId: instanceId, // Unique instance ID (e.g., 'sma_1234567890')
        params: params
      };
      newEnabled = [...enabledIndicators, newIndicator];
      console.log(`Added new indicator instance: ${instanceId}`);
    }
    
    setEnabledIndicators(newEnabled);
    localStorage.setItem('enabledIndicators', JSON.stringify(newEnabled));
    // Trigger custom event to notify chart
    window.dispatchEvent(new Event('indicatorsChanged'));
    
    // Close modal
    setSelectedIndicator(null);
    setEditingInstance(null);
  };

  const removeIndicator = (instanceId) => {
    const newEnabled = enabledIndicators.filter(ind => ind.instanceId !== instanceId);
    setEnabledIndicators(newEnabled);
    localStorage.setItem('enabledIndicators', JSON.stringify(newEnabled));
    // Trigger custom event to notify chart
    window.dispatchEvent(new Event('indicatorsChanged'));
  };

  // Group indicators by category
  const groupedIndicators = indicators.reduce((acc, indicator) => {
    const category = indicator.categoryDisplayName || indicator.category;
    if (!acc[category]) {
      acc[category] = [];
    }
    acc[category].push(indicator);
    return acc;
  }, {});

  return (
    <div className="h-full w-full flex flex-col">
      <div className="flex items-center justify-between px-3 py-2 border-b border-white/10 bg-black/30 flex-shrink-0">
        <div className="text-white/80 text-sm font-medium">Available Indicators</div>
        <div className="flex items-center gap-2">
          {loading && (
            <div className="text-xs text-white/50">Loading…</div>
          )}
          <button
            onClick={fetchIndicators}
            className="px-2 py-1 text-xs rounded border border-white/10 text-white/80 hover:bg-white/10"
          >
            Refresh
          </button>
        </div>
      </div>
      {error ? (
        <div className="p-3 text-red-400 text-sm flex-shrink-0">Failed to load indicators: {error}</div>
      ) : (
        <div className="flex-1 overflow-y-auto overflow-x-auto min-h-0">
          <div className="p-3 space-y-4">
            {indicators.length === 0 && !loading ? (
              <div className="text-center text-white/50 py-6">No indicators found</div>
            ) : (
              Object.entries(groupedIndicators).map(([category, categoryIndicators]) => (
                <div key={category} className="space-y-2">
                  {/* Category Header */}
                  <div className="text-cyan-400 text-sm font-semibold uppercase tracking-wide px-2">
                    {category}
                  </div>
                  
                  {/* Indicators in this category */}
                  <div className="grid grid-cols-1 gap-2">
                    {categoryIndicators.map((indicator) => {
                      const enabledInstances = enabledIndicators.filter(ind => ind.id === indicator.id);
                      
                      return (
                        <div 
                          key={indicator.id} 
                          className="bg-black/30 rounded-lg border border-white/10 hover:border-cyan-500/30 transition-colors p-3"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex-1">
                              <div className="flex items-center gap-2 mb-1">
                                <h3 className="text-white font-medium">{indicator.name}</h3>
                                <span className="text-xs text-white/50 font-mono bg-white/5 px-2 py-0.5 rounded">
                                  {indicator.id}
                                </span>
                                {enabledInstances.length > 0 && (
                                  <span className="text-xs px-2 py-1 rounded bg-blue-500/20 text-blue-300 border border-blue-500/30">
                                    📊 {enabledInstances.length} on Chart
                                  </span>
                                )}
                              </div>
                              <p className="text-xs text-white/70 leading-relaxed">
                                {indicator.description}
                              </p>
                              <div className="flex items-center gap-2 mt-2">
                                <span className="text-xs px-2 py-1 rounded bg-gradient-to-r from-cyan-500/20 to-blue-500/20 text-cyan-300 border border-cyan-500/30">
                                  {indicator.category}
                                </span>
                              </div>
                              
                              {/* Show active instances */}
                              {enabledInstances.length > 0 && (
                                <div className="mt-3 space-y-1">
                                  <div className="text-xs text-white/60 font-medium mb-1">Active Instances:</div>
                                  {enabledInstances.map((instance) => {
                                    const params = instance.params?.params || {};
                                    const paramStr = Object.entries(params)
                                      .map(([k, v]) => `${k}=${v}`)
                                      .join(', ');
                                    
                                    return (
                                      <div 
                                        key={instance.instanceId}
                                        className="flex items-center justify-between gap-2 bg-white/5 hover:bg-white/10 rounded px-2 py-1 transition-colors"
                                      >
                                        <span className="text-xs text-white/70 font-mono flex-1">
                                          {paramStr || 'default'}
                                        </span>
                                        <div className="flex items-center gap-1">
                                          <button
                                            onClick={() => handleOpenConfig(indicator, instance)}
                                            className="text-xs text-cyan-400 hover:text-cyan-300 transition-colors px-1"
                                            title="Edit this instance"
                                          >
                                            ✎
                                          </button>
                                          <button
                                            onClick={() => removeIndicator(instance.instanceId)}
                                            className="text-xs text-red-400 hover:text-red-300 transition-colors px-1"
                                            title="Remove this instance"
                                          >
                                            ✕
                                          </button>
                                        </div>
                                      </div>
                                    );
                                  })}
                                </div>
                              )}
                            </div>
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => handleOpenConfig(indicator)}
                                className="px-3 py-1.5 text-xs font-medium rounded-md bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 hover:from-cyan-500/40 hover:to-blue-500/40 transition-all whitespace-nowrap"
                              >
                                + Add
                              </button>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Modal */}
      {selectedIndicator && (
        <IndicatorConfigModal
          indicator={selectedIndicator}
          initialParams={getInitialParams(selectedIndicator.id)}
          onClose={handleCloseModal}
          onApply={handleApplyIndicator}
          isEditing={!!editingInstance}
        />
      )}
    </div>
  );
}
