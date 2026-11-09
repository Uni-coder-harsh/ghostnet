package com.ghostnet.room;

import java.time.Instant;

/**
 * Immutable domain record representing membership of a user in a room.
 */
public record RoomMemberRecord(
        String roomId,
        String userId,
        RoomMemberRole role,
        Instant joinedAt
) {
    public RoomMemberRecord {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("Room ID cannot be empty");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User ID cannot be empty");
        if (role == null) role = RoomMemberRole.MEMBER;
        if (joinedAt == null) joinedAt = Instant.now();
    }
}
