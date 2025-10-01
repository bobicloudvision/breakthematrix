package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.account.Position;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.cloudvision.trading.bot.TradingBot;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/positions")
@Tag(name = "Positions", description = "Track and manage trading positions")
public class PositionController {
    
    private final TradingBot tradingBot;
    
    public PositionController(TradingBot tradingBot) {
        this.tradingBot = tradingBot;
    }
    
    @Operation(summary = "Get All Open Positions", description = "Get all currently open positions")
    @GetMapping("/open")
    public List<Position> getOpenPositions() {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        return activeAccount.getOpenPositions();
    }
    
    @Operation(summary = "Get Open Positions by Symbol", description = "Get open positions for a specific trading symbol")
    @GetMapping("/open/{symbol}")
    public List<Position> getOpenPositionsBySymbol(@PathVariable String symbol) {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        return activeAccount.getOpenPositionsBySymbol(symbol);
    }
    
    @Operation(summary = "Get Position by ID", description = "Get detailed information about a specific position")
    @GetMapping("/{positionId}")
    public Position getPosition(@PathVariable String positionId) {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        Position position = activeAccount.getPosition(positionId);
        if (position == null) {
            throw new IllegalArgumentException("Position not found: " + positionId);
        }
        return position;
    }
    
    @Operation(summary = "Get Position History", description = "Get all closed positions")
    @GetMapping("/history")
    public List<Position> getPositionHistory() {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        return activeAccount.getPositionHistory();
    }
    
    @Operation(summary = "Get Position Summary", description = "Get summary statistics about all positions")
    @GetMapping("/summary")
    public Map<String, Object> getPositionSummary() {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        
        return activeAccount.getPositionManager().getPositionSummary();
    }
    
    @Operation(summary = "Close Position", description = "Manually close a position")
    @PostMapping("/{positionId}/close")
    public Map<String, String> closePosition(
            @PathVariable String positionId,
            @RequestParam BigDecimal closePrice,
            @RequestParam(required = false) BigDecimal quantity) {
        
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        
        Position position = activeAccount.getPosition(positionId);
        if (position == null || !position.isOpen()) {
            throw new IllegalArgumentException("Position not found or already closed: " + positionId);
        }
        
        BigDecimal closeQty = quantity != null ? quantity : position.getQuantity();
        activeAccount.getPositionManager().closePosition(positionId, closePrice, closeQty);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Position closed");
        response.put("positionId", positionId);
        
        return response;
    }
    
    @Operation(summary = "Set Stop Loss", description = "Set stop loss for a position")
    @PostMapping("/{positionId}/stop-loss")
    public Map<String, String> setStopLoss(
            @PathVariable String positionId,
            @RequestParam BigDecimal stopLoss) {
        
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        
        Position position = activeAccount.getPosition(positionId);
        if (position == null || !position.isOpen()) {
            throw new IllegalArgumentException("Position not found or already closed: " + positionId);
        }
        
        position.setStopLoss(stopLoss);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Stop loss set");
        response.put("positionId", positionId);
        response.put("stopLoss", stopLoss.toString());
        
        return response;
    }
    
    @Operation(summary = "Set Take Profit", description = "Set take profit for a position")
    @PostMapping("/{positionId}/take-profit")
    public Map<String, String> setTakeProfit(
            @PathVariable String positionId,
            @RequestParam BigDecimal takeProfit) {
        
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        
        Position position = activeAccount.getPosition(positionId);
        if (position == null || !position.isOpen()) {
            throw new IllegalArgumentException("Position not found or already closed: " + positionId);
        }
        
        position.setTakeProfit(takeProfit);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Take profit set");
        response.put("positionId", positionId);
        response.put("takeProfit", takeProfit.toString());
        
        return response;
    }
}

