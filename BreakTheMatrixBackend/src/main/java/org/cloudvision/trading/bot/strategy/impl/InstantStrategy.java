package org.cloudvision.trading.bot.strategy.impl;

import org.cloudvision.trading.bot.account.PaperTradingAccount;
import org.cloudvision.trading.bot.account.Position;
import org.cloudvision.trading.bot.indicators.TechnicalIndicators;
import org.cloudvision.trading.bot.model.Order;
import org.cloudvision.trading.bot.model.OrderSide;
import org.cloudvision.trading.bot.model.OrderType;
import org.cloudvision.trading.bot.strategy.AbstractTradingStrategy;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Instant Strategy
 */
@Component
public class InstantStrategy extends AbstractTradingStrategy {

    @Override
    protected Object onSymbolInit(String symbol, List<CandlestickData> historicalCandles, Map<String, Object> params) {

        System.out.println("✅ InstantStrategy Strategy: Initialized for symbol " + symbol +
                         " with " + (historicalCandles != null ? historicalCandles.size() : 0) + " historical candles");

        return null;
    }

    @Override
    protected Map<String, Object> onCandleClosed(String symbol, CandlestickData candle, Map<String, Object> params, Object state) {

        BigDecimal currentPrice = candle.getClose();

        System.out.println("📈 InstantStrategy: New candle closed for " + symbol +
                         " at " + currentPrice + " | Time: " + candle.getCloseTime());





        List<Order> orders = new ArrayList<>();





//        PaperTradingAccount account = new PaperTradingAccount();

        System.out.println("2. Trailing Stop Loss");
        System.out.println("=====================");

        // Open a position
        Order order = new Order(UUID.randomUUID().toString(), symbol, OrderType.MARKET, OrderSide.BUY,
                new BigDecimal("1.0"), currentPrice, getStrategyId());
//        account.executeOrder(order);

        // Get the position ID (in real implementation, this would be returned from executeOrder)
//        List<Position> positions = account.getOpenPositions();
//        if (!positions.isEmpty()) {
//            String positionId = positions.get(0).getPositionId();
//
//            // Set trailing stop loss
//            BigDecimal trailingDistance = new BigDecimal("1"); // $1 trailing distance
////            boolean success = account.setTrailingStopLoss(positionId, trailingDistance, currentPrice);
//
////            if (success) {
////                System.out.println("Trailing stop loss set with distance: $" + trailingDistance);
////            }
//        }


        orders.add(order);


        // Return event-driven result
        Map<String, Object> result = new HashMap<>();
        result.put("orders", orders);
        result.put("state", state);
        return result;
    }

    @Override
    public String getStrategyId() {
        return "instant-strategy";
    }

    @Override
    public String getStrategyName() {
        return "Instant Strategy";
    }
    
    @Override
    public int getMinRequiredCandles() {
        return 50;
    }
    
    @Override
    public void reset() {
        // Call parent reset to clear base state
        // RSI strategy doesn't have additional state to clear
        super.reset();
        
        System.out.println("🔄 InstantStrategy: State reset");
    }
    
    @Override
    public Map<String, IndicatorMetadata> getIndicatorMetadata() {
        Map<String, IndicatorMetadata> metadata = new HashMap<>();

        return metadata;
    }
}

