package com.ghostnet.vault;

import java.time.Instant;

public record VaultFolderDto(
        String id,
        String parentId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    public static VaultFolderDto fromRecord(VaultFolderRecord record) {
        if (record == null) {
            return null;
        }
        return new VaultFolderDto(
                record.id(),
                record.parentId(),
                record.name(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
