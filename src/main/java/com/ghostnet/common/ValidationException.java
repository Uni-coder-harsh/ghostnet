package com.ghostnet.common;

/**
 * Thrown when incoming request validation fails.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
