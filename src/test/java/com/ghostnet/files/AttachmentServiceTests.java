package com.ghostnet.files;

import com.ghostnet.common.ValidationException;
import com.ghostnet.guest.GuestAuthResponse;
import com.ghostnet.guest.GuestJoinRequest;
import com.ghostnet.guest.GuestService;
import com.ghostnet.room.CreateRoomRequest;
import com.ghostnet.room.RoomDto;
import com.ghostnet.room.RoomService;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.user.RegisterRequest;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AttachmentServiceTests {

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private UserService userService;

    @Autowired
    private GuestService guestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private com.ghostnet.admin.AdminBootstrapService adminBootstrapService;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        adminBootstrapService.ensureDefaultRoom();
    }

    @Test
    @DisplayName("Registered operative can upload and download room attachment")
    void testRoomAttachmentUploadAndDownload() {
        String uname = "operative_" + System.currentTimeMillis();
        userService.register(new RegisterRequest(uname, "password123", "Operative"), "127.0.0.1", "test");
        UserRecord user = userRepository.findByUsername(uname).orElseThrow();

        RoomDto room = roomService.createRoom(user.id(), new CreateRoomRequest("Intel Vault", "Secure ops", false, null, 10));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "field_intel.txt",
                "text/plain",
                "TOP SECRET MISSION INTEL: HOST OPERATIONAL".getBytes(StandardCharsets.UTF_8)
        );

        AttachmentDto uploaded = attachmentService.uploadAttachment(user, file, "ROOM", room.id());
        assertNotNull(uploaded);
        assertEquals("field_intel.txt", uploaded.originalFilename());
        assertEquals("text/plain", uploaded.mimeType());
        assertFalse(uploaded.isGuestUpload());
        assertNull(uploaded.expiresAt());

        // Download
        AttachmentService.DownloadResource download = attachmentService.getAttachmentForDownload(user, uploaded.id());
        assertNotNull(download);
        assertEquals("field_intel.txt", download.filename());
        assertEquals(file.getSize(), download.contentLength());
    }

    @Test
    @DisplayName("Guest can upload temporary attachment with automatic expiration")
    void testGuestTemporaryAttachment() {
        GuestAuthResponse guestAuth = guestService.joinAsGuest(new GuestJoinRequest("Nomad_Test"), "127.0.0.1", "test");
        UserRecord guestUser = userRepository.findById(guestAuth.user().id()).orElseThrow();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guest_notes.csv",
                "text/csv",
                "sensor,value\ntemp,23.5".getBytes(StandardCharsets.UTF_8)
        );

        AttachmentDto uploaded = attachmentService.uploadAttachment(guestUser, file, "ROOM", "GN-MAIN");
        assertNotNull(uploaded);
        assertTrue(uploaded.isGuestUpload());
        assertNotNull(uploaded.expiresAt());
        assertTrue(uploaded.expiresAt().isAfter(java.time.Instant.now()));
    }

    @Test
    @DisplayName("Non-member is denied download access to private room attachment")
    void testUnauthorizedDownloadRejection() {
        String ownerName = "owner_" + System.currentTimeMillis();
        String outsiderName = "outsider_" + System.currentTimeMillis();

        userService.register(new RegisterRequest(ownerName, "password123", "Owner"), "127.0.0.1", "test");
        userService.register(new RegisterRequest(outsiderName, "password123", "Outsider"), "127.0.0.1", "test");

        UserRecord owner = userRepository.findByUsername(ownerName).orElseThrow();
        UserRecord outsider = userRepository.findByUsername(outsiderName).orElseThrow();

        RoomDto room = roomService.createRoom(owner.id(), new CreateRoomRequest("Classified", "Secret", true, "roompass123", 10));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "secret.pdf",
                "application/pdf",
                "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8)
        );

        AttachmentDto uploaded = attachmentService.uploadAttachment(owner, file, "ROOM", room.id());

        // Outsider attempts download -> UnauthorizedException
        assertThrows(UnauthorizedException.class, () -> attachmentService.getAttachmentForDownload(outsider, uploaded.id()));
    }

    @Test
    @DisplayName("Path traversal filename is sanitized safely without escaping base directory")
    void testPathTraversalSanitization() {
        String uname = "traversal_tester_" + System.currentTimeMillis();
        userService.register(new RegisterRequest(uname, "password123", "Tester"), "127.0.0.1", "test");
        UserRecord user = userRepository.findByUsername(uname).orElseThrow();

        MockMultipartFile maliciousFile = new MockMultipartFile(
                "file",
                "../../../../etc/passwd",
                "text/plain",
                "root:x:0:0".getBytes(StandardCharsets.UTF_8)
        );

        AttachmentDto uploaded = attachmentService.uploadAttachment(user, maliciousFile, "ROOM", "GN-MAIN");
        assertEquals("passwd", uploaded.originalFilename());
        assertFalse(uploaded.originalFilename().contains(".."));
    }
}
