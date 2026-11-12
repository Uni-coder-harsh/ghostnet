package com.ghostnet.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service contract for 1-to-1 Direct Messaging persistence.
 */
public interface DirectMessageRepository {
    void save(DirectMessageRecord message);
    Optional<DirectMessageRecord> findById(String id);
    List<DirectMessageRecord> findConversationHistory(String user1, String user2, int limit, int offset);
    void updateStatus(String messageId, MessageStatus status);
    void markConversationDelivered(String recipientId, String senderId);
    void markConversationRead(String recipientId, String senderId);
    List<String> findDistinctConversationPartners(String userId);
    long countTotal();
    int deleteOlderThan(Instant cutoff);
    void deleteConversation(String user1, String user2);
}
