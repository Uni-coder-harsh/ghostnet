package com.ghostnet.security;

import com.ghostnet.session.SessionRecord;
import com.ghostnet.user.UserRecord;

import java.util.Optional;

/**
 * Thread-local security context holding the authenticated user and session for the current thread.
 */
public final class SecurityContext {

    private static final ThreadLocal<UserRecord> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<SessionRecord> currentSession = new ThreadLocal<>();

    private SecurityContext() {}

    public static void set(UserRecord user, SessionRecord session) {
        currentUser.set(user);
        currentSession.set(session);
    }

    public static Optional<UserRecord> getCurrentUser() {
        return Optional.ofNullable(currentUser.get());
    }

    public static UserRecord requireUser() {
        return getCurrentUser().orElseThrow(() -> new AuthenticationException("Authentication required"));
    }

    public static Optional<SessionRecord> getCurrentSession() {
        return Optional.ofNullable(currentSession.get());
    }

    public static void clear() {
        currentUser.remove();
        currentSession.remove();
    }
}
