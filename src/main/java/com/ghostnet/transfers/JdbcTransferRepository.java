package com.ghostnet.transfers;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcTransferRepository implements TransferRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<TransferRecord> rowMapper = new TransferRowMapper();

    public JdbcTransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(TransferRecord transfer) {
        String sql = """
            INSERT INTO transfers (
                id, transfer_type, category, user_id, filename, file_size,
                bytes_transferred, status, speed_bytes_per_sec, error_message,
                created_at, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                bytes_transferred = excluded.bytes_transferred,
                status = excluded.status,
                speed_bytes_per_sec = excluded.speed_bytes_per_sec,
                error_message = excluded.error_message,
                completed_at = excluded.completed_at
            """;
        jdbcTemplate.update(sql,
                transfer.id(),
                transfer.transferType(),
                transfer.category(),
                transfer.userId(),
                transfer.filename(),
                transfer.fileSize(),
                transfer.bytesTransferred(),
                transfer.status(),
                transfer.speedBytesPerSec(),
                transfer.errorMessage(),
                transfer.createdAt().toString(),
                transfer.completedAt() != null ? transfer.completedAt().toString() : null
        );
    }

    @Override
    public void updateProgress(String id, long bytesTransferred, String status, long speedBytesPerSec, String errorMessage) {
        String completedAtStr = ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status))
                ? Instant.now().toString() : null;
        String sql = "UPDATE transfers SET bytes_transferred = ?, status = ?, speed_bytes_per_sec = ?, error_message = ?, completed_at = COALESCE(?, completed_at) WHERE id = ?";
        jdbcTemplate.update(sql, bytesTransferred, status, speedBytesPerSec, errorMessage, completedAtStr, id);
    }

    @Override
    public Optional<TransferRecord> findById(String id) {
        String sql = "SELECT * FROM transfers WHERE id = ?";
        List<TransferRecord> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<TransferRecord> findRecentByUserId(String userId, int limit) {
        String sql = "SELECT * FROM transfers WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, userId, limit);
    }

    @Override
    public List<TransferRecord> findAllRecent(int limit) {
        String sql = "SELECT * FROM transfers ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, limit);
    }

    private static class TransferRowMapper implements RowMapper<TransferRecord> {
        @Override
        public TransferRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            String compStr = rs.getString("completed_at");
            return new TransferRecord(
                    rs.getString("id"),
                    rs.getString("transfer_type"),
                    rs.getString("category"),
                    rs.getString("user_id"),
                    rs.getString("filename"),
                    rs.getLong("file_size"),
                    rs.getLong("bytes_transferred"),
                    rs.getString("status"),
                    rs.getLong("speed_bytes_per_sec"),
                    rs.getString("error_message"),
                    Instant.parse(rs.getString("created_at")),
                    compStr != null ? Instant.parse(compStr) : null
            );
        }
    }
}
