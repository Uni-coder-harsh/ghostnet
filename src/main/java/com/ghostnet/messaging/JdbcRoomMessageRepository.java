package com.ghostnet.messaging;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of RoomMessageRepository with pagination support and safe SQL.
 */
@Repository
public class JdbcRoomMessageRepository implements RoomMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<RoomMessageRecord> rowMapper = (rs, rowNum) -> new RoomMessageRecord(
            rs.getString("id"),
            rs.getString("room_id"),
            rs.getString("sender_id"),
            rs.getString("sender_display_name"),
            rs.getString("content"),
            Instant.parse(rs.getString("created_at"))
    );

    @Override
    public void save(RoomMessageRecord message) {
        String sql = """
            INSERT INTO room_messages (id, room_id, sender_id, sender_display_name, content, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                message.id(),
                message.roomId(),
                message.senderId(),
                message.senderDisplayName(),
                message.content(),
                message.createdAt().toString()
        );
    }

    @Override
    public Optional<RoomMessageRecord> findById(String id) {
        String sql = "SELECT * FROM room_messages WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<RoomMessageRecord> findHistoryByRoomId(String roomId, int limit, int offset) {
        String sql = """
            SELECT * FROM (
                SELECT * FROM room_messages
                WHERE room_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
            ) sub ORDER BY created_at ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, roomId, limit, offset);
    }

    @Override
    public long countTotal() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_messages", Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByRoomId(String roomId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_messages WHERE room_id = ?",
                Long.class,
                roomId
        );
        return count != null ? count : 0;
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM room_messages WHERE created_at < ?", cutoff.toString());
    }

    @Override
    public void deleteByRoomId(String roomId) {
        jdbcTemplate.update("DELETE FROM room_messages WHERE room_id = ?", roomId);
    }
}
