package com.ghostnet.user;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of UserRepository using parameterized queries.
 */
@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UserRecord> rowMapper = (rs, rowNum) -> new UserRecord(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            UserRole.valueOf(rs.getString("role")),
            rs.getString("bio"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
    );

    @Override
    public void save(UserRecord user) {
        String sql = """
            INSERT INTO users (id, username, password_hash, display_name, role, bio, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                user.id(),
                user.username(),
                user.passwordHash(),
                user.displayName(),
                user.role().name(),
                user.bio(),
                user.createdAt().toString(),
                user.updatedAt().toString()
        );
    }

    @Override
    public void update(UserRecord user) {
        String sql = """
            UPDATE users SET password_hash = ?, display_name = ?, role = ?, bio = ?, updated_at = ?
            WHERE id = ?
            """;
        jdbcTemplate.update(sql,
                user.passwordHash(),
                user.displayName(),
                user.role().name(),
                user.bio(),
                Instant.now().toString(),
                user.id()
        );
    }

    @Override
    public Optional<UserRecord> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, username));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<UserRecord> findAll() {
        String sql = "SELECT * FROM users ORDER BY username ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<UserRecord> searchByUsernameOrDisplayName(String query, int limit) {
        String sql = "SELECT * FROM users WHERE LOWER(username) LIKE LOWER(?) OR LOWER(display_name) LIKE LOWER(?) LIMIT ?";
        String wildcard = "%" + query + "%";
        return jdbcTemplate.query(sql, rowMapper, wildcard, wildcard, limit);
    }

    @Override
    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count != null ? count : 0;
    }

    @Override
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }
}
