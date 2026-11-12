package com.ghostnet.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the physical filesystem layout under the data directory (Req Section 6).
 * Segregates user vaults, chat attachments, temporary guest uploads, and transfer buffers.
 */
@Service
public class StorageLayoutService {

    private static final Logger log = LoggerFactory.getLogger(StorageLayoutService.class);

    @Value("${ghostnet.storage.base-path:data}")
    private String basePathStr;

    @Value("${ghostnet.storage.max-file-size-mb:100}")
    private long maxFileSizeMb;

    @Value("${ghostnet.storage.user-vault-quota-mb:1024}")
    private long userVaultQuotaMb;

    @Value("${ghostnet.storage.guest-temp-quota-mb:256}")
    private long guestTempQuotaMb;

    @Value("${ghostnet.storage.guest-expiration-hours:24}")
    private int guestExpirationHours;

    private Path basePath;
    private Path vaultsPath;
    private Path chatFilesPath;
    private Path guestUploadsPath;
    private Path transferBuffersPath;
    private Path backupsPath;

    @PostConstruct
    public void init() {
        this.basePath = Paths.get(basePathStr).toAbsolutePath().normalize();
        this.vaultsPath = basePath.resolve("vaults");
        this.chatFilesPath = basePath.resolve("chat-files");
        this.guestUploadsPath = basePath.resolve("temporary").resolve("guest-uploads");
        this.transferBuffersPath = basePath.resolve("temporary").resolve("transfer-buffers");
        this.backupsPath = basePath.resolve("backups");

        createDirectories(basePath);
        createDirectories(vaultsPath);
        createDirectories(chatFilesPath);
        createDirectories(guestUploadsPath);
        createDirectories(transferBuffersPath);
        createDirectories(backupsPath);

        log.info("GhostNet Storage Layout initialized at: {}", basePath);
    }

    private void createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.debug("Created storage directory: {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to initialize storage directory: {}", path, e);
            throw new IllegalStateException("Storage initialization failure: " + path, e);
        }
    }

    public Path getBasePath() {
        return basePath;
    }

    public Path getVaultsPath() {
        return vaultsPath;
    }

    public Path getUserVaultDir(String userId) {
        // Sanitize userId to prevent any path manipulation
        String cleanId = userId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path userDir = vaultsPath.resolve(cleanId);
        createDirectories(userDir);
        return userDir;
    }

    public Path getChatFilesDir(String conversationId) {
        String cleanId = conversationId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path convDir = chatFilesPath.resolve(cleanId);
        createDirectories(convDir);
        return convDir;
    }

    public Path getGuestUploadsPath() {
        return guestUploadsPath;
    }

    public Path getTransferBuffersPath() {
        return transferBuffersPath;
    }

    public Path getBackupsPath() {
        return backupsPath;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeMb * 1024 * 1024;
    }

    public long getUserVaultQuotaBytes() {
        return userVaultQuotaMb * 1024 * 1024;
    }

    public long getGuestTempQuotaBytes() {
        return guestTempQuotaMb * 1024 * 1024;
    }

    public int getGuestExpirationHours() {
        return guestExpirationHours;
    }

    public StorageStats getDiskStorageStats() {
        try {
            java.io.File file = basePath.toFile();
            long freeBytes = file.getFreeSpace();
            long totalBytes = file.getTotalSpace();
            long usableBytes = file.getUsableSpace();
            return new StorageStats(totalBytes, freeBytes, usableBytes);
        } catch (Exception e) {
            return new StorageStats(0, 0, 0);
        }
    }

    public record StorageStats(long totalBytes, long freeBytes, long usableBytes) {}
}
