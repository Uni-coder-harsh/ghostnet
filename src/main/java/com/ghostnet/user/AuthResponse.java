package com.ghostnet.user;

/**
 * Authentication response containing the active session token and user profile.
 */
public record AuthResponse(
        String token,
        UserDto user
) {
}
