package com.ghostnet.guest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for guest identities.
 */
public interface GuestRepository {
    void save(GuestIdentityRecord guest);
    Optional<GuestIdentityRecord> findById(String id);
    Optional<GuestIdentityRecord> findByDeviceCredential(String deviceCredential);
    List<GuestIdentityRecord> findActiveGuests();
    void updateLastSeen(String id, Instant now);
    void revoke(String id);
    int deleteExpired(Instant now);
    long countActive();
}
