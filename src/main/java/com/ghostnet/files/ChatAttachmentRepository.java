package com.ghostnet.files;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatAttachmentRepository {
    void save(ChatAttachmentRecord attachment);
    Optional<ChatAttachmentRecord> findById(String id);
    List<ChatAttachmentRecord> findByMessageId(String messageId);
    List<ChatAttachmentRecord> findByConversation(String conversationType, String conversationId, int limit, int offset);
    List<ChatAttachmentRecord> findExpired(Instant now);
    void linkToMessage(String attachmentId, String messageId);
    void incrementDownloadCount(String id);
    void deleteById(String id);
    long sumGuestStorageBytes();
    long count();
}
