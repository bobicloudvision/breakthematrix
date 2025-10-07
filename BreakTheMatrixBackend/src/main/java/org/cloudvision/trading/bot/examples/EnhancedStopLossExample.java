package org.cloudvision.trading.bot.examples;

import org.cloudvision.trading.bot.account.PaperTradingAccount;
import org.cloudvision.trading.bot.account.Position;
import org.cloudvision.trading.bot.account.StopLossType;
import org.cloudvision.trading.bot.account.TakeProfitType;
import org.cloudvision.trading.bot.account.PositionSide;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example demonstrating enhanced stop loss and take profit functionality
 */
public class EnhancedStopLossExample {
    
    public static void main(String[] args) {
        // Create paper trading account
        PaperTradingAccount account = new PaperTradingAccount();
        
        System.out.println("=== Enhanced Stop Loss and Take Profit Example ===\n");
        
        // Example 1: Basic fixed stop loss and take profit
        demonstrateFixedStopLoss(account);
        
        // Example 2: Trailing stop loss
        demonstrateTrailingStopLoss(account);
        
        // Example 3: Breakeven stop loss
        demonstrateBreakevenStopLoss(account);
        
        // Example 4: ATR-based stop loss and take profit
        demonstrateATRBasedStops(account);
        
        // Example 5: Dynamic stop loss management
        demonstrateDynamicStopLossManagement(account);
    }
    
    /**
     * Example 1: Basic fixed stop loss and take profit
     */
    private static void demonstrateFixedStopLoss(PaperTradingAccount account) {
        System.out.println("1. Basic Fixed Stop Loss and Take Profit");
        System.out.println("========================================");
        
        // Create order with fixed stop loss and take profit
        Order order = new Order("order1", "BTCUSDT", OrderType.MARKET, OrderSide.BUY, 
            new BigDecimal("0.1"), new BigDecimal("50000"), "example-strategy");
        order.setSuggestedStopLoss(new BigDecimal("48000"));  // 4% stop loss
        order.setSuggestedTakeProfit(new BigDecimal("52000")); // 4% take profit
        
        // Execute order
        account.executeOrder(order);
        
        System.out.println("Position opened with fixed stop loss and take profit\n");
    }
    
    /**
     * Example 2: Trailing stop loss
     */
    private static void demonstrateTrailingStopLoss(PaperTradingAccount account) {
        System.out.println("2. Trailing Stop Loss");
        System.out.println("=====================");
        
        // Open a position
        Order order = new Order("order2", "ETHUSDT", OrderType.MARKET, OrderSide.BUY, 
            new BigDecimal("1.0"), new BigDecimal("3000"), "example-strategy");
        account.executeOrder(order);
        
        // Get the position ID (in real implementation, this would be returned from executeOrder)
        List<Position> positions = account.getOpenPositions();
        if (!positions.isEmpty()) {
            String positionId = positions.get(0).getPositionId();
            
            // Set trailing stop loss
            BigDecimal trailingDistance = new BigDecimal("100"); // $100 trailing distance
            BigDecimal currentPrice = new BigDecimal("3000"); // Simulate current price
            boolean success = account.setTrailingStopLoss(positionId, trailingDistance, currentPrice);
            
            if (success) {
                System.out.println("Trailing stop loss set with distance: $" + trailingDistance);
            }
        }
        
        System.out.println();
    }
    
    /**
     * Example 3: Breakeven stop loss
     */
    private static void demonstrateBreakevenStopLoss(PaperTradingAccount account) {
        System.out.println("3. Breakeven Stop Loss");
        System.out.println("======================");
        
        // Open a position
        Order order = new Order("order3", "ADAUSDT", OrderType.MARKET, OrderSide.BUY, 
            new BigDecimal("1000"), new BigDecimal("0.5"), "example-strategy");
        account.executeOrder(order);
        
        List<Position> positions = account.getOpenPositions();
        if (!positions.isEmpty()) {
            String positionId = positions.get(0).getPositionId();
            
            // Set breakeven stop loss at 2% profit
            BigDecimal triggerPrice = new BigDecimal("0.51"); // 2% above entry
            boolean includeSmallProfit = true;
            
            boolean success = account.setBreakevenStopLoss(positionId, triggerPrice, includeSmallProfit);
            
            if (success) {
                System.out.println("Breakeven stop loss set at trigger price: $" + triggerPrice);
                System.out.println("Will move to breakeven + small profit when triggered");
            }
        }
        
        System.out.println();
    }
    
