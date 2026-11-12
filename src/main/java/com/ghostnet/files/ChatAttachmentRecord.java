package com.ghostnet.files;

import java.time.Instant;

/**
 * Immutable domain record representing a conversation file attachment (Req Section 4).
 * Supports both persistent registered attachments and ephemeral guest attachments.
 */
public record ChatAttachmentRecord(
        String id,
        String messageId,
        String uploaderId,
        String uploaderRole,
        String conversationType, // 'ROOM' or 'DIRECT'
        String conversationId,   // roomId or targetUserId
        String originalFilename,
        String storedFilename,
        String storagePath,
        String mimeType,
        long fileSize,
        String checksumSha256,
        Instant createdAt,
        Instant expiresAt,
        int downloadCount
) {
    public ChatAttachmentRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Attachment ID cannot be empty");
        if (uploaderId == null || uploaderId.isBlank()) throw new IllegalArgumentException("Uploader ID cannot be empty");
        if (originalFilename == null || originalFilename.isBlank()) originalFilename = "attachment.dat";
        if (storedFilename == null || storedFilename.isBlank()) throw new IllegalArgumentException("Stored filename cannot be empty");
        if (storagePath == null || storagePath.isBlank()) throw new IllegalArgumentException("Storage path cannot be empty");
        if (mimeType == null || mimeType.isBlank()) mimeType = "application/octet-stream";
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isGuestUpload() {
        return "ROLE_GUEST".equalsIgnoreCase(uploaderRole) || expiresAt != null;
    }
}
