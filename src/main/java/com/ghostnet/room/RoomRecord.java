package com.ghostnet.room;

import java.time.Instant;

/**
 * Immutable domain record representing a communication room.
 */
public record RoomRecord(
        String id,
        String code,
        String name,
        String description,
        boolean isPrivate,
        String passwordHash,
        int capacity,
        String ownerId,
        Instant createdAt
) {
    public RoomRecord {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Room ID cannot be empty");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Room code cannot be empty");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Room name cannot be empty");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Room owner ID cannot be empty");
        if (capacity <= 0) capacity = 50;
        if (createdAt == null) createdAt = Instant.now();
    }
}
