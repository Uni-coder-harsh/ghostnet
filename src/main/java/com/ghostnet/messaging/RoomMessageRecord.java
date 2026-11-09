package com.ghostnet.messaging;

import java.time.Instant;

/**
 * Immutable domain record representing a message posted inside a communication room.
 */
public record RoomMessageRecord(
        String id,
        String roomId,
        String senderId,
        String senderDisplayName,
        String content,
        Instant createdAt
) {
    public RoomMessageRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Message ID cannot be empty");
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("Room ID cannot be empty");
        if (senderId == null || senderId.isBlank()) throw new IllegalArgumentException("Sender ID cannot be empty");
        if (content == null) content = "";
        if (senderDisplayName == null || senderDisplayName.isBlank()) senderDisplayName = "Unknown";
        if (createdAt == null) createdAt = Instant.now();
    }
}
