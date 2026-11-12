package com.ghostnet.vault;

import java.time.Instant;

public record VaultFileDto(
        String id,
        String folderId,
        String filename,
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
) {
    public static VaultFileDto fromRecord(VaultFileRecord record) {
        if (record == null) {
            return null;
        }
        return new VaultFileDto(
                record.id(),
                record.folderId(),
                record.filename(),
                record.mimeType(),
                record.fileSize(),
                record.encryptedSize(),
                record.isClientEncrypted(),
                record.encryptionIv(),
                record.encryptionTag(),
                record.checksumSha256(),
                record.createdAt(),
                record.updatedAt(),
                record.lastAccessedAt()
        );
    }
}
