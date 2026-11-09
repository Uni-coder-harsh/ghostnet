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

        // 8. Performance indexes
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

        log.info("GhostNet database schema initialized and indexes verified.");
    }
}
