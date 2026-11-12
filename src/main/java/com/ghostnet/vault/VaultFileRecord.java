package com.ghostnet.vault;

import java.time.Instant;

/**
 * Domain record representing an encrypted vault file on disk.
 */
public record VaultFileRecord(
        String id,
        String userId,
        String folderId,
        String filename,
        String storedFilename,
        String storagePath,
        String mimeType,
        long fileSize,
        long encryptedSize,
        boolean isClientEncrypted,
        String encryptionIv,
        String encryptionTag,
        String checksumSha256,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAccessedAt
) {}
