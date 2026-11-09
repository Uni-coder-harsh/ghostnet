package com.ghostnet.security;

/**
 * Thrown when a client exceeds rate limits.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
