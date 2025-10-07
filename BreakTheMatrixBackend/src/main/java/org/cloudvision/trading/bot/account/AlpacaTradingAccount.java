package org.cloudvision.trading.bot.account;

import net.jacobpeterson.alpaca.AlpacaAPI;
import net.jacobpeterson.alpaca.model.util.apitype.TraderAPIEndpointType;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Alpaca Trading Account
 * Integrates with Alpaca for live trading. Disabled by default for safety.
 */
public class AlpacaTradingAccount implements TradingAccount {

    private final String accountId;
    private final String accountName;
    private final String apiKey;
    private final String apiSecret;
    private final String baseURL; // e.g., https://paper-api.alpaca.markets or https://api.alpaca.markets

    private final PositionManager positionManager;
    private final net.jacobpeterson.alpaca.AlpacaAPI alpacaAPI;
    private final Map<String, Order> orders;
    private boolean enabled;
    private final Instant createdAt;

    public AlpacaTradingAccount(String accountId,
                                String accountName,
                                String apiKey,
                                String apiSecret,
                                String baseURL) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.baseURL = baseURL;
        this.positionManager = new PositionManager();
        this.orders = new HashMap<>();
        this.enabled = false; // Disabled by default for safety
        this.createdAt = Instant.now();
        this.alpacaAPI = new AlpacaAPI(apiKey, apiSecret, TraderAPIEndpointType.PAPER, null,  null);

        System.out.println("🔗 Alpaca Trading Account created: " + accountName +
                " (Base URL: " + baseURL + ")");
        System.out.println("⚠️ LIVE/PAPER TRADING - VERIFY KEYS AND SETTINGS BEFORE ENABLING");
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public String getAccountName() {
        return accountName;
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.LIVE_TRADING;
    }

    @Override
    public Order executeOrder(Order order) {
        if (!enabled) {
            order.setStatus(OrderStatus.REJECTED);
            System.err.println("❌ Account disabled: enable Alpaca account before executing orders");
            return order;
        }

        if (alpacaAPI == null) {
            order.setStatus(OrderStatus.REJECTED);
            System.err.println("❌ Alpaca API not configured for this account");
            return order;
        }

        try {
            // Only basic MARKET orders supported for now
            if (order.getType() != org.cloudvision.trading.bot.model.OrderType.MARKET) {
                order.setStatus(OrderStatus.REJECTED);
                System.err.println("❌ Only MARKET orders are supported in AlpacaTradingAccount currently");
                return order;
            }

            net.jacobpeterson.alpaca.openapi.trader.model.PostOrderRequest request =
                    new net.jacobpeterson.alpaca.openapi.trader.model.PostOrderRequest()
                            .symbol(order.getSymbol())
                            .qty(order.getQuantity().toPlainString())
                            .side(order.getSide() == org.cloudvision.trading.bot.model.OrderSide.BUY
                                    ? net.jacobpeterson.alpaca.openapi.trader.model.OrderSide.BUY
                                    : net.jacobpeterson.alpaca.openapi.trader.model.OrderSide.SELL)
                            .type(net.jacobpeterson.alpaca.openapi.trader.model.OrderType.MARKET)
                            .timeInForce(net.jacobpeterson.alpaca.openapi.trader.model.TimeInForce.GTC);

            net.jacobpeterson.alpaca.openapi.trader.model.Order alpacaOrder =
                    alpacaAPI.trader().orders().postOrder(request);

            // Update local order state based on Alpaca response
            order.setStatus(OrderStatus.SUBMITTED);
            order.setExecutedAt(Instant.now());

            String filledAvgPrice = alpacaOrder.getFilledAvgPrice();
            if (filledAvgPrice != null) {
                order.setExecutedPrice(new BigDecimal(filledAvgPrice));
                order.setExecutedQuantity(order.getQuantity());
                order.setStatus(OrderStatus.FILLED);
            }

            orders.put(order.getId(), order);
            System.out.println("✅ [ALPACA] Submitted order: " + alpacaOrder.getId() + " for " + order);
            return order;
        } catch (Exception e) {
            System.err.println("❌ Alpaca order error: " + e.getMessage());
            order.setStatus(OrderStatus.REJECTED);
            return order;
        }
    }

