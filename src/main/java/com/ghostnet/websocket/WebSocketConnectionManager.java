package com.ghostnet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe WebSocket connection registry and messaging broadcaster (Req 043, 046, 048, 071, 072, 076).
 * Handles multiple simultaneous connections per user and synchronized frame delivery.
 */
@Component
public class WebSocketConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionManager.class);

    private final ObjectMapper objectMapper;

    // connectionId -> WebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // userId -> Set of connectionIds
    private final ConcurrentHashMap<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    // connectionId -> last activity timestamp (epoch ms)
    private final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();

    public WebSocketConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(String userId, WebSocketSession session) {
        sessions.put(session.getId(), session);
        userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session.getId());
        lastActivity.put(session.getId(), System.currentTimeMillis());
        log.debug("Registered WebSocket connection '{}' for user '{}'", session.getId(), userId);
    }

    public String removeSession(String connectionId) {
        WebSocketSession session = sessions.remove(connectionId);
        lastActivity.remove(connectionId);
        String foundUserId = null;

        for (var entry : userConnections.entrySet()) {
            if (entry.getValue().remove(connectionId)) {
                foundUserId = entry.getKey();
                if (entry.getValue().isEmpty()) {
                    userConnections.remove(foundUserId);
                }
                break;
            }
        }

        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("Error closing WebSocket connection: {}", e.getMessage());
            }
        }

        log.debug("Removed WebSocket connection '{}'", connectionId);
        return foundUserId;
    }

    public void recordActivity(String connectionId) {
        lastActivity.put(connectionId, System.currentTimeMillis());
    }

    /**
     * Sends a message frame to all active connections belonging to a user.
     */
    public void sendToUser(String userId, WsMessage message) {
        Set<String> connIds = userConnections.get(userId);
        if (connIds != null && !connIds.isEmpty()) {
            for (String connId : connIds) {
                WebSocketSession session = sessions.get(connId);
                sendDirect(session, message);
            }
        }
    }

    /**
     * Sends a message frame to a list of users (e.g. room members).
     */
    public void sendToUsers(Collection<String> userIds, WsMessage message) {
        for (String uid : userIds) {
            sendToUser(uid, message);
        }
    }

    /**
     * Broadcasts a message frame to all currently connected clients across the network.
     */
    public void broadcastAll(WsMessage message) {
        for (WebSocketSession session : sessions.values()) {
            sendDirect(session, message);
        }
    }

    /**
     * Safely serializes and sends a WebSocket text message with synchronization per session (Req 076).
     */
    public void sendDirect(WebSocketSession session, WsMessage message) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to deliver WebSocket message to session '{}': {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Inspects active connections and terminates those exceeding idle timeout (Req 046, 047, 048).
     */
    public int closeStaleConnections(long maxIdleMillis) {
        long now = System.currentTimeMillis();
        int closedCount = 0;

        for (var entry : lastActivity.entrySet()) {
            String connId = entry.getKey();
            long last = entry.getValue();
            if (now - last > maxIdleMillis) {
                WebSocketSession session = sessions.get(connId);
                if (session != null) {
                    try {
                        log.info("Terminating stale WebSocket session '{}' (idle for {}ms)", connId, now - last);
                        session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Heartbeat timeout"));
                        closedCount++;
                    } catch (IOException e) {
                        log.debug("Error while closing stale session: {}", e.getMessage());
                    }
                }
                removeSession(connId);
            }
        }
        return closedCount;
    }

    public int getActiveConnectionCount() {
        return sessions.size();
    }

    public int getConnectedUserCount() {
        return userConnections.size();
    }

    public Set<String> getConnectedUserIds() {
        return Collections.unmodifiableSet(userConnections.keySet());
    }
}
