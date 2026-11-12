package com.ghostnet.files;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcChatAttachmentRepository implements ChatAttachmentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ChatAttachmentRecord> rowMapper = new ChatAttachmentRowMapper();

    public JdbcChatAttachmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ChatAttachmentRecord attachment) {
        String sql = """
            INSERT INTO chat_attachments (
                id, message_id, uploader_id, uploader_role, conversation_type, conversation_id,
                original_filename, stored_filename, storage_path, mime_type, file_size,
                checksum_sha256, created_at, expires_at, download_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                message_id = excluded.message_id,
                download_count = excluded.download_count
            """;
        jdbcTemplate.update(sql,
                attachment.id(),
                attachment.messageId(),
                attachment.uploaderId(),
                attachment.uploaderRole(),
                attachment.conversationType(),
                attachment.conversationId(),
                attachment.originalFilename(),
                attachment.storedFilename(),
                attachment.storagePath(),
                attachment.mimeType(),
                attachment.fileSize(),
                attachment.checksumSha256(),
                attachment.createdAt().toString(),
                attachment.expiresAt() != null ? attachment.expiresAt().toString() : null,
                attachment.downloadCount()
        );
    }

    @Override
    public Optional<ChatAttachmentRecord> findById(String id) {
        String sql = "SELECT * FROM chat_attachments WHERE id = ?";
        List<ChatAttachmentRecord> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<ChatAttachmentRecord> findByMessageId(String messageId) {
        String sql = "SELECT * FROM chat_attachments WHERE message_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, rowMapper, messageId);
    }

    @Override
    public List<ChatAttachmentRecord> findByConversation(String conversationType, String conversationId, int limit, int offset) {
        String sql = "SELECT * FROM chat_attachments WHERE conversation_type = ? AND conversation_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, conversationType, conversationId, limit, offset);
    }

    @Override
    public List<ChatAttachmentRecord> findExpired(Instant now) {
        String sql = "SELECT * FROM chat_attachments WHERE expires_at IS NOT NULL AND expires_at <= ?";
        return jdbcTemplate.query(sql, rowMapper, now.toString());
    }

    @Override
    public void linkToMessage(String attachmentId, String messageId) {
        String sql = "UPDATE chat_attachments SET message_id = ? WHERE id = ?";
        jdbcTemplate.update(sql, messageId, attachmentId);
    }

    @Override
    public void incrementDownloadCount(String id) {
        String sql = "UPDATE chat_attachments SET download_count = download_count + 1 WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public void deleteById(String id) {
        String sql = "DELETE FROM chat_attachments WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public long sumGuestStorageBytes() {
        String sql = "SELECT COALESCE(SUM(file_size), 0) FROM chat_attachments WHERE expires_at IS NOT NULL";
        Long sum = jdbcTemplate.queryForObject(sql, Long.class);
        return sum != null ? sum : 0L;
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM chat_attachments";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    private static class ChatAttachmentRowMapper implements RowMapper<ChatAttachmentRecord> {
        @Override
        public ChatAttachmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            String expStr = rs.getString("expires_at");
            return new ChatAttachmentRecord(
                    rs.getString("id"),
                    rs.getString("message_id"),
                    rs.getString("uploader_id"),
                    rs.getString("uploader_role"),
                    rs.getString("conversation_type"),
                    rs.getString("conversation_id"),
                    rs.getString("original_filename"),
                    rs.getString("stored_filename"),
                    rs.getString("storage_path"),
                    rs.getString("mime_type"),
                    rs.getLong("file_size"),
                    rs.getString("checksum_sha256"),
                    Instant.parse(rs.getString("created_at")),
                    expStr != null ? Instant.parse(expStr) : null,
                    rs.getInt("download_count")
            );
        }
    }
}
