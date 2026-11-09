package com.ghostnet.messaging;

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
        Instant createdAt
) {
    public static DirectMessageDto from(DirectMessageRecord record, String senderDisplayName) {
        return new DirectMessageDto(
                record.id(),
                record.senderId(),
                senderDisplayName,
                record.recipientId(),
                record.content(),
                record.status(),
                record.createdAt()
        );
    }
}
