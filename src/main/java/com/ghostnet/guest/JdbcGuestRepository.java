package com.ghostnet.guest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC SQLite implementation for GuestRepository.
 */
@Repository
public class JdbcGuestRepository implements GuestRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<GuestIdentityRecord> rowMapper = new GuestRowMapper();

    public JdbcGuestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(GuestIdentityRecord guest) {
        String sql = """
            INSERT INTO guest_identities (id, device_credential, display_name, created_at, last_seen_at, expires_at, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                display_name = excluded.display_name,
                last_seen_at = excluded.last_seen_at,
                expires_at = excluded.expires_at,
                is_active = excluded.is_active
            """;
        jdbcTemplate.update(sql,
                guest.id(),
                guest.deviceCredential(),
                guest.displayName(),
                guest.createdAt().toString(),
                guest.lastSeenAt().toString(),
                guest.expiresAt().toString(),
                guest.isActive() ? 1 : 0
        );
    }

    @Override
    public Optional<GuestIdentityRecord> findById(String id) {
        String sql = "SELECT * FROM guest_identities WHERE id = ?";
        List<GuestIdentityRecord> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<GuestIdentityRecord> findByDeviceCredential(String deviceCredential) {
        String sql = "SELECT * FROM guest_identities WHERE device_credential = ?";
        List<GuestIdentityRecord> list = jdbcTemplate.query(sql, rowMapper, deviceCredential);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<GuestIdentityRecord> findActiveGuests() {
        String sql = "SELECT * FROM guest_identities WHERE is_active = 1 AND expires_at > ? ORDER BY last_seen_at DESC";
        return jdbcTemplate.query(sql, rowMapper, Instant.now().toString());
    }

    @Override
    public void updateLastSeen(String id, Instant now) {
        String sql = "UPDATE guest_identities SET last_seen_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, now.toString(), id);
    }

    @Override
    public void revoke(String id) {
        String sql = "UPDATE guest_identities SET is_active = 0 WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public int deleteExpired(Instant now) {
        String sql = "DELETE FROM guest_identities WHERE expires_at <= ? OR is_active = 0";
        return jdbcTemplate.update(sql, now.toString());
    }

    @Override
    public long countActive() {
        String sql = "SELECT COUNT(*) FROM guest_identities WHERE is_active = 1 AND expires_at > ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, Instant.now().toString());
        return count != null ? count : 0L;
    }

    private static class GuestRowMapper implements RowMapper<GuestIdentityRecord> {
        @Override
        public GuestIdentityRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new GuestIdentityRecord(
                    rs.getString("id"),
                    rs.getString("device_credential"),
                    rs.getString("display_name"),
                    Instant.parse(rs.getString("created_at")),
                    Instant.parse(rs.getString("last_seen_at")),
                    Instant.parse(rs.getString("expires_at")),
                    rs.getInt("is_active") == 1
            );
        }
    }
}
