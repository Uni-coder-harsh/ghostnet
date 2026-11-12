package com.ghostnet.user;

import java.time.Instant;

/**
 * Public User Data Transfer Object. Never leaks password hashes (Req 122).
 */
public record UserDto(
        String id,
        String username,
        String displayName,
        UserRole role,
        String bio,
        boolean online,
        Instant createdAt
) {
    public boolean isGuest() {
        return role == UserRole.ROLE_GUEST;
    }

    public static UserDto from(UserRecord user, boolean online) {
        return new UserDto(
                user.id(),
                user.username(),
                user.displayName(),
                user.role(),
                user.bio(),
                online,
                user.createdAt()
        );
    }
}
