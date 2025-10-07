package org.cloudvision.trading.bot;

import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderManager {
    
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final List<Order> orderHistory = new CopyOnWriteArrayList<>();

    /**
     * Submit an order for execution
     */
    public void submitOrder(Order order) {
        order.setStatus(OrderStatus.SUBMITTED);
        orders.put(order.getId(), order);
        orderHistory.add(order);
        
        System.out.println("📋 Order submitted: " + order);
        
        // Simulate order execution (in real implementation, this would call exchange API)
        simulateOrderExecution(order);
    }

    /**
     * Cancel an order
     */
    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null && order.getStatus() == OrderStatus.SUBMITTED) {
            order.setStatus(OrderStatus.CANCELLED);
            orders.remove(orderId);
            System.out.println("❌ Order cancelled: " + orderId);
            return true;
        }
        return false;
    }

    /**
     * Cancel all pending orders
     */
    public void cancelAllOrders() {
        orders.values().forEach(order -> {
            if (order.getStatus() == OrderStatus.SUBMITTED) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        });
        orders.clear();
        System.out.println("❌ All orders cancelled");
    }

    /**
     * Get order by ID
     */
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    /**
     * Get all active orders
     */
    public List<Order> getActiveOrders() {
        return List.copyOf(orders.values());
    }

    /**
     * Get order history
     */
    public List<Order> getOrderHistory() {
        return List.copyOf(orderHistory);
    }

    /**
     * Get orders by strategy
     */
    public List<Order> getOrdersByStrategy(String strategyId) {
        return orderHistory.stream()
//                .filter(order -> strategyId.equals(order.getStrategyId()))
                .toList();
    }

    /**
     * Simulate order execution (replace with real exchange integration)
     */
    private void simulateOrderExecution(Order order) {
        // Simulate execution after a short delay
        new Thread(() -> {
            try {
                Thread.sleep(1000 + (long)(Math.random() * 2000)); // 1-3 second delay
                
                // Simulate execution
                order.setStatus(OrderStatus.FILLED);
                order.setExecutedAt(Instant.now());
                order.setExecutedQuantity(order.getQuantity());
                order.setExecutedPrice(order.getPrice());
                
                orders.remove(order.getId());
                
                System.out.println("✅ Order executed: " + order.getId() + 
                                 " - " + order.getSide() + " " + order.getQuantity() + 
                                 " " + order.getSymbol() + " @ " + order.getExecutedPrice());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Get order statistics
     */
    public OrderStats getOrderStats() {
        long totalOrders = orderHistory.size();
        long filledOrders = orderHistory.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.FILLED ? 1 : 0)
                .sum();
        long cancelledOrders = orderHistory.stream()
                .mapToLong(order -> order.getStatus() == OrderStatus.CANCELLED ? 1 : 0)
                .sum();
        
        double fillRate = totalOrders > 0 ? (double) filledOrders / totalOrders * 100 : 0;
        
        return new OrderStats(totalOrders, filledOrders, cancelledOrders, fillRate);
    }

    public static class OrderStats {
        private final long totalOrders;
        private final long filledOrders;
        private final long cancelledOrders;
        private final double fillRate;

        public OrderStats(long totalOrders, long filledOrders, long cancelledOrders, double fillRate) {
            this.totalOrders = totalOrders;
            this.filledOrders = filledOrders;
            this.cancelledOrders = cancelledOrders;
            this.fillRate = fillRate;
        }

        public long getTotalOrders() { return totalOrders; }
        public long getFilledOrders() { return filledOrders; }
        public long getCancelledOrders() { return cancelledOrders; }
        public double getFillRate() { return fillRate; }

        @Override
        public String toString() {
            return String.format("OrderStats{total=%d, filled=%d, cancelled=%d, fillRate=%.1f%%}",
                    totalOrders, filledOrders, cancelledOrders, fillRate);
        }
    }
}
