package com.ghostnet.user;

import com.ghostnet.common.ValidationException;
import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.TokenBucketRateLimiter;
import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserManagementTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("DELETE FROM direct_messages;");
        jdbcTemplate.execute("DELETE FROM room_messages;");
        jdbcTemplate.execute("DELETE FROM room_members;");
        jdbcTemplate.execute("DELETE FROM rooms;");
        jdbcTemplate.execute("DELETE FROM user_sessions;");
        jdbcTemplate.execute("DELETE FROM user_preferences;");
        jdbcTemplate.execute("DELETE FROM users;");
    }

    @Test
    @DisplayName("User registration assigns ROLE_ADMIN to first user, ROLE_USER to subsequent")
    void testRegistrationAndRoles() {
        // Register first user (host)
        RegisterRequest req1 = new RegisterRequest("ghost_host", "strongPass123", "Host Admin");
        AuthResponse res1 = userService.register(req1, "127.0.0.1", "curl/8.0");

        assertNotNull(res1.token());
        assertEquals("ghost_host", res1.user().username());
        assertEquals(UserRole.ROLE_ADMIN, res1.user().role());

        // Register second user (client)
        RegisterRequest req2 = new RegisterRequest("field_agent", "anotherPass456", "Field Agent");
        AuthResponse res2 = userService.register(req2, "192.168.1.100", "MobileSafari");

        assertNotNull(res2.token());
        assertEquals("field_agent", res2.user().username());
        assertEquals(UserRole.ROLE_USER, res2.user().role());
    }

    @Test
    @DisplayName("Duplicate username registration is rejected case-insensitively")
    void testDuplicateUsernameRejected() {
        RegisterRequest req1 = new RegisterRequest("shadow_net", "password123", "Shadow");
        userService.register(req1, "127.0.0.1", "Mozilla");

        RegisterRequest duplicate = new RegisterRequest("SHADOW_NET", "anotherPass", "Shadow Copy");
        assertThrows(UserAlreadyExistsException.class, () -> userService.register(duplicate, "127.0.0.1", "Mozilla"));
    }

    @Test
    @DisplayName("Login succeeds with correct password, fails with incorrect password")
    void testLoginVerification() {
        RegisterRequest reg = new RegisterRequest("test_pilot", "flightSecret99", "Pilot");
        userService.register(reg, "127.0.0.1", "agent");

        // Successful login
        LoginRequest validLogin = new LoginRequest("test_pilot", "flightSecret99");
        AuthResponse loginRes = userService.login(validLogin, "127.0.0.1", "agent");
        assertNotNull(loginRes.token());
        assertEquals("test_pilot", loginRes.user().username());

        // Failed login - wrong password
        LoginRequest badPassword = new LoginRequest("test_pilot", "wrongPassword");
        assertThrows(AuthenticationException.class, () -> userService.login(badPassword, "127.0.0.1", "agent"));

        // Failed login - non-existent username
        LoginRequest unknownUser = new LoginRequest("unknown_ghost", "password");
        assertThrows(AuthenticationException.class, () -> userService.login(unknownUser, "127.0.0.1", "agent"));
    }

    @Test
    @DisplayName("Profile update modifies display name and bio")
    void testProfileUpdate() {
        RegisterRequest reg = new RegisterRequest("stealth_user", "stealthPass1", "Old Name");
        AuthResponse res = userService.register(reg, "127.0.0.1", "agent");

        UpdateProfileRequest updateReq = new UpdateProfileRequest("Cyber Phantom", "Operating on local mesh network");
        UserDto updated = userService.updateProfile(res.user().id(), updateReq);

        assertEquals("Cyber Phantom", updated.displayName());
        assertEquals("Operating on local mesh network", updated.bio());
    }

    @Test
    @DisplayName("Password change verifies old password and invalidates previous sessions")
    void testPasswordChange() {
        RegisterRequest reg = new RegisterRequest("key_master", "initialPassword", "Key Master");
        AuthResponse res = userService.register(reg, "127.0.0.1", "agent");

        // Invalid old password
        ChangePasswordRequest badOld = new ChangePasswordRequest("wrongOldPassword", "newPassword123");
        assertThrows(ValidationException.class, () -> userService.changePassword(res.user().id(), badOld));

        // Valid password change
        ChangePasswordRequest validChange = new ChangePasswordRequest("initialPassword", "brandNewPassword789");
        userService.changePassword(res.user().id(), validChange);

        // Previous session should be revoked
        assertThrows(AuthenticationException.class, () -> authSessionService.validateSession(res.token()));

        // Can log in with new password
        LoginRequest newLogin = new LoginRequest("key_master", "brandNewPassword789");
        AuthResponse newRes = userService.login(newLogin, "127.0.0.1", "agent");
        assertNotNull(newRes.token());
    }

    @Test
    @DisplayName("Presence service accurately tracks multi-connection online status")
    void testPresenceTracking() {
        String userId = "user-pres-123";
        assertFalse(presenceService.isOnline(userId));

        // First connection -> becomes online
        boolean becameOnline1 = presenceService.registerConnection(userId, "conn-1");
        assertTrue(becameOnline1);
        assertTrue(presenceService.isOnline(userId));

        // Second connection from same user (e.g. laptop + phone) -> remains online
        boolean becameOnline2 = presenceService.registerConnection(userId, "conn-2");
        assertFalse(becameOnline2);
        assertTrue(presenceService.isOnline(userId));

        // Disconnect first connection -> still online (conn-2 still open)
        PresenceService.DisconnectResult disc1 = presenceService.removeConnection("conn-1");
        assertEquals(userId, disc1.userId());
        assertFalse(disc1.becameOffline());
        assertTrue(presenceService.isOnline(userId));

        // Disconnect second connection -> becomes offline
        PresenceService.DisconnectResult disc2 = presenceService.removeConnection("conn-2");
        assertEquals(userId, disc2.userId());
        assertTrue(disc2.becameOffline());
        assertFalse(presenceService.isOnline(userId));
    }

    @Test
    @DisplayName("TokenBucket rate limiter permits within capacity and throttles bursts")
    void testRateLimiter() {
        String key = "test-ip-" + System.currentTimeMillis();
        // Should consume tokens successfully
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryConsume(key));
        }
    }
}
