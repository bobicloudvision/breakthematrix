package org.cloudvision.trading.bot.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request to close positions")
public class ClosePositionRequest {
    
    @Schema(description = "Trading symbol", example = "BTCUSDT", required = true)
    private String symbol;
    
    @Schema(description = "Position side (LONG or SHORT, defaults to LONG)", example = "LONG", allowableValues = {"LONG", "SHORT"})
    private String positionSide;
    
    @Schema(description = "Close price", example = "46500.00", required = true)
    private BigDecimal price;
    
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
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}

