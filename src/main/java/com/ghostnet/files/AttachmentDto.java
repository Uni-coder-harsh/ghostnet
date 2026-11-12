package com.ghostnet.files;

import java.time.Instant;

/**
 * Public DTO for conversation attachments.
 */
public record AttachmentDto(
        String id,
        String messageId,
        String uploaderId,
        String conversationType,
        String conversationId,
        String originalFilename,
        String mimeType,
        long fileSize,
        String checksumSha256,
        String downloadUrl,
        String previewUrl,
        boolean isGuestUpload,
        boolean isExpired,
        Instant expiresAt,
        int downloadCount,
        Instant createdAt
) {
    public static AttachmentDto from(ChatAttachmentRecord record) {
        boolean isImgOrMedia = record.mimeType().startsWith("image/")
                || record.mimeType().startsWith("audio/")
                || record.mimeType().startsWith("video/")
                || record.mimeType().equals("text/plain");

        String previewUrl = isImgOrMedia ? "/api/files/" + record.id() + "/preview" : null;

        return new AttachmentDto(
                record.id(),
                record.messageId(),
                record.uploaderId(),
                record.conversationType(),
                record.conversationId(),
                record.originalFilename(),
                record.mimeType(),
                record.fileSize(),
                record.checksumSha256(),
                "/api/files/" + record.id() + "/download",
                previewUrl,
                record.isGuestUpload(),
                record.isExpired(),
                record.expiresAt(),
                record.downloadCount(),
                record.createdAt()
        );
    }
}
