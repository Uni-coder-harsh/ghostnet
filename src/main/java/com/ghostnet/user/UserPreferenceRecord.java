package com.ghostnet.user;

/**
 * Immutable domain record representing an arbitrary user preference.
 */
public record UserPreferenceRecord(
        String userId,
        String prefKey,
        String prefValue
) {
    public UserPreferenceRecord {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User ID cannot be empty");
        if (prefKey == null || prefKey.isBlank()) throw new IllegalArgumentException("Preference key cannot be empty");
    }
}
