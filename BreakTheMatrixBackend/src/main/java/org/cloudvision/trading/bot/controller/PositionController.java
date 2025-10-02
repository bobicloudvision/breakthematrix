package org.cloudvision.trading.bot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cloudvision.trading.bot.account.Position;
import org.cloudvision.trading.bot.account.PositionSide;
import org.cloudvision.trading.bot.account.TradingAccount;
import org.cloudvision.trading.bot.controller.dto.ClosePositionRequest;
import org.cloudvision.trading.bot.controller.dto.OpenPositionRequest;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderType;
import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.model.TimeInterval;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/positions")
@Tag(name = "Positions", description = "Track and manage trading positions")
public class PositionController {
    
    private final TradingBot tradingBot;
    private final UniversalTradingDataService tradingDataService;
    
    public PositionController(TradingBot tradingBot, UniversalTradingDataService tradingDataService) {
        this.tradingBot = tradingBot;
        this.tradingDataService = tradingDataService;
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
    
    @Operation(summary = "Get All Orders", description = "Get all orders from active account")
    @GetMapping("/orders")
    public List<Order> getAllOrders() {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        return activeAccount.getAllOrders();
    }
    
    @Operation(summary = "Get Filled Orders", description = "Get all executed orders (order history)")
    @GetMapping("/orders/filled")
    public List<Order> getFilledOrders() {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        return activeAccount.getFilledOrders();
    }
    
    @Operation(summary = "Get Open Orders", description = "Get all pending/open orders")
    @GetMapping("/orders/open")
    public List<Order> getOpenOrders() {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        return activeAccount.getOpenOrders();
    }
    
    @Operation(
        summary = "Open Position Manually", 
        description = "Manually open a LONG or SHORT position. MARKET orders execute immediately at current price. LIMIT orders only execute when price reaches target.",
        requestBody = @RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "MARKET Order - LONG",
                        summary = "Open long position at current market price (instant execution)",
                        value = """
                            {
                              "symbol": "BTCUSDT",
                              "positionSide": "LONG",
                              "orderType": "MARKET",
                              "quantity": 0.001,
                              "stopLoss": 44000.00,
                              "takeProfit": 47000.00
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "MARKET Order - SHORT",
                        summary = "Open short position at current market price",
                        value = """
                            {
                              "symbol": "ETHUSDT",
                              "positionSide": "SHORT",
                              "orderType": "MARKET",
                              "quantity": 0.1,
                              "stopLoss": 3100.00,
                              "takeProfit": 2800.00
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "LIMIT Order - LONG",
                        summary = "Open long position only when price reaches 45000",
                        value = """
                            {
                              "symbol": "BTCUSDT",
                              "positionSide": "LONG",
                              "orderType": "LIMIT",
                              "price": 45000.00,
                              "quantity": 0.001,
                              "stopLoss": 44000.00,
                              "takeProfit": 47000.00
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "LIMIT Order - SHORT",
                        summary = "Open short position only when price reaches 3000",
                        value = """
                            {
                              "symbol": "ETHUSDT",
                              "positionSide": "SHORT",
                              "orderType": "LIMIT",
                              "price": 3000.00,
                              "quantity": 0.1
                            }
                            """
                    )
                }
            )
        )
    )
    @PostMapping("/open")
    public Map<String, Object> openPosition(@org.springframework.web.bind.annotation.RequestBody OpenPositionRequest request) {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        
        // Parse request
        String symbol = request.getSymbol();
        String positionSideStr = request.getPositionSide(); // "LONG" or "SHORT"
        String orderTypeStr = request.getOrderType() != null ? request.getOrderType() : "MARKET"; // Default to MARKET
        BigDecimal quantity = request.getQuantity();
        BigDecimal stopLoss = request.getStopLoss();
        BigDecimal takeProfit = request.getTakeProfit();
        
        // Validate
        PositionSide positionSide = PositionSide.valueOf(positionSideStr.toUpperCase());
        OrderType orderType = OrderType.valueOf(orderTypeStr.toUpperCase());
        
        // Determine execution price based on order type
        BigDecimal executionPrice;
        if (orderType == OrderType.MARKET) {
            // MARKET ORDER: Get current market price
            executionPrice = getCurrentMarketPrice(symbol);
            
            // Fallback: If market data isn't available, allow user to provide price
            if (executionPrice == null) {
                if (request.getPrice() != null) {
                    executionPrice = request.getPrice();
                    System.out.println("⚠️  Market data unavailable - using provided price: " + executionPrice);
                } else {
                    throw new IllegalStateException(
                        "Cannot get current market price for " + symbol + ". " +
                        "Please ensure market data is streaming, or provide a 'price' parameter as fallback."
                    );
                }
            } else {
                System.out.println("📊 MARKET order - Executing at current price: " + executionPrice);
            }
        } else {
            // LIMIT ORDER: Use provided price
            executionPrice = request.getPrice();
            if (executionPrice == null) {
                throw new IllegalArgumentException("Price is required for LIMIT orders");
            }
            
            BigDecimal currentPrice = getCurrentMarketPrice(symbol);
            if (currentPrice != null) {
                System.out.println("⏳ LIMIT order - Will execute at: " + executionPrice + " (current price: " + currentPrice + ")");
            } else {
                System.out.println("⏳ LIMIT order - Will execute at: " + executionPrice + " (current price: unavailable)");
            }
            
            // TODO: For now, LIMIT orders execute immediately in paper trading
            // In production, these should be queued and executed when price is reached
            System.out.println("⚠️  Paper trading: LIMIT order executing immediately");
        }
        
        // Determine order side
        // LONG = BUY, SHORT = SELL
        OrderSide orderSide = (positionSide == PositionSide.LONG) ? OrderSide.BUY : OrderSide.SELL;
        
        // Create order
        Order order = new Order(
            UUID.randomUUID().toString(),
            symbol,
            orderType,
            orderSide,
            quantity,
            executionPrice,
            "manual-trade"
        );
        
        order.setPositionSide(positionSide);
        if (stopLoss != null) order.setSuggestedStopLoss(stopLoss);
        if (takeProfit != null) order.setSuggestedTakeProfit(takeProfit);
        
        // Execute order
        Order executedOrder = activeAccount.executeOrder(order);
        
        // Find the created position
        List<Position> positions = activeAccount.getOpenPositionsBySymbol(symbol);
        Position createdPosition = positions.isEmpty() ? null : positions.get(positions.size() - 1);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", executedOrder.getStatus().toString());
        response.put("message", orderType == OrderType.MARKET ? "Position opened at market price" : "Position opened at limit price");
        response.put("orderType", orderType.toString());
        response.put("executionPrice", executionPrice);
        response.put("orderId", executedOrder.getId());
        response.put("positionId", createdPosition != null ? createdPosition.getPositionId() : null);
        response.put("order", executedOrder);
        
        return response;
    }
    
    /**
     * Get current market price for a symbol
     */
    private BigDecimal getCurrentMarketPrice(String symbol) {
        try {
            System.out.println("🔍 Fetching current market price for: " + symbol);
            
            // First, check what providers are available
            List<String> availableProviders = tradingDataService.getProviders();
            System.out.println("📋 Available providers: " + availableProviders);
            
            if (availableProviders.isEmpty()) {
                System.err.println("❌ No providers registered in trading service!");
                return null;
            }
            
            // Try all available providers
            for (String providerName : availableProviders) {
                try {
                    System.out.println("🔄 Trying provider: " + providerName);
                    List<CandlestickData> candles = tradingDataService.getHistoricalKlines(
                        providerName,
                        symbol,
                        TimeInterval.ONE_MINUTE,
                        1 // Get just the latest candle
                    );
                    
                    System.out.println("📊 Received " + candles.size() + " candles from " + providerName);
                    
                    if (!candles.isEmpty()) {
                        BigDecimal price = candles.get(candles.size() - 1).getClose();
                        System.out.println("✅ Got price from " + providerName + ": " + price);
                        return price;
                    } else {
                        System.out.println("⚠️  Provider " + providerName + " returned empty candle list");
                    }
                } catch (Exception e) {
                    System.err.println("⚠️  Provider " + providerName + " failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.err.println("❌ No price data available from any provider");
        } catch (Exception e) {
            System.err.println("❌ Error fetching current market price: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    @Operation(
        summary = "Close Position by Symbol", 
        description = "Close all positions for a specific symbol and side",
        requestBody = @RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "Close LONG Position",
                        summary = "Close all long positions on BTCUSDT",
                        value = """
                            {
                              "symbol": "BTCUSDT",
                              "positionSide": "LONG",
                              "price": 46500.00
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Close SHORT Position",
                        summary = "Close all short positions on ETHUSDT",
                        value = """
                            {
                              "symbol": "ETHUSDT",
                              "positionSide": "SHORT",
                              "price": 2950.00
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Close Default (LONG)",
                        summary = "Close positions (defaults to LONG if not specified)",
                        value = """
                            {
                              "symbol": "BTCUSDT",
                              "price": 46500.00
                            }
                            """
                    )
                }
            )
        )
    )
    @PostMapping("/close")
    public Map<String, Object> closePositionsBySymbol(@org.springframework.web.bind.annotation.RequestBody ClosePositionRequest request) {
        TradingAccount activeAccount = tradingBot.getAccountManager().getActiveAccount();
        if (activeAccount == null) {
            throw new IllegalStateException("No active trading account");
        }
        
        String symbol = request.getSymbol();
        String positionSideStr = request.getPositionSide() != null ? request.getPositionSide() : "LONG";
        BigDecimal price = request.getPrice();
        
        PositionSide positionSide = PositionSide.valueOf(positionSideStr.toUpperCase());
        
        // Get open positions
        List<Position> positions = activeAccount.getOpenPositionsBySymbol(symbol).stream()
            .filter(p -> p.getSide() == positionSide)
            .toList();
        
        if (positions.isEmpty()) {
            throw new IllegalStateException("No open " + positionSide + " positions for " + symbol);
        }
        
        // Calculate total quantity
        BigDecimal totalQuantity = positions.stream()
            .map(Position::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Create close order
        OrderSide orderSide = (positionSide == PositionSide.LONG) ? OrderSide.SELL : OrderSide.BUY;
        
        Order closeOrder = new Order(
            UUID.randomUUID().toString(),
            symbol,
            OrderType.MARKET,
            orderSide,
            totalQuantity,
            price,
            "manual-trade"
        );
        
        closeOrder.setPositionSide(positionSide);
        
        // Execute close order
        Order executedOrder = activeAccount.executeOrder(closeOrder);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", executedOrder.getStatus().toString());
        response.put("message", "Positions closed");
        response.put("orderId", executedOrder.getId());
        response.put("closedPositions", positions.size());
        response.put("totalQuantity", totalQuantity);
        
        return response;
    }
    
    @Operation(summary = "Close Specific Position", description = "Close a specific position by ID")
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
    
    // Exception Handlers
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", e.getMessage());
        error.put("status", 400);
        System.err.println("❌ Bad Request: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Service Unavailable");
        error.put("message", e.getMessage());
        error.put("status", 503);
        System.err.println("❌ Service Error: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", e.getMessage());
        error.put("type", e.getClass().getSimpleName());
        error.put("status", 500);
        System.err.println("❌ Unexpected Error: " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

