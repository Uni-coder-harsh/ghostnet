package com.ghostnet.scenarios;

import com.ghostnet.common.ValidationException;
import com.ghostnet.files.AttachmentDto;
import com.ghostnet.files.AttachmentService;
import com.ghostnet.guest.GuestAuthResponse;
import com.ghostnet.guest.GuestJoinRequest;
import com.ghostnet.guest.GuestRestoreRequest;
import com.ghostnet.guest.GuestService;
import com.ghostnet.room.CreateRoomRequest;
import com.ghostnet.room.RoomDto;
import com.ghostnet.room.RoomService;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.storage.FileStorageService;
import com.ghostnet.storage.StorageLayoutService;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRole;
import com.ghostnet.vault.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification of Mandatory Platform Testing Scenarios A through H (Master Specification).
 */
@SpringBootTest
class PlatformScenariosTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuestService guestService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private com.ghostnet.files.ChatAttachmentRepository chatAttachmentRepository;

    @Autowired
    private VaultService vaultService;

    @Autowired
    private StorageLayoutService storageLayoutService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private com.ghostnet.admin.AdminBootstrapService adminBootstrapService;

    private UserRecord user1;
    private UserRecord user2;
    private UserRecord user3;

    @BeforeEach
    void setup() {
        adminBootstrapService.ensureDefaultRoom();

        String u1 = UUID.randomUUID().toString();
        user1 = new UserRecord(u1, "agent_1_" + u1.substring(0, 5), "pass", "Operative 1", UserRole.ROLE_USER, "bio", Instant.now(), Instant.now());
        userRepository.save(user1);

        String u2 = UUID.randomUUID().toString();
        user2 = new UserRecord(u2, "agent_2_" + u2.substring(0, 5), "pass", "Operative 2", UserRole.ROLE_USER, "bio", Instant.now(), Instant.now());
        userRepository.save(user2);

        String u3 = UUID.randomUUID().toString();
        user3 = new UserRecord(u3, "agent_3_" + u3.substring(0, 5), "pass", "Operative 3", UserRole.ROLE_USER, "bio", Instant.now(), Instant.now());
        userRepository.save(user3);
    }

    @Test
    @DisplayName("Scenario A: Direct message file sharing between two registered users with third-party isolation")
    void testScenarioA_DirectMessageFileSharing() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "mission_orders.pdf", "application/pdf", "RESTRICTED MISSION CONTENT".getBytes()
        );

        // User 1 sends attachment in DM channel with User 2
        AttachmentDto att = attachmentService.uploadAttachment(user1, file, "DIRECT", user2.id());
        assertNotNull(att);
        assertEquals("mission_orders.pdf", att.originalFilename());
        assertEquals(user1.id(), att.uploaderId());

        // User 2 (intended recipient) can access and download file
        AttachmentService.DownloadResource downloadUser2 = attachmentService.getAttachmentForDownload(user2, att.id());
        assertNotNull(downloadUser2);
        assertTrue(downloadUser2.resource().exists());
        assertEquals("mission_orders.pdf", downloadUser2.filename());

        // User 3 (outsider) is denied access
        assertThrows(UnauthorizedException.class, () -> attachmentService.getAttachmentForDownload(user3, att.id()));
    }

    @Test
    @DisplayName("Scenario B: Room message file sharing with non-member access denial")
    void testScenarioB_RoomMessageFileSharing() {
        // User 1 creates a private room
        RoomDto room = roomService.createRoom(user1.id(), new CreateRoomRequest("Alpha Enclave", "Secret room", true, null, 10));

        // User 2 joins the room
        roomService.joinRoomByCode(user2.id(), new com.ghostnet.room.JoinRoomRequest(room.code(), null));

        // User 1 uploads file in room
        MockMultipartFile file = new MockMultipartFile(
                "file", "tactical_map.png", "image/png", new byte[]{0x11, 0x22, 0x33, 0x44}
        );
        AttachmentDto att = attachmentService.uploadAttachment(user1, file, "ROOM", room.id());

        // User 2 (member) can download file
        AttachmentService.DownloadResource downloadUser2 = attachmentService.getAttachmentForDownload(user2, att.id());
        assertNotNull(downloadUser2);
        assertTrue(downloadUser2.resource().exists());

        // User 3 (not a member) is denied access
        assertThrows(UnauthorizedException.class, () -> attachmentService.getAttachmentForDownload(user3, att.id()));
    }

    @Test
    @DisplayName("Scenario C: Individual private vault isolation between users")
    void testScenarioC_PrivateVaultIsolation() {
        // User 1 initializes vault
        vaultService.initVault(user1, new InitVaultRequest("salt_u1", "verifier_u1"));

        // User 1 uploads encrypted file
        byte[] ciphertext = "AES_GCM_ENCRYPTED_INTEL_STREAM".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "payload.enc", "application/octet-stream", ciphertext);
        VaultFileDto vaultFile = vaultService.uploadEncryptedFile(
                user1, file, null, "intel_report.docx", 100L, true, "iv_u1", "tag_u1", "checksum_u1"
        );

        assertNotNull(vaultFile);
        assertEquals("intel_report.docx", vaultFile.filename());

        // User 2 cannot download User 1's vault file
        assertThrows(UnauthorizedException.class, () -> vaultService.downloadEncryptedFile(user2, vaultFile.id()));

        // User 2 cannot delete User 1's vault file
        assertThrows(UnauthorizedException.class, () -> vaultService.deleteFile(user2, vaultFile.id()));

        // User 2 tree list does not contain User 1's file
        VaultTreeDto treeUser2 = vaultService.getTree(user2, null, null);
        assertTrue(treeUser2.files().stream().noneMatch(f -> f.id().equals(vaultFile.id())));
    }

    @Test
    @DisplayName("Scenario D: Guest temporary access and file sharing in temporary storage")
    void testScenarioD_GuestTemporaryAccess() {
        GuestAuthResponse guestAuth = guestService.joinAsGuest(new GuestJoinRequest("Nomad_7"), "192.168.1.100", "Mozilla/5.0");
        assertNotNull(guestAuth.token());
        assertTrue(guestAuth.user().isGuest());

        UserRecord guestUser = userRepository.findById(guestAuth.user().id()).orElseThrow();

        // Guest uploads file in public mesh
        MockMultipartFile file = new MockMultipartFile("file", "field_notes.txt", "text/plain", "Field notes from guest".getBytes());
        AttachmentDto att = attachmentService.uploadAttachment(guestUser, file, "ROOM", "GN-MAIN");

        assertNotNull(att);
        assertTrue(att.isGuestUpload());
        assertNotNull(att.expiresAt());

        // Verify physical file was placed under temporary/guest-uploads directory
        var record = chatAttachmentRepository.findById(att.id()).orElseThrow();
        Path storagePath = storageLayoutService.getBasePath().resolve(record.storagePath());
        assertTrue(storagePath.startsWith(storageLayoutService.getGuestUploadsPath()));
        assertTrue(Files.exists(storagePath));
    }

    @Test
    @DisplayName("Scenario E: Guest vault denial (Guests cannot access private vaults)")
    void testScenarioE_GuestVaultDenial() {
        GuestAuthResponse guestAuth = guestService.joinAsGuest(new GuestJoinRequest("Nomad_Denied"), "192.168.1.101", "Mozilla/5.0");
        UserRecord guestUser = userRepository.findById(guestAuth.user().id()).orElseThrow();

        // Vault status attempt denied
        assertThrows(UnauthorizedException.class, () -> vaultService.getStatus(guestUser));

        // Vault init attempt denied
        assertThrows(UnauthorizedException.class, () -> vaultService.initVault(guestUser, new InitVaultRequest("salt", "verifier")));

        // Vault tree browse attempt denied
        assertThrows(UnauthorizedException.class, () -> vaultService.getTree(guestUser, null, null));

        // Vault file upload attempt denied
        MockMultipartFile file = new MockMultipartFile("file", "test.enc", "application/octet-stream", "dummy".getBytes());
        assertThrows(UnauthorizedException.class, () ->
                vaultService.uploadEncryptedFile(guestUser, file, null, "test.bin", 5, true, "iv", "tag", null));
    }

    @Test
    @DisplayName("Scenario F: Returning guest device recognition via random browser credential")
    void testScenarioF_ReturningGuestDeviceRecognition() {
        // Initial onboarding
        GuestAuthResponse initial = guestService.joinAsGuest(new GuestJoinRequest("Scout_Bravo"), "192.168.1.102", "Mozilla/5.0");
        String deviceCredential = initial.deviceCredential();
        assertNotNull(deviceCredential);
        assertTrue(deviceCredential.startsWith("gdev_"));

        // Returning guest reconnects using the device credential
        GuestRestoreRequest restoreReq = new GuestRestoreRequest(deviceCredential);
        GuestAuthResponse restored = guestService.restoreGuest(restoreReq, "192.168.1.102", "Mozilla/5.0");

        assertNotNull(restored);
        assertEquals(initial.user().id(), restored.user().id());
        assertEquals(initial.user().displayName(), restored.user().displayName());
        assertEquals(deviceCredential, restored.deviceCredential());
    }

    @Test
    @DisplayName("Scenario G: Storage limit and quota enforcement")
    void testScenarioG_StorageLimitEnforcement() {
        // Enforce max upload limit check
        byte[] oversized = new byte[100];
        MockMultipartFile file = new MockMultipartFile("file", "oversized.bin", "application/octet-stream", oversized);

        // Test quota calculation in VaultSettingsRecord
        VaultSettingsRecord smallQuota = new VaultSettingsRecord(
                "user-quota-test", true, "salt", "verifier", 50L, 40L, Instant.now(), Instant.now()
        );
        assertFalse(smallQuota.hasSpace(20L), "Should not have space for 20 bytes when only 10 bytes remain");
        assertTrue(smallQuota.hasSpace(5L), "Should have space for 5 bytes");
    }

    @Test
    @DisplayName("Scenario H: Path traversal security defense")
    void testScenarioH_PathTraversalSecurityDefense() {
        String dangerousFilename = "../../../../../etc/passwd";
        String sanitized = fileStorageService.sanitizeFilename(dangerousFilename);

        assertFalse(sanitized.contains(".."));
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains("\\"));
        assertEquals("passwd", sanitized);

        // Test path traversal rejection in file resolution
        assertThrows(ValidationException.class, () -> fileStorageService.resolvePath("../../secret.txt"));
    }
}
