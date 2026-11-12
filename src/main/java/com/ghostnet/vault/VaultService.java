package com.ghostnet.vault;

import com.ghostnet.common.ResourceNotFoundException;
import com.ghostnet.common.ValidationException;
import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.storage.StorageLayoutService;
import com.ghostnet.user.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Service managing individual zero-knowledge encrypted vaults for registered users (Req Section 5).
 * Guests are strictly prohibited from accessing vault services (Req Section 3, Scenario E).
 */
@Service
public class VaultService {

    private static final Logger log = LoggerFactory.getLogger(VaultService.class);

    private final VaultRepository vaultRepository;
    private final StorageLayoutService storageLayoutService;

    public VaultService(VaultRepository vaultRepository, StorageLayoutService storageLayoutService) {
        this.vaultRepository = vaultRepository;
        this.storageLayoutService = storageLayoutService;
    }

    public void assertRegisteredUser(UserRecord user) {
        if (user == null) {
            throw new AuthenticationException("Authentication required to access private vault");
        }
        if (user.isGuest()) {
            throw new UnauthorizedException("Guests cannot access private vaults. Registration required.");
        }
    }

    public VaultSettingsRecord getOrCreateSettings(UserRecord user) {
        assertRegisteredUser(user);
        return vaultRepository.findSettingsByUserId(user.id()).orElseGet(() -> {
            VaultSettingsRecord initial = new VaultSettingsRecord(
                    user.id(),
                    false,
                    null,
                    null,
                    storageLayoutService.getUserVaultQuotaBytes(),
                    0L,
                    Instant.now(),
                    Instant.now()
            );
            vaultRepository.saveSettings(initial);
            return initial;
        });
    }

    public VaultStatusDto getStatus(UserRecord user) {
        VaultSettingsRecord settings = getOrCreateSettings(user);
        return VaultStatusDto.of(settings);
    }

    public VaultStatusDto getChallenge(UserRecord user) {
        return getStatus(user);
    }

    public VaultStatusDto initVault(UserRecord user, InitVaultRequest req) {
        assertRegisteredUser(user);
        VaultSettingsRecord existing = getOrCreateSettings(user);
        if (existing.isInitialized()) {
            throw new ValidationException("Vault is already initialized");
        }
        if (req.salt() == null || req.salt().isBlank() || req.authVerifier() == null || req.authVerifier().isBlank()) {
            throw new ValidationException("Salt and auth verifier must be provided");
        }

        VaultSettingsRecord updated = new VaultSettingsRecord(
                user.id(),
                true,
                req.salt().trim(),
                req.authVerifier().trim(),
                existing.quotaBytes(),
                existing.usedBytes(),
                existing.createdAt(),
                Instant.now()
        );
        vaultRepository.saveSettings(updated);
        log.info("Initialized zero-knowledge vault for registered operative: {}", user.username());
        return VaultStatusDto.of(updated);
    }

