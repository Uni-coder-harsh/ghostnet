package com.ghostnet.session;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe user presence tracker (Req 117, 185, 071, 072, 130).
 * Decouples user identity from multiple physical/WebSocket network connections.
 */
@Service
public class PresenceService {

    // Maps userId -> Set of active connection IDs
    private final ConcurrentHashMap<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    // Maps connectionId -> userId
    private final ConcurrentHashMap<String, String> connectionToUser = new ConcurrentHashMap<>();

    // Maps userId -> last seen timestamp
    private final ConcurrentHashMap<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /**
     * Registers a new network connection for a user.
     * @return true if user just transitioned from offline to online.
     */
    public boolean registerConnection(String userId, String connectionId) {
        lastSeen.put(userId, Instant.now());
        connectionToUser.put(connectionId, userId);

        Set<String> connections = userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        boolean wasOffline = connections.isEmpty();
        connections.add(connectionId);
        return wasOffline;
    }

    /**
     * Removes a network connection when a client disconnects.
     * @return DisconnectResult containing the userId and whether the user is now completely offline.
     */
    public DisconnectResult removeConnection(String connectionId) {
        String userId = connectionToUser.remove(connectionId);
        if (userId == null) {
            return new DisconnectResult(null, false);
        }

        lastSeen.put(userId, Instant.now());
        Set<String> connections = userConnections.get(userId);
        boolean becameOffline = false;
        if (connections != null) {
            connections.remove(connectionId);
            if (connections.isEmpty()) {
                userConnections.remove(userId);
                becameOffline = true;
            }
        }
        return new DisconnectResult(userId, becameOffline);
    }

    public boolean isOnline(String userId) {
        Set<String> connections = userConnections.get(userId);
        return connections != null && !connections.isEmpty();
    }

    public Set<String> getOnlineUserIds() {
        return Collections.unmodifiableSet(userConnections.keySet());
    }

    public int countOnlineUsers() {
        return userConnections.size();
    }

    public int countTotalActiveConnections() {
        return connectionToUser.size();
    }

    public Instant getLastSeen(String userId) {
        return lastSeen.getOrDefault(userId, Instant.EPOCH);
    }

    public Set<String> getConnectionsForUser(String userId) {
        Set<String> connections = userConnections.get(userId);
        return connections != null ? Collections.unmodifiableSet(connections) : Collections.emptySet();
    }

    public record DisconnectResult(String userId, boolean becameOffline) {}
}
