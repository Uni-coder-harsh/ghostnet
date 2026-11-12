package com.ghostnet.vault;

import jakarta.validation.constraints.NotBlank;

public record InitVaultRequest(
        @NotBlank(message = "Salt is required")
        String salt,

        @NotBlank(message = "Auth verifier is required")
        String authVerifier
) {}
