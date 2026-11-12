package com.ghostnet.guest;

import com.ghostnet.security.AuthenticationException;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GuestAccessTests {

    @Autowired
    private GuestService guestService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Guest onboarding creates ephemeral session, device credential and ROLE_GUEST principal")
    void testGuestOnboarding() {
        GuestJoinRequest req = new GuestJoinRequest("Nomad");
        GuestAuthResponse resp = guestService.joinAsGuest(req, "192.168.1.55", "Mozilla/5.0 Test");

        assertNotNull(resp);
        assertNotNull(resp.token());
        assertNotNull(resp.deviceCredential());
        assertTrue(resp.deviceCredential().startsWith("gdev_"));
        assertNotNull(resp.user());
        assertEquals(UserRole.ROLE_GUEST, resp.user().role());
        assertTrue(resp.user().displayName().contains("Nomad"));
        assertTrue(resp.user().isGuest());
        assertFalse(resp.expiresAt().isBefore(java.time.Instant.now()));

        // Backing UserRecord check
        Optional<UserRecord> userOpt = userRepository.findById(resp.user().id());
        assertTrue(userOpt.isPresent());
        assertTrue(userOpt.get().isGuest());
    }

    @Test
    @DisplayName("Returning guest can restore identity using device credential without re-registering")
    void testReturningGuestRestoration() {
        GuestJoinRequest joinReq = new GuestJoinRequest("Scout_99");
        GuestAuthResponse initial = guestService.joinAsGuest(joinReq, "192.168.1.60", "Browser A");

        // Reconnect with same device credential
        GuestRestoreRequest restoreReq = new GuestRestoreRequest(initial.deviceCredential());
        GuestAuthResponse restored = guestService.restoreGuest(restoreReq, "192.168.1.60", "Browser A");

        assertNotNull(restored);
        assertEquals(initial.user().id(), restored.user().id());
        assertEquals(initial.deviceCredential(), restored.deviceCredential());
        assertEquals(UserRole.ROLE_GUEST, restored.user().role());
    }

    @Test
    @DisplayName("Guest identity reset revokes credential and rejects subsequent restoration")
    void testGuestResetAndRevocation() {
        GuestJoinRequest joinReq = new GuestJoinRequest("Ghost_Recon");
        GuestAuthResponse initial = guestService.joinAsGuest(joinReq, "192.168.1.70", "Browser B");

        // Reset credential
        guestService.resetGuest(initial.deviceCredential());

        // Attempt restore should fail
        GuestRestoreRequest restoreReq = new GuestRestoreRequest(initial.deviceCredential());
        assertThrows(AuthenticationException.class, () -> guestService.restoreGuest(restoreReq, "192.168.1.70", "Browser B"));
    }
}
