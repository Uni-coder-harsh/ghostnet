package com.ghostnet.vault;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcVaultRepository implements VaultRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<VaultSettingsRecord> settingsMapper = new SettingsRowMapper();
    private final RowMapper<VaultFolderRecord> folderMapper = new FolderRowMapper();
    private final RowMapper<VaultFileRecord> fileMapper = new FileRowMapper();

    public JdbcVaultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 1. Settings & Quotas
    @Override
    public void saveSettings(VaultSettingsRecord s) {
        String sql = """
            INSERT INTO vault_settings (user_id, is_initialized, salt, auth_verifier, quota_bytes, used_bytes, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                is_initialized = excluded.is_initialized,
                salt = excluded.salt,
                auth_verifier = excluded.auth_verifier,
                quota_bytes = excluded.quota_bytes,
                used_bytes = excluded.used_bytes,
                updated_at = excluded.updated_at
            """;
        jdbcTemplate.update(sql,
                s.userId(),
                s.isInitialized() ? 1 : 0,
                s.salt() != null ? s.salt() : "",
                s.authVerifier() != null ? s.authVerifier() : "",
                s.quotaBytes(),
                s.usedBytes(),
                s.createdAt().toString(),
                s.updatedAt().toString()
        );
    }

    @Override
    public Optional<VaultSettingsRecord> findSettingsByUserId(String userId) {
        String sql = "SELECT * FROM vault_settings WHERE user_id = ?";
        List<VaultSettingsRecord> list = jdbcTemplate.query(sql, settingsMapper, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public void updateUsedBytes(String userId, long usedBytes) {
        String sql = "UPDATE vault_settings SET used_bytes = ?, updated_at = ? WHERE user_id = ?";
        jdbcTemplate.update(sql, usedBytes, Instant.now().toString(), userId);
    }

    // 2. Folders
    @Override
    public void saveFolder(VaultFolderRecord f) {
        String sql = """
            INSERT INTO vault_folders (id, user_id, parent_id, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                parent_id = excluded.parent_id,
                name = excluded.name,
                updated_at = excluded.updated_at
            """;
        jdbcTemplate.update(sql,
                f.id(),
                f.userId(),
                f.parentId(),
                f.name(),
                f.createdAt().toString(),
                f.updatedAt().toString()
        );
    }

    @Override
    public Optional<VaultFolderRecord> findFolderById(String id) {
        String sql = "SELECT * FROM vault_folders WHERE id = ?";
        List<VaultFolderRecord> list = jdbcTemplate.query(sql, folderMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<VaultFolderRecord> findFoldersByParent(String userId, String parentId) {
        if (parentId == null || parentId.isBlank()) {
            String sql = "SELECT * FROM vault_folders WHERE user_id = ? AND parent_id IS NULL ORDER BY name ASC";
            return jdbcTemplate.query(sql, folderMapper, userId);
        } else {
            String sql = "SELECT * FROM vault_folders WHERE user_id = ? AND parent_id = ? ORDER BY name ASC";
            return jdbcTemplate.query(sql, folderMapper, userId, parentId);
        }
    }

    @Override
    public List<VaultFolderRecord> findAllFolders(String userId) {
        String sql = "SELECT * FROM vault_folders WHERE user_id = ? ORDER BY name ASC";
        return jdbcTemplate.query(sql, folderMapper, userId);
    }

    @Override
    public void updateFolder(VaultFolderRecord f) {
        String sql = "UPDATE vault_folders SET parent_id = ?, name = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, f.parentId(), f.name(), f.updatedAt().toString(), f.id());
    }

    @Override
    public void deleteFolder(String id) {
        String sql = "DELETE FROM vault_folders WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    // 3. Files
    @Override
    public void saveFile(VaultFileRecord f) {
        String sql = """
            INSERT INTO vault_files (
                id, user_id, folder_id, filename, stored_filename, storage_path,
                mime_type, file_size, encrypted_size, is_client_encrypted,
                encryption_iv, encryption_tag, checksum_sha256, created_at,
                updated_at, last_accessed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                folder_id = excluded.folder_id,
                filename = excluded.filename,
                updated_at = excluded.updated_at,
                last_accessed_at = excluded.last_accessed_at
            """;
        jdbcTemplate.update(sql,
                f.id(),
                f.userId(),
                f.folderId(),
                f.filename(),
                f.storedFilename(),
                f.storagePath(),
                f.mimeType(),
                f.fileSize(),
                f.encryptedSize(),
                f.isClientEncrypted() ? 1 : 0,
                f.encryptionIv(),
                f.encryptionTag(),
                f.checksumSha256(),
                f.createdAt().toString(),
                f.updatedAt().toString(),
                f.lastAccessedAt() != null ? f.lastAccessedAt().toString() : null
        );
    }

    @Override
    public Optional<VaultFileRecord> findFileById(String id) {
        String sql = "SELECT * FROM vault_files WHERE id = ?";
        List<VaultFileRecord> list = jdbcTemplate.query(sql, fileMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<VaultFileRecord> findFilesByFolder(String userId, String folderId) {
        if (folderId == null || folderId.isBlank()) {
            String sql = "SELECT * FROM vault_files WHERE user_id = ? AND folder_id IS NULL ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, fileMapper, userId);
        } else {
            String sql = "SELECT * FROM vault_files WHERE user_id = ? AND folder_id = ? ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, fileMapper, userId, folderId);
        }
    }

    @Override
    public List<VaultFileRecord> searchFiles(String userId, String query) {
        String sql = "SELECT * FROM vault_files WHERE user_id = ? AND filename LIKE ? ORDER BY created_at DESC LIMIT 50";
        return jdbcTemplate.query(sql, fileMapper, userId, "%" + query.trim() + "%");
    }

    @Override
    public List<VaultFileRecord> findRecentFiles(String userId, int limit) {
        String sql = "SELECT * FROM vault_files WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, fileMapper, userId, limit);
    }

    @Override
    public void updateFile(VaultFileRecord f) {
        String sql = "UPDATE vault_files SET folder_id = ?, filename = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, f.folderId(), f.filename(), f.updatedAt().toString(), f.id());
    }

    @Override
    public void updateLastAccessed(String id, Instant now) {
        String sql = "UPDATE vault_files SET last_accessed_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, now.toString(), id);
    }

    @Override
    public void deleteFile(String id) {
        String sql = "DELETE FROM vault_files WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public long sumUsedBytesByUserId(String userId) {
        String sql = "SELECT COALESCE(SUM(encrypted_size), 0) FROM vault_files WHERE user_id = ?";
        Long sum = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return sum != null ? sum : 0L;
    }

    // Row Mappers
    private static class SettingsRowMapper implements RowMapper<VaultSettingsRecord> {
        @Override
        public VaultSettingsRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            String salt = rs.getString("salt");
            String authVerifier = rs.getString("auth_verifier");
            return new VaultSettingsRecord(
                    rs.getString("user_id"),
                    rs.getInt("is_initialized") == 1,
                    (salt != null && !salt.isEmpty()) ? salt : null,
                    (authVerifier != null && !authVerifier.isEmpty()) ? authVerifier : null,
                    rs.getLong("quota_bytes"),
                    rs.getLong("used_bytes"),
                    Instant.parse(rs.getString("created_at")),
                    Instant.parse(rs.getString("updated_at"))
            );
        }
    }

    private static class FolderRowMapper implements RowMapper<VaultFolderRecord> {
        @Override
        public VaultFolderRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new VaultFolderRecord(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("parent_id"),
                    rs.getString("name"),
                    Instant.parse(rs.getString("created_at")),
                    Instant.parse(rs.getString("updated_at"))
            );
        }
    }

    private static class FileRowMapper implements RowMapper<VaultFileRecord> {
        @Override
        public VaultFileRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            String accStr = rs.getString("last_accessed_at");
            return new VaultFileRecord(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("folder_id"),
                    rs.getString("filename"),
                    rs.getString("stored_filename"),
                    rs.getString("storage_path"),
                    rs.getString("mime_type"),
                    rs.getLong("file_size"),
                    rs.getLong("encrypted_size"),
                    rs.getInt("is_client_encrypted") == 1,
                    rs.getString("encryption_iv"),
                    rs.getString("encryption_tag"),
                    rs.getString("checksum_sha256"),
                    Instant.parse(rs.getString("created_at")),
                    Instant.parse(rs.getString("updated_at")),
                    accStr != null ? Instant.parse(accStr) : null
            );
        }
    }
}
