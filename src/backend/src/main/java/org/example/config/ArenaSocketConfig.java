package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ArenaSocketConfig implements WebSocketConfigurer {

    private final ArenaSocketHandler handler;

    public ArenaSocketConfig(ArenaSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/arena").setAllowedOriginPatterns(WebConfig.ORIGINS);
    }
}
