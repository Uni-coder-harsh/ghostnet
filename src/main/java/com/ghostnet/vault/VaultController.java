package com.ghostnet.vault;

import com.ghostnet.security.RequireAuth;
import com.ghostnet.security.SecurityContext;
import com.ghostnet.user.UserRecord;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller providing encrypted private vault access for registered users (Req Section 5).
 * All endpoints enforce authenticated registered user access; guests receive 403 Forbidden.
 */
@RestController
@RequestMapping("/api/vault")
@RequireAuth
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping("/status")
    public ResponseEntity<VaultStatusDto> getStatus() {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.getStatus(user));
    }

    @PostMapping("/init")
    public ResponseEntity<VaultStatusDto> initVault(@Valid @RequestBody InitVaultRequest req) {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.initVault(user, req));
    }

    @GetMapping("/challenge")
    public ResponseEntity<VaultStatusDto> getChallenge() {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.getChallenge(user));
    }

    @GetMapping("/tree")
    public ResponseEntity<VaultTreeDto> getTree(
            @RequestParam(value = "folderId", required = false) String folderId,
            @RequestParam(value = "search", required = false) String search) {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.getTree(user, folderId, search));
    }

    @PostMapping("/folders")
    public ResponseEntity<VaultFolderDto> createFolder(@Valid @RequestBody CreateFolderRequest req) {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.createFolder(user, req));
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<VaultFolderDto> updateFolder(
            @PathVariable String id,
            @Valid @RequestBody UpdateFolderRequest req) {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.updateFolder(user, id, req));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) {
        UserRecord user = SecurityContext.requireUser();
        vaultService.deleteFolder(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VaultFileDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) String folderId,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "originalSize", defaultValue = "0") long originalSize,
            @RequestParam(value = "isClientEncrypted", defaultValue = "true") boolean isClientEncrypted,
            @RequestParam(value = "iv", required = false) String iv,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "checksum", required = false) String checksum) {
        UserRecord user = SecurityContext.requireUser();
        VaultFileDto dto = vaultService.uploadEncryptedFile(
                user, file, folderId, filename, originalSize, isClientEncrypted, iv, tag, checksum
        );
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String id) {
        UserRecord user = SecurityContext.requireUser();
        VaultFileDownload download = vaultService.downloadEncryptedFile(user, id);
        VaultFileRecord file = download.file();

        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");

        var response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.encryptedSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"; filename*=UTF-8''" + encodedFilename)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header("X-GhostNet-Encrypted", String.valueOf(file.isClientEncrypted()));

        if (file.encryptionIv() != null) {
            response.header("X-GhostNet-IV", file.encryptionIv());
        }
        if (file.encryptionTag() != null) {
            response.header("X-GhostNet-Tag", file.encryptionTag());
        }
        if (file.checksumSha256() != null) {
            response.header("X-GhostNet-SHA256", file.checksumSha256());
        }

        return response.body(download.resource());
    }

    @PutMapping("/files/{id}")
    public ResponseEntity<VaultFileDto> updateFile(
            @PathVariable String id,
            @Valid @RequestBody UpdateFileRequest req) {
        UserRecord user = SecurityContext.requireUser();
        return ResponseEntity.ok(vaultService.updateFile(user, id, req));
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable String id) {
        UserRecord user = SecurityContext.requireUser();
        vaultService.deleteFile(user, id);
        return ResponseEntity.noContent().build();
    }
}
