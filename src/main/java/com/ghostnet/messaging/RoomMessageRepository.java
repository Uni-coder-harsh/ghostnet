package com.ghostnet.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service contract for Room Messages persistence.
 */
public interface RoomMessageRepository {
    void save(RoomMessageRecord message);
    Optional<RoomMessageRecord> findById(String id);
    List<RoomMessageRecord> findHistoryByRoomId(String roomId, int limit, int offset);
    long countTotal();
    long countByRoomId(String roomId);
    int deleteOlderThan(Instant cutoff);
    void deleteByRoomId(String roomId);
}
