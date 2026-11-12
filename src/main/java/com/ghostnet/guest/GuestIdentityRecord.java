package com.ghostnet.guest;

import java.time.Instant;

/**
 * Immutable domain record representing an ephemeral guest identity (Req Section 3.B).
 * Recognized across reconnections using an optional browser-stored device credential.
 */
public record GuestIdentityRecord(
        String id,
        String deviceCredential,
        String displayName,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean isActive
) {
    public GuestIdentityRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Guest ID cannot be empty");
        if (deviceCredential == null || deviceCredential.isBlank()) throw new IllegalArgumentException("Device credential cannot be empty");
        if (displayName == null || displayName.isBlank()) displayName = "Guest Operative";
        if (createdAt == null) createdAt = Instant.now();
        if (lastSeenAt == null) lastSeenAt = createdAt;
        if (expiresAt == null) expiresAt = createdAt.plusSeconds(86400); // 24 hours default
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
