package com.ghostnet.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a new room (Req 151, 155-157, 167).
 */
public record CreateRoomRequest(
        @NotBlank(message = "Room name is required")
        @Size(min = 2, max = 100, message = "Room name must be between 2 and 100 characters")
        String name,

        @Size(max = 500, message = "Room description cannot exceed 500 characters")
        String description,

        boolean isPrivate,

        @Size(max = 100, message = "Password cannot exceed 100 characters")
        String password,

        @Min(value = 2, message = "Capacity must be at least 2")
        @Max(value = 500, message = "Capacity cannot exceed 500")
        int capacity
) {
    public CreateRoomRequest {
        if (capacity <= 0) capacity = 50;
    }
}
