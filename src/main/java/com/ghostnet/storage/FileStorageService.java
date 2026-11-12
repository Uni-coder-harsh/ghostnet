package com.ghostnet.storage;

import com.ghostnet.common.ValidationException;
import com.ghostnet.files.ChatAttachmentRepository;
import com.ghostnet.user.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HexFormat;
import java.util.UUID;

/**
 * Low-level safe file streaming, SHA-256 integrity verification,
 * quota enforcement, and path traversal protection (Req Section 6).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final StorageLayoutService storageLayoutService;
    private final ChatAttachmentRepository chatAttachmentRepository;

    public FileStorageService(StorageLayoutService storageLayoutService,
                              ChatAttachmentRepository chatAttachmentRepository) {
        this.storageLayoutService = storageLayoutService;
        this.chatAttachmentRepository = chatAttachmentRepository;
    }

    public StoredFileResult storeChatAttachment(UserRecord uploader, MultipartFile file, String convId) {
        validateFile(file);

        // Enforce guest storage quota if guest
        if (uploader.isGuest()) {
            long currentGuestUsage = chatAttachmentRepository.sumGuestStorageBytes();
            if (currentGuestUsage + file.getSize() > storageLayoutService.getGuestTempQuotaBytes()) {
                throw new ValidationException("Temporary guest storage quota exceeded (Max " +
                        (storageLayoutService.getGuestTempQuotaBytes() / (1024 * 1024)) + "MB)");
            }
        }

        String safeOriginalName = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(safeOriginalName);
        String storedFilename = UUID.randomUUID().toString() + extension;

        Path targetDir = uploader.isGuest()
                ? storageLayoutService.getGuestUploadsPath()
                : storageLayoutService.getChatFilesDir(convId);

        Path targetPath = targetDir.resolve(storedFilename).normalize();

        // Enforce path traversal defense
        if (!targetPath.startsWith(storageLayoutService.getBasePath())) {
            throw new ValidationException("Security violation: Path traversal detected");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytesWritten = 0;

            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    bytesWritten += read;
                }
            }

            String sha256 = HexFormat.of().formatHex(digest.digest());
            String relativeStoragePath = storageLayoutService.getBasePath().relativize(targetPath).toString();
            String mimeType = probeMimeType(file, safeOriginalName);

            log.info("Stored chat attachment '{}' (Size: {} bytes, SHA-256: {})", safeOriginalName, bytesWritten, sha256);

            return new StoredFileResult(
                    safeOriginalName,
                    storedFilename,
                    relativeStoragePath,
                    targetPath,
                    mimeType,
                    bytesWritten,
                    sha256
            );
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to store file: {}", e.getMessage(), e);
            try { Files.deleteIfExists(targetPath); } catch (Exception ignored) {}
            throw new IllegalStateException("Failed to write file to disk: " + e.getMessage(), e);
        }
    }

    public boolean deletePhysicalFile(String relativePath) {
        try {
            Path path = storageLayoutService.getBasePath().resolve(relativePath).normalize();
            if (path.startsWith(storageLayoutService.getBasePath())) {
                return Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            log.warn("Failed to delete physical file '{}': {}", relativePath, e.getMessage());
        }
        return false;
    }

    public Path resolvePath(String relativePath) {
        Path resolved = storageLayoutService.getBasePath().resolve(relativePath).normalize();
        if (!resolved.startsWith(storageLayoutService.getBasePath())) {
            throw new ValidationException("Security violation: Path traversal attempted");
        }
        return resolved;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Cannot upload empty file");
        }

        long maxBytes = storageLayoutService.getMaxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new ValidationException("File size (" + (file.getSize() / (1024 * 1024)) +
                    "MB) exceeds maximum permitted limit (" + (maxBytes / (1024 * 1024)) + "MB)");
        }
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment.dat";
        }

        // Strip path traversal attempts and directory separators
        String clean = filename.replace("\\", "/");
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }

        // Remove dangerous control characters or null bytes
        clean = clean.replaceAll("[\\p{Cntrl}\\u0000]", "").trim();

        // Remove leading dots or path traversal patterns
        clean = clean.replaceAll("^\\.+", "");

        if (clean.isBlank()) {
            clean = "attachment.dat";
        }

        // Limit length
        if (clean.length() > 200) {
            String ext = extractExtension(clean);
            clean = clean.substring(0, 190) + ext;
        }

        return clean;
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot).toLowerCase();
            if (ext.matches("^\\.[a-z0-9]{1,10}$")) {
                return ext;
            }
        }
        return ".bin";
    }

    private String probeMimeType(MultipartFile file, String filename) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.equals("application/octet-stream")) {
            return contentType;
        }

        try {
            String probed = Files.probeContentType(Path.of(filename));
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (Exception ignored) {}

        return "application/octet-stream";
    }

    public record StoredFileResult(
            String originalFilename,
            String storedFilename,
            String relativeStoragePath,
            Path fullPath,
            String mimeType,
            long fileSize,
            String checksumSha256
    ) {}
}
