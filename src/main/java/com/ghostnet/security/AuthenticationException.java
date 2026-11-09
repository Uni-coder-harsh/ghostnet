package com.ghostnet.security;

/**
 * Thrown when user credentials or session verification fails.
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
