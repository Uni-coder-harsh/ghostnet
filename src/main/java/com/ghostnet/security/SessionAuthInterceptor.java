package com.ghostnet.security;

import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.SessionRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * HTTP HandlerInterceptor enforcing session authentication and role-based authorization (Req 124, 127, 204, 205).
 */
@Component
public class SessionAuthInterceptor implements HandlerInterceptor {

    @Value("${ghostnet.security.cookie-name:GHOSTNET_SESSION}")
    private String cookieName;

    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;

    public SessionAuthInterceptor(AuthSessionService authSessionService, UserRepository userRepository) {
        this.authSessionService = authSessionService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = extractToken(request);
        UserRecord user = null;
        SessionRecord session = null;

        if (token != null) {
            try {
                session = authSessionService.validateSession(token);
                Optional<UserRecord> userOpt = userRepository.findById(session.userId());
                if (userOpt.isPresent()) {
                    user = userOpt.get();
                    SecurityContext.set(user, session);
                }
            } catch (Exception e) {
                // Token invalid or expired
                SecurityContext.clear();
            }
        }

        boolean requireAuth = handlerMethod.hasMethodAnnotation(RequireAuth.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAuth.class);
        boolean requireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);

        if (requireAdmin) {
            if (user == null) {
                throw new AuthenticationException("Administrator authentication required");
            }
            if (user.role() != UserRole.ROLE_ADMIN) {
                throw new UnauthorizedException("Administrative privileges required");
            }
            return true;
        }

        if (requireAuth) {
            if (user == null) {
                throw new AuthenticationException("Authentication required for this resource");
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContext.clear();
    }

    public String extractToken(HttpServletRequest request) {
        // 1. Check Authorization header: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. Check cookies
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue().trim();
                }
            }
        }

        // 3. Check query param (for WebSocket handshake or direct links)
        String param = request.getParameter("token");
        if (param != null && !param.isBlank()) {
            return param.trim();
        }

        return null;
    }
}
