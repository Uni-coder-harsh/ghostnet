package com.ghostnet.persistence;

import com.ghostnet.messaging.*;
import com.ghostnet.room.*;
import com.ghostnet.session.*;
import com.ghostnet.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DatabasePersistenceTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMembershipRepository roomMembershipRepository;

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private DatabaseBackupService databaseBackupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupCleanState() {
        jdbcTemplate.execute("DELETE FROM direct_messages;");
        jdbcTemplate.execute("DELETE FROM room_messages;");
        jdbcTemplate.execute("DELETE FROM room_members;");
        jdbcTemplate.execute("DELETE FROM rooms;");
        jdbcTemplate.execute("DELETE FROM user_sessions;");
        jdbcTemplate.execute("DELETE FROM user_preferences;");
        jdbcTemplate.execute("DELETE FROM users;");
    }

    @Test
    @DisplayName("User persistence lifecycle and query operations")
    void testUserPersistence() {
        String userId = UUID.randomUUID().toString();
        UserRecord user = new UserRecord(
                userId,
                "cipher_operative",
                "$2a$12$eXampLeHashForTestingPurposesOnly",
                "Cipher Operative",
                UserRole.ROLE_USER,
                "GhostNet field node",
                Instant.now(),
                Instant.now()
        );

        userRepository.save(user);

        assertTrue(userRepository.existsByUsername("cipher_operative"));
        assertTrue(userRepository.existsByUsername("CIPHER_OPERATIVE")); // case-insensitive check

        Optional<UserRecord> found = userRepository.findById(userId);
        assertTrue(found.isPresent());
        assertEquals("cipher_operative", found.get().username());
        assertEquals("Cipher Operative", found.get().displayName());

        // Update profile
        UserRecord updated = new UserRecord(
                userId,
                "cipher_operative",
                user.passwordHash(),
                "Updated Operative",
                UserRole.ROLE_ADMIN,
                "Promoted to host admin",
                user.createdAt(),
                Instant.now()
        );
        userRepository.update(updated);

        Optional<UserRecord> reloaded = userRepository.findById(userId);
        assertTrue(reloaded.isPresent());
        assertEquals("Updated Operative", reloaded.get().displayName());
        assertEquals(UserRole.ROLE_ADMIN, reloaded.get().role());
        assertEquals("Promoted to host admin", reloaded.get().bio());

        // Search
        List<UserRecord> searchRes = userRepository.searchByUsernameOrDisplayName("operative", 10);
        assertEquals(1, searchRes.size());
    }

    @Test
    @DisplayName("Session persistence and expiration handling")
    void testSessionPersistence() {
        String userId = UUID.randomUUID().toString();
        userRepository.save(new UserRecord(userId, "agent1", "hash", "Agent 1", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));

        String activeToken = UUID.randomUUID().toString();
        String expiredToken = UUID.randomUUID().toString();

        SessionRecord activeSession = new SessionRecord(
                activeToken,
                userId,
                Instant.now().plus(24, ChronoUnit.HOURS),
                Instant.now(),
                "192.168.1.50",
                "Mozilla/5.0"
        );

        SessionRecord expiredSession = new SessionRecord(
                expiredToken,
                userId,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().minus(2, ChronoUnit.HOURS),
                "192.168.1.50",
                "Mozilla/5.0"
        );

        sessionRepository.save(activeSession);
        sessionRepository.save(expiredSession);

        assertEquals(1, sessionRepository.countActive());

        sessionRepository.deleteExpired();

        assertTrue(sessionRepository.findByToken(activeToken).isPresent());
        assertTrue(sessionRepository.findByToken(expiredToken).isEmpty());
    }

    @Test
    @DisplayName("Room and Membership persistence operations")
    void testRoomAndMembershipPersistence() {
        String ownerId = UUID.randomUUID().toString();
        userRepository.save(new UserRecord(ownerId, "commander", "hash", "Commander", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));

        String roomId = UUID.randomUUID().toString();
        String roomCode = "GN-TEST1";
        RoomRecord room = new RoomRecord(
                roomId,
                roomCode,
                "Tactical Briefing",
                "Offline strategic planning room",
                false,
                null,
                25,
                ownerId,
                Instant.now()
        );

        roomRepository.save(room);

        Optional<RoomRecord> found = roomRepository.findByCode("gn-test1");
        assertTrue(found.isPresent());
        assertEquals("Tactical Briefing", found.get().name());

        // Add members
        roomMembershipRepository.addMember(new RoomMemberRecord(roomId, ownerId, RoomMemberRole.OWNER, Instant.now()));

        String member2Id = UUID.randomUUID().toString();
        userRepository.save(new UserRecord(member2Id, "scout", "hash", "Scout", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));
        roomMembershipRepository.addMember(new RoomMemberRecord(roomId, member2Id, RoomMemberRole.MEMBER, Instant.now()));

        assertEquals(2, roomMembershipRepository.countMembers(roomId));
        assertTrue(roomMembershipRepository.isMember(roomId, ownerId));
        assertTrue(roomMembershipRepository.isMember(roomId, member2Id));

        // Remove member
        roomMembershipRepository.removeMember(roomId, member2Id);
        assertEquals(1, roomMembershipRepository.countMembers(roomId));
        assertFalse(roomMembershipRepository.isMember(roomId, member2Id));
    }

    @Test
    @DisplayName("Direct messaging persistence, pagination and delivery status")
    void testDirectMessagingPersistence() {
        String aliceId = UUID.randomUUID().toString();
        String bobId = UUID.randomUUID().toString();
        userRepository.save(new UserRecord(aliceId, "alice", "hash", "Alice", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));
        userRepository.save(new UserRecord(bobId, "bob", "hash", "Bob", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));

        for (int i = 0; i < 5; i++) {
            DirectMessageRecord msg = new DirectMessageRecord(
                    UUID.randomUUID().toString(),
                    aliceId,
                    bobId,
                    "Alice message " + i,
                    MessageStatus.SENT,
                    Instant.now().plusMillis(i * 10)
            );
            directMessageRepository.save(msg);
        }

        DirectMessageRecord bobReply = new DirectMessageRecord(
                UUID.randomUUID().toString(),
                bobId,
                aliceId,
                "Bob reply",
                MessageStatus.SENT,
                Instant.now().plusMillis(100)
        );
        directMessageRepository.save(bobReply);

        List<DirectMessageRecord> history = directMessageRepository.findConversationHistory(aliceId, bobId, 10, 0);
        assertEquals(6, history.size());

        // Mark delivered & read
        directMessageRepository.markConversationDelivered(bobId, aliceId);
        directMessageRepository.markConversationRead(bobId, aliceId);

        DirectMessageRecord checkDelivered = directMessageRepository.findById(history.get(0).id()).orElseThrow();
        assertEquals(MessageStatus.READ, checkDelivered.status());
    }

    @Test
    @DisplayName("Room messaging persistence and history retrieval")
    void testRoomMessagingPersistence() {
        String ownerId = UUID.randomUUID().toString();
        userRepository.save(new UserRecord(ownerId, "leader", "hash", "Leader", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));

        String roomId = UUID.randomUUID().toString();
        roomRepository.save(new RoomRecord(roomId, "RM-001", "General", "", false, null, 100, ownerId, Instant.now()));

        for (int i = 0; i < 4; i++) {
            roomMessageRepository.save(new RoomMessageRecord(
                    UUID.randomUUID().toString(),
                    roomId,
                    ownerId,
                    "Leader",
                    "Room broadcast " + i,
                    Instant.now().plusMillis(i * 10)
            ));
        }

        assertEquals(4, roomMessageRepository.countByRoomId(roomId));
        List<RoomMessageRecord> history = roomMessageRepository.findHistoryByRoomId(roomId, 10, 0);
        assertEquals(4, history.size());
    }

    @Test
    @DisplayName("Retention policy purges old messages")
    void testRetentionPolicy() {
        String u1 = UUID.randomUUID().toString();
        String u2 = UUID.randomUUID().toString();
        userRepository.save(new UserRecord(u1, "u1", "h", "U1", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));
        userRepository.save(new UserRecord(u2, "u2", "h", "U2", UserRole.ROLE_USER, "", Instant.now(), Instant.now()));

        // Insert message older than 40 days
        DirectMessageRecord oldMsg = new DirectMessageRecord(
                UUID.randomUUID().toString(),
                u1,
                u2,
                "Ancient message",
                MessageStatus.READ,
                Instant.now().minus(45, ChronoUnit.DAYS)
        );
        directMessageRepository.save(oldMsg);

        // Insert fresh message
        DirectMessageRecord freshMsg = new DirectMessageRecord(
                UUID.randomUUID().toString(),
                u1,
                u2,
                "Fresh message",
                MessageStatus.SENT,
                Instant.now()
        );
        directMessageRepository.save(freshMsg);

        RetentionService.RetentionReport report = retentionService.cleanupRetention();
        assertEquals(1, report.directMessagesPurged());
        assertTrue(directMessageRepository.findById(oldMsg.id()).isEmpty());
        assertTrue(directMessageRepository.findById(freshMsg.id()).isPresent());
    }

    @Test
    @DisplayName("Database backup creation and snapshot inspection")
    void testDatabaseBackup() {
        DatabaseBackupService.BackupResult result = databaseBackupService.createBackup();
        assertTrue(result.success());
        assertNotNull(result.filename());
        assertTrue(result.sizeBytes() > 0);

        List<DatabaseBackupService.BackupInfo> backups = databaseBackupService.listBackups();
        assertFalse(backups.isEmpty());
    }
}
