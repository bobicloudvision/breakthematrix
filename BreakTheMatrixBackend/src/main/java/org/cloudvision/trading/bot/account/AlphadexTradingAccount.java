package org.cloudvision.trading.bot.account;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderStatus;
import org.cloudvision.trading.bot.model.OrderType;
import org.cloudvision.trading.bot.model.OrderSide;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Alphadex Trading Account
 * Placeholder integration for a hypothetical Alphadex exchange.
 * Disabled by default for safety; implement real API calls where noted.
 */
public class AlphadexTradingAccount implements TradingAccount {

    private final String accountId;
    private final String accountName;
    private final String apiKey;
    private final String apiSecret;
    private final String baseURL;

    private final PositionManager positionManager;
    private final Map<String, Order> orders;
    private boolean enabled;
    private final Instant createdAt;
    private final RestTemplate http;

    public AlphadexTradingAccount(String accountId,
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
        this.http = new RestTemplate();

        System.out.println("🔗 Alphadex Trading Account created: " + accountName +
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
            System.err.println("❌ Account disabled: enable Alphadex account before executing orders");
            return order;
        }

        try {
            String url = normalizeBaseUrl(baseURL) + "/api/accounts/" + accountId + "/orders";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = buildPlaceOrderRequest(order);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map<String, Object>> resp = http.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            order.setStatus(OrderStatus.SUBMITTED);
            order.setExecutedAt(Instant.now());
            orders.put(order.getId(), order);

            if (resp.getBody() != null) {
                Object status = resp.getBody().get("status");
                if (status instanceof String s) {
                    if ("FILLED".equalsIgnoreCase(s)) {
                        order.setStatus(OrderStatus.FILLED);
                        order.setExecutedQuantity(order.getQuantity());
                        if (order.getPrice() != null) {
                            order.setExecutedPrice(order.getPrice());
                        }
                    }
                }
                Object returnedId = resp.getBody().get("orderId");
                if (returnedId != null) {
                    System.out.println("✅ [ALPHADEX] Submitted order, remoteId=" + returnedId + ", localId=" + order.getId());
                }
            }

            return order;
        } catch (Exception e) {
            System.err.println("❌ [ALPHADEX] Order error: " + e.getMessage());
            order.setStatus(OrderStatus.REJECTED);
            return order;
        }
    }

    @Override
    public boolean cancelOrder(String orderId) {
        try {
            String url = normalizeBaseUrl(baseURL) + "/api/accounts/" + accountId + "/orders/" + orderId;
            http.delete(url);
            Order o = orders.get(orderId);
            if (o != null) {
                o.setStatus(OrderStatus.CANCELLED);
            }
            System.out.println("✅ [ALPHADEX] Cancelled order: " + orderId);
            return true;
        } catch (Exception e) {
            System.err.println("❌ [ALPHADEX] Cancel order error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public BigDecimal getBalance() {
        try {
            String url = normalizeBaseUrl(baseURL) + "/api/accounts/" + accountId + "/summary";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-KEY", apiKey);
            headers.set("X-API-SECRET", apiSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> resp = http.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (resp.getBody() != null && resp.getBody().get("balance") != null) {
                return new BigDecimal(String.valueOf(resp.getBody().get("balance")));
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            System.err.println("⚠️ [ALPHADEX] Failed to fetch balance: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal getAvailableBalance() {
        // TODO: Fetch available balance from Alphadex
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getAssetBalance(String asset) {
        // TODO: Query specific asset balance from Alphadex
        return BigDecimal.ZERO;
    }

    @Override
    public Map<String, BigDecimal> getAllBalances() {
        Map<String, BigDecimal> balances = new HashMap<>();
        try {
            String url = normalizeBaseUrl(baseURL) + "/api/accounts/" + accountId + "/summary";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-KEY", apiKey);
            headers.set("X-API-SECRET", apiSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> resp = http.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (resp.getBody() != null) {
                Map<String, Object> body = resp.getBody();
                if (body.get("balance") != null) {
                    balances.put("USDT", new BigDecimal(String.valueOf(body.get("balance"))));
                }
                if (body.get("freeBalance") != null) {
                    balances.put("available", new BigDecimal(String.valueOf(body.get("freeBalance"))));
                }
                if (body.get("lockedMargin") != null) {
                    balances.put("lockedMargin", new BigDecimal(String.valueOf(body.get("lockedMargin"))));
                }
                return balances;
            }
        } catch (Exception e) {
            System.err.println("⚠️ [ALPHADEX] Failed to fetch balances: " + e.getMessage());
        }
        balances.put("USDT", getBalance());
        balances.put("available", getAvailableBalance());
        return balances;
    }

    @Override
    public BigDecimal getTotalExposure() {
        // TODO: Sum value of all open positions
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getDailyPnL() {
        // TODO: Derive from trade history/positions
        return BigDecimal.ZERO;
    }

    @Override
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    @Override
    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
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

    @Override
    public BigDecimal getTotalPnL() {
        // TODO: Calculate from realized and unrealized P&L
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
        System.out.println("ℹ️ Reset called on Alphadex account - no action performed");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        System.out.println((enabled ? "✅" : "🚫") + " Alphadex trading account " +
                (enabled ? "enabled" : "disabled"));
    }

    // Position management

    @Override
    public List<Position> getOpenPositions() {
        return positionManager.getOpenPositions();
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
        return positionManager.getPositionHistory();
    }

    @Override
    public PositionManager getPositionManager() {
        return positionManager;
    }

    @Override
    public void updateCurrentPrices(Map<String, BigDecimal> currentPrices) {
        positionManager.updatePrices(currentPrices);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.endsWith("/")) return url.substring(0, url.length() - 1);
        return url;
    }

    private static Map<String, Object> buildPlaceOrderRequest(Order order) {
        Map<String, Object> body = new HashMap<>();
        body.put("symbol", order.getSymbol());
        body.put("type", mapOrderType(order.getType()));
        body.put("side", mapOrderSide(order.getSide()));
        body.put("quantity", order.getQuantity());
        if (order.getPrice() != null && order.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            body.put("price", order.getPrice());
        }
        // If STOP_LOSS or TAKE_PROFIT, use price as stopPrice when provided
        if (order.getType() == OrderType.STOP_LOSS || order.getType() == OrderType.TAKE_PROFIT) {
            if (order.getPrice() != null && order.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                body.put("stopPrice", order.getPrice());
            }
        }
        return body;
    }

    private static String mapOrderType(OrderType type) {
        if (type == null) return "MARKET";
        return switch (type) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP_LOSS -> "STOP_LOSS";
            case STOP_LIMIT -> "STOP_LIMIT";
            case TAKE_PROFIT -> "TAKE_PROFIT";
        };
    }

    private static String mapOrderSide(OrderSide side) {
        if (side == null) return "LONG";
        // Map BUY->LONG, SELL->SHORT for futures-style API in the provided OpenAPI
        return switch (side) {
            case BUY -> "LONG";
            case SELL -> "SHORT";
        };
    }
}


