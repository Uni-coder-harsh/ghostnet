package com.ghostnet.room;

import com.ghostnet.common.ResourceNotFoundException;
import com.ghostnet.common.ValidationException;
import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.PasswordService;
import com.ghostnet.security.TokenGenerator;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.session.PresenceService;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing room lifecycles, joining, leaving, capacity, owner moderation,
 * and authorization (Req 151-175).
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final TokenGenerator tokenGenerator;
    private final PresenceService presenceService;

    public RoomService(RoomRepository roomRepository,
                       RoomMembershipRepository roomMembershipRepository,
                       UserRepository userRepository,
                       PasswordService passwordService,
                       TokenGenerator tokenGenerator,
                       PresenceService presenceService) {
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.tokenGenerator = tokenGenerator;
        this.presenceService = presenceService;
    }

    /**
     * Creates a new communication room and registers the creator as OWNER (Req 151-153, 158).
     */
    @Transactional
    public RoomDto createRoom(String creatorUserId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();

        // Generate unique human-readable room code
        String code;
        int attempts = 0;
        do {
            code = tokenGenerator.generateRoomCode();
            attempts++;
            if (attempts > 20) {
                code = "GN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            }
        } while (roomRepository.existsByCode(code));

        String passwordHash = null;
        if (request.isPrivate() && request.password() != null && !request.password().isBlank()) {
            passwordHash = passwordService.hashPassword(request.password());
        }

        int capacity = request.capacity() > 0 ? request.capacity() : 50;

        RoomRecord room = new RoomRecord(
                roomId,
                code,
                request.name().trim(),
                request.description() != null ? request.description().trim() : "",
                request.isPrivate(),
                passwordHash,
                capacity,
                creatorUserId,
                Instant.now()
        );

        roomRepository.save(room);

        // Add creator as OWNER
        RoomMemberRecord ownerMember = new RoomMemberRecord(
                roomId,
                creatorUserId,
                RoomMemberRole.OWNER,
                Instant.now()
        );
        roomMembershipRepository.addMember(ownerMember);

        log.info("Created room '{}' ({}) by user '{}'", room.name(), room.code(), creatorUserId);
        return RoomDto.from(room, 1, creatorUserId).withMembership(true);
    }

    /**
     * Joins a room by room code (Req 154, 157, 167, 173-175).
     */
    @Transactional
    public RoomDto joinRoomByCode(String userId, JoinRoomRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        RoomRecord room = roomRepository.findByCode(cleanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or non-existent room code: " + cleanCode));

        // Check if already a member
        if (roomMembershipRepository.isMember(room.id(), userId)) {
            int memberCount = roomMembershipRepository.countMembers(room.id());
            return RoomDto.from(room, memberCount, userId).withMembership(true);
        }

        // Check capacity
        int currentCount = roomMembershipRepository.countMembers(room.id());
        if (currentCount >= room.capacity()) {
            throw new ValidationException("Room '" + room.name() + "' has reached its maximum capacity of " + room.capacity());
        }

        // Verify password if protected
        if (room.isPrivate() && room.passwordHash() != null) {
            if (request.password() == null || !passwordService.matches(request.password(), room.passwordHash())) {
                throw new AuthenticationException("Incorrect room password for room " + room.name());
            }
        }

        // Add user as MEMBER
        RoomMemberRecord member = new RoomMemberRecord(
                room.id(),
                userId,
                RoomMemberRole.MEMBER,
                Instant.now()
        );
        roomMembershipRepository.addMember(member);

        int updatedCount = currentCount + 1;
        log.info("User '{}' joined room '{}' ({})", userId, room.name(), room.code());
        return RoomDto.from(room, updatedCount, userId).withMembership(true);
    }

    /**
     * Allows a member to leave a room (Req 161).
     */
    @Transactional
    public void leaveRoom(String userId, String roomId) {
        RoomRecord room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if (room.ownerId().equals(userId)) {
            // Check if there are other members to transfer to or if room should be deleted
            List<RoomMemberRecord> members = roomMembershipRepository.findByRoomId(roomId);
            Optional<RoomMemberRecord> nextMember = members.stream()
                    .filter(m -> !m.userId().equals(userId))
                    .findFirst();

            if (nextMember.isPresent()) {
                // Auto-promote next member to OWNER
                transferOwnershipInternal(roomId, nextMember.get().userId());
            } else {
                // Last person left, delete room
                deleteRoomInternal(roomId);
                return;
            }
        }

        roomMembershipRepository.removeMember(roomId, userId);
        log.info("User '{}' left room '{}'", userId, room.name());
    }

    /**
     * Kicks a participant from a room (Req 159, 258).
     * Must be called by room OWNER, MODERATOR, or host system admin.
     */
    @Transactional
    public void kickMember(String requestingUserId, String roomId, String targetUserId) {
        RoomRecord room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        UserRecord requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

        boolean isHostAdmin = requester.role() == UserRole.ROLE_ADMIN;
        boolean isRoomOwner = room.ownerId().equals(requestingUserId);

        Optional<RoomMemberRecord> requesterMember = roomMembershipRepository.findMembership(roomId, requestingUserId);
        boolean isModerator = requesterMember.isPresent() && requesterMember.get().role() == RoomMemberRole.MODERATOR;

        if (!isHostAdmin && !isRoomOwner && !isModerator) {
            throw new UnauthorizedException("Only room owners, moderators, or host administrators can remove participants");
        }

        if (room.ownerId().equals(targetUserId)) {
            throw new ValidationException("Cannot remove the room owner");
        }

        roomMembershipRepository.removeMember(roomId, targetUserId);
        log.info("User '{}' was removed from room '{}' by '{}'", targetUserId, room.name(), requestingUserId);
    }

    /**
     * Transfers room ownership to another participant (Req 170).
     */
    @Transactional
    public void transferOwnership(String requestingUserId, String roomId, String newOwnerUserId) {
        RoomRecord room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        if (!room.ownerId().equals(requestingUserId)) {
            throw new UnauthorizedException("Only the room owner can transfer ownership");
        }

        if (!roomMembershipRepository.isMember(roomId, newOwnerUserId)) {
            throw new ValidationException("Target user must be a member of the room to become owner");
        }

        transferOwnershipInternal(roomId, newOwnerUserId);
    }

    private void transferOwnershipInternal(String roomId, String newOwnerUserId) {
        RoomRecord room = roomRepository.findById(roomId).orElseThrow();
        RoomRecord updatedRoom = new RoomRecord(
                room.id(),
                room.code(),
                room.name(),
                room.description(),
                room.isPrivate(),
                room.passwordHash(),
                room.capacity(),
                newOwnerUserId,
                room.createdAt()
        );
        roomRepository.update(updatedRoom);

        // Update membership roles
        roomMembershipRepository.updateRole(roomId, room.ownerId(), RoomMemberRole.MEMBER);
        roomMembershipRepository.updateRole(roomId, newOwnerUserId, RoomMemberRole.OWNER);
        log.info("Transferred ownership of room '{}' to user '{}'", room.name(), newOwnerUserId);
    }

    /**
     * Deletes a room and cleans up members and message history (Req 160).
     */
    @Transactional
    public void deleteRoom(String requestingUserId, String roomId) {
        RoomRecord room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        UserRecord requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isHostAdmin = requester.role() == UserRole.ROLE_ADMIN;
        boolean isRoomOwner = room.ownerId().equals(requestingUserId);

        if (!isHostAdmin && !isRoomOwner) {
            throw new UnauthorizedException("Only the room owner or host administrator can delete this room");
        }

        deleteRoomInternal(roomId);
    }

    private void deleteRoomInternal(String roomId) {
        roomMembershipRepository.deleteByRoomId(roomId);
        roomRepository.deleteById(roomId);
        log.info("Deleted room '{}'", roomId);
    }

    /**
     * Lists public rooms (Req 155).
     */
    public List<RoomDto> getPublicRooms(String currentUserId) {
        List<RoomRecord> rooms = roomRepository.findPublicRooms();
        List<RoomDto> dtos = new ArrayList<>();
        for (RoomRecord r : rooms) {
            int memberCount = roomMembershipRepository.countMembers(r.id());
            boolean isMember = roomMembershipRepository.isMember(r.id(), currentUserId);
            dtos.add(RoomDto.from(r, memberCount, currentUserId).withMembership(isMember));
        }
        return dtos;
    }

    /**
     * Lists all rooms the current user is a member of.
     */
    public List<RoomDto> getMyRooms(String currentUserId) {
        List<RoomMemberRecord> memberships = roomMembershipRepository.findByUserId(currentUserId);
        List<RoomDto> dtos = new ArrayList<>();
        for (RoomMemberRecord m : memberships) {
            roomRepository.findById(m.roomId()).ifPresent(r -> {
                int memberCount = roomMembershipRepository.countMembers(r.id());
                dtos.add(RoomDto.from(r, memberCount, currentUserId).withMembership(true));
            });
        }
        return dtos;
    }

    /**
     * Retrieves room details by ID, verifying access if private (Req 169, 171).
     */
    public RoomDto getRoomDetails(String currentUserId, String roomId) {
        RoomRecord room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        boolean isMember = roomMembershipRepository.isMember(roomId, currentUserId);
        if (room.isPrivate() && !isMember) {
            throw new UnauthorizedException("Access denied: Private room requires membership");
        }

        int memberCount = roomMembershipRepository.countMembers(roomId);
        return RoomDto.from(room, memberCount, currentUserId).withMembership(isMember);
    }

    /**
     * Returns participant list with online presence indicators (Req 162, 163).
     */
    public List<RoomParticipantDto> getRoomParticipants(String currentUserId, String roomId) {
        verifyMembership(currentUserId, roomId);

        List<RoomMemberRecord> members = roomMembershipRepository.findByRoomId(roomId);
        List<RoomParticipantDto> result = new ArrayList<>();
        for (RoomMemberRecord m : members) {
            userRepository.findById(m.userId()).ifPresent(u -> {
                boolean isOnline = presenceService.isOnline(u.id());
                result.add(new RoomParticipantDto(
                        u.id(),
                        u.username(),
                        u.displayName(),
                        m.role(),
                        isOnline,
                        m.joinedAt()
                ));
            });
        }
        return result;
    }

    /**
     * Verifies that the specified user is an active member of the room (Req 165, 171).
     */
    public void verifyMembership(String userId, String roomId) {
        if (!roomMembershipRepository.isMember(roomId, userId)) {
            throw new UnauthorizedException("User is not a participant of room " + roomId);
        }
    }
}
