package com.ghostnet.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Robust BCrypt password hashing service (Req 121, 122).
 * Plaintext passwords are never stored or logged.
 */
@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return encoder.matches(rawPassword, hashedPassword);
    }
}