    /**
     * Example 4: ATR-based stop loss and take profit
     */
    private static void demonstrateATRBasedStops(PaperTradingAccount account) {
        System.out.println("4. ATR-Based Stop Loss and Take Profit");
        System.out.println("======================================");
        
        // Open a position
        Order order = new Order("order4", "SOLUSDT", OrderType.MARKET, OrderSide.BUY, 
            new BigDecimal("10"), new BigDecimal("100"), "example-strategy");
        account.executeOrder(order);
        
        List<Position> positions = account.getOpenPositions();
        if (!positions.isEmpty()) {
            String positionId = positions.get(0).getPositionId();
            
            // Simulate ATR values (in real implementation, these would come from indicators)
            Map<String, BigDecimal> atrValues = new HashMap<>();
            BigDecimal atrValue = new BigDecimal("5.0"); // $5 ATR value
            atrValues.put("SOLUSDT", atrValue);
            
            // Update ATR values
            account.updateATRValues(atrValues);
            
            // Set ATR-based stop loss (2x ATR)
            BigDecimal atrMultiplier = new BigDecimal("2.0");
            BigDecimal currentPrice = new BigDecimal("100"); // Simulate current price
            boolean slSuccess = account.setATRStopLoss(positionId, atrMultiplier, atrValue, currentPrice);
            
            // Set ATR-based take profit (3x ATR)
            BigDecimal tpMultiplier = new BigDecimal("3.0");
            boolean tpSuccess = account.setATRTakeProfit(positionId, tpMultiplier, atrValue, currentPrice);
            
            if (slSuccess && tpSuccess) {
                System.out.println("ATR-based stop loss set with multiplier: " + atrMultiplier);
                System.out.println("ATR-based take profit set with multiplier: " + tpMultiplier);
            }
        }
        
        System.out.println();
    }
    
    /**
     * Example 5: Dynamic stop loss management
     */
    private static void demonstrateDynamicStopLossManagement(PaperTradingAccount account) {
        System.out.println("5. Dynamic Stop Loss Management");
        System.out.println("===============================");
        
        // Open a position
        Order order = new Order("order5", "DOTUSDT", OrderType.MARKET, OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("7"), "example-strategy");
        account.executeOrder(order);
        
        List<Position> positions = account.getOpenPositions();
        if (!positions.isEmpty()) {
            String positionId = positions.get(0).getPositionId();
            
            // Set initial fixed stop loss
            BigDecimal initialStopLoss = new BigDecimal("6.5");
            boolean success = account.getPositionManager().setStopLoss(positionId, initialStopLoss, StopLossType.FIXED);
            
            if (success) {
                System.out.println("Initial fixed stop loss set at: $" + initialStopLoss);
                
                // Later, convert to trailing stop loss
                BigDecimal trailingDistance = new BigDecimal("0.2");
                BigDecimal currentPrice = new BigDecimal("7"); // Simulate current price
                boolean trailingSuccess = account.setTrailingStopLoss(positionId, trailingDistance, currentPrice);
                
                if (trailingSuccess) {
                    System.out.println("Converted to trailing stop loss with distance: $" + trailingDistance);
                }
            }
        }
        
        // Query positions by stop loss type
        List<Position> trailingPositions = account.getPositionsByStopLossType(StopLossType.TRAILING);
        System.out.println("Positions with trailing stop loss: " + trailingPositions.size());
        
        List<Position> atrPositions = account.getPositionsByStopLossType(StopLossType.ATR_BASED);
        System.out.println("Positions with ATR-based stop loss: " + atrPositions.size());
        
        System.out.println();
    }
    
    /**
     * Simulate price updates to demonstrate dynamic behavior
     */
    private static void simulatePriceUpdates(PaperTradingAccount account) {
        System.out.println("6. Simulating Price Updates");
        System.out.println("============================");
        
        // Simulate price updates
        Map<String, BigDecimal> priceUpdates = new HashMap<>();
        priceUpdates.put("BTCUSDT", new BigDecimal("51000"));
        priceUpdates.put("ETHUSDT", new BigDecimal("3100"));
        priceUpdates.put("ADAUSDT", new BigDecimal("0.52"));
        priceUpdates.put("SOLUSDT", new BigDecimal("105"));
        priceUpdates.put("DOTUSDT", new BigDecimal("7.2"));
        
        // Update prices (this will trigger dynamic stop loss updates)
        account.updateCurrentPrices(priceUpdates);
        
        System.out.println("Price updates applied - dynamic stop losses updated");
        System.out.println();
    }
}
