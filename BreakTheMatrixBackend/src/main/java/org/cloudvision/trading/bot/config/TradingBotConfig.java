package org.cloudvision.trading.bot.config;

import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.strategy.StrategyConfig;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.bot.strategy.impl.MovingAverageStrategy;
import org.cloudvision.trading.bot.strategy.impl.RSIStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

/**
 * Trading Bot Configuration
 * Registers and configures all trading strategies
 */
@Configuration
public class TradingBotConfig {

    @Autowired
    private TradingBot tradingBot;

    @Autowired
    private MovingAverageStrategy movingAverageStrategy;
    
    @Autowired
    private RSIStrategy rsiStrategy;
    
    // Option 1: Auto-register all strategies (uncomment to use)
    // @Autowired(required = false)
    // private List<TradingStrategy> allStrategies;

    @PostConstruct
    public void setupTradingBot() {
        System.out.println("🔧 Configuring Trading Bot strategies...");
        
        // Option 1: Manual registration (more control)
        configureMovingAverageStrategy();
        configureRSIStrategy();
        
        // Option 2: Auto-register all strategies (uncomment to use)
        // autoRegisterAllStrategies();
        
        System.out.println("✅ Trading Bot configured with " + tradingBot.getStrategies().size() + " strategies");
        System.out.println("📊 Strategies: " + tradingBot.getStrategies().stream()
            .map(TradingStrategy::getStrategyName)
            .toList());
        System.out.println("🚀 Use /api/bot/enable to start the bot");
    }
    
    /**
     * Configure Moving Average Crossover Strategy
     */
    private void configureMovingAverageStrategy() {
        StrategyConfig config = new StrategyConfig(
            "ma-strategy", 
            List.of("BTCUSDT", "ETHUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("5000")); // $5000 per position
        config.setStopLossPercentage(new BigDecimal("0.03")); // 3% stop loss
        config.setTakeProfitPercentage(new BigDecimal("0.06")); // 6% take profit
        
        // Strategy-specific parameters
        config.setParameter("shortPeriod", 10);  // 10-period MA
        config.setParameter("longPeriod", 20);   // 20-period MA
        
        movingAverageStrategy.initialize(config);
        tradingBot.addStrategy(movingAverageStrategy);
        
        System.out.println("📈 Registered: Moving Average Strategy (10/20)");
    }
    
    /**
     * Configure RSI Strategy
     */
    private void configureRSIStrategy() {
        StrategyConfig config = new StrategyConfig(
            "rsi-strategy",
            List.of("BTCUSDT")  // Trade only BTC with RSI
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("3000")); // $3000 per position
        config.setStopLossPercentage(new BigDecimal("0.02")); // 2% stop loss
        config.setTakeProfitPercentage(new BigDecimal("0.05")); // 5% take profit
        
        // Strategy-specific parameters
        config.setParameter("rsiPeriod", 14);           // 14-period RSI
        config.setParameter("oversoldThreshold", 30);   // Buy when RSI < 30
        config.setParameter("overboughtThreshold", 70); // Sell when RSI > 70
        
        rsiStrategy.initialize(config);
        tradingBot.addStrategy(rsiStrategy);
        
        System.out.println("📊 Registered: RSI Strategy (14-period, 30/70 thresholds)");
    }
    
    /**
     * Alternative: Auto-register all strategies found in the application context
     * This automatically picks up any @Component annotated strategy classes
     */
    @SuppressWarnings("unused")
    private void autoRegisterAllStrategies() {
        // Uncomment @Autowired List<TradingStrategy> allStrategies above to use this
        
        // if (allStrategies != null && !allStrategies.isEmpty()) {
        //     for (TradingStrategy strategy : allStrategies) {
        //         // Create default config for each strategy
        //         StrategyConfig config = new StrategyConfig(
        //             strategy.getStrategyId(),
        //             List.of("BTCUSDT")
        //         );
        //         config.setMaxPositionSize(new BigDecimal("1000"));
        //         
        //         strategy.initialize(config);
        //         tradingBot.addStrategy(strategy);
        //         
        //         System.out.println("📌 Auto-registered: " + strategy.getStrategyName());
        //     }
        // }
    }
}
