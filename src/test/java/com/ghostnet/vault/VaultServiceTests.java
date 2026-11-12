package com.ghostnet.vault;

import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VaultServiceTests {

    @Autowired
    private VaultService vaultService;

    @Autowired
    private UserRepository userRepository;

    private UserRecord registeredUserA;
    private UserRecord registeredUserB;
    private UserRecord guestUser;

    @BeforeEach
    void setup() {
        String uidA = UUID.randomUUID().toString();
        registeredUserA = new UserRecord(
                uidA,
                "agent_a_" + uidA.substring(0, 6),
                "hashedpass",
                "Operative A",
                UserRole.ROLE_USER,
                "Operative A bio",
                Instant.now(),
                Instant.now()
        );
        userRepository.save(registeredUserA);

        String uidB = UUID.randomUUID().toString();
        registeredUserB = new UserRecord(
                uidB,
                "agent_b_" + uidB.substring(0, 6),
                "hashedpass",
                "Operative B",
                UserRole.ROLE_USER,
                "Operative B bio",
                Instant.now(),
                Instant.now()
        );
        userRepository.save(registeredUserB);

        String gid = UUID.randomUUID().toString();
        guestUser = new UserRecord(
                gid,
                "guest_" + gid.substring(0, 6),
                "ephemeral",
                "Guest Traveler",
                UserRole.ROLE_GUEST,
                "Ephemeral guest",
                Instant.now(),
                Instant.now()
        );
        userRepository.save(guestUser);
    }

    @Test
    @DisplayName("Registered user can initialize zero-knowledge vault and verify status")
    void testVaultInitialization() {
        VaultStatusDto initial = vaultService.getStatus(registeredUserA);
        assertNotNull(initial);
        assertFalse(initial.isInitialized());

        InitVaultRequest req = new InitVaultRequest("random_salt_hex_1234", "derived_verifier_hash_5678");
        VaultStatusDto updated = vaultService.initVault(registeredUserA, req);

        assertTrue(updated.isInitialized());
        assertEquals("random_salt_hex_1234", updated.salt());
        assertEquals("derived_verifier_hash_5678", updated.authVerifier());
    }

    @Test
    @DisplayName("Registered user can create folder hierarchies and breadcrumbs")
    void testFolderHierarchyAndBreadcrumbs() {
        VaultFolderDto rootFolder = vaultService.createFolder(registeredUserA, new CreateFolderRequest("Classified Plans", null));
        assertNotNull(rootFolder);
        assertNotNull(rootFolder.id());
        assertEquals("Classified Plans", rootFolder.name());

        VaultFolderDto subFolder = vaultService.createFolder(registeredUserA, new CreateFolderRequest("Phase 1", rootFolder.id()));
        assertNotNull(subFolder);
        assertEquals(rootFolder.id(), subFolder.parentId());

        VaultTreeDto tree = vaultService.getTree(registeredUserA, subFolder.id(), null);
        assertNotNull(tree.currentFolder());
        assertEquals("Phase 1", tree.currentFolder().name());
        assertEquals(2, tree.breadcrumbs().size());
        assertEquals("Classified Plans", tree.breadcrumbs().get(0).name());
        assertEquals("Phase 1", tree.breadcrumbs().get(1).name());
    }

    @Test
    @DisplayName("Registered user can upload, inspect, download, and delete encrypted files")
    void testEncryptedFileUploadDownloadDelete() {
        byte[] encryptedPayload = "AES256GCM_CIPHERTEXT_SIMULATION_DATA".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "intel.doc.enc", "application/octet-stream", encryptedPayload
        );

        VaultFileDto uploaded = vaultService.uploadEncryptedFile(
                registeredUserA,
                file,
                null,
                "intel.doc",
                128L,
                true,
                "iv_sample_hex_12b",
                "tag_sample_hex_16b",
                null
        );

        assertNotNull(uploaded);
        assertEquals("intel.doc", uploaded.filename());
        assertTrue(uploaded.isClientEncrypted());
        assertEquals("iv_sample_hex_12b", uploaded.encryptionIv());
        assertEquals("tag_sample_hex_16b", uploaded.encryptionTag());
        assertEquals(encryptedPayload.length, uploaded.encryptedSize());

        // Download
        VaultFileDownload download = vaultService.downloadEncryptedFile(registeredUserA, uploaded.id());
        assertNotNull(download);
        assertEquals(uploaded.id(), download.file().id());
        assertTrue(download.resource().exists());

        // Update name
        VaultFileDto renamed = vaultService.updateFile(registeredUserA, uploaded.id(), new UpdateFileRequest("intel_final.doc", null));
        assertEquals("intel_final.doc", renamed.filename());

        // Delete
        vaultService.deleteFile(registeredUserA, uploaded.id());
        assertThrows(RuntimeException.class, () -> vaultService.downloadEncryptedFile(registeredUserA, uploaded.id()));
    }

    @Test
    @DisplayName("Scenario E: Guests are strictly forbidden from accessing private vaults")
    void testScenarioE_GuestForbiddenFromVault() {
        UnauthorizedException ex1 = assertThrows(UnauthorizedException.class, () -> vaultService.getStatus(guestUser));
        assertTrue(ex1.getMessage().contains("Guests cannot access private vaults"));

        UnauthorizedException ex2 = assertThrows(UnauthorizedException.class, () ->
                vaultService.initVault(guestUser, new InitVaultRequest("salt", "verifier")));
        assertTrue(ex2.getMessage().contains("Guests cannot access private vaults"));

        UnauthorizedException ex3 = assertThrows(UnauthorizedException.class, () ->
                vaultService.getTree(guestUser, null, null));
        assertTrue(ex3.getMessage().contains("Guests cannot access private vaults"));

        MockMultipartFile file = new MockMultipartFile("file", "test.enc", "application/octet-stream", "dummy".getBytes());
        UnauthorizedException ex4 = assertThrows(UnauthorizedException.class, () ->
                vaultService.uploadEncryptedFile(guestUser, file, null, "test.bin", 5, true, "iv", "tag", null));
        assertTrue(ex4.getMessage().contains("Guests cannot access private vaults"));
    }

    @Test
    @DisplayName("Scenario C: Vault isolation ensures User A cannot access or delete User B's vault items")
    void testScenarioC_VaultIsolationBetweenUsers() {
        VaultFolderDto folderA = vaultService.createFolder(registeredUserA, new CreateFolderRequest("UserA Folder", null));

        MockMultipartFile file = new MockMultipartFile("file", "secret.enc", "application/octet-stream", "secret-user-a".getBytes());
        VaultFileDto fileA = vaultService.uploadEncryptedFile(
                registeredUserA, file, folderA.id(), "secret.txt", 13, true, "iv_a", "tag_a", null
        );

        // User B cannot download User A's file
        assertThrows(UnauthorizedException.class, () -> vaultService.downloadEncryptedFile(registeredUserB, fileA.id()));

        // User B cannot delete User A's file
        assertThrows(UnauthorizedException.class, () -> vaultService.deleteFile(registeredUserB, fileA.id()));

        // User B cannot delete User A's folder
        assertThrows(UnauthorizedException.class, () -> vaultService.deleteFolder(registeredUserB, folderA.id()));

        // User B tree does not show User A's folder or file
        VaultTreeDto treeB = vaultService.getTree(registeredUserB, null, null);
        assertTrue(treeB.folders().stream().noneMatch(f -> f.id().equals(folderA.id())));
        assertTrue(treeB.files().stream().noneMatch(f -> f.id().equals(fileA.id())));
    }
}
