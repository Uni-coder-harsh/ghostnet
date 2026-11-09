package com.ghostnet.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostnet.messaging.MessagingService;
import com.ghostnet.messaging.SendDirectMessageRequest;
import com.ghostnet.messaging.SendRoomMessageRequest;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.session.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * High-performance WebSocket Handler coordinating real-time events,
 * heartbeats, typing indicators, and messaging delivery (Req 041-048).
 */
@Component
public class GhostNetWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GhostNetWebSocketHandler.class);

    private final WebSocketConnectionManager connectionManager;
    private final MessagingService messagingService;
    private final PresenceService presenceService;
    private final RoomMembershipRepository roomMembershipRepository;
    private final ObjectMapper objectMapper;

    @Value("${ghostnet.security.max-message-length:4096}")
    private int maxMessageLength;

    public GhostNetWebSocketHandler(WebSocketConnectionManager connectionManager,
                                   MessagingService messagingService,
                                   PresenceService presenceService,
                                   RoomMembershipRepository roomMembershipRepository,
                                   ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.messagingService = messagingService;
        this.presenceService = presenceService;
        this.roomMembershipRepository = roomMembershipRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("Terminating unauthenticated session connection: {}", session.getId());
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignored) {}
            return;
        }

        connectionManager.addSession(userId, session);
        boolean becameOnline = presenceService.registerConnection(userId, session.getId());

        // Acknowledge connection
        connectionManager.sendDirect(session, WsMessage.of("CONNECTED_ACK", Map.of(
                "userId", userId,
                "connectionId", session.getId(),
                "serverTime", Instant.now().toString(),
                "onlineUsersCount", presenceService.countOnlineUsers()
        )));

        // If user transitioned from offline to online, broadcast presence update
        if (becameOnline) {
            log.info("User '{}' came ONLINE", userId);
            connectionManager.broadcastAll(WsMessage.of("PRESENCE_CHANGE", Map.of(
                    "userId", userId,
                    "online", true,
                    "timestamp", Instant.now().toString()
            )));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        connectionManager.recordActivity(session.getId());

        String payload = message.getPayload();
        if (payload.length() > maxMessageLength) {
            connectionManager.sendDirect(session, WsMessage.error("Message exceeds maximum allowed length of " + maxMessageLength + " bytes", null));
            return;
        }

        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.has("type") ? root.get("type").asText() : "";
            String requestId = root.has("requestId") ? root.get("requestId").asText() : null;
            JsonNode data = root.get("payload");

            switch (type) {
                case "PING" -> connectionManager.sendDirect(session, new WsMessage("PONG", Map.of("serverTime", Instant.now().toString()), requestId));

                case "SEND_DIRECT_MESSAGE" -> {
                    SendDirectMessageRequest req = objectMapper.treeToValue(data, SendDirectMessageRequest.class);
                    messagingService.sendDirectMessage(userId, req);
                }

                case "SEND_ROOM_MESSAGE" -> {
                    SendRoomMessageRequest req = objectMapper.treeToValue(data, SendRoomMessageRequest.class);
                    messagingService.sendRoomMessage(userId, req);
                }

                case "MARK_READ" -> {
                    String otherUserId = data.has("otherUserId") ? data.get("otherUserId").asText() : null;
                    if (otherUserId != null) {
                        messagingService.markConversationRead(userId, otherUserId);
                    }
                }

                case "TYPING_INDICATOR" -> {
                    // Ephemeral typing notification
                    boolean isRoom = data.has("roomId") && !data.get("roomId").isNull();
                    if (isRoom) {
                        String roomId = data.get("roomId").asText();
                        if (roomMembershipRepository.isMember(roomId, userId)) {
                            List<String> members = roomMembershipRepository.findByRoomId(roomId).stream()
                                    .map(m -> m.userId())
                                    .filter(uid -> !uid.equals(userId))
                                    .toList();
                            connectionManager.sendToUsers(members, WsMessage.of("USER_TYPING", Map.of(
                                    "roomId", roomId,
                                    "userId", userId,
                                    "typing", data.has("typing") && data.get("typing").asBoolean()
                            )));
                        }
                    } else if (data.has("recipientId")) {
                        String recipientId = data.get("recipientId").asText();
                        connectionManager.sendToUser(recipientId, WsMessage.of("USER_TYPING", Map.of(
                                "userId", userId,
                                "typing", data.has("typing") && data.get("typing").asBoolean()
                        )));
                    }
                }

                default -> log.debug("Received unrecognized WebSocket message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("Error processing WebSocket frame from user '{}': {}", userId, e.getMessage());
            connectionManager.sendDirect(session, WsMessage.error(e.getMessage(), null));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error for session '{}': {}", session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        connectionManager.removeSession(session.getId());

        PresenceService.DisconnectResult disc = presenceService.removeConnection(session.getId());
        if (disc.becameOffline() && disc.userId() != null) {
            log.info("User '{}' went OFFLINE (all connections closed)", disc.userId());
            connectionManager.broadcastAll(WsMessage.of("PRESENCE_CHANGE", Map.of(
                    "userId", disc.userId(),
                    "online", false,
                    "timestamp", Instant.now().toString()
            )));
        }
    }
}
