package com.ghostnet.messaging;

import com.ghostnet.files.AttachmentDto;

import java.time.Instant;

/**
 * Public Direct Message DTO (Req 135-139, 144, 145).
 */
public record DirectMessageDto(
        String id,
        String senderId,
        String senderDisplayName,
        String recipientId,
        String content,
        MessageStatus status,
        AttachmentDto attachment,
        Instant createdAt
) {
    public DirectMessageDto(String id, String senderId, String senderDisplayName, String recipientId,
                            String content, MessageStatus status, Instant createdAt) {
        this(id, senderId, senderDisplayName, recipientId, content, status, null, createdAt);
    }

    public static DirectMessageDto from(DirectMessageRecord record, String senderDisplayName) {
        return new DirectMessageDto(
                record.id(),
                record.senderId(),
                senderDisplayName,
                record.recipientId(),
                record.content(),
                record.status(),
                null,
                record.createdAt()
        );
    }

    public DirectMessageDto withAttachment(AttachmentDto attachment) {
        return new DirectMessageDto(id, senderId, senderDisplayName, recipientId, content, status, attachment, createdAt);
    }
}
