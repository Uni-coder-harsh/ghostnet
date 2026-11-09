package com.ghostnet.messaging;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of DirectMessageRepository with pagination support and safe SQL.
 */
@Repository
public class JdbcDirectMessageRepository implements DirectMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDirectMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DirectMessageRecord> rowMapper = (rs, rowNum) -> new DirectMessageRecord(
            rs.getString("id"),
            rs.getString("sender_id"),
            rs.getString("recipient_id"),
            rs.getString("content"),
            MessageStatus.valueOf(rs.getString("status")),
            Instant.parse(rs.getString("created_at"))
    );

    @Override
    public void save(DirectMessageRecord message) {
        String sql = """
            INSERT INTO direct_messages (id, sender_id, recipient_id, content, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                message.id(),
                message.senderId(),
                message.recipientId(),
                message.content(),
                message.status().name(),
                message.createdAt().toString()
        );
    }

    @Override
    public Optional<DirectMessageRecord> findById(String id) {
        String sql = "SELECT * FROM direct_messages WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<DirectMessageRecord> findConversationHistory(String user1, String user2, int limit, int offset) {
        String sql = """
            SELECT * FROM (
                SELECT * FROM direct_messages
                WHERE (sender_id = ? AND recipient_id = ?)
                   OR (sender_id = ? AND recipient_id = ?)
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
            ) sub ORDER BY created_at ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, user1, user2, user2, user1, limit, offset);
    }

    @Override
    public void updateStatus(String messageId, MessageStatus status) {
        jdbcTemplate.update("UPDATE direct_messages SET status = ? WHERE id = ?", status.name(), messageId);
    }

    @Override
    public void markConversationDelivered(String recipientId, String senderId) {
        String sql = """
            UPDATE direct_messages
            SET status = 'DELIVERED'
            WHERE recipient_id = ? AND sender_id = ? AND status = 'SENT'
            """;
        jdbcTemplate.update(sql, recipientId, senderId);
    }

    @Override
    public void markConversationRead(String recipientId, String senderId) {
        String sql = """
            UPDATE direct_messages
            SET status = 'READ'
            WHERE recipient_id = ? AND sender_id = ? AND status IN ('SENT', 'DELIVERED')
            """;
        jdbcTemplate.update(sql, recipientId, senderId);
    }

    @Override
    public long countTotal() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM direct_messages", Long.class);
        return count != null ? count : 0;
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM direct_messages WHERE created_at < ?", cutoff.toString());
    }

    @Override
    public void deleteConversation(String user1, String user2) {
        String sql = """
            DELETE FROM direct_messages
            WHERE (sender_id = ? AND recipient_id = ?)
               OR (sender_id = ? AND recipient_id = ?)
            """;
        jdbcTemplate.update(sql, user1, user2, user2, user1);
    }
}