    public VaultTreeDto getTree(UserRecord user, String folderId, String search) {
        assertRegisteredUser(user);

        // If search query is active, return matching files across all folders
        if (search != null && !search.isBlank()) {
            List<VaultFileRecord> matched = vaultRepository.searchFiles(user.id(), search.trim());
            List<VaultFileDto> fileDtos = matched.stream().map(VaultFileDto::fromRecord).toList();
            return new VaultTreeDto(null, List.of(), List.of(), fileDtos, fileDtos.size(), 0);
        }

        String normFolderId = (folderId == null || folderId.isBlank() || folderId.equalsIgnoreCase("root")) ? null : folderId;
        VaultFolderDto currentFolderDto = null;
        List<VaultFolderDto> breadcrumbs = new ArrayList<>();

        if (normFolderId != null) {
            VaultFolderRecord curr = vaultRepository.findFolderById(normFolderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vault folder not found: " + normFolderId));
            if (!curr.userId().equals(user.id())) {
                throw new UnauthorizedException("Access denied to requested folder");
            }
            currentFolderDto = VaultFolderDto.fromRecord(curr);

            VaultFolderRecord runner = curr;
            while (runner != null) {
                breadcrumbs.add(0, VaultFolderDto.fromRecord(runner));
                if (runner.parentId() != null) {
                    runner = vaultRepository.findFolderById(runner.parentId()).orElse(null);
                } else {
                    runner = null;
                }
            }
        }

        List<VaultFolderDto> subfolders = vaultRepository.findFoldersByParent(user.id(), normFolderId)
                .stream().map(VaultFolderDto::fromRecord).toList();
        List<VaultFileDto> files = vaultRepository.findFilesByFolder(user.id(), normFolderId)
                .stream().map(VaultFileDto::fromRecord).toList();

        long totalFolders = vaultRepository.findAllFolders(user.id()).size();
        long totalFiles = vaultRepository.findRecentFiles(user.id(), 10000).size();

        return new VaultTreeDto(currentFolderDto, breadcrumbs, subfolders, files, totalFiles, totalFolders);
    }

    public VaultFolderDto createFolder(UserRecord user, CreateFolderRequest req) {
        assertRegisteredUser(user);
        if (req.name() == null || req.name().isBlank()) {
            throw new ValidationException("Folder name is required");
        }
        String cleanName = req.name().trim();
        String parentId = (req.parentId() == null || req.parentId().isBlank() || req.parentId().equalsIgnoreCase("root")) ? null : req.parentId();

        if (parentId != null) {
            VaultFolderRecord parent = vaultRepository.findFolderById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found: " + parentId));
            if (!parent.userId().equals(user.id())) {
                throw new UnauthorizedException("Parent folder does not belong to user");
            }
        }

        VaultFolderRecord folder = new VaultFolderRecord(
                UUID.randomUUID().toString(),
                user.id(),
                parentId,
                cleanName,
                Instant.now(),
                Instant.now()
        );
        vaultRepository.saveFolder(folder);
        log.info("Created vault folder '{}' for operative '{}'", cleanName, user.username());
        return VaultFolderDto.fromRecord(folder);
    }

    public VaultFolderDto updateFolder(UserRecord user, String folderId, UpdateFolderRequest req) {
        assertRegisteredUser(user);
        VaultFolderRecord existing = vaultRepository.findFolderById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        if (!existing.userId().equals(user.id())) {
            throw new UnauthorizedException("Access denied to folder");
        }

        String newName = (req.name() != null && !req.name().isBlank()) ? req.name().trim() : existing.name();
        final String targetParentId = (req.parentId() == null || req.parentId().isBlank() || req.parentId().equalsIgnoreCase("root"))
                ? null : req.parentId();

        if (targetParentId != null) {
            if (targetParentId.equals(folderId)) {
                throw new ValidationException("Folder cannot be its own parent");
            }
            VaultFolderRecord parent = vaultRepository.findFolderById(targetParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found: " + targetParentId));
            if (!parent.userId().equals(user.id())) {
                throw new UnauthorizedException("Target parent folder does not belong to user");
            }
        }

        VaultFolderRecord updated = new VaultFolderRecord(
                existing.id(),
                user.id(),
                targetParentId,
                newName,
                existing.createdAt(),
                Instant.now()
        );
        vaultRepository.updateFolder(updated);
        return VaultFolderDto.fromRecord(updated);
    }

    public void deleteFolder(UserRecord user, String folderId) {
        assertRegisteredUser(user);
        VaultFolderRecord folder = vaultRepository.findFolderById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        if (!folder.userId().equals(user.id())) {
            throw new UnauthorizedException("Access denied to folder");
        }

        // Collect all descendant folder IDs
        List<String> folderIdsToDelete = new ArrayList<>();
        folderIdsToDelete.add(folderId);

        Queue<String> queue = new ArrayDeque<>();
        queue.add(folderId);
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            List<VaultFolderRecord> children = vaultRepository.findFoldersByParent(user.id(), currentId);
            for (VaultFolderRecord child : children) {
                folderIdsToDelete.add(child.id());
                queue.add(child.id());
            }
        }

        // Delete all files in these folders
        for (String fId : folderIdsToDelete) {
            List<VaultFileRecord> files = vaultRepository.findFilesByFolder(user.id(), fId);
            for (VaultFileRecord file : files) {
                deletePhysicalFile(file);
                vaultRepository.deleteFile(file.id());
            }
            vaultRepository.deleteFolder(fId);
        }

        vaultRepository.updateUsedBytes(user.id(), vaultRepository.sumUsedBytesByUserId(user.id()));
        log.info("Deleted vault folder '{}' and all child entries for operative '{}'", folder.name(), user.username());
    }

    public VaultFileDto uploadEncryptedFile(
            UserRecord user,
            MultipartFile file,
            String folderId,
            String filename,
            long originalSize,
            boolean isClientEncrypted,
            String iv,
            String tag,
            String clientChecksum
    ) {
        assertRegisteredUser(user);
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Cannot upload empty file to vault");
        }

        VaultSettingsRecord settings = getOrCreateSettings(user);
        long encryptedSize = file.getSize();
        if (!settings.hasSpace(encryptedSize)) {
            throw new ValidationException(String.format(
                    "Vault storage quota exceeded (Limit: %d MB, Used: %d MB, Upload: %d MB)",
                    settings.quotaBytes() / (1024 * 1024),
                    settings.usedBytes() / (1024 * 1024),
                    encryptedSize / (1024 * 1024)
            ));
        }

        String normFolderId = (folderId == null || folderId.isBlank() || folderId.equalsIgnoreCase("root")) ? null : folderId;
        if (normFolderId != null) {
            VaultFolderRecord f = vaultRepository.findFolderById(normFolderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination folder not found: " + normFolderId));
            if (!f.userId().equals(user.id())) {
                throw new UnauthorizedException("Target folder does not belong to operative");
            }
        }

        String rawName = (filename != null && !filename.isBlank()) ? filename : file.getOriginalFilename();
        if (rawName == null || rawName.isBlank()) {
            rawName = "vault-file.bin";
        }
        String cleanName = rawName.replace("\\", "/");
        int lastSlash = cleanName.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleanName = cleanName.substring(lastSlash + 1);
        }
        cleanName = cleanName.replaceAll("[\\p{Cntrl}\\u0000]", "").trim();
        if (cleanName.isBlank()) cleanName = "vault-file.bin";

        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + ".vault";
        Path userDir = storageLayoutService.getUserVaultDir(user.id());
        Path targetPath = userDir.resolve(storedFilename).normalize();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytesWritten = 0;

            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {

                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    digest.update(buf, 0, read);
                    bytesWritten += read;
                }
            }

