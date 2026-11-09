package com.ghostnet.room;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of RoomRepository using parameterized queries.
 */
@Repository
public class JdbcRoomRepository implements RoomRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<RoomRecord> rowMapper = (rs, rowNum) -> new RoomRecord(
            rs.getString("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("is_private") == 1,
            rs.getString("password_hash"),
            rs.getInt("capacity"),
            rs.getString("owner_id"),
            Instant.parse(rs.getString("created_at"))
    );

    @Override
    public void save(RoomRecord room) {
        String sql = """
            INSERT INTO rooms (id, code, name, description, is_private, password_hash, capacity, owner_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                room.id(),
                room.code().toUpperCase(),
                room.name(),
                room.description(),
                room.isPrivate() ? 1 : 0,
                room.passwordHash(),
                room.capacity(),
                room.ownerId(),
                room.createdAt().toString()
        );
    }

    @Override
    public void update(RoomRecord room) {
        String sql = """
            UPDATE rooms SET name = ?, description = ?, is_private = ?, password_hash = ?, capacity = ?, owner_id = ?
            WHERE id = ?
            """;
        jdbcTemplate.update(sql,
                room.name(),
                room.description(),
                room.isPrivate() ? 1 : 0,
                room.passwordHash(),
                room.capacity(),
                room.ownerId(),
                room.id()
        );
    }

    @Override
    public Optional<RoomRecord> findById(String id) {
        String sql = "SELECT * FROM rooms WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RoomRecord> findByCode(String code) {
        String sql = "SELECT * FROM rooms WHERE UPPER(code) = UPPER(?)";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, code));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<RoomRecord> findAll() {
        String sql = "SELECT * FROM rooms ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<RoomRecord> findPublicRooms() {
        String sql = "SELECT * FROM rooms WHERE is_private = 0 ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<RoomRecord> findByOwnerId(String ownerId) {
        String sql = "SELECT * FROM rooms WHERE owner_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, ownerId);
    }

    @Override
    public boolean existsByCode(String code) {
        String sql = "SELECT COUNT(*) FROM rooms WHERE UPPER(code) = UPPER(?)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, code);
        return count != null && count > 0;
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Long.class);
        return count != null ? count : 0;
    }

    @Override
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM rooms WHERE id = ?", id);
    }
}
