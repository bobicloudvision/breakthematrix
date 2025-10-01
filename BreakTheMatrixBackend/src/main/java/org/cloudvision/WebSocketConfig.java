package org.cloudvision;

import org.cloudvision.trading.websocket.TradingWebSocketHandler;
import org.cloudvision.trading.bot.websocket.StrategyVisualizationWebSocketHandler;
import org.cloudvision.trading.bot.websocket.PositionsWebSocketHandler;
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
    private final PositionsWebSocketHandler positionsWebSocketHandler;

    public WebSocketConfig(EchoWebSocketHandler echoWebSocketHandler, 
                          TradingWebSocketHandler tradingWebSocketHandler,
                          StrategyVisualizationWebSocketHandler strategyVisualizationWebSocketHandler,
                          PositionsWebSocketHandler positionsWebSocketHandler) {
        this.echoWebSocketHandler = echoWebSocketHandler;
        this.tradingWebSocketHandler = tradingWebSocketHandler;
        this.strategyVisualizationWebSocketHandler = strategyVisualizationWebSocketHandler;
        this.positionsWebSocketHandler = positionsWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoWebSocketHandler, "/ws").setAllowedOrigins("*");
        registry.addHandler(tradingWebSocketHandler, "/trading-ws").setAllowedOrigins("*");
        registry.addHandler(strategyVisualizationWebSocketHandler, "/strategy-viz-ws").setAllowedOrigins("*");
        registry.addHandler(positionsWebSocketHandler, "/positions-ws").setAllowedOrigins("*");
    }
}


