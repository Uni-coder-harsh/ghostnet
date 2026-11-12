package com.ghostnet.vault;

import java.time.Instant;

/**
 * Domain record representing zero-knowledge vault metadata and quota (Req Section 5).
 */
public record VaultSettingsRecord(
        String userId,
        boolean isInitialized,
        String salt,
        String authVerifier,
        long quotaBytes,
        long usedBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean hasSpace(long additionalBytes) {
        return (usedBytes + additionalBytes) <= quotaBytes;
    }
}
