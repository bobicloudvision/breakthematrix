package org.cloudvision;

import org.cloudvision.trading.websocket.TradingWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EchoWebSocketHandler echoWebSocketHandler;
    private final TradingWebSocketHandler tradingWebSocketHandler;

    public WebSocketConfig(EchoWebSocketHandler echoWebSocketHandler, 
                          TradingWebSocketHandler tradingWebSocketHandler) {
        this.echoWebSocketHandler = echoWebSocketHandler;
        this.tradingWebSocketHandler = tradingWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoWebSocketHandler, "/ws").setAllowedOrigins("*");
        registry.addHandler(tradingWebSocketHandler, "/trading-ws").setAllowedOrigins("*");
    }
}


