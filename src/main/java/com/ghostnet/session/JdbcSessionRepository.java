package com.ghostnet.session;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of SessionRepository.
 */
@Repository
public class JdbcSessionRepository implements SessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SessionRecord> rowMapper = (rs, rowNum) -> new SessionRecord(
            rs.getString("token"),
            rs.getString("user_id"),
            Instant.parse(rs.getString("expires_at")),
            Instant.parse(rs.getString("created_at")),
            rs.getString("client_ip"),
            rs.getString("user_agent")
    );

    @Override
    public void save(SessionRecord session) {
        String sql = """
            INSERT INTO user_sessions (token, user_id, expires_at, created_at, client_ip, user_agent)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                session.token(),
                session.userId(),
                session.expiresAt().toString(),
                session.createdAt().toString(),
                session.clientIp(),
                session.userAgent()
        );
    }

    @Override
    public Optional<SessionRecord> findByToken(String token) {
        String sql = "SELECT * FROM user_sessions WHERE token = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, token));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SessionRecord> findByUserId(String userId) {
        String sql = "SELECT * FROM user_sessions WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    @Override
    public List<SessionRecord> findAllActive() {
        String sql = "SELECT * FROM user_sessions WHERE expires_at > ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, Instant.now().toString());
    }

    @Override
    public void deleteByToken(String token) {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE token = ?", token);
    }

    @Override
    public void deleteByUserId(String userId) {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
    }

    @Override
    public void deleteExpired() {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE expires_at <= ?", Instant.now().toString());
    }

    @Override
    public long countActive() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_sessions WHERE expires_at > ?",
                Long.class,
                Instant.now().toString()
        );
        return count != null ? count : 0;
    }
}
