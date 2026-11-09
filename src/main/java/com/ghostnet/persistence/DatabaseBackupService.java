package com.ghostnet.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides database backup and recovery operations for GhostNet SQLite storage (Req 191, 192).
 * Uses SQLite VACUUM INTO for online, lock-free, atomic snapshots without stopping the server.
 */
@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${ghostnet.database.path:data/ghostnet.db}")
    private String dbPath;

    public DatabaseBackupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates an atomic point-in-time backup snapshot of the active SQLite database.
     */
    public BackupResult createBackup() {
        File dbFile = new File(dbPath);
        File backupDir = new File(dbFile.getParentFile() != null ? dbFile.getParentFile() : new File("data"), "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFileName = "ghostnet_backup_" + timestamp + ".db";
        File targetBackupFile = new File(backupDir, backupFileName);

        try {
            // VACUUM INTO performs an atomic online backup snapshot in SQLite
            String sql = "VACUUM INTO '" + targetBackupFile.getAbsolutePath().replace("'", "''") + "';";
            jdbcTemplate.execute(sql);
            long sizeBytes = targetBackupFile.length();
            log.info("Database backup created successfully: {} ({} bytes)", targetBackupFile.getAbsolutePath(), sizeBytes);
            return new BackupResult(true, targetBackupFile.getName(), targetBackupFile.getAbsolutePath(), sizeBytes, "Backup created successfully");
        } catch (Exception e) {
            log.error("Failed to create database backup using VACUUM INTO: {}", e.getMessage(), e);
            // Fallback: file copy if VACUUM INTO fails
            try {
                Files.copy(dbFile.toPath(), targetBackupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                long sizeBytes = targetBackupFile.length();
                log.info("Database backup created via fallback file copy: {} ({} bytes)", targetBackupFile.getAbsolutePath(), sizeBytes);
                return new BackupResult(true, targetBackupFile.getName(), targetBackupFile.getAbsolutePath(), sizeBytes, "Backup created via file snapshot");
            } catch (IOException ioe) {
                log.error("Fallback backup also failed: {}", ioe.getMessage(), ioe);
                return new BackupResult(false, null, null, 0, "Backup failed: " + e.getMessage());
            }
        }
    }

    /**
     * Lists existing backup snapshots.
     */
    public List<BackupInfo> listBackups() {
        File dbFile = new File(dbPath);
        File backupDir = new File(dbFile.getParentFile() != null ? dbFile.getParentFile() : new File("data"), "backups");
        if (!backupDir.exists()) {
            return List.of();
        }

        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("ghostnet_backup_") && name.endsWith(".db"));
        if (files == null) {
            return List.of();
        }

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        List<BackupInfo> result = new ArrayList<>();
        for (File f : files) {
            result.add(new BackupInfo(f.getName(), f.getAbsolutePath(), f.length(), f.lastModified()));
        }
        return result;
    }

    public record BackupResult(boolean success, String filename, String absolutePath, long sizeBytes, String message) {}
    public record BackupInfo(String filename, String absolutePath, long sizeBytes, long lastModifiedMillis) {}
}
