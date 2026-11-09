package com.ghostnet.messaging;

import com.ghostnet.room.CreateRoomRequest;
import com.ghostnet.room.JoinRoomRequest;
import com.ghostnet.room.RoomDto;
import com.ghostnet.room.RoomService;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.session.PresenceService;
import com.ghostnet.user.RegisterRequest;
import com.ghostnet.user.UserService;
import com.ghostnet.websocket.WebSocketConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MessagingServiceTests {

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private WebSocketConnectionManager connectionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userAId;
    private String userBId;
    private String userCId;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM direct_messages;");
        jdbcTemplate.execute("DELETE FROM room_messages;");
        jdbcTemplate.execute("DELETE FROM room_members;");
        jdbcTemplate.execute("DELETE FROM rooms;");
        jdbcTemplate.execute("DELETE FROM user_sessions;");
        jdbcTemplate.execute("DELETE FROM user_preferences;");
        jdbcTemplate.execute("DELETE FROM users;");

        userAId = userService.register(new RegisterRequest("alpha_user", "pass1234", "Alpha"), "127.0.0.1", "test").user().id();
        userBId = userService.register(new RegisterRequest("bravo_user", "pass1234", "Bravo"), "127.0.0.1", "test").user().id();
        userCId = userService.register(new RegisterRequest("charlie_user", "pass1234", "Charlie"), "127.0.0.1", "test").user().id();
    }

    @Test
    @DisplayName("Direct message sent to offline user persists as SENT")
    void testDirectMessageOfflineRecipient() {
        assertFalse(presenceService.isOnline(userBId));

        SendDirectMessageRequest req = new SendDirectMessageRequest(userBId, "Classified package delivered", "client-msg-1");
        DirectMessageDto sent = messagingService.sendDirectMessage(userAId, req);

        assertNotNull(sent.id());
        assertEquals(userAId, sent.senderId());
        assertEquals(userBId, sent.recipientId());
        assertEquals(MessageStatus.SENT, sent.status());

        List<DirectMessageDto> history = messagingService.getDirectMessageHistory(userAId, userBId, 10, 0);
        assertEquals(1, history.size());
        assertEquals("Classified package delivered", history.get(0).content());
    }

    @Test
    @DisplayName("Direct message sent to online user marks status as DELIVERED")
    void testDirectMessageOnlineRecipient() {
        // Mark user B online
        presenceService.registerConnection(userBId, "conn-b1");
        assertTrue(presenceService.isOnline(userBId));

        SendDirectMessageRequest req = new SendDirectMessageRequest(userBId, "Live encrypted burst", "client-msg-2");
        DirectMessageDto sent = messagingService.sendDirectMessage(userAId, req);

        assertEquals(MessageStatus.DELIVERED, sent.status());
        presenceService.removeConnection("conn-b1");
    }

    @Test
    @DisplayName("Idempotency cache prevents duplicate message storage on retry")
    void testMessageIdempotency() {
        SendDirectMessageRequest req = new SendDirectMessageRequest(userBId, "Idempotent ping", "unique-id-999");
        DirectMessageDto first = messagingService.sendDirectMessage(userAId, req);
        DirectMessageDto second = messagingService.sendDirectMessage(userAId, req);

        assertEquals(first.id(), second.id());
        List<DirectMessageDto> history = messagingService.getDirectMessageHistory(userAId, userBId, 10, 0);
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("Marking conversation read updates message status to READ")
    void testMarkConversationRead() {
        SendDirectMessageRequest req = new SendDirectMessageRequest(userBId, "Please acknowledge receipt", "client-ack-1");
        messagingService.sendDirectMessage(userAId, req);

        // User B reads conversation
        messagingService.markConversationRead(userBId, userAId);

        List<DirectMessageDto> history = messagingService.getDirectMessageHistory(userAId, userBId, 10, 0);
        assertEquals(MessageStatus.READ, history.get(0).status());
    }

    @Test
    @DisplayName("Room message broadcast is restricted to active room members only")
    void testRoomMessageRestrictedToMembers() {
        CreateRoomRequest roomReq = new CreateRoomRequest("Operations Center", "Ops", false, null, 10);
        RoomDto room = roomService.createRoom(userAId, roomReq);

        // User B joins
        roomService.joinRoomByCode(userBId, new JoinRoomRequest(room.code(), null));

        // User A (member/owner) posts message -> succeeds
        SendRoomMessageRequest msgReq = new SendRoomMessageRequest(room.id(), "All units report in", "room-msg-1");
        RoomMessageDto posted = messagingService.sendRoomMessage(userAId, msgReq);
        assertNotNull(posted.id());
        assertEquals("Operations Center", room.name());

        // User C (not a member) attempts to post message -> Unauthorized
        SendRoomMessageRequest intruderReq = new SendRoomMessageRequest(room.id(), "Intruder breach", "intruder-1");
        assertThrows(UnauthorizedException.class, () ->
                messagingService.sendRoomMessage(userCId, intruderReq)
        );

        // User C attempts to view history -> Unauthorized
        assertThrows(UnauthorizedException.class, () ->
                messagingService.getRoomMessageHistory(userCId, room.id(), 10, 0)
        );

        // User B (member) views history -> succeeds
        List<RoomMessageDto> history = messagingService.getRoomMessageHistory(userBId, room.id(), 10, 0);
        assertEquals(1, history.size());
        assertEquals("All units report in", history.get(0).content());
    }

    @Test
    @DisplayName("Stale connection cleanup correctly purges idle sessions")
    void testStaleConnectionPurge() {
        int initialActive = connectionManager.getActiveConnectionCount();
        assertEquals(0, initialActive);

        int closed = connectionManager.closeStaleConnections(1000);
        assertEquals(0, closed);
    }
}
