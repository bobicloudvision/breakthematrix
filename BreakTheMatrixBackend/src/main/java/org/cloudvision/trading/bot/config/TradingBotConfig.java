package org.cloudvision.trading.bot.config;

import org.cloudvision.trading.bot.TradingBot;
import org.cloudvision.trading.bot.strategy.StrategyConfig;
import org.cloudvision.trading.bot.strategy.TradingStrategy;
import org.cloudvision.trading.bot.strategy.impl.MovingAverageStrategy;
import org.cloudvision.trading.bot.strategy.impl.OrderBlockStrategy;
import org.cloudvision.trading.bot.strategy.impl.OrderFlowStrategy;
import org.cloudvision.trading.bot.strategy.impl.RSIStrategy;
import org.cloudvision.trading.bot.strategy.impl.SuperTrendStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

/**
 * Trading Bot Configuration
 * Registers and configures all trading strategies
 * Note: Infrastructure (connection, subscriptions) is handled by TradingConfig
 */
@Configuration
@org.springframework.core.annotation.Order(1) // Run before TradingConfig (which needs strategies)
public class TradingBotConfig {

    @Autowired
    private TradingBot tradingBot;

    @Autowired
    private MovingAverageStrategy movingAverageStrategy;
    
    @Autowired
    private RSIStrategy rsiStrategy;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // Option 1: Auto-register all strategies (uncomment to use)
    // @Autowired(required = false)
    // private List<TradingStrategy> allStrategies;

    @PostConstruct
    public void setupTradingBot() {
        System.out.println("🔧 Configuring Trading Bot strategies...");
        
        // Option 1: Manual registration (more control)
        configureMovingAverageStrategy();
        configureRSIStrategy();
        
        // Configure SuperTrend strategies with different parameters
        configureSuperTrendStrategy1(); // Conservative (10, 3)
        configureSuperTrendStrategy2(); // Moderate (10, 2)
        configureSuperTrendStrategy3(); // Aggressive (7, 3)
        
        // Configure Order Block strategies with different parameters
        configureOrderBlockStrategy1(); // Standard (pivot: 5)
        configureOrderBlockStrategy2(); // Scalping (pivot: 3)
        
        // Configure Order Flow strategies with different parameters
        configureOrderFlowStrategy1(); // Balanced (5m, recommended)
        configureOrderFlowStrategy2(); // Scalping (1m, aggressive)
        configureOrderFlowStrategy3(); // Swing (15m, conservative)
        
        // Option 2: Auto-register all strategies (uncomment to use)
        // autoRegisterAllStrategies();
        
        System.out.println("✅ Trading Bot configured with " + tradingBot.getStrategies().size() + " strategies");
        System.out.println("📊 Registered strategies (all disabled): " + tradingBot.getStrategies().stream()
            .map(TradingStrategy::getStrategyName)
            .toList());
        System.out.println("⚠️  Note: Only ONE strategy can be enabled at a time per account");
        System.out.println("🔧 Enable a strategy: POST /api/bot/strategies/{strategyId}/enable");
        System.out.println("🚀 Start the bot: POST /api/bot/enable");
        System.out.println("\nℹ️  Market data connection will be established by TradingConfig\n");
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
    }
    
