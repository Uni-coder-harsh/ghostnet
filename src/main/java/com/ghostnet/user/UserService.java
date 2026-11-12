package com.ghostnet.user;

import com.ghostnet.common.ResourceNotFoundException;
import com.ghostnet.common.ValidationException;
import com.ghostnet.room.RoomMemberRecord;
import com.ghostnet.room.RoomMemberRole;
import com.ghostnet.messaging.DirectMessageRepository;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.room.RoomRecord;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.PasswordService;
import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.PresenceService;
import com.ghostnet.session.SessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service coordinating user lifecycle, registration, authentication, and profiles (Req 111-130).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuthSessionService authSessionService;
    private final PresenceService presenceService;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final DirectMessageRepository directMessageRepository;

    public UserService(UserRepository userRepository,
                       PasswordService passwordService,
                       AuthSessionService authSessionService,
                       PresenceService presenceService,
                       RoomRepository roomRepository,
                       RoomMembershipRepository roomMembershipRepository,
                       DirectMessageRepository directMessageRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.authSessionService = authSessionService;
        this.presenceService = presenceService;
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.directMessageRepository = directMessageRepository;
    }

    /**
     * Registers a new offline user account (Req 111-114, 120-122).
     * The first user account created on the host laptop automatically receives ROLE_ADMIN.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp, String userAgent) {
        String cleanUsername = request.username().trim();

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new UserAlreadyExistsException("Username '" + cleanUsername + "' is already taken");
        }

        // Determine role: First user is administrator (Host)
        long currentCount = userRepository.count();
        UserRole assignedRole = (currentCount == 0) ? UserRole.ROLE_ADMIN : UserRole.ROLE_USER;

        String displayName = (request.displayName() != null && !request.displayName().isBlank())
                ? request.displayName().trim()
                : cleanUsername;

        String passwordHash = passwordService.hashPassword(request.password());
        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        UserRecord newUser = new UserRecord(
                userId,
                cleanUsername,
                passwordHash,
                displayName,
                assignedRole,
                "",
                now,
                now
        );

        userRepository.save(newUser);
        log.info("Registered new user '{}' with role {}", cleanUsername, assignedRole);

        // Auto-enroll operative into default public room if available
        autoEnrollInDefaultRoom(userId);

        SessionRecord session = authSessionService.createSession(userId, clientIp, userAgent);
        return new AuthResponse(session.token(), UserDto.from(newUser, true));
    }

    /**
     * Authenticates an existing user and creates a secure session (Req 115, 121, 123).
     */
    public AuthResponse login(LoginRequest request, String clientIp, String userAgent) {
        String cleanUsername = request.username().trim();
        UserRecord user = userRepository.findByUsername(cleanUsername)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!passwordService.matches(request.password(), user.passwordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }

        SessionRecord session = authSessionService.createSession(user.id(), clientIp, userAgent);
        autoEnrollInDefaultRoom(user.id());
        log.info("User '{}' logged in successfully from IP {}", cleanUsername, clientIp);
        boolean isOnline = presenceService.isOnline(user.id());
        return new AuthResponse(session.token(), UserDto.from(user, isOnline));
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
                log.info("Auto-enrolled user '{}' into #GN-MAIN", userId);
            }
        } catch (Exception e) {
            log.warn("Could not auto-enroll user into default room: {}", e.getMessage());
        }
    }

    /**
     * Terminates an authenticated session (Req 116).
     */
    public void logout(String sessionToken) {
        authSessionService.revokeSession(sessionToken);
    }

    /**
     * Updates user profile (Req 119).
     */
    @Transactional
    public UserDto updateProfile(String userId, UpdateProfileRequest request) {
        UserRecord existing = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newDisplayName = (request.displayName() != null && !request.displayName().isBlank())
                ? request.displayName().trim()
                : existing.displayName();

        String newBio = (request.bio() != null) ? request.bio().trim() : existing.bio();

        UserRecord updated = new UserRecord(
                existing.id(),
                existing.username(),
                existing.passwordHash(),
                newDisplayName,
                existing.role(),
                newBio,
                existing.createdAt(),
                Instant.now()
        );

        userRepository.update(updated);
        boolean isOnline = presenceService.isOnline(userId);
        return UserDto.from(updated, isOnline);
    }

    /**
     * Updates user password and revokes past sessions for security.
     */
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        UserRecord user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordService.matches(request.currentPassword(), user.passwordHash())) {
            throw new ValidationException("Current password does not match");
        }

        String newHash = passwordService.hashPassword(request.newPassword());
        UserRecord updated = new UserRecord(
                user.id(),
                user.username(),
                newHash,
                user.displayName(),
                user.role(),
                user.bio(),
                user.createdAt(),
                Instant.now()
        );

        userRepository.update(updated);
        // Revoke active sessions to enforce re-authentication
        authSessionService.revokeAllUserSessions(userId);
    }

    public UserDto getUserDto(String userId) {
        UserRecord user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean isOnline = presenceService.isOnline(userId);
        return UserDto.from(user, isOnline);
    }

    public List<UserDto> searchUsers(String query, String excludeUserId) {
        List<UserRecord> users = userRepository.searchByUsernameOrDisplayName(query, 50);
        return users.stream()
                .filter(u -> !u.id().equals(excludeUserId))
                .map(u -> UserDto.from(u, presenceService.isOnline(u.id())))
                .toList();
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> UserDto.from(u, presenceService.isOnline(u.id())))
                .toList();
    }

    public List<UserDto> getDirectMessageContacts(String currentUserId) {
        List<String> partnerIds = directMessageRepository.findDistinctConversationPartners(currentUserId);
        return partnerIds.stream()
                .map(userRepository::findById)
                .flatMap(Optional::stream)
                .map(u -> UserDto.from(u, presenceService.isOnline(u.id())))
                .toList();
    }
}
