package com.ghostnet.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuestJoinRequest(
        @NotBlank(message = "Guest display name cannot be blank")
        @Size(min = 2, max = 50, message = "Guest display name must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_\\-\\s.]+$", message = "Guest name contains invalid characters")
        String displayName
) {}
