package com.ghostnet.user;

/**
 * Thrown when trying to register a username that is already taken.
 */
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
