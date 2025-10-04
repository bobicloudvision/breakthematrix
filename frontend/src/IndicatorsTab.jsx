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
  const [enabledIndicators, setEnabledIndicators] = useState([]);
  const [instancesLoading, setInstancesLoading] = useState(false);

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

  const fetchActiveInstances = async () => {
    try {
      setInstancesLoading(true);
      const provider = localStorage.getItem('tradingProvider') || 'Binance';
      const symbol = localStorage.getItem('tradingSymbol') || 'BTCUSDT';
      const interval = localStorage.getItem('tradingInterval') || '5m';
      
      const res = await fetch(
        `http://localhost:8080/api/indicators/instances?provider=${provider}&symbol=${symbol}&interval=${interval}`,
        {
          method: 'GET',
          headers: { accept: 'application/json' },
        }
      );
      
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      
      const data = await res.json();
      if (data.success && Array.isArray(data.instances)) {
        setEnabledIndicators(data.instances);
      }
    } catch (e) {
      console.error('Failed to fetch active instances:', e);
      setEnabledIndicators([]);
    } finally {
      setInstancesLoading(false);
    }
  };

  useEffect(() => {
    fetchIndicators();
    fetchActiveInstances();
  }, []);

  const getInitialParams = (indicatorId) => {
    // If editing an existing instance, return its params
    if (editingInstance) {
      return {
        provider: editingInstance.provider,
        symbol: editingInstance.symbol,
        interval: editingInstance.interval,
        count: 5000,
        params: editingInstance.params || {}
      };
    }
    
    // Otherwise return fresh default params
    return {
      provider: localStorage.getItem('tradingProvider') || 'Binance',
      symbol: localStorage.getItem('tradingSymbol') || 'BTCUSDT',
      interval: localStorage.getItem('tradingInterval') || '5m',
      count: 5000,
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

  const handleApplyIndicator = async (indicatorId, params) => {
    try {
      // If editing, deactivate the old instance first
      if (editingInstance) {
        await fetch(
          `http://localhost:8080/api/indicators/instances/${encodeURIComponent(editingInstance.instanceKey)}`,
          {
            method: 'DELETE',
            headers: { accept: '*/*' },
          }
        );
        console.log(`Deactivated old indicator instance: ${editingInstance.instanceKey}`);
      }
      
      // Activate the new/updated instance
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
      
      // Refresh the active instances list
      await fetchActiveInstances();
      
      // Trigger custom event to notify chart
      window.dispatchEvent(new Event('indicatorsChanged'));
      
      // Close modal
      setSelectedIndicator(null);
      setEditingInstance(null);
    } catch (e) {
      console.error('Error applying indicator:', e);
      setError(e.message);
    }
  };

  const removeIndicator = async (instanceKey) => {
    try {
      const res = await fetch(
        `http://localhost:8080/api/indicators/instances/${encodeURIComponent(instanceKey)}`,
        {
          method: 'DELETE',
          headers: { accept: '*/*' },
        }
      );
      
      if (!res.ok) {
        throw new Error(`Failed to remove indicator: HTTP ${res.status}`);
      }
      
      const data = await res.json();
      console.log(`Removed indicator instance:`, data);
      
      // Refresh the active instances list
      await fetchActiveInstances();
      
      // Trigger custom event to notify chart
      window.dispatchEvent(new Event('indicatorsChanged'));
    } catch (e) {
      console.error('Error removing indicator:', e);
      setError(e.message);
    }
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
        <div className="text-white/80 text-sm font-medium">
          Active Indicators {instancesLoading && <span className="text-xs opacity-50">(loading...)</span>}
        </div>
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
                const indicator = getIndicatorById(instance.indicatorId);
                if (!indicator) return null;
                
                const params = instance.params || {};
                const paramStr = Object.entries(params)
                  .map(([k, v]) => `${k}=${v}`)
                  .join(', ');
                
                return (
                  <div 
                    key={instance.instanceKey}
                    className="bg-black/30 rounded border border-white/10 hover:border-cyan-500/30 transition-all p-2"
                  >
                    <div className="flex flex-col gap-1.5">
                      <div className="flex items-start justify-between gap-1">
                        <h3 className="text-xs font-medium truncate flex-1 text-white" title={indicator.name}>
                          {indicator.name}
                        </h3>
                      </div>
                      
                      <div className="text-xs text-white/50 font-mono truncate" title={paramStr || 'default'}>
                        {paramStr || 'default'}
                      </div>
                      
                      <div className="text-xs text-white/30 truncate" title={instance.instanceKey}>
                        {instance.symbol} • {instance.interval}
                      </div>
                      
                      <div className="flex items-center gap-1 mt-1">
                        <button
                          onClick={() => handleOpenConfig(indicator, instance)}
                          className="flex-1 px-2 py-1 text-xs rounded bg-cyan-500/20 text-cyan-300 border border-cyan-400/30 hover:bg-cyan-500/30 transition-all"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => removeIndicator(instance.instanceKey)}
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
        <div 
          className="fixed inset-0 bg-black/5 backdrop-blur-md flex items-center justify-center z-50 p-4 animate-fadeIn"
          onClick={handleCloseBrowseModal}
        >
          <div 
            className="bg-gradient-to-br from-gray-900 via-gray-900 to-gray-800 rounded-2xl border border-white/10 shadow-2xl w-full max-w-6xl max-h-[90vh] flex flex-col animate-slideUp"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-white/10 bg-gradient-to-r from-cyan-500/5 to-blue-500/5">
              <div>
                <h2 className="text-white text-xl font-bold bg-gradient-to-r from-cyan-400 to-blue-400 bg-clip-text text-transparent">
                  Browse Indicators
                </h2>
                <p className="text-white/50 text-xs mt-0.5">
                  {indicators.length} indicators available
                </p>
              </div>
              <button
                onClick={handleCloseBrowseModal}
                className="text-white/40 hover:text-white hover:bg-white/10 transition-all rounded-full w-8 h-8 flex items-center justify-center text-xl font-light"
                title="Close"
              >
                ×
              </button>
            </div>

            {/* Search Bar */}
            <div className="px-6 py-4 border-b border-white/5 bg-black/20">
              <div className="relative">
                <svg 
                  className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input
                  type="text"
                  placeholder="Search indicators by name, category, or description..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-10 pr-4 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white placeholder-white/40 text-sm focus:outline-none focus:border-cyan-400/50 focus:bg-white/10 transition-all"
                  autoFocus
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-white/40 hover:text-white text-sm"
                  >
                    ✕
                  </button>
                )}
              </div>
            </div>

            {/* Loading/Error States */}
            {error && (
              <div className="mx-6 mt-4 px-4 py-3 bg-red-500/10 border border-red-500/20 rounded-lg">
                <div className="text-red-400 text-sm flex items-center gap-2">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                  Failed to load indicators: {error}
                </div>
              </div>
            )}

            {loading && (
              <div className="px-6 py-8 text-center">
                <div className="inline-flex items-center gap-3 text-white/50">
                  <div className="w-5 h-5 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin"></div>
                  <span>Loading indicators...</span>
                </div>
              </div>
            )}

            {/* Indicators List - Scrollable */}
            <div className="flex-1 overflow-y-auto px-6 py-4 custom-scrollbar">
              {!loading && indicators.length === 0 ? (
                <div className="text-center text-white/50 py-16">
                  <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-white/5 flex items-center justify-center">
                    <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                    </svg>
                  </div>
                  <div className="mb-3 text-lg">No indicators found</div>
                  <button
                    onClick={fetchIndicators}
                    className="text-cyan-400 hover:text-cyan-300 text-sm font-medium"
                  >
                    Try refreshing
                  </button>
                </div>
              ) : (
                <div className="space-y-8">
                  {Object.entries(groupedIndicators).map(([category, categoryIndicators]) => (
                    <div key={category}>
                      {/* Category Header */}
                      <div className="flex items-center gap-3 mb-4 sticky top-0 bg-gradient-to-r from-gray-900 via-gray-900 to-transparent py-2 -mx-2 px-2 z-10">
                        <div className="h-px flex-1 bg-gradient-to-r from-transparent via-cyan-500/30 to-transparent"></div>
                        <h3 className="text-cyan-400 text-sm font-bold uppercase tracking-wider">
                          {category}
                        </h3>
                        <div className="h-px flex-1 bg-gradient-to-r from-transparent via-cyan-500/30 to-transparent"></div>
                      </div>
                      
                      {/* Indicators Grid */}
                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {categoryIndicators.map((indicator) => (
                          <div 
                            key={indicator.id}
                            className="group relative bg-gradient-to-br from-black/40 to-black/20 rounded-xl border border-white/10 hover:border-cyan-500/40 hover:shadow-lg hover:shadow-cyan-500/10 transition-all duration-300 p-4 cursor-pointer overflow-hidden"
                            onClick={() => handleOpenConfig(indicator)}
                          >
                            {/* Gradient overlay on hover */}
                            <div className="absolute inset-0 bg-gradient-to-br from-cyan-500/0 to-blue-500/0 group-hover:from-cyan-500/5 group-hover:to-blue-500/5 transition-all duration-300 rounded-xl"></div>
                            
                            <div className="relative flex flex-col gap-3">
                              <div className="flex items-start justify-between gap-2">
                                <div className="flex-1 min-w-0">
                                  <h3 className="text-white font-semibold text-sm mb-1 group-hover:text-cyan-300 transition-colors">
                                    {indicator.name}
                                  </h3>
                                  <span className="text-xs text-white/40 font-mono">
                                    {indicator.id}
                                  </span>
                                </div>
                                <div className="flex-shrink-0 w-2 h-2 rounded-full bg-cyan-400/50 group-hover:bg-cyan-400 transition-all group-hover:scale-125"></div>
                              </div>
                              
                              <p className="text-xs text-white/50 leading-relaxed line-clamp-2 group-hover:text-white/70 transition-colors">
                                {indicator.description}
                              </p>
                              
                              <div className="flex items-center justify-between pt-2 border-t border-white/5">
                                <span className="text-xs px-2 py-1 rounded-md bg-gradient-to-r from-cyan-500/10 to-blue-500/10 text-cyan-300 border border-cyan-500/20 font-medium">
                                  {indicator.category}
                                </span>
                                <span className="text-xs text-cyan-400 font-medium opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1">
                                  Add
                                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                  </svg>
                                </span>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                  
                  {filteredIndicators.length === 0 && searchQuery && (
                    <div className="text-center text-white/50 py-16">
                      <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-white/5 flex items-center justify-center">
                        <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                      </div>
                      <p className="text-lg mb-1">No indicators match</p>
                      <p className="text-white/40">"{searchQuery}"</p>
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
