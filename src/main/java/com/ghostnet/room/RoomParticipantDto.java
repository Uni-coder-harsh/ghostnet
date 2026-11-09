package com.ghostnet.room;

import java.time.Instant;

/**
 * Public participant DTO with role and real-time online status (Req 162, 163).
 */
public record RoomParticipantDto(
        String userId,
        String username,
        String displayName,
        RoomMemberRole role,
        boolean online,
        Instant joinedAt
) {
}
