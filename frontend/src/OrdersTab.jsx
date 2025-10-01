import React, { useEffect, useMemo, useState } from 'react';

export function OrdersTab() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchOrders = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetch('http://localhost:8080/api/bot/orders/history', {
        method: 'GET',
        headers: { accept: '*/*' },
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      setOrders(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
    const id = setInterval(fetchOrders, 7000);
    return () => clearInterval(id);
  }, []);

  const columns = useMemo(
    () => [
      { key: 'createdAt', label: 'Created' },
      { key: 'executedAt', label: 'Executed' },
      { key: 'symbol', label: 'Symbol' },
      { key: 'side', label: 'Side' },
      { key: 'type', label: 'Type' },
      { key: 'quantity', label: 'Qty' },
      { key: 'price', label: 'Price' },
      { key: 'status', label: 'Status' },
      { key: 'strategyId', label: 'Strategy' },
    ],
    []
  );

  const formatDate = (iso) => {
    if (!iso) return '-';
    try {
      const d = new Date(iso);
      return d.toLocaleString();
    } catch {
      return iso;
    }
  };

  return (
    <div className="h-full w-full flex flex-col">
      <div className="flex items-center justify-between px-3 py-2 border-b border-white/10 bg-black/30 flex-shrink-0">
        <div className="text-white/80 text-sm font-medium">Orders History</div>
        <div className="flex items-center gap-2">
          {loading && (
            <div className="text-xs text-white/50">Refreshing…</div>
          )}
          <button
            onClick={fetchOrders}
            className="px-2 py-1 text-xs rounded border border-white/10 text-white/80 hover:bg-white/10"
          >
            Refresh
          </button>
        </div>
      </div>
      {error ? (
        <div className="p-3 text-red-400 text-sm flex-shrink-0">Failed to load orders: {error}</div>
      ) : (
        <div className="flex-1 overflow-y-auto overflow-x-auto min-h-0">
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-black/50 backdrop-blur-sm z-10">
              <tr>
                {columns.map((c) => (
                  <th key={c.key} className="px-3 py-2 text-left font-semibold text-white/70 border-b border-white/10 whitespace-nowrap">
                    {c.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 ? (
                <tr>
                  <td colSpan={columns.length} className="px-3 py-6 text-center text-white/50">
                    No orders
                  </td>
                </tr>
              ) : (
                orders.map((o) => (
                  <tr key={o.id} className="hover:bg-white/5 border-b border-white/5">
                    <td className="px-3 py-2 text-white/80 font-mono whitespace-nowrap">{formatDate(o.createdAt)}</td>
                    <td className="px-3 py-2 text-white/60 font-mono whitespace-nowrap">{formatDate(o.executedAt)}</td>
                    <td className="px-3 py-2 text-white/80 whitespace-nowrap">{o.symbol}</td>
                    <td className={`px-3 py-2 whitespace-nowrap ${o.side === 'BUY' ? 'text-green-400' : 'text-red-400'}`}>{o.side}</td>
                    <td className="px-3 py-2 text-white/70 whitespace-nowrap">{o.type}</td>
                    <td className="px-3 py-2 text-white/80 font-mono whitespace-nowrap">{Number(o.quantity).toFixed(6)}</td>
                    <td className="px-3 py-2 text-white/80 font-mono whitespace-nowrap">{Number(o.price || o.executedPrice || 0).toLocaleString()}</td>
                    <td className="px-3 py-2 text-white/70 whitespace-nowrap">{o.status}</td>
                    <td className="px-3 py-2 text-white/60 whitespace-nowrap">{o.strategyId || '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}


