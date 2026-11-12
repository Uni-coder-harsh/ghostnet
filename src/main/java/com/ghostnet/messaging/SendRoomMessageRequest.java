package com.ghostnet.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for sending a room broadcast message (Req 164, 207).
 */
public record SendRoomMessageRequest(
        @NotBlank(message = "Room ID is required")
        String roomId,

        @NotBlank(message = "Message content cannot be blank")
        @Size(max = 4096, message = "Message content cannot exceed 4096 bytes")
        String content,

        String clientMessageId,

        String attachmentId
) {
    public SendRoomMessageRequest(String roomId, String content, String clientMessageId) {
        this(roomId, content, clientMessageId, null);
    }
}
