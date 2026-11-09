package com.ghostnet.room;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for transferring room ownership to another participant (Req 170).
 */
public record TransferOwnershipRequest(
        @NotBlank(message = "New owner user ID is required")
        String newOwnerUserId
) {
}
