package com.ghostnet.persistence;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Initializes and migrates the GhostNet SQLite database schema.
 * Ensures all necessary tables and performance indexes are in place before services start.
 */
@Service
public class DatabaseMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        log.info("Running GhostNet database migrations...");

        // 1. Users table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(36) PRIMARY KEY,
                username VARCHAR(64) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                display_name VARCHAR(100) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
                bio TEXT DEFAULT '',
                created_at VARCHAR(40) NOT NULL,
                updated_at VARCHAR(40) NOT NULL
            );
            """);

        // 2. User sessions table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_sessions (
                token VARCHAR(64) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                expires_at VARCHAR(40) NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                client_ip VARCHAR(45),
                user_agent TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 3. Rooms table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS rooms (
                id VARCHAR(36) PRIMARY KEY,
                code VARCHAR(16) UNIQUE NOT NULL,
                name VARCHAR(100) NOT NULL,
                description TEXT DEFAULT '',
                is_private INTEGER NOT NULL DEFAULT 0,
                password_hash VARCHAR(255),
                capacity INTEGER NOT NULL DEFAULT 50,
                owner_id VARCHAR(36) NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 4. Room members table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS room_members (
                room_id VARCHAR(36) NOT NULL,
                user_id VARCHAR(36) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
                joined_at VARCHAR(40) NOT NULL,
                PRIMARY KEY (room_id, user_id),
                FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 5. Direct messages table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS direct_messages (
                id VARCHAR(36) PRIMARY KEY,
                sender_id VARCHAR(36) NOT NULL,
                recipient_id VARCHAR(36) NOT NULL,
                content TEXT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'SENT',
                created_at VARCHAR(40) NOT NULL,
                FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 6. Room messages table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS room_messages (
                id VARCHAR(36) PRIMARY KEY,
                room_id VARCHAR(36) NOT NULL,
                sender_id VARCHAR(36) NOT NULL,
                sender_display_name VARCHAR(100) NOT NULL,
                content TEXT NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
                FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 7. User preferences table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_preferences (
                user_id VARCHAR(36) NOT NULL,
                pref_key VARCHAR(100) NOT NULL,
                pref_value TEXT,
                PRIMARY KEY (user_id, pref_key),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 8. Guest identities table (Req Section 3.B - Ephemeral guest device token recognition)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS guest_identities (
                id VARCHAR(36) PRIMARY KEY,
                device_credential VARCHAR(64) UNIQUE NOT NULL,
                display_name VARCHAR(100) NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                last_seen_at VARCHAR(40) NOT NULL,
                expires_at VARCHAR(40) NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1
            );
            """);

        // 9. Conversation File Attachments table (Req Section 4 - File sharing in conversations)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS chat_attachments (
                id VARCHAR(36) PRIMARY KEY,
                message_id VARCHAR(36),
                uploader_id VARCHAR(36) NOT NULL,
                uploader_role VARCHAR(20) NOT NULL,
                conversation_type VARCHAR(20) NOT NULL,
                conversation_id VARCHAR(36) NOT NULL,
                original_filename VARCHAR(255) NOT NULL,
                stored_filename VARCHAR(255) NOT NULL,
                storage_path VARCHAR(500) NOT NULL,
                mime_type VARCHAR(100) NOT NULL,
                file_size BIGINT NOT NULL,
                checksum_sha256 VARCHAR(64) NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                expires_at VARCHAR(40),
                download_count INTEGER NOT NULL DEFAULT 0
            );
            """);

        // 10. Private Vault Settings table (Req Section 5 - Zero-knowledge personal vault metadata)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vault_settings (
                user_id VARCHAR(36) PRIMARY KEY,
                is_initialized INTEGER NOT NULL DEFAULT 0,
                salt VARCHAR(64),
                auth_verifier VARCHAR(128),
                quota_bytes BIGINT NOT NULL DEFAULT 1073741824,
                used_bytes BIGINT NOT NULL DEFAULT 0,
                created_at VARCHAR(40) NOT NULL,
                updated_at VARCHAR(40) NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """);

        // 11. Private Vault Folders table (Req Section 5 - Folder hierarchy)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vault_folders (
                id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                parent_id VARCHAR(36),
                name VARCHAR(255) NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                updated_at VARCHAR(40) NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (parent_id) REFERENCES vault_folders(id) ON DELETE CASCADE
            );
            """);

        // 12. Private Vault Files table (Req Section 5 - Authenticated encrypted personal files)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vault_files (
                id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                folder_id VARCHAR(36),
                filename VARCHAR(255) NOT NULL,
                stored_filename VARCHAR(255) NOT NULL,
                storage_path VARCHAR(500) NOT NULL,
                mime_type VARCHAR(100) NOT NULL,
                file_size BIGINT NOT NULL,
                encrypted_size BIGINT NOT NULL,
                is_client_encrypted INTEGER NOT NULL DEFAULT 1,
                encryption_iv VARCHAR(64) NOT NULL,
                encryption_tag VARCHAR(64),
                checksum_sha256 VARCHAR(64) NOT NULL,
                created_at VARCHAR(40) NOT NULL,
                updated_at VARCHAR(40) NOT NULL,
                last_accessed_at VARCHAR(40),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (folder_id) REFERENCES vault_folders(id) ON DELETE SET NULL
            );
            """);

        // 13. File Transfers Audit table (Req Section 7 - File Transfer Center telemetry)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS transfers (
                id VARCHAR(36) PRIMARY KEY,
                transfer_type VARCHAR(20) NOT NULL,
                category VARCHAR(20) NOT NULL,
                user_id VARCHAR(36) NOT NULL,
                filename VARCHAR(255) NOT NULL,
                file_size BIGINT NOT NULL,
                bytes_transferred BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL,
                speed_bytes_per_sec BIGINT DEFAULT 0,
                error_message TEXT,
                created_at VARCHAR(40) NOT NULL,
                completed_at VARCHAR(40)
            );
            """);

        // 14. Performance indexes
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sessions_token ON user_sessions(token);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user ON user_sessions(user_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rooms_code ON rooms(code);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_room_members_user ON room_members(user_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dm_pair ON direct_messages(sender_id, recipient_id, created_at);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dm_recipient ON direct_messages(recipient_id, created_at);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dm_created ON direct_messages(created_at);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_room_messages_room_time ON room_messages(room_id, created_at);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_room_messages_created ON room_messages(created_at);");

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_guest_credential ON guest_identities(device_credential);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_guest_expires ON guest_identities(expires_at);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_attachments_msg ON chat_attachments(message_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_attachments_conv ON chat_attachments(conversation_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_attachments_expires ON chat_attachments(expires_at);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_vault_folders_user ON vault_folders(user_id, parent_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_vault_files_user ON vault_files(user_id, folder_id);");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_transfers_user ON transfers(user_id);");

        log.info("GhostNet database schema initialized and indexes verified.");
    }
}
