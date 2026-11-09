package com.ghostnet.room;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of RoomMembershipRepository using parameterized queries.
 */
@Repository
public class JdbcRoomMembershipRepository implements RoomMembershipRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<RoomMemberRecord> rowMapper = (rs, rowNum) -> new RoomMemberRecord(
            rs.getString("room_id"),
            rs.getString("user_id"),
            RoomMemberRole.valueOf(rs.getString("role")),
            Instant.parse(rs.getString("joined_at"))
    );

    @Override
    public void addMember(RoomMemberRecord member) {
        String sql = """
            INSERT OR REPLACE INTO room_members (room_id, user_id, role, joined_at)
            VALUES (?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                member.roomId(),
                member.userId(),
                member.role().name(),
                member.joinedAt().toString()
        );
    }

    @Override
    public void removeMember(String roomId, String userId) {
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ? AND user_id = ?", roomId, userId);
    }

    @Override
    public List<RoomMemberRecord> findByRoomId(String roomId) {
        String sql = "SELECT * FROM room_members WHERE room_id = ? ORDER BY joined_at ASC";
        return jdbcTemplate.query(sql, rowMapper, roomId);
    }

    @Override
    public List<RoomMemberRecord> findByUserId(String userId) {
        String sql = "SELECT * FROM room_members WHERE user_id = ? ORDER BY joined_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    @Override
    public Optional<RoomMemberRecord> findMembership(String roomId, String userId) {
        String sql = "SELECT * FROM room_members WHERE room_id = ? AND user_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, roomId, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public int countMembers(String roomId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ?",
                Integer.class,
                roomId
        );
        return count != null ? count : 0;
    }

    @Override
    public boolean isMember(String roomId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_members WHERE room_id = ? AND user_id = ?",
                Integer.class,
                roomId,
                userId
        );
        return count != null && count > 0;
    }

    @Override
    public void updateRole(String roomId, String userId, RoomMemberRole role) {
        jdbcTemplate.update(
                "UPDATE room_members SET role = ? WHERE room_id = ? AND user_id = ?",
                role.name(),
                roomId,
                userId
        );
    }

    @Override
    public void deleteByRoomId(String roomId) {
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
    }
}
