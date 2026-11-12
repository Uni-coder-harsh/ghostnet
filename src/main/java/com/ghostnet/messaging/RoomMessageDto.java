package com.ghostnet.messaging;

import com.ghostnet.files.AttachmentDto;

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
        AttachmentDto attachment,
        Instant createdAt
) {
    public RoomMessageDto(String id, String roomId, String senderId, String senderDisplayName,
                          String content, Instant createdAt) {
        this(id, roomId, senderId, senderDisplayName, content, null, createdAt);
    }

    public static RoomMessageDto from(RoomMessageRecord record) {
        return new RoomMessageDto(
                record.id(),
                record.roomId(),
                record.senderId(),
                record.senderDisplayName(),
                record.content(),
                null,
                record.createdAt()
        );
    }

    public RoomMessageDto withAttachment(AttachmentDto attachment) {
        return new RoomMessageDto(id, roomId, senderId, senderDisplayName, content, attachment, createdAt);
    }
}