    /**
     * Configure SuperTrend Strategy #1 - Conservative
     * ATR Period: 10, Multiplier: 3 (standard settings)
     * Best for: Less frequent signals, higher confidence trades
     */
    private void configureSuperTrendStrategy1() {
        SuperTrendStrategy strategy = applicationContext.getBean(SuperTrendStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "supertrend-conservative",
            List.of("BTCUSDT", "ETHUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("4000")); // $4000 per position
        config.setStopLossPercentage(new BigDecimal("0.025")); // 2.5% stop loss
        config.setTakeProfitPercentage(new BigDecimal("0.05")); // 5% take profit (1:2 R:R)
        
        // Strategy-specific parameters
        config.setParameter("atrPeriod", 10);           // 10-period ATR
        config.setParameter("atrMultiplier", 3.0);      // Multiplier of 3 (conservative)
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure SuperTrend Strategy #2 - Moderate
     * ATR Period: 10, Multiplier: 2 (more sensitive)
     * Best for: More frequent signals, balanced approach
     */
    private void configureSuperTrendStrategy2() {
        SuperTrendStrategy strategy = applicationContext.getBean(SuperTrendStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "supertrend-moderate",
            List.of("BTCUSDT", "ETHUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("3000")); // $3000 per position
        config.setStopLossPercentage(new BigDecimal("0.02")); // 2% stop loss
        config.setTakeProfitPercentage(new BigDecimal("0.04")); // 4% take profit
        
        // Strategy-specific parameters
        config.setParameter("atrPeriod", 10);           // 10-period ATR
        config.setParameter("atrMultiplier", 2.0);      // Multiplier of 2 (moderate)
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure SuperTrend Strategy #3 - Aggressive
     * ATR Period: 7, Multiplier: 3 (faster response)
     * Best for: Quick entries, shorter timeframes
     */
    private void configureSuperTrendStrategy3() {
        SuperTrendStrategy strategy = applicationContext.getBean(SuperTrendStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "supertrend-aggressive",
            List.of("BTCUSDT", "ETHUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("2500")); // $2500 per position (smaller size for aggressive)
        config.setStopLossPercentage(new BigDecimal("0.015")); // 1.5% stop loss (tighter)
        config.setTakeProfitPercentage(new BigDecimal("0.03")); // 3% take profit (1:2 R:R)
        
        // Strategy-specific parameters
        config.setParameter("atrPeriod", 7);            // 7-period ATR (faster)
        config.setParameter("atrMultiplier", 3.0);      // Multiplier of 3
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure Order Block Strategy #1 - Standard
     * Volume Pivot Length: 5, Max OBs: 3 (standard settings)
     * Best for: Swing trading, identifying institutional zones
     */
    private void configureOrderBlockStrategy1() {
        OrderBlockStrategy strategy = applicationContext.getBean(OrderBlockStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "orderblock-standard",
            List.of("BTCUSDT", "ETHUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("3500")); // $3500 per position
        config.setStopLossPercentage(new BigDecimal("0.02")); // 2% stop loss
        config.setTakeProfitPercentage(new BigDecimal("0.04")); // 4% take profit (1:2 R:R)
        
        // Strategy-specific parameters
        config.setParameter("volumePivotLength", 5);        // 5-period pivot detection
        config.setParameter("maxBullishOrderBlocks", 3);    // Track 3 bullish OBs
        config.setParameter("maxBearishOrderBlocks", 3);    // Track 3 bearish OBs
        config.setParameter("mitigationMethod", "Wick");    // Wick-based mitigation
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure Order Block Strategy #2 - Scalping
     * Volume Pivot Length: 3, Max OBs: 2 (faster signals)
     * Best for: Scalping, quick entries on lower timeframes
     */
    private void configureOrderBlockStrategy2() {
        OrderBlockStrategy strategy = applicationContext.getBean(OrderBlockStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "orderblock-scalping",
            List.of("BTCUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("2000")); // $2000 per position (smaller for scalping)
        config.setStopLossPercentage(new BigDecimal("0.015")); // 1.5% stop loss (tighter)
        config.setTakeProfitPercentage(new BigDecimal("0.03")); // 3% take profit
        
        // Strategy-specific parameters
        config.setParameter("volumePivotLength", 3);        // 3-period pivot (faster signals)
        config.setParameter("maxBullishOrderBlocks", 2);    // Track only 2 most recent OBs
        config.setParameter("maxBearishOrderBlocks", 2);
        config.setParameter("mitigationMethod", "Close");   // Close-based mitigation (more conservative)
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure Order Flow Strategy #1 - Balanced (RECOMMENDED)
     * Timeframe: 5m, CVD Lookback: 20
     * Best for: Day trading, balanced approach with good signal quality
     */
    private void configureOrderFlowStrategy1() {
        OrderFlowStrategy strategy = applicationContext.getBean(OrderFlowStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "orderflow-balanced",
            List.of("BTCUSDT")
        );
        
        // Position sizing and risk parameters
        config.setMaxPositionSize(new BigDecimal("3000")); // $3000 per position
        config.setStopLossPercentage(new BigDecimal("0.02")); // 2% stop loss
        config.setTakeProfitPercentage(new BigDecimal("0.05")); // 5% take profit (1:2.5 R:R)
        
        // Timeframe & lookback parameters
        config.setParameter("timeInterval", "5m");           // 5-minute candles
        config.setParameter("cvdLookback", 20);              // 20 candles for CVD trend
        config.setParameter("divergenceLookback", 50);       // 50 candles for divergences
        config.setParameter("swingLookback", 5);             // 5 candles for swing detection
        
        // Signal requirements (balanced)
        config.setParameter("requireDivergence", false);     // Divergence OR imbalance (more signals)
        config.setParameter("imbalanceThreshold", 2.0);      // 2:1 buy/sell ratio
        config.setParameter("absorptionThreshold", 50.0);    // 50 absorption score
        config.setParameter("useVolumeConfirmation", true);  // Require volume confirmation
        config.setParameter("minVolumeMultiplier", 1.2);     // 120% of average volume
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure Order Flow Strategy #2 - Scalping
     * Timeframe: 1m, CVD Lookback: 10
     * Best for: Active scalpers, quick entries/exits on 1-minute timeframe
     */
    private void configureOrderFlowStrategy2() {
        OrderFlowStrategy strategy = applicationContext.getBean(OrderFlowStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "orderflow-scalping",
            List.of("BTCUSDT")
        );
        
        // Position sizing and risk parameters (smaller for scalping)
        config.setMaxPositionSize(new BigDecimal("1500")); // $1500 per position
        config.setStopLossPercentage(new BigDecimal("0.005")); // 0.5% stop loss (tight)
        config.setTakeProfitPercentage(new BigDecimal("0.01")); // 1% take profit (quick)
        
        // Timeframe & lookback parameters (short-term)
        config.setParameter("timeInterval", "1m");           // 1-minute candles
        config.setParameter("cvdLookback", 10);              // 10 candles (shorter)
        config.setParameter("divergenceLookback", 20);       // 20 candles
        config.setParameter("swingLookback", 3);             // 3 candles (faster swings)
        
        // Signal requirements (more lenient for more signals)
        config.setParameter("requireDivergence", false);     // Divergence optional
        config.setParameter("imbalanceThreshold", 1.8);      // 1.8:1 ratio (more sensitive)
        config.setParameter("absorptionThreshold", 45.0);    // Lower threshold
        config.setParameter("useVolumeConfirmation", true);
        config.setParameter("minVolumeMultiplier", 1.1);     // 110% of average (easier to trigger)
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
    }
    
    /**
     * Configure Order Flow Strategy #3 - Swing Trading
     * Timeframe: 15m, CVD Lookback: 40
     * Best for: Swing traders, fewer high-quality signals, longer holds
     */
    private void configureOrderFlowStrategy3() {
        OrderFlowStrategy strategy = applicationContext.getBean(OrderFlowStrategy.class);
        
        StrategyConfig config = new StrategyConfig(
            "orderflow-swing",
            List.of("BTCUSDT")
        );
        
        // Position sizing and risk parameters (larger for swing)
        config.setMaxPositionSize(new BigDecimal("5000")); // $5000 per position
        config.setStopLossPercentage(new BigDecimal("0.03")); // 3% stop loss (wider)
        config.setTakeProfitPercentage(new BigDecimal("0.10")); // 10% take profit (1:3.3 R:R)
        
        // Timeframe & lookback parameters (long-term)
        config.setParameter("timeInterval", "15m");          // 15-minute candles
        config.setParameter("cvdLookback", 40);              // 40 candles (longer trend)
        config.setParameter("divergenceLookback", 100);      // 100 candles (deeper history)
        config.setParameter("swingLookback", 7);             // 7 candles (stronger swings)
        
        // Signal requirements (strict for quality)
        config.setParameter("requireDivergence", true);      // MUST have divergence (high quality)
        config.setParameter("imbalanceThreshold", 2.5);      // 2.5:1 ratio (strong imbalance)
        config.setParameter("absorptionThreshold", 55.0);    // Higher threshold
        config.setParameter("useVolumeConfirmation", true);
        config.setParameter("minVolumeMultiplier", 1.3);     // 130% of average (strong volume)
        
        strategy.initialize(config);
        tradingBot.addStrategy(strategy);
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
