package com.chengjing.platform.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class LiveWebSocketConfig implements WebSocketConfigurer {
    private final LiveSocketHandler handler;
    private final String origins;

    public LiveWebSocketConfig(LiveSocketHandler handler,
            @Value("${chengjing.live.allowed-origins:http://127.0.0.1:5173,http://localhost:5173}") String origins) {
        this.handler = handler; this.origins = origins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/platform/live").setAllowedOriginPatterns(origins.split(","));
    }
}
