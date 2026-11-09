package com.ghostnet.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload with validation rules (Req 111, 112).
 */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$", message = "Username must be 3-30 characters (letters, numbers, underscores)")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @Size(max = 100, message = "Display name cannot exceed 100 characters")
        String displayName
) {
}