            String serverSha256 = HexFormat.of().formatHex(digest.digest());
            String relativeStoragePath = storageLayoutService.getBasePath().relativize(targetPath).toString();

            VaultFileRecord record = new VaultFileRecord(
                    fileId,
                    user.id(),
                    normFolderId,
                    cleanName,
                    storedFilename,
                    relativeStoragePath,
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                    originalSize > 0 ? originalSize : bytesWritten,
                    bytesWritten,
                    isClientEncrypted,
                    iv,
                    tag,
                    serverSha256,
                    Instant.now(),
                    Instant.now(),
                    Instant.now()
            );

            vaultRepository.saveFile(record);
            vaultRepository.updateUsedBytes(user.id(), vaultRepository.sumUsedBytesByUserId(user.id()));

            log.info("Stored vault file '{}' (Encrypted size: {} bytes, IV: {}) for operative '{}'",
                    cleanName, bytesWritten, iv, user.username());

            return VaultFileDto.fromRecord(record);

        } catch (NoSuchAlgorithmException | IOException e) {
            try { Files.deleteIfExists(targetPath); } catch (Exception ignored) {}
            log.error("Failed to store encrypted vault file: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to write encrypted file to disk: " + e.getMessage(), e);
        }
    }

    public VaultFileDownload downloadEncryptedFile(UserRecord user, String fileId) {
        assertRegisteredUser(user);
        VaultFileRecord file = vaultRepository.findFileById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault file not found: " + fileId));

        if (!file.userId().equals(user.id())) {
            throw new UnauthorizedException("Access denied: File belongs to another operative vault");
        }

        Path fullPath = storageLayoutService.getBasePath().resolve(file.storagePath()).normalize();
        if (!Files.exists(fullPath)) {
            throw new ResourceNotFoundException("Physical file missing on server storage");
        }

        vaultRepository.updateLastAccessed(fileId, Instant.now());
        return new VaultFileDownload(file, new FileSystemResource(fullPath));
    }

    public VaultFileDto updateFile(UserRecord user, String fileId, UpdateFileRequest req) {
        assertRegisteredUser(user);
        VaultFileRecord file = vaultRepository.findFileById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault file not found: " + fileId));

        if (!file.userId().equals(user.id())) {
            throw new UnauthorizedException("Access denied: File belongs to another operative vault");
        }

        String newName = (req.filename() != null && !req.filename().isBlank()) ? req.filename().trim() : file.filename();
        final String targetFolderId = (req.folderId() == null || req.folderId().isBlank() || req.folderId().equalsIgnoreCase("root"))
                ? null : req.folderId();

        if (targetFolderId != null) {
            VaultFolderRecord targetFolder = vaultRepository.findFolderById(targetFolderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination folder not found: " + targetFolderId));
            if (!targetFolder.userId().equals(user.id())) {
                throw new UnauthorizedException("Destination folder does not belong to operative");
            }
        }

        VaultFileRecord updated = new VaultFileRecord(
                file.id(),
                file.userId(),
                targetFolderId,
                newName,
                file.storedFilename(),
                file.storagePath(),
                file.mimeType(),
                file.fileSize(),
                file.encryptedSize(),
                file.isClientEncrypted(),
                file.encryptionIv(),
                file.encryptionTag(),
                file.checksumSha256(),
                file.createdAt(),
                Instant.now(),
                file.lastAccessedAt()
        );

        vaultRepository.updateFile(updated);
        return VaultFileDto.fromRecord(updated);
    }

    public void deleteFile(UserRecord user, String fileId) {
        assertRegisteredUser(user);
        VaultFileRecord file = vaultRepository.findFileById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault file not found: " + fileId));

        if (!file.userId().equals(user.id())) {
            throw new UnauthorizedException("Access denied: File belongs to another operative vault");
        }

        deletePhysicalFile(file);
        vaultRepository.deleteFile(fileId);
        vaultRepository.updateUsedBytes(user.id(), vaultRepository.sumUsedBytesByUserId(user.id()));
        log.info("Deleted vault file '{}' for operative '{}'", file.filename(), user.username());
    }

    private void deletePhysicalFile(VaultFileRecord file) {
        try {
            Path fullPath = storageLayoutService.getBasePath().resolve(file.storagePath()).normalize();
            Files.deleteIfExists(fullPath);
        } catch (Exception e) {
            log.warn("Failed to delete physical vault file: {}", file.storagePath(), e);
        }
    }
}
