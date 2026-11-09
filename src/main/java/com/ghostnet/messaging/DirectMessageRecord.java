package com.ghostnet.messaging;

import java.time.Instant;

/**
 * Immutable domain record representing a 1-to-1 direct message.
 */
public record DirectMessageRecord(
        String id,
        String senderId,
        String recipientId,
        String content,
        MessageStatus status,
        Instant createdAt
) {
    public DirectMessageRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Message ID cannot be empty");
        if (senderId == null || senderId.isBlank()) throw new IllegalArgumentException("Sender ID cannot be empty");
        if (recipientId == null || recipientId.isBlank()) throw new IllegalArgumentException("Recipient ID cannot be empty");
        if (content == null) content = "";
        if (status == null) status = MessageStatus.SENT;
        if (createdAt == null) createdAt = Instant.now();
    }
}
