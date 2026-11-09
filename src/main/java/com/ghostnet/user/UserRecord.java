package com.ghostnet.user;

import java.time.Instant;

/**
 * Immutable domain record representing a registered GhostNet user.
 */
public record UserRecord(
        String id,
        String username,
        String passwordHash,
        String displayName,
        UserRole role,
        String bio,
        Instant createdAt,
        Instant updatedAt
) {
    public UserRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("User ID cannot be empty");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username cannot be empty");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Password hash cannot be empty");
        if (displayName == null || displayName.isBlank()) displayName = username;
        if (role == null) role = UserRole.ROLE_USER;
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}
