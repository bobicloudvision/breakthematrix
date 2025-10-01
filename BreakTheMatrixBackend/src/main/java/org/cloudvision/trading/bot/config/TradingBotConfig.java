package org.cloudvision.trading.bot.config;

import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.strategy.StrategyConfig;
import org.cloudvision.trading.bot.strategy.impl.MovingAverageStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

@Configuration
public class TradingBotConfig {

    @Autowired
    private TradingBot tradingBot;

    @Autowired
    private MovingAverageStrategy movingAverageStrategy;

    @PostConstruct
    public void setupTradingBot() {
        // Configure Moving Average Strategy
        StrategyConfig maConfig = new StrategyConfig("ma-strategy", List.of("BTCUSDT", "ETHUSDT"));
        maConfig.setMaxPositionSize(new BigDecimal("5000")); // $5000 per position
        maConfig.setStopLossPercentage(new BigDecimal("0.03")); // 3% stop loss
        maConfig.setTakeProfitPercentage(new BigDecimal("0.06")); // 6% take profit
        maConfig.setParameter("shortPeriod", 10);
        maConfig.setParameter("longPeriod", 20);

        movingAverageStrategy.initialize(maConfig);
        tradingBot.addStrategy(movingAverageStrategy);

        System.out.println("🤖 Trading Bot configured with strategies");
        System.out.println("📊 Use /api/bot/start to begin automated trading");
    }
}
