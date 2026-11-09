package com.ghostnet.config;

import com.ghostnet.websocket.GhostNetHandshakeInterceptor;
import com.ghostnet.websocket.GhostNetWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration binding GhostNet real-time handlers to /ws (Req 041-044).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GhostNetWebSocketHandler webSocketHandler;
    private final GhostNetHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(GhostNetWebSocketHandler webSocketHandler,
                           GhostNetHandshakeInterceptor handshakeInterceptor) {
        this.webSocketHandler = webSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*"); // Permissive for dynamic hotspot IPv4 addresses
    }
}
