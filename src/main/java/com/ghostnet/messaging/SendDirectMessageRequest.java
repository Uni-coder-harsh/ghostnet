package com.ghostnet.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for sending a 1-to-1 direct message (Req 131, 207).
 */
public record SendDirectMessageRequest(
        @NotBlank(message = "Recipient user ID is required")
        String recipientId,

        @NotBlank(message = "Message content cannot be blank")
        @Size(max = 4096, message = "Message content cannot exceed 4096 bytes")
        String content,

        String clientMessageId,

        String attachmentId
) {
    public SendDirectMessageRequest(String recipientId, String content, String clientMessageId) {
        this(recipientId, content, clientMessageId, null);
    }
}
