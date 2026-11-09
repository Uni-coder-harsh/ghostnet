package com.ghostnet.user;

import java.util.Map;
import java.util.Optional;

/**
 * Service contract for User Preferences persistence.
 */
public interface UserPreferenceRepository {
    void setPreference(String userId, String key, String value);
    Optional<String> getPreference(String userId, String key);
    Map<String, String> getAllPreferences(String userId);
    void deletePreference(String userId, String key);
    void deleteAllPreferences(String userId);
}
