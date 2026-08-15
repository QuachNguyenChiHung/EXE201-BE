package com.ailogis.api.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/api/ws/notifications")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "https://www.logicha.io.vn",
                        "https://api.logicha.io.vn",
                        "http://localhost:8080",
                        "https://logicha.io.vn");
    }
}
