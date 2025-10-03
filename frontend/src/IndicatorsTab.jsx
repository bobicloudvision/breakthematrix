import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { IndicatorConfigModal } from './IndicatorConfigModal';

export function IndicatorsTab() {
  const [indicators, setIndicators] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedIndicator, setSelectedIndicator] = useState(null);
  const [editingInstance, setEditingInstance] = useState(null); // Track which instance is being edited
  const [showBrowseModal, setShowBrowseModal] = useState(false); // Control browse modal
  const [searchQuery, setSearchQuery] = useState(''); // Search filter
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
    setShowBrowseModal(false); // Close browse modal when opening config
  };

  const handleCloseModal = () => {
    setSelectedIndicator(null);
    setEditingInstance(null);
  };

  const handleCloseBrowseModal = () => {
    setShowBrowseModal(false);
    setSearchQuery('');
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

  // Filter indicators based on search query
  const filteredIndicators = indicators.filter(indicator => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    return (
      indicator.name.toLowerCase().includes(query) ||
      indicator.id.toLowerCase().includes(query) ||
      indicator.description.toLowerCase().includes(query) ||
      indicator.category.toLowerCase().includes(query)
    );
  });

  // Group filtered indicators by category
  const groupedIndicators = filteredIndicators.reduce((acc, indicator) => {
    const category = indicator.categoryDisplayName || indicator.category;
    if (!acc[category]) {
      acc[category] = [];
    }
    acc[category].push(indicator);
    return acc;
  }, {});

  // Get indicator details by ID
  const getIndicatorById = (id) => indicators.find(ind => ind.id === id);

  return (
    <div className="h-full w-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-white/10 bg-black/30 flex-shrink-0">
        <div className="text-white/80 text-sm font-medium">Active Indicators</div>
        <button
          onClick={() => setShowBrowseModal(true)}
          className="px-3 py-1.5 text-xs font-medium rounded bg-gradient-to-r from-cyan-500/30 to-blue-500/30 text-cyan-100 border border-cyan-400/50 hover:from-cyan-500/40 hover:to-blue-500/40 transition-all"
        >
          + Add New Indicator
        </button>
      </div>

      {/* Active Indicators - Compact Boxes */}
      <div className="flex-1 overflow-y-auto min-h-0">
        <div className="p-3">
          {enabledIndicators.length === 0 ? (
            <div className="text-center text-white/50 py-12">
              <div className="mb-2">No indicators active</div>
              <button
                onClick={() => setShowBrowseModal(true)}
                className="text-cyan-400 hover:text-cyan-300 text-sm"
              >
                Click "Add New Indicator" to get started
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2">
              {enabledIndicators.map((instance) => {
                const indicator = getIndicatorById(instance.id);
                if (!indicator) return null;
                
                const params = instance.params?.params || {};
                const paramStr = Object.entries(params)
                  .map(([k, v]) => `${k}=${v}`)
                  .join(', ');
                
                return (
                  <div 
                    key={instance.instanceId}
                    className="bg-black/30 rounded border border-white/10 hover:border-cyan-500/30 transition-colors p-2"
                  >
                    <div className="flex flex-col gap-1.5">
                      <div className="flex items-start justify-between gap-1">
                        <h3 className="text-white text-xs font-medium truncate flex-1" title={indicator.name}>
                          {indicator.name}
                        </h3>
                      </div>
                      
                      <div className="text-xs text-white/50 font-mono truncate" title={paramStr || 'default'}>
                        {paramStr || 'default'}
                      </div>
                      
                      <div className="flex items-center gap-1 mt-1">
                        <button
                          onClick={() => handleOpenConfig(indicator, instance)}
                          className="flex-1 px-2 py-1 text-xs rounded bg-cyan-500/20 text-cyan-300 border border-cyan-400/30 hover:bg-cyan-500/30 transition-all"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => removeIndicator(instance.instanceId)}
                          className="px-2 py-1 text-xs rounded bg-red-500/20 text-red-300 border border-red-400/30 hover:bg-red-500/30 transition-all"
                        >
                          ×
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Browse Indicators Modal */}
      {showBrowseModal && createPortal(
        <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 rounded-lg border border-white/20 w-full max-w-5xl max-h-[90vh] flex flex-col">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
              <h2 className="text-white text-lg font-semibold">Browse Indicators</h2>
              <button
                onClick={handleCloseBrowseModal}
                className="text-white/60 hover:text-white text-2xl leading-none"
              >
                ×
              </button>
            </div>

            {/* Search Bar */}
            <div className="px-4 py-3 border-b border-white/10">
              <input
                type="text"
                placeholder="Search indicators by name, category, or description..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full px-3 py-2 bg-black/30 border border-white/20 rounded text-white placeholder-white/40 text-sm focus:outline-none focus:border-cyan-400/50"
                autoFocus
              />
            </div>

            {/* Loading/Error States */}
            {error && (
              <div className="px-4 py-3 bg-red-500/10 border-b border-red-500/20">
                <div className="text-red-400 text-sm">Failed to load indicators: {error}</div>
              </div>
            )}

            {loading && (
              <div className="px-4 py-3 text-center text-white/50">
                Loading indicators...
              </div>
            )}

            {/* Indicators List - Scrollable */}
            <div className="flex-1 overflow-y-auto p-4">
              {!loading && indicators.length === 0 ? (
                <div className="text-center text-white/50 py-12">
                  <div className="mb-2">No indicators found</div>
                  <button
                    onClick={fetchIndicators}
                    className="text-cyan-400 hover:text-cyan-300 text-sm"
                  >
                    Try refreshing
                  </button>
                </div>
              ) : (
                <div className="space-y-6">
                  {Object.entries(groupedIndicators).map(([category, categoryIndicators]) => (
                    <div key={category}>
                      {/* Category Header */}
                      <div className="text-cyan-400 text-sm font-semibold uppercase tracking-wide mb-3 sticky top-0 bg-gray-900 py-1">
                        {category}
                      </div>
                      
                      {/* Indicators Grid */}
                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                        {categoryIndicators.map((indicator) => (
                          <div 
                            key={indicator.id}
                            className="bg-black/30 rounded border border-white/10 hover:border-cyan-500/30 transition-colors p-3 cursor-pointer"
                            onClick={() => handleOpenConfig(indicator)}
                          >
                            <div className="flex flex-col gap-2">
                              <div>
                                <h3 className="text-white font-medium text-sm mb-1">{indicator.name}</h3>
                                <span className="text-xs text-white/50 font-mono">{indicator.id}</span>
                              </div>
                              
                              <p className="text-xs text-white/60 leading-relaxed line-clamp-3">
                                {indicator.description}
                              </p>
                              
                              <div className="flex items-center justify-between mt-1">
                                <span className="text-xs px-2 py-1 rounded bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
                                  {indicator.category}
                                </span>
                                <span className="text-xs text-cyan-400 font-medium">
                                  Click to add →
                                </span>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                  
                  {filteredIndicators.length === 0 && searchQuery && (
                    <div className="text-center text-white/50 py-12">
                      No indicators match "{searchQuery}"
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* Config Modal */}
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
