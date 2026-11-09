package com.ghostnet.session;

import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Manages persistent user sessions, validation, and controlled revocation (Req 123-126).
 */
@Service
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final SessionRepository sessionRepository;
    private final TokenGenerator tokenGenerator;

    @Value("${ghostnet.security.session-duration-hours:72}")
    private int sessionDurationHours;

    public AuthSessionService(SessionRepository sessionRepository, TokenGenerator tokenGenerator) {
        this.sessionRepository = sessionRepository;
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * Creates a new cryptographically secure session.
     */
    public SessionRecord createSession(String userId, String clientIp, String userAgent) {
        String token = tokenGenerator.generateSessionToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionDurationHours, ChronoUnit.HOURS);

        SessionRecord session = new SessionRecord(
                token,
                userId,
                expiresAt,
                now,
                clientIp != null ? clientIp : "unknown",
                userAgent != null ? userAgent : "unknown"
        );

        sessionRepository.save(session);
        log.debug("Created session for user '{}', expires at: {}", userId, expiresAt);
        return session;
    }

    /**
     * Validates that a session token exists and has not expired.
     */
    public SessionRecord validateSession(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("Missing authentication session token");
        }

        Optional<SessionRecord> sessionOpt = sessionRepository.findByToken(token);
        if (sessionOpt.isEmpty()) {
            throw new AuthenticationException("Invalid or revoked session token");
        }

        SessionRecord session = sessionOpt.get();
        if (session.isExpired()) {
            sessionRepository.deleteByToken(token);
            throw new AuthenticationException("Session has expired");
        }

        return session;
    }

    /**
     * Revokes an individual session.
     */
    public void revokeSession(String token) {
        if (token != null) {
            sessionRepository.deleteByToken(token);
            log.info("Revoked session token");
        }
    }

    /**
     * Revokes all active sessions for a user (e.g. password change or admin action).
     */
    public void revokeAllUserSessions(String userId) {
        sessionRepository.deleteByUserId(userId);
        log.info("Revoked all sessions for user '{}'", userId);
    }

    /**
     * Retrieves all active sessions for administrative oversight.
     */
    public List<SessionRecord> getActiveSessions() {
        return sessionRepository.findAllActive();
    }

    /**
     * Scheduled background cleanup of expired sessions every hour.
     */
    @Scheduled(fixedRate = 3600000)
    public void purgeExpiredSessions() {
        sessionRepository.deleteExpired();
    }
}
