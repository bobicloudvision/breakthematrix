import React, { useState, useEffect } from 'react';

export const TradingProviderDropdown = ({ onProviderSelect, selectedProvider }) => {
    const [providers, setProviders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isOpen, setIsOpen] = useState(false);

    useEffect(() => {
        const fetchProviders = async () => {
            try {
                setLoading(true);
                const response = await fetch('http://localhost:8080/api/trading/providers');
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data = await response.json();
                setProviders(data);
            } catch (err) {
                setError(err.message);
                console.error('Failed to fetch providers:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchProviders();
    }, []);

    const handleProviderClick = (provider) => {
        onProviderSelect(provider);
        setIsOpen(false);
    };

    if (loading) {
        return (
            <div className="relative">
                <button 
                    className="w-full px-4 py-2 bg-gray-700 text-white rounded-lg border border-gray-600 flex items-center justify-between"
                    disabled
                >
                    <span>Loading providers...</span>
                    <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                </button>
            </div>
        );
    }

    if (error) {
        return (
            <div className="relative">
                <button 
                    className="w-full px-4 py-2 bg-red-700 text-white rounded-lg border border-red-600 flex items-center justify-between"
                    disabled
                >
                    <span>Error loading providers</span>
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                </button>
            </div>
        );
    }

    return (
        <div className="relative">
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="w-full px-4 py-2 bg-gray-700 text-white rounded-lg border border-gray-600 hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500 flex items-center justify-between"
            >
                <span>{selectedProvider ? selectedProvider.name || selectedProvider : 'Select Provider'}</span>
                <svg 
                    className={`w-4 h-4 transition-transform ${isOpen ? 'rotate-180' : ''}`} 
                    fill="none" 
                    stroke="currentColor" 
                    viewBox="0 0 24 24"
                >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
            </button>

            {isOpen && (
                <div className="absolute z-10 w-full mt-1 bg-gray-700 border border-gray-600 rounded-lg shadow-lg max-h-60 overflow-auto">
                    {providers.length === 0 ? (
                        <div className="px-4 py-2 text-gray-400">No providers available</div>
                    ) : (
                        providers.map((provider, index) => {
                            const providerName = typeof provider === 'string' ? provider : (provider.name || provider);
                            return (
                                <button
                                    key={provider.id || index}
                                    onClick={() => handleProviderClick(providerName)}
                                    className="w-full px-4 py-2 text-left text-white hover:bg-gray-600 focus:outline-none focus:bg-gray-600 first:rounded-t-lg last:rounded-b-lg"
                                >
                                    {providerName}
                                </button>
                            );
                        })
                    )}
                </div>
            )}
        </div>
    );
};
