package com.ghostnet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostnet.common.ErrorResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that enforces rate limiting on API requests (Req 209).
 */
@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(TokenBucketRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Only rate limit API requests, bypass static web assets
        if (path.startsWith("/api/")) {
            String clientIp = getClientIp(httpRequest);
            if (!rateLimiter.tryConsume(clientIp)) {
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                ErrorResponse errorResponse = new ErrorResponse(
                        429,
                        "Too Many Requests",
                        "Rate limit exceeded. Please slow down your requests."
                );
                httpResponse.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
