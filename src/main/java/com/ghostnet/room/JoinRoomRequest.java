package com.ghostnet.room;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for joining a room by code (Req 154, 157).
 */
public record JoinRoomRequest(
        @NotBlank(message = "Room code is required")
        String code,

        String password
) {
}
