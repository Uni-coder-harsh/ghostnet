package com.ghostnet.messaging;

import java.time.Instant;

/**
 * Public Room Message DTO (Req 164, 166).
 */
public record RoomMessageDto(
        String id,
        String roomId,
        String senderId,
        String senderDisplayName,
        String content,
        Instant createdAt
) {
    public static RoomMessageDto from(RoomMessageRecord record) {
        return new RoomMessageDto(
                record.id(),
                record.roomId(),
                record.senderId(),
                record.senderDisplayName(),
                record.content(),
                record.createdAt()
        );
    }
}
