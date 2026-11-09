package com.ghostnet.websocket;

import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.SessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;

/**
 * WebSocket handshake interceptor that authenticates connections before establishing (Req 044, 210).
 */
@Component
public class GhostNetHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GhostNetHandshakeInterceptor.class);

    @Value("${ghostnet.security.cookie-name:GHOSTNET_SESSION}")
    private String cookieName;

    private final AuthSessionService authSessionService;

    public GhostNetHandshakeInterceptor(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            // 1. Check cookies
            Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                        token = c.getValue().trim();
                        break;
                    }
                }
            }
        }

        // 2. Fallback to query parameter ?token=...
        if (token == null) {
            URI uri = request.getURI();
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                        token = pair[1].trim();
                        break;
                    }
                }
            }
        }

        if (token == null) {
            log.warn("WebSocket handshake rejected: missing authentication session token from {}", request.getRemoteAddress());
            return false;
        }

        try {
            SessionRecord session = authSessionService.validateSession(token);
            attributes.put("userId", session.userId());
            attributes.put("sessionToken", session.token());
            log.debug("WebSocket handshake authenticated for user '{}'", session.userId());
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: invalid session from {}: {}", request.getRemoteAddress(), e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }
}
