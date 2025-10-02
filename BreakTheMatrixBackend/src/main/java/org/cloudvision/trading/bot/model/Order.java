package org.cloudvision.trading.bot.model;

import org.cloudvision.trading.bot.account.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public class Order {
    private String id;
    private String symbol;
    private OrderType type;
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private OrderStatus status;
    private Instant createdAt;
    private Instant executedAt;
    private BigDecimal executedQuantity;
    private BigDecimal executedPrice;
    private String strategyId;
    
    // FUTURES: Position side (LONG or SHORT)
    private PositionSide positionSide;
    
    // Risk management - optional stop loss and take profit suggested by strategy
    private BigDecimal suggestedStopLoss;
    private BigDecimal suggestedTakeProfit;

    public Order(String id, String symbol, OrderType type, OrderSide side, 
                BigDecimal quantity, BigDecimal price, String strategyId) {
        this.id = id;
        this.symbol = symbol;
        this.type = type;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.strategyId = strategyId;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.executedQuantity = BigDecimal.ZERO;
        // positionSide must be set explicitly by the strategy
    }

    // Getters and setters
    public String getId() { return id; }
    public String getSymbol() { return symbol; }
    public OrderType getType() { return type; }
    public OrderSide getSide() { return side; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExecutedAt() { return executedAt; }
    public BigDecimal getExecutedQuantity() { return executedQuantity; }
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public String getStrategyId() { return strategyId; }

    public void setStatus(OrderStatus status) { this.status = status; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public void setExecutedQuantity(BigDecimal executedQuantity) { this.executedQuantity = executedQuantity; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }
    
    public BigDecimal getSuggestedStopLoss() { return suggestedStopLoss; }
    public void setSuggestedStopLoss(BigDecimal suggestedStopLoss) { this.suggestedStopLoss = suggestedStopLoss; }
    
    public BigDecimal getSuggestedTakeProfit() { return suggestedTakeProfit; }
    public void setSuggestedTakeProfit(BigDecimal suggestedTakeProfit) { this.suggestedTakeProfit = suggestedTakeProfit; }
    
    // FUTURES: Position side
    public PositionSide getPositionSide() { return positionSide; }
    public void setPositionSide(PositionSide positionSide) { this.positionSide = positionSide; }

    @Override
    public String toString() {
        return String.format("Order{id='%s', symbol='%s', %s %s, qty=%s, price=%s, status=%s}",
                id, symbol, side, type, quantity, price, status);
    }
}
