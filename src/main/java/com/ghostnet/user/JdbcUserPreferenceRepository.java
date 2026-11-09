package com.ghostnet.user;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of UserPreferenceRepository.
 */
@Repository
public class JdbcUserPreferenceRepository implements UserPreferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserPreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void setPreference(String userId, String key, String value) {
        String sql = """
            INSERT INTO user_preferences (user_id, pref_key, pref_value)
            VALUES (?, ?, ?)
            ON CONFLICT(user_id, pref_key) DO UPDATE SET pref_value = excluded.pref_value
            """;
        jdbcTemplate.update(sql, userId, key, value);
    }

    @Override
    public Optional<String> getPreference(String userId, String key) {
        String sql = "SELECT pref_value FROM user_preferences WHERE user_id = ? AND pref_key = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, String.class, userId, key));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> getAllPreferences(String userId) {
        String sql = "SELECT pref_key, pref_value FROM user_preferences WHERE user_id = ?";
        Map<String, String> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getString("pref_key"), rs.getString("pref_value"));
        }, userId);
        return result;
    }

    @Override
    public void deletePreference(String userId, String key) {
        jdbcTemplate.update("DELETE FROM user_preferences WHERE user_id = ? AND pref_key = ?", userId, key);
    }

    @Override
    public void deleteAllPreferences(String userId) {
        jdbcTemplate.update("DELETE FROM user_preferences WHERE user_id = ?", userId);
    }
}
