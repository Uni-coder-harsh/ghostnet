package com.ghostnet.security;

/**
 * Thrown when an authenticated user attempts an action outside their role or permissions.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
