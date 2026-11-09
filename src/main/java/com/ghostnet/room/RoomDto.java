package com.ghostnet.room;

import java.time.Instant;

/**
 * Public Room DTO. Never leaks password hashes (Req 151-163).
 */
public record RoomDto(
        String id,
        String code,
        String name,
        String description,
        boolean isPrivate,
        boolean hasPassword,
        int capacity,
        int memberCount,
        String ownerId,
        boolean isMember,
        boolean isOwner,
        Instant createdAt
) {
    public static RoomDto from(RoomRecord room, int memberCount, String currentUserId) {
        boolean isMember = false;
        boolean isOwner = room.ownerId().equals(currentUserId);
        return new RoomDto(
                room.id(),
                room.code(),
                room.name(),
                room.description(),
                room.isPrivate(),
                room.passwordHash() != null && !room.passwordHash().isBlank(),
                room.capacity(),
                memberCount,
                room.ownerId(),
                isMember,
                isOwner,
                room.createdAt()
        );
    }

    public RoomDto withMembership(boolean member) {
        return new RoomDto(
                id, code, name, description, isPrivate, hasPassword, capacity, memberCount, ownerId, member, isOwner, createdAt
        );
    }
}
