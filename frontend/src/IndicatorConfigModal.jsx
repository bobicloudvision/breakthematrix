import React, { useEffect, useState, useRef } from 'react';

export function IndicatorConfigModal({ 
  indicator, 
  onClose, 
  onApply,
  initialParams,
  isEditing = false,
  isSidebar = false
}) {
  const [params, setParams] = useState(initialParams || {});
  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(true);
  const [autoSaving, setAutoSaving] = useState(false);
  const saveTimeoutRef = useRef(null);

  useEffect(() => {
    fetchIndicatorDetails();
  }, [indicator?.id]);

  const fetchIndicatorDetails = async () => {
    try {
      setLoading(true);
      const res = await fetch(`http://localhost:8080/api/indicators/${indicator.id}`, {
        method: 'GET',
        headers: { accept: 'application/json' },
      });
      
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      
      const data = await res.json();
      setDetails(data);
      
      // Initialize params from details if not already set
      if (data.parameters && Object.keys(params.params || {}).length === 0) {
        const defaultParams = {};
        Object.values(data.parameters).forEach(param => {
          defaultParams[param.name] = param.defaultValue;
        });
        
        setParams(prev => ({
          ...prev,
          params: defaultParams
        }));
      }
    } catch (e) {
      console.error('Failed to fetch indicator details:', e);
    } finally {
      setLoading(false);
    }
  };

  // Auto-save when editing (debounced)
  useEffect(() => {
    if (!isEditing || loading) return;
    
    // Clear existing timeout
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }
    
    // Set new timeout for auto-save
    saveTimeoutRef.current = setTimeout(() => {
      setAutoSaving(true);
      // shouldClose = false for auto-save (don't close the modal)
      onApply(indicator.id, params, false);
      
      // Show saving indicator briefly
      setTimeout(() => {
        setAutoSaving(false);
      }, 800);
    }, 500); // 500ms debounce
    
    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, [params, isEditing, loading]);

  const handleParamChange = (field, value) => {
    setParams(prev => ({
      ...prev,
      [field]: value
    }));
  };

  const handleIndicatorParamChange = (paramKey, value) => {
    setParams(prev => ({
      ...prev,
      params: {
        ...prev.params,
        [paramKey]: value
      }
    }));
  };

  const handleApply = () => {
    // Pass the configuration to parent component
    // Parent will handle either POST (new instance) or PATCH (update existing)
    // shouldClose = true for manual apply
    onApply(indicator.id, params, true);
  };

  if (!indicator) return null;

  // Sidebar version - no portal, full height
  if (isSidebar) {
    return (
      <div className="h-full w-full bg-gradient-to-br from-slate-900 via-gray-900 to-slate-900 flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-cyan-500/20 bg-gradient-to-r from-slate-800/50 to-slate-700/50 flex-shrink-0">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <h2 className="text-xl font-bold text-transparent bg-gradient-to-r from-cyan-400 to-blue-400 bg-clip-text">
                  {isEditing ? 'Edit' : 'Add'} {indicator.name}
                </h2>
              </div>
            
              <div className="flex items-center gap-2 mt-2">
          
                <span className="text-xs text-white/50 font-mono">
                  {indicator.id}
                </span>
              </div>
            </div>
            <button
              onClick={onClose}
              className="ml-4 p-2 hover:bg-white/10 rounded-lg transition-colors text-white/70 hover:text-white"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          
          
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 min-h-0">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="w-8 h-8 border-3 border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
              <span className="ml-3 text-white/60">Loading configuration...</span>
            </div>
          ) : (
            <>
              {/* Indicator Parameters */}
              {details?.parameters && Object.keys(details.parameters).length > 0 ? (
                <div className="space-y-3">
                  <h3 className="text-sm font-semibold text-white/80 uppercase tracking-wide">Indicator Parameters</h3>
                  <div className="space-y-4">
                    {Object.values(details.parameters).map((param) => {
                      const currentValue = params.params?.[param.name] ?? param.defaultValue;
                      
                      return (
                        <div key={param.name} className="space-y-2">
                          <div className="flex items-center justify-between">
                            <label className="text-sm text-white/80 font-medium">
                              {param.displayName || param.name}
                            </label>
                            {param.required && (
                              <span className="text-xs text-red-400 font-medium">Required</span>
                            )}
                          </div>
                          {param.description && (
                            <p className="text-xs text-white/50 leading-relaxed">{param.description}</p>
                          )}
                          <div className="flex items-center gap-3">
                            {param.type === 'INTEGER' || param.type === 'DOUBLE' ? (
                              <>
                                <input
                                  type="number"
                                  value={currentValue}
                                  min={param.minValue}
                                  max={param.maxValue}
                                  step={param.type === 'DOUBLE' ? '0.01' : '1'}
                                  onChange={(e) => handleIndicatorParamChange(
                                    param.name, 
                                    param.type === 'DOUBLE' ? parseFloat(e.target.value) : parseInt(e.target.value)
                                  )}
                                  className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
                                />
                                {(param.minValue !== undefined || param.maxValue !== undefined) && (
                                  <span className="text-xs text-white/40 min-w-[100px]">
                                    {param.minValue !== undefined && param.maxValue !== undefined
                                      ? `Range: ${param.minValue}-${param.maxValue}`
                                      : param.minValue !== undefined
                                      ? `Min: ${param.minValue}`
                                      : `Max: ${param.maxValue}`}
                                  </span>
                                )}
                              </>
                            ) : param.type === 'BOOLEAN' ? (
                              <label className="flex items-center gap-3 cursor-pointer">
                                <input
                                  type="checkbox"
                                  checked={currentValue === true}
                                  onChange={(e) => handleIndicatorParamChange(param.name, e.target.checked)}
                                  className="w-5 h-5 bg-white/5 border border-white/10 rounded focus:ring-2 focus:ring-cyan-500/50"
                                />
                                <span className="text-sm text-white/70">
                                  {currentValue ? 'Enabled' : 'Disabled'}
                                </span>
                              </label>
                            ) : param.type === 'STRING' && (param.name.toLowerCase().includes('color') || (typeof currentValue === 'string' && currentValue.startsWith('#'))) ? (
                              <div className="flex items-center gap-3 flex-1">
                                <input
                                  type="color"
                                  value={currentValue}
                                  onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                  className="w-14 h-10 bg-white/5 border border-white/10 rounded-lg cursor-pointer"
                                />
                                <input
                                  type="text"
                                  value={currentValue}
                                  onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                  className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 font-mono"
                                  placeholder="#2962FF"
                                />
                              </div>
                            ) : param.type === 'STRING' && param.name.toLowerCase() === 'source' ? (
                              <select
                                value={currentValue}
                                onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
                              >
                                <option value="open">Open</option>
                                <option value="high">High</option>
                                <option value="low">Low</option>
                                <option value="close">Close</option>
                                <option value="hl2">(High+Low)/2</option>
                                <option value="hlc3">(High+Low+Close)/3</option>
                                <option value="ohlc4">(Open+High+Low+Close)/4</option>
                              </select>
                            ) : (
                              <input
                                type="text"
                                value={currentValue}
                                onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
                              />
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <div className="text-center text-white/50 py-12">
                  <p className="text-sm">This indicator has no configurable parameters.</p>
                  <p className="text-xs text-white/40 mt-2">It will use default settings.</p>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-cyan-500/20 bg-gradient-to-r from-slate-800/30 to-slate-700/30 flex items-center justify-between gap-3 flex-shrink-0">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-white/5 text-white/70 border border-white/10 hover:bg-white/10 hover:text-white transition-all"
          >
            {isEditing ? 'Close' : 'Cancel'}
          </button>
          {isEditing ? (
            <div className="flex items-center gap-2 px-4 py-2">
              {autoSaving ? (
                <>
                  <div className="w-4 h-4 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
                  <span className="text-sm text-cyan-400">Saving...</span>
                </>
              ) : (
                <>
                  <svg className="w-4 h-4 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  <span className="text-sm text-white/60">Changes saved</span>
                </>
              )}
            </div>
          ) : (
            <button
              onClick={handleApply}
              disabled={loading}
              className={`px-6 py-2 text-sm font-medium rounded-lg transition-all ${
                loading
                  ? 'bg-white/10 text-white/50 cursor-not-allowed'
                  : 'bg-gradient-to-r from-cyan-500/40 to-blue-500/40 text-cyan-100 border border-cyan-400/50 hover:from-cyan-500/60 hover:to-blue-500/60 shadow-lg shadow-cyan-500/20'
              }`}
            >
              Apply to Chart
            </button>
          )}
        </div>
      </div>
    );
  }

  // Modal version (original) - uses portal
  return (
    <div 
      className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div 
        className="bg-gradient-to-br from-slate-900 via-gray-900 to-slate-900 border border-cyan-500/30 rounded-xl shadow-2xl shadow-cyan-500/20 w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col m-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-cyan-500/20 bg-gradient-to-r from-slate-800/50 to-slate-700/50">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <h2 className="text-xl font-bold text-transparent bg-gradient-to-r from-cyan-400 to-blue-400 bg-clip-text">
                  {isEditing ? 'Edit' : 'Add'} {indicator.name}
                </h2>
              </div>
              <p className="text-sm text-white/60 mt-1">{indicator.description}</p>
              <div className="flex items-center gap-2 mt-2">
                <span className="text-xs px-2 py-1 rounded bg-gradient-to-r from-cyan-500/20 to-blue-500/20 text-cyan-300 border border-cyan-500/30">
                  {indicator.category}
                </span>
                <span className="text-xs text-white/50 font-mono">
                  {indicator.id}
                </span>
              </div>
            </div>
            <button
              onClick={onClose}
              className="ml-4 p-2 hover:bg-white/10 rounded-lg transition-colors text-white/70 hover:text-white"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          
          {/* Metadata badges */}
          {details && (
            <div className="flex items-center gap-2 mt-3">
              {details.minRequiredCandles && (
                <div className="flex items-center gap-1 px-2 py-1 bg-purple-500/10 text-purple-300 border border-purple-500/30 rounded text-xs">
                  <span className="text-white/50">Min Candles:</span>
                  <span className="font-mono">{details.minRequiredCandles}</span>
                </div>
              )}
              {details.visualizationMetadata && Object.keys(details.visualizationMetadata).length > 0 && (
                <div className="flex items-center gap-1 px-2 py-1 bg-blue-500/10 text-blue-300 border border-blue-500/30 rounded text-xs">
                  <span className="text-white/50">Type:</span>
                  <span className="capitalize">{Object.values(details.visualizationMetadata)[0]?.seriesType || 'line'}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="w-8 h-8 border-3 border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
              <span className="ml-3 text-white/60">Loading configuration...</span>
            </div>
          ) : (
            <>
              {/* Indicator Parameters */}
              {details?.parameters && Object.keys(details.parameters).length > 0 ? (
                <div className="space-y-3">
                  <h3 className="text-sm font-semibold text-white/80 uppercase tracking-wide">Indicator Parameters</h3>
                  <div className="space-y-4">
                    {Object.values(details.parameters).map((param) => {
                      const currentValue = params.params?.[param.name] ?? param.defaultValue;
                      
                      return (
                        <div key={param.name} className="space-y-2">
                          <div className="flex items-center justify-between">
                            <label className="text-sm text-white/80 font-medium">
                              {param.displayName || param.name}
                            </label>
                            {param.required && (
                              <span className="text-xs text-red-400 font-medium">Required</span>
                            )}
                          </div>
                          {param.description && (
                            <p className="text-xs text-white/50 leading-relaxed">{param.description}</p>
                          )}
                          <div className="flex items-center gap-3">
                            {param.type === 'INTEGER' || param.type === 'DOUBLE' ? (
                              <>
                                <input
                                  type="number"
                                  value={currentValue}
                                  min={param.minValue}
                                  max={param.maxValue}
                                  step={param.type === 'DOUBLE' ? '0.01' : '1'}
                                  onChange={(e) => handleIndicatorParamChange(
                                    param.name, 
                                    param.type === 'DOUBLE' ? parseFloat(e.target.value) : parseInt(e.target.value)
                                  )}
                                  className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
                                />
                                {(param.minValue !== undefined || param.maxValue !== undefined) && (
                                  <span className="text-xs text-white/40 min-w-[100px]">
                                    {param.minValue !== undefined && param.maxValue !== undefined
                                      ? `Range: ${param.minValue}-${param.maxValue}`
                                      : param.minValue !== undefined
                                      ? `Min: ${param.minValue}`
                                      : `Max: ${param.maxValue}`}
                                  </span>
                                )}
                              </>
                            ) : param.type === 'BOOLEAN' ? (
                              <label className="flex items-center gap-3 cursor-pointer">
                                <input
                                  type="checkbox"
                                  checked={currentValue === true}
                                  onChange={(e) => handleIndicatorParamChange(param.name, e.target.checked)}
                                  className="w-5 h-5 bg-white/5 border border-white/10 rounded focus:ring-2 focus:ring-cyan-500/50"
                                />
                                <span className="text-sm text-white/70">
                                  {currentValue ? 'Enabled' : 'Disabled'}
                                </span>
                              </label>
                            ) : param.type === 'STRING' && (param.name.toLowerCase().includes('color') || (typeof currentValue === 'string' && currentValue.startsWith('#'))) ? (
                              <div className="flex items-center gap-3 flex-1">
                                <input
                                  type="color"
                                  value={currentValue}
                                  onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                  className="w-14 h-10 bg-white/5 border border-white/10 rounded-lg cursor-pointer"
                                />
                                <input
                                  type="text"
                                  value={currentValue}
                                  onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                  className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 font-mono"
                                  placeholder="#2962FF"
                                />
                              </div>
                            ) : param.type === 'STRING' && param.name.toLowerCase() === 'source' ? (
                              <select
                                value={currentValue}
                                onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
                              >
                                <option value="open">Open</option>
                                <option value="high">High</option>
                                <option value="low">Low</option>
                                <option value="close">Close</option>
                                <option value="hl2">(High+Low)/2</option>
                                <option value="hlc3">(High+Low+Close)/3</option>
                                <option value="ohlc4">(Open+High+Low+Close)/4</option>
                              </select>
                            ) : (
                              <input
                                type="text"
                                value={currentValue}
                                onChange={(e) => handleIndicatorParamChange(param.name, e.target.value)}
                                className="flex-1 px-3 py-2 text-sm bg-white/5 border border-white/10 rounded-lg text-white focus:border-cyan-500/50 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
                              />
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <div className="text-center text-white/50 py-12">
                  <p className="text-sm">This indicator has no configurable parameters.</p>
                  <p className="text-xs text-white/40 mt-2">It will use default settings.</p>
                </div>
              )}

            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-cyan-500/20 bg-gradient-to-r from-slate-800/30 to-slate-700/30 flex items-center justify-between gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-white/5 text-white/70 border border-white/10 hover:bg-white/10 hover:text-white transition-all"
          >
            {isEditing ? 'Close' : 'Cancel'}
          </button>
          {isEditing ? (
            <div className="flex items-center gap-2 px-4 py-2">
              {autoSaving ? (
                <>
                  <div className="w-4 h-4 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
                  <span className="text-sm text-cyan-400">Saving...</span>
                </>
              ) : (
                <>
                  <svg className="w-4 h-4 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  <span className="text-sm text-white/60">Changes saved</span>
                </>
              )}
            </div>
          ) : (
            <button
              onClick={handleApply}
              disabled={loading}
              className={`px-6 py-2 text-sm font-medium rounded-lg transition-all ${
                loading
                  ? 'bg-white/10 text-white/50 cursor-not-allowed'
                  : 'bg-gradient-to-r from-cyan-500/40 to-blue-500/40 text-cyan-100 border border-cyan-400/50 hover:from-cyan-500/60 hover:to-blue-500/60 shadow-lg shadow-cyan-500/20'
              }`}
            >
              Apply to Chart
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

