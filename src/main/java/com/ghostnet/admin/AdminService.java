package com.ghostnet.admin;

import com.ghostnet.messaging.DirectMessageRepository;
import com.ghostnet.messaging.RoomMessageRepository;
import com.ghostnet.persistence.DatabaseBackupService;
import com.ghostnet.persistence.RetentionService;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.room.RoomService;
import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.SessionRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Administrative operations coordinator for host management,
 * moderation, sessions, snapshots, and graceful shutdown (Req 246-261).
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final RoomService roomService;
    private final RetentionService retentionService;
    private final DatabaseBackupService backupService;
    private final ApplicationContext applicationContext;

    public AdminService(AuthSessionService authSessionService,
                        UserRepository userRepository,
                        RoomRepository roomRepository,
                        RoomMembershipRepository roomMembershipRepository,
                        RoomService roomService,
                        RetentionService retentionService,
                        DatabaseBackupService backupService,
                        ApplicationContext applicationContext) {
        this.authSessionService = authSessionService;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.roomService = roomService;
        this.retentionService = retentionService;
        this.backupService = backupService;
        this.applicationContext = applicationContext;
    }

    public List<AdminSessionDto> getActiveSessions() {
        List<SessionRecord> sessions = authSessionService.getActiveSessions();
        List<AdminSessionDto> result = new ArrayList<>();
        for (SessionRecord s : sessions) {
            String username = "Unknown";
            String displayName = "Unknown";
            var userOpt = userRepository.findById(s.userId());
            if (userOpt.isPresent()) {
                username = userOpt.get().username();
                displayName = userOpt.get().displayName();
            }
            result.add(new AdminSessionDto(
                    s.token(),
                    s.userId(),
                    username,
                    displayName,
                    s.clientIp(),
                    s.userAgent(),
                    s.createdAt(),
                    s.expiresAt()
            ));
        }
        return result;
    }

    public void revokeSession(String token) {
        authSessionService.revokeSession(token);
    }

    public void revokeAllUserSessions(String userId) {
        authSessionService.revokeAllUserSessions(userId);
    }

    public void removeRoomParticipant(String adminUserId, String roomId, String targetUserId) {
        roomService.kickMember(adminUserId, roomId, targetUserId);
    }

    public RetentionService.RetentionReport runRetentionCleanup() {
        return retentionService.cleanupRetention();
    }

    public DatabaseBackupService.BackupResult createBackup() {
        return backupService.createBackup();
    }

    public List<DatabaseBackupService.BackupInfo> listBackups() {
        return backupService.listBackups();
    }

    /**
     * Initiates a graceful shutdown of GhostNet from the admin interface (Req 090, 256).
     */
    public void initiateGracefulShutdown() {
        log.warn("ADMIN INITIATED GRACEFUL SHUTDOWN OF GHOSTNET");
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(1000); // Allow HTTP response to complete
                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
            } catch (Exception e) {
                log.error("Error during graceful shutdown: {}", e.getMessage(), e);
                System.exit(1);
            }
        }, "GhostNet-Shutdown-Hook");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    public record AdminSessionDto(
            String token,
            String userId,
            String username,
            String displayName,
            String clientIp,
            String userAgent,
            Instant createdAt,
            Instant expiresAt
    ) {}
}
