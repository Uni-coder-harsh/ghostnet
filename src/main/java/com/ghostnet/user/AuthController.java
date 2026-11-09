package com.ghostnet.user;

import com.ghostnet.security.RequireAuth;
import com.ghostnet.security.SecurityContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST controller for registration, login, logout and profile check.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    @Value("${ghostnet.security.cookie-name:GHOSTNET_SESSION}")
    private String cookieName;

    @Value("${ghostnet.security.session-duration-hours:72}")
    private int sessionDurationHours;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = userService.register(request, clientIp, userAgent);
        setSessionCookie(httpResponse, response.token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = userService.login(request, clientIp, userAgent);
        setSessionCookie(httpResponse, response.token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        SecurityContext.getCurrentSession().ifPresent(s -> userService.logout(s.token()));
        clearSessionCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @RequireAuth
    public ResponseEntity<UserDto> getCurrentUser() {
        UserRecord current = SecurityContext.requireUser();
        return ResponseEntity.ok(userService.getUserDto(current.id()));
    }

    private void setSessionCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(sessionDurationHours * 3600);
        // Note: SameSite is set in headers or standard servlet cookie
        response.addCookie(cookie);
    }

    private void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
