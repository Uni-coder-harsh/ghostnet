package com.ghostnet.user;

import jakarta.validation.constraints.Size;

/**
 * Payload for updating user profile display name or bio.
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = "Display name cannot exceed 100 characters")
        String displayName,

        @Size(max = 500, message = "Bio cannot exceed 500 characters")
        String bio
) {
}
