package com.ghostnet.guest;

import com.ghostnet.room.RoomMemberRecord;
import com.ghostnet.room.RoomMemberRole;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.room.RoomRecord;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.TokenGenerator;
import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.SessionRecord;
import com.ghostnet.storage.StorageLayoutService;
import com.ghostnet.user.UserDto;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates guest onboarding, device credential restoration, and lifecycle enforcement (Req Section 3.B).
 */
@Service
public class GuestService {

    private static final Logger log = LoggerFactory.getLogger(GuestService.class);

    private final GuestRepository guestRepository;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final TokenGenerator tokenGenerator;
    private final StorageLayoutService storageLayoutService;

    public GuestService(GuestRepository guestRepository,
                        UserRepository userRepository,
                        AuthSessionService authSessionService,
                        RoomRepository roomRepository,
                        RoomMembershipRepository roomMembershipRepository,
                        TokenGenerator tokenGenerator,
                        StorageLayoutService storageLayoutService) {
        this.guestRepository = guestRepository;
        this.userRepository = userRepository;
        this.authSessionService = authSessionService;
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.tokenGenerator = tokenGenerator;
        this.storageLayoutService = storageLayoutService;
    }

    /**
     * Issues an ephemeral guest identity and persistent browser device credential.
     */
    @Transactional
    public GuestAuthResponse joinAsGuest(GuestJoinRequest request, String clientIp, String userAgent) {
        String guestId = "guest_" + UUID.randomUUID().toString();
        String deviceCredential = "gdev_" + tokenGenerator.generateSessionToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(storageLayoutService.getGuestExpirationHours()));

        String cleanDisplayName = request.displayName().trim();
        if (!cleanDisplayName.toLowerCase().endsWith("(guest)")) {
            cleanDisplayName = cleanDisplayName + " (Guest)";
        }

        // 1. Create Guest Identity Record
        GuestIdentityRecord guestRecord = new GuestIdentityRecord(
                guestId,
                deviceCredential,
                cleanDisplayName,
                now,
                now,
                expiresAt,
                true
        );
        guestRepository.save(guestRecord);

        // 2. Register backing UserRecord with ROLE_GUEST
        String guestUsername = "guest_" + UUID.randomUUID().toString().substring(0, 8);
        UserRecord userRecord = new UserRecord(
                guestId,
                guestUsername,
                "GUEST_NO_PASSWORD",
                cleanDisplayName,
                UserRole.ROLE_GUEST,
                "Ephemeral Guest Operative",
                now,
                now
        );
        userRepository.save(userRecord);

        // 3. Auto-enroll guest into default room # General Mesh
        autoEnrollInDefaultRoom(guestId);

        // 4. Issue authenticated session
        SessionRecord session = authSessionService.createSession(guestId, clientIp, userAgent);
        log.info("Guest operative joined: '{}' (ID: {}) from IP {}", cleanDisplayName, guestId, clientIp);

        return new GuestAuthResponse(
                session.token(),
                deviceCredential,
                UserDto.from(userRecord, true),
                expiresAt
        );
    }

    /**
     * Recognizes and restores returning guest identity via cryptographically random device credential.
     */
    @Transactional
    public GuestAuthResponse restoreGuest(GuestRestoreRequest request, String clientIp, String userAgent) {
        String cred = request.deviceCredential().trim();
        GuestIdentityRecord guest = guestRepository.findByDeviceCredential(cred)
                .orElseThrow(() -> new AuthenticationException("Unrecognized or expired guest device credential. Please join as a new guest."));

        if (!guest.isActive() || guest.isExpired()) {
            guestRepository.revoke(guest.id());
            throw new AuthenticationException("Guest session has expired or was revoked by host administrator. Please rejoin.");
        }

        Instant now = Instant.now();
        guestRepository.updateLastSeen(guest.id(), now);

        // Ensure backing UserRecord is present
        UserRecord userRecord = userRepository.findById(guest.id()).orElseGet(() -> {
            UserRecord reCreated = new UserRecord(
                    guest.id(),
                    "guest_" + UUID.randomUUID().toString().substring(0, 8),
                    "GUEST_NO_PASSWORD",
                    guest.displayName(),
                    UserRole.ROLE_GUEST,
                    "Ephemeral Guest Operative (Restored)",
                    guest.createdAt(),
                    now
            );
            userRepository.save(reCreated);
            return reCreated;
        });

        autoEnrollInDefaultRoom(guest.id());

        SessionRecord session = authSessionService.createSession(guest.id(), clientIp, userAgent);
        log.info("Returning guest restored: '{}' (ID: {}) from IP {}", guest.displayName(), guest.id(), clientIp);

        return new GuestAuthResponse(
                session.token(),
                guest.deviceCredential(),
                UserDto.from(userRecord, true),
                guest.expiresAt()
        );
    }

    /**
     * Allows a guest to reset / clear their local device identity.
     */
    @Transactional
    public void resetGuest(String deviceCredential) {
        guestRepository.findByDeviceCredential(deviceCredential).ifPresent(g -> {
            guestRepository.revoke(g.id());
            authSessionService.revokeAllUserSessions(g.id());
            userRepository.deleteById(g.id());
            log.info("Guest identity reset and revoked: {}", g.id());
        });
    }

    public List<GuestIdentityRecord> getActiveGuests() {
        return guestRepository.findActiveGuests();
    }

    @Transactional
    public void revokeGuestById(String guestId) {
        guestRepository.revoke(guestId);
        authSessionService.revokeAllUserSessions(guestId);
        userRepository.deleteById(guestId);
        log.info("Host admin revoked guest: {}", guestId);
    }

    @Transactional
    public int cleanupExpiredGuests() {
        Instant now = Instant.now();
        int deleted = guestRepository.deleteExpired(now);
        if (deleted > 0) {
            log.info("Purged {} expired guest identities", deleted);
        }
        return deleted;
    }

    private void autoEnrollInDefaultRoom(String userId) {
        try {
            RoomRecord defaultRoom = roomRepository.findByCode("GN-MAIN")
                    .orElseGet(() -> {
                        String rId = UUID.randomUUID().toString();
                        RoomRecord r = new RoomRecord(
                                rId,
                                "GN-MAIN",
                                "General Mesh",
                                "Public broadcast enclave for all connected GhostNet operatives",
                                false,
                                null,
                                100,
                                userId,
                                Instant.now()
                        );
                        roomRepository.save(r);
                        return r;
                    });
            String roomId = defaultRoom.id();
            if (!roomMembershipRepository.isMember(roomId, userId)) {
                roomMembershipRepository.addMember(new RoomMemberRecord(
                        roomId,
                        userId,
                        RoomMemberRole.MEMBER,
                        Instant.now()
                ));
                log.debug("Auto-enrolled guest '{}' into #GN-MAIN", userId);
            }
        } catch (Exception e) {
            log.warn("Could not auto-enroll guest into default room: {}", e.getMessage());
        }
    }
}
