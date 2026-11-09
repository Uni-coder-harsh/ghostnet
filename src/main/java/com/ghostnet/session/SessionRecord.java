package com.ghostnet.session;

import java.time.Instant;

/**
 * Immutable domain record representing an authenticated user session.
 */
public record SessionRecord(
        String token,
        String userId,
        Instant expiresAt,
        Instant createdAt,
        String clientIp,
        String userAgent
) {
    public SessionRecord {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Session token cannot be empty");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User ID cannot be empty");
        if (expiresAt == null) throw new IllegalArgumentException("Expires at cannot be null");
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
