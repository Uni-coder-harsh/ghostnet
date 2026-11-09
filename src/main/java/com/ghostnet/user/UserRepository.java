package com.ghostnet.user;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for User persistence operations.
 */
public interface UserRepository {
    void save(UserRecord user);
    void update(UserRecord user);
    Optional<UserRecord> findById(String id);
    Optional<UserRecord> findByUsername(String username);
    List<UserRecord> findAll();
    List<UserRecord> searchByUsernameOrDisplayName(String query, int limit);
    boolean existsByUsername(String username);
    long count();
    void deleteById(String id);
}
