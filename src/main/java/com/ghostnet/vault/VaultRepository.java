package com.ghostnet.vault;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VaultRepository {
    // Settings & Quota
    void saveSettings(VaultSettingsRecord settings);
    Optional<VaultSettingsRecord> findSettingsByUserId(String userId);
    void updateUsedBytes(String userId, long usedBytes);

    // Folders
    void saveFolder(VaultFolderRecord folder);
    Optional<VaultFolderRecord> findFolderById(String id);
    List<VaultFolderRecord> findFoldersByParent(String userId, String parentId);
    List<VaultFolderRecord> findAllFolders(String userId);
    void updateFolder(VaultFolderRecord folder);
    void deleteFolder(String id);

    // Files
    void saveFile(VaultFileRecord file);
    Optional<VaultFileRecord> findFileById(String id);
    List<VaultFileRecord> findFilesByFolder(String userId, String folderId);
    List<VaultFileRecord> searchFiles(String userId, String query);
    List<VaultFileRecord> findRecentFiles(String userId, int limit);
    void updateFile(VaultFileRecord file);
    void updateLastAccessed(String id, Instant now);
    void deleteFile(String id);
    long sumUsedBytesByUserId(String userId);
}
