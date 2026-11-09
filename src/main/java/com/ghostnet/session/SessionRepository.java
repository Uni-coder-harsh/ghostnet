package com.ghostnet.session;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for persistent authentication sessions.
 */
public interface SessionRepository {
    void save(SessionRecord session);
    Optional<SessionRecord> findByToken(String token);
    List<SessionRecord> findByUserId(String userId);
    List<SessionRecord> findAllActive();
    void deleteByToken(String token);
    void deleteByUserId(String userId);
    void deleteExpired();
    long countActive();
}
