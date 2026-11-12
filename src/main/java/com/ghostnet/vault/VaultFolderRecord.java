package com.ghostnet.vault;

import java.time.Instant;

/**
 * Domain record representing an encrypted vault folder.
 */
public record VaultFolderRecord(
        String id,
        String userId,
        String parentId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {}
