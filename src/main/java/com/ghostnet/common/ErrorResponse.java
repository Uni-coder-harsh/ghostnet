package com.ghostnet.common;

import java.time.Instant;

/**
 * Standard structured error payload for REST API responses.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, Instant.now());
    }
}
