package com.ghostnet.room;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for Room metadata persistence.
 */
public interface RoomRepository {
    void save(RoomRecord room);
    void update(RoomRecord room);
    Optional<RoomRecord> findById(String id);
    Optional<RoomRecord> findByCode(String code);
    List<RoomRecord> findAll();
    List<RoomRecord> findPublicRooms();
    List<RoomRecord> findByOwnerId(String ownerId);
    boolean existsByCode(String code);
    long count();
    void deleteById(String id);
}
