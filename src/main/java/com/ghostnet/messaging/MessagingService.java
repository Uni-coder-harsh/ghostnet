package com.ghostnet.messaging;

import com.ghostnet.common.ResourceNotFoundException;
import com.ghostnet.common.ValidationException;
import com.ghostnet.room.RoomMemberRecord;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.session.PresenceService;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRecord;
import com.ghostnet.websocket.WebSocketConnectionManager;
import com.ghostnet.websocket.WsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Messaging Service for 1-to-1 Direct Messages and Room Messages (Req 131-150, 164-166).
 * Safe delivery over WebSockets, offline persistence, authorization checks, and zero content logging (Req 149, 197).
 */
@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final DirectMessageRepository directMessageRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final WebSocketConnectionManager wsConnectionManager;
    private final PresenceService presenceService;

    // Idempotency caches to prevent duplicate message delivery on client reconnects (Req 147, 148)
    private final ConcurrentHashMap<String, DirectMessageDto> recentDms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RoomMessageDto> recentRoomMsgs = new ConcurrentHashMap<>();

    public MessagingService(DirectMessageRepository directMessageRepository,
                            RoomMessageRepository roomMessageRepository,
                            UserRepository userRepository,
                            RoomRepository roomRepository,
                            RoomMembershipRepository roomMembershipRepository,
                            WebSocketConnectionManager wsConnectionManager,
                            PresenceService presenceService) {
        this.directMessageRepository = directMessageRepository;
        this.roomMessageRepository = roomMessageRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.wsConnectionManager = wsConnectionManager;
        this.presenceService = presenceService;
    }

    /**
     * Sends a 1-to-1 direct message with offline persistence and live WebSocket delivery (Req 131-139).
     */
    @Transactional
    public DirectMessageDto sendDirectMessage(String senderUserId, SendDirectMessageRequest request) {
        String recipientId = request.recipientId();
        if (senderUserId.equals(recipientId)) {
            throw new ValidationException("Cannot send a direct message to yourself");
        }

        UserRecord sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender user not found"));
        UserRecord recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found"));

        // Idempotency check (Req 147, 148)
        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            String cacheKey = senderUserId + ":" + request.clientMessageId().trim();
            DirectMessageDto cached = recentDms.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        boolean recipientOnline = presenceService.isOnline(recipientId);
        MessageStatus initialStatus = recipientOnline ? MessageStatus.DELIVERED : MessageStatus.SENT;

        String messageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        DirectMessageRecord record = new DirectMessageRecord(
                messageId,
                senderUserId,
                recipientId,
                request.content(),
                initialStatus,
                now
        );

        directMessageRepository.save(record);
        // Requirement 149 & 197: Do NOT log message content!
        log.info("Direct message delivered (id: {}, from: {}, to: {}, status: {})", messageId, senderUserId, recipientId, initialStatus);

        DirectMessageDto dto = DirectMessageDto.from(record, sender.displayName());

        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            String cacheKey = senderUserId + ":" + request.clientMessageId().trim();
            recentDms.put(cacheKey, dto);
        }

        // Live real-time delivery via WebSocket
        WsMessage wsFrame = WsMessage.of("DIRECT_MESSAGE_RECEIVED", dto);
        wsConnectionManager.sendToUser(recipientId, wsFrame);

        // Also notify sender connection of delivery status
        wsConnectionManager.sendToUser(senderUserId, WsMessage.of("DIRECT_MESSAGE_SENT", dto));

        return dto;
    }

    /**
     * Marks conversation messages as READ when viewed by the recipient.
     */
    @Transactional
    public void markConversationRead(String readerUserId, String otherUserId) {
        directMessageRepository.markConversationRead(readerUserId, otherUserId);
        log.info("Conversation between '{}' and '{}' marked as read by '{}'", otherUserId, readerUserId, readerUserId);

        // Notify original sender that their messages were read
        WsMessage readAck = WsMessage.of("MESSAGE_READ_RECEIPT", Map.of(
                "readerId", readerUserId,
                "senderId", otherUserId
        ));
        wsConnectionManager.sendToUser(otherUserId, readAck);
    }

    /**
     * Retrieves paginated direct message history, ensuring only authorized conversation participants can access (Req 140, 141, 146).
     */
    public List<DirectMessageDto> getDirectMessageHistory(String currentUserId, String otherUserId, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);

        UserRecord sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserRecord other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target conversation user not found"));

        List<DirectMessageRecord> records = directMessageRepository.findConversationHistory(currentUserId, otherUserId, safeLimit, safeOffset);

        Map<String, String> nameCache = Map.of(
                sender.id(), sender.displayName(),
                other.id(), other.displayName()
        );

        return records.stream()
                .map(r -> DirectMessageDto.from(r, nameCache.getOrDefault(r.senderId(), "Unknown")))
                .toList();
    }

    /**
     * Broadcasts a message to all participants of a room with access verification (Req 164-166, 171).
     */
    @Transactional
    public RoomMessageDto sendRoomMessage(String senderUserId, SendRoomMessageRequest request) {
        String roomId = request.roomId();

        if (!roomRepository.findById(roomId).isPresent()) {
            throw new ResourceNotFoundException("Room not found");
        }

        // Strict authorization: Only active room members can post messages (Req 165, 171)
        if (!roomMembershipRepository.isMember(roomId, senderUserId)) {
            throw new UnauthorizedException("You must join this room before sending messages");
        }

        UserRecord sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender user not found"));

        // Idempotency check
        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            String cacheKey = senderUserId + ":" + request.clientMessageId().trim();
            RoomMessageDto cached = recentRoomMsgs.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        String messageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        RoomMessageRecord record = new RoomMessageRecord(
                messageId,
                roomId,
                senderUserId,
                sender.displayName(),
                request.content(),
                now
        );

        roomMessageRepository.save(record);
        log.info("Room message posted (id: {}, room: {}, sender: {})", messageId, roomId, senderUserId);

        RoomMessageDto dto = RoomMessageDto.from(record);

        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            String cacheKey = senderUserId + ":" + request.clientMessageId().trim();
            recentRoomMsgs.put(cacheKey, dto);
        }

        // Broadcast to all room members only (isolated room broadcast, Req 164, 165)
        List<RoomMemberRecord> members = roomMembershipRepository.findByRoomId(roomId);
        List<String> memberUserIds = members.stream().map(RoomMemberRecord::userId).toList();

        WsMessage wsFrame = WsMessage.of("ROOM_MESSAGE_RECEIVED", dto);
        wsConnectionManager.sendToUsers(memberUserIds, wsFrame);

        return dto;
    }

    /**
     * Retrieves paginated room message history, ensuring only room members can view (Req 165, 166, 171).
     */
    public List<RoomMessageDto> getRoomMessageHistory(String currentUserId, String roomId, int limit, int offset) {
        if (!roomMembershipRepository.isMember(roomId, currentUserId)) {
            throw new UnauthorizedException("Only room participants can access room history");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);

        List<RoomMessageRecord> records = roomMessageRepository.findHistoryByRoomId(roomId, safeLimit, safeOffset);
        return records.stream().map(RoomMessageDto::from).toList();
    }
}