    @Override
    public boolean cancelOrder(String orderId) {
        // Placeholder: cancel via Alpaca API when implemented
        System.err.println("⚠️ Cancel not implemented for Alpaca order: " + orderId);
        return false;
    }

    @Override
    public BigDecimal getBalance() {
        // Placeholder: fetch from Alpaca account API when implemented
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getAvailableBalance() {
        // Placeholder
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getAssetBalance(String asset) {
        // Placeholder: query Alpaca positions/cash
        return BigDecimal.ZERO;
    }

    @Override
    public Map<String, BigDecimal> getAllBalances() {
        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put("USD", getBalance());
        balances.put("available", getAvailableBalance());
        return balances;
    }

    @Override
    public BigDecimal getTotalExposure() {
        // Placeholder: sum of all open positions value
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getDailyPnL() {
        // Placeholder: can be derived from Alpaca account/activities
        return BigDecimal.ZERO;
    }

    @Override
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    @Override
    public List<Order> getAllOrders() {

        if (alpacaAPI == null) {
            return new ArrayList<>(orders.values());
        }
        try {
            java.util.List<net.jacobpeterson.alpaca.openapi.trader.model.Order> alpacaOrders =
                    alpacaAPI.trader().orders().getAllOrders(null, 100, null, null, null, null, null, null);
            List<Order> result = new ArrayList<>();
            for (net.jacobpeterson.alpaca.openapi.trader.model.Order a : alpacaOrders) {
                result.add(mapAlpacaOrderToInternal(a));
            }
            System.out.println("AlpacaTradingAccount.getAllOrders(): " + result);
            return result;
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch Alpaca orders: " + e.getMessage());
            return new ArrayList<>(orders.values());
        }
    }

    @Override
    public List<Order> getOpenOrders() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING ||
                        o.getStatus() == OrderStatus.SUBMITTED ||
                        o.getStatus() == OrderStatus.PARTIALLY_FILLED)
                .toList();
    }

    @Override
    public List<Order> getFilledOrders() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.FILLED)
                .toList();
    }

    private Order mapAlpacaOrderToInternal(net.jacobpeterson.alpaca.openapi.trader.model.Order alpacaOrder) {
        String id = alpacaOrder.getId();
        String symbol = alpacaOrder.getSymbol();
        org.cloudvision.trading.bot.model.OrderType type = switch (alpacaOrder.getType()) {
            case MARKET -> org.cloudvision.trading.bot.model.OrderType.MARKET;
            case LIMIT -> org.cloudvision.trading.bot.model.OrderType.LIMIT;
            case STOP, STOP_LIMIT -> org.cloudvision.trading.bot.model.OrderType.STOP_LIMIT;
            default -> org.cloudvision.trading.bot.model.OrderType.MARKET;
        };
        org.cloudvision.trading.bot.model.OrderSide side = alpacaOrder.getSide() ==
                net.jacobpeterson.alpaca.openapi.trader.model.OrderSide.BUY
                ? org.cloudvision.trading.bot.model.OrderSide.BUY
                : org.cloudvision.trading.bot.model.OrderSide.SELL;

        BigDecimal qty = new BigDecimal(alpacaOrder.getQty());
        BigDecimal price = alpacaOrder.getLimitPrice() != null ? new BigDecimal(alpacaOrder.getLimitPrice()) : BigDecimal.ZERO;

        Order o = new Order(id, symbol, type, side, qty, price, "alpaca");

        OrderStatus status = switch (alpacaOrder.getStatus()) {
            case NEW, ACCEPTED -> OrderStatus.SUBMITTED;
            case PARTIALLY_FILLED -> OrderStatus.PARTIALLY_FILLED;
            case FILLED -> OrderStatus.FILLED;
            case CANCELED -> OrderStatus.CANCELLED;
            case REJECTED -> OrderStatus.REJECTED;
            case EXPIRED -> OrderStatus.EXPIRED;
            default -> OrderStatus.PENDING;
        };
        o.setStatus(status);

        String filledAvgPrice = alpacaOrder.getFilledAvgPrice();
        if (filledAvgPrice != null) {
            o.setExecutedPrice(new BigDecimal(filledAvgPrice));
            o.setExecutedQuantity(qty);
            o.setExecutedAt(Instant.now());
        }
        return o;
    }

    @Override
    public BigDecimal getTotalPnL() {
        // Placeholder: derive from positionManager or Alpaca data
        return BigDecimal.ZERO;
    }

    @Override
    public AccountStats getAccountStats() {
        // Minimal placeholder stats for now
        return new AccountStats(
                accountId,
                BigDecimal.ZERO,
                getBalance(),
                getTotalPnL(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                orders.size(),
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                createdAt,
                (Instant) null
        );
    }

    @Override
    public void reset() {
        // No-op for live trading
        System.out.println("ℹ️ Reset called on Alpaca account - no action performed");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        System.out.println((enabled ? "✅" : "🚫") + " Alpaca trading account " +
                (enabled ? "enabled" : "disabled"));
    }

    @Override
    public List<Position> getOpenPositions() {
        if (alpacaAPI == null) {
            return positionManager.getOpenPositions();
        }
        try {
            java.util.List<net.jacobpeterson.alpaca.openapi.trader.model.Position> alpacaPositions =
                    alpacaAPI.trader().positions().getAllOpenPositions();
            List<Position> result = new ArrayList<>();
            for (net.jacobpeterson.alpaca.openapi.trader.model.Position p : alpacaPositions) {
                result.add(mapAlpacaPositionToInternal(p));
            }
            return result;
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch Alpaca open positions: " + e.getMessage());
            return positionManager.getOpenPositions();
        }
    }

    @Override
    public List<Position> getOpenPositionsBySymbol(String symbol) {
        return positionManager.getOpenPositionsBySymbol(symbol);
    }

    @Override
    public Position getPosition(String positionId) {
        return positionManager.getPosition(positionId);
    }

    @Override
    public List<Position> getPositionHistory() {
        // Alpaca API does not directly expose closed positions history via Positions API.
        // We return local position history; extend later via AccountActivities if needed.
        return positionManager.getPositionHistory();
    }

    @Override
    public PositionManager getPositionManager() {
        return positionManager;
    }

    private Position mapAlpacaPositionToInternal(net.jacobpeterson.alpaca.openapi.trader.model.Position alpacaPosition) {
        String symbol = alpacaPosition.getSymbol();
        PositionSide side =
                "long".equalsIgnoreCase(alpacaPosition.getSide()) ? PositionSide.LONG : PositionSide.SHORT;

        BigDecimal entryPrice = alpacaPosition.getAvgEntryPrice() != null
                ? new BigDecimal(alpacaPosition.getAvgEntryPrice())
                : BigDecimal.ZERO;
        BigDecimal qty = alpacaPosition.getQty() != null
                ? new BigDecimal(alpacaPosition.getQty())
                : BigDecimal.ZERO;

        Position pos = new Position(symbol, side, entryPrice, qty);
        // Best-effort: if market value provided, we can derive a rough current price, but we skip here.
        return pos;
    }

    @Override
    public void updateCurrentPrices(Map<String, BigDecimal> currentPrices) {
        // Delegate to PositionManager for unrealized P&L updates when used
        positionManager.updatePrices(currentPrices);
    }

    /**
     * Close an open position on Alpaca by symbol.
     * If qty is null and percentage is null, closes the full position.
     */
    public boolean closeOpenPosition(String symbol, BigDecimal qty, BigDecimal percentage) {
        if (!enabled || alpacaAPI == null) {
            System.err.println("❌ Cannot close position: account disabled or API not configured");
            return false;
        }
        try {
            net.jacobpeterson.alpaca.openapi.trader.model.Order closeOrder =
                    alpacaAPI.trader().positions().deleteOpenPosition(symbol, qty, percentage);
            System.out.println("✅ [ALPACA] Close position submitted for " + symbol + ": " + closeOrder.getId());
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error closing position for " + symbol + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns filled average price for an Alpaca order ID if available.
     */
    public Optional<BigDecimal> getOrderFilledAvgPrice(String alpacaOrderId) {
        if (alpacaAPI == null) {
            return Optional.empty();
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(alpacaOrderId);
            net.jacobpeterson.alpaca.openapi.trader.model.Order order =
                    alpacaAPI.trader().orders().getOrderByOrderID(uuid, false);
            String filledAvgPrice = order.getFilledAvgPrice();
            if (filledAvgPrice == null) return Optional.empty();
            return Optional.of(new BigDecimal(filledAvgPrice));
        } catch (Exception e) {
            System.err.println("⚠️ Could not fetch filledAvgPrice for order " + alpacaOrderId + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}


