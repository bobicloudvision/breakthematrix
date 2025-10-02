package org.cloudvision.trading.bot.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request to open a new position")
public class OpenPositionRequest {
    
    @Schema(description = "Trading symbol", example = "BTCUSDT", required = true)
    private String symbol;
    
    @Schema(description = "Position side (LONG or SHORT)", example = "LONG", required = true, allowableValues = {"LONG", "SHORT"})
    private String positionSide;
    
    @Schema(description = "Order type (MARKET or LIMIT)", example = "MARKET", allowableValues = {"MARKET", "LIMIT"})
    private String orderType;
    
    @Schema(description = "Entry price (required for LIMIT orders, ignored for MARKET orders)", example = "45000.00")
    private BigDecimal price;
    
    @Schema(description = "Position quantity", example = "0.001", required = true)
    private BigDecimal quantity;
    
    @Schema(description = "Stop loss price (optional)", example = "44000.00")
    private BigDecimal stopLoss;
    
    @Schema(description = "Take profit price (optional)", example = "47000.00")
    private BigDecimal takeProfit;
    
    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getPositionSide() {
        return positionSide;
    }
    
    public void setPositionSide(String positionSide) {
        this.positionSide = positionSide;
    }
    
    public String getOrderType() {
        return orderType;
    }
    
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getStopLoss() {
        return stopLoss;
    }
    
    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }
    
    public BigDecimal getTakeProfit() {
        return takeProfit;
    }
    
    public void setTakeProfit(BigDecimal takeProfit) {
        this.takeProfit = takeProfit;
    }
}

