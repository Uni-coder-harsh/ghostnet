package com.ghostnet.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload (Req 115).
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {
}
