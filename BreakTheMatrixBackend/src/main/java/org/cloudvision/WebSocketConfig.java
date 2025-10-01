package org.cloudvision;

import org.cloudvision.trading.websocket.TradingWebSocketHandler;
import org.cloudvision.trading.bot.websocket.StrategyVisualizationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EchoWebSocketHandler echoWebSocketHandler;
    private final TradingWebSocketHandler tradingWebSocketHandler;
    private final StrategyVisualizationWebSocketHandler strategyVisualizationWebSocketHandler;

    public WebSocketConfig(EchoWebSocketHandler echoWebSocketHandler, 
                          TradingWebSocketHandler tradingWebSocketHandler,
                          StrategyVisualizationWebSocketHandler strategyVisualizationWebSocketHandler) {
        this.echoWebSocketHandler = echoWebSocketHandler;
        this.tradingWebSocketHandler = tradingWebSocketHandler;
        this.strategyVisualizationWebSocketHandler = strategyVisualizationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoWebSocketHandler, "/ws").setAllowedOrigins("*");
        registry.addHandler(tradingWebSocketHandler, "/trading-ws").setAllowedOrigins("*");
        registry.addHandler(strategyVisualizationWebSocketHandler, "/strategy-viz-ws").setAllowedOrigins("*");
    }
}


