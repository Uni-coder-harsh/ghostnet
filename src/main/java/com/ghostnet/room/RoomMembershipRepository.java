package com.ghostnet.room;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for Room Membership persistence.
 */
public interface RoomMembershipRepository {
    void addMember(RoomMemberRecord member);
    void removeMember(String roomId, String userId);
    List<RoomMemberRecord> findByRoomId(String roomId);
    List<RoomMemberRecord> findByUserId(String userId);
    Optional<RoomMemberRecord> findMembership(String roomId, String userId);
    int countMembers(String roomId);
    boolean isMember(String roomId, String userId);
    void updateRole(String roomId, String userId, RoomMemberRole role);
    void deleteByRoomId(String roomId);
}
