package com.ghostnet.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestRestoreRequest(
        @NotBlank(message = "Device credential is required")
        @Size(min = 16, max = 100, message = "Invalid device credential format")
        String deviceCredential
) {}
