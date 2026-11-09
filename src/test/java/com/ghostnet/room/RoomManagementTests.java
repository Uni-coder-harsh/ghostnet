package com.ghostnet.room;

import com.ghostnet.common.ValidationException;
import com.ghostnet.security.AuthenticationException;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.user.RegisterRequest;
import com.ghostnet.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RoomManagementTests {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String user1Id;
    private String user2Id;
    private String user3Id;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM direct_messages;");
        jdbcTemplate.execute("DELETE FROM room_messages;");
        jdbcTemplate.execute("DELETE FROM room_members;");
        jdbcTemplate.execute("DELETE FROM rooms;");
        jdbcTemplate.execute("DELETE FROM user_sessions;");
        jdbcTemplate.execute("DELETE FROM user_preferences;");
        jdbcTemplate.execute("DELETE FROM users;");

        user1Id = userService.register(new RegisterRequest("captain", "pass1234", "Captain"), "127.0.0.1", "agent").user().id();
        user2Id = userService.register(new RegisterRequest("lieutenant", "pass1234", "Lieutenant"), "127.0.0.1", "agent").user().id();
        user3Id = userService.register(new RegisterRequest("private_pawn", "pass1234", "Pawn"), "127.0.0.1", "agent").user().id();
    }

    @Test
    @DisplayName("Create room generates unique code and assigns creator as OWNER")
    void testCreateRoom() {
        CreateRoomRequest req = new CreateRoomRequest("Command Bunker", "Primary ops room", false, null, 10);
        RoomDto room = roomService.createRoom(user1Id, req);

        assertNotNull(room.id());
        assertNotNull(room.code());
        assertTrue(room.code().startsWith("GN-"));
        assertEquals("Command Bunker", room.name());
        assertEquals(user1Id, room.ownerId());
        assertTrue(room.isMember());
        assertTrue(room.isOwner());
        assertEquals(1, room.memberCount());
    }

    @Test
    @DisplayName("Join room by code adds member and increments participant count")
    void testJoinRoomByCode() {
        CreateRoomRequest req = new CreateRoomRequest("Radio Station", "Broadcast tower", false, null, 10);
        RoomDto room = roomService.createRoom(user1Id, req);

        // User 2 joins
        JoinRoomRequest joinReq = new JoinRoomRequest(room.code(), null);
        RoomDto joined = roomService.joinRoomByCode(user2Id, joinReq);

        assertTrue(joined.isMember());
        assertEquals(2, joined.memberCount());

        List<RoomParticipantDto> participants = roomService.getRoomParticipants(user2Id, room.id());
        assertEquals(2, participants.size());
    }

    @Test
    @DisplayName("Private room with password requires correct password to join")
    void testPrivatePasswordRoom() {
        CreateRoomRequest req = new CreateRoomRequest("Black Ops", "Classified", true, "classified123", 5);
        RoomDto room = roomService.createRoom(user1Id, req);

        // Try join with bad password
        JoinRoomRequest badReq = new JoinRoomRequest(room.code(), "wrongPassword");
        assertThrows(AuthenticationException.class, () -> roomService.joinRoomByCode(user2Id, badReq));

        // Join with correct password
        JoinRoomRequest goodReq = new JoinRoomRequest(room.code(), "classified123");
        RoomDto joined = roomService.joinRoomByCode(user2Id, goodReq);
        assertTrue(joined.isMember());
    }

    @Test
    @DisplayName("Room capacity limit is enforced strictly")
    void testCapacityEnforcement() {
        // Room with capacity = 2
        CreateRoomRequest req = new CreateRoomRequest("Two Person Pod", "Small room", false, null, 2);
        RoomDto room = roomService.createRoom(user1Id, req);

        // User 2 joins (reaches capacity 2)
        roomService.joinRoomByCode(user2Id, new JoinRoomRequest(room.code(), null));

        // User 3 attempts to join -> throws ValidationException
        assertThrows(ValidationException.class, () ->
                roomService.joinRoomByCode(user3Id, new JoinRoomRequest(room.code(), null))
        );
    }

    @Test
    @DisplayName("Room owner can kick participants, non-owners cannot")
    void testKickParticipant() {
        CreateRoomRequest req = new CreateRoomRequest("Squad HQ", "HQ", false, null, 10);
        RoomDto room = roomService.createRoom(user1Id, req);
        roomService.joinRoomByCode(user2Id, new JoinRoomRequest(room.code(), null));

        // User 2 attempts to kick User 1 (or other member) -> Forbidden
        assertThrows(UnauthorizedException.class, () ->
                roomService.kickMember(user2Id, room.id(), user1Id)
        );

        // User 1 (owner) kicks User 2 -> succeeds
        roomService.kickMember(user1Id, room.id(), user2Id);

        List<RoomParticipantDto> participants = roomService.getRoomParticipants(user1Id, room.id());
        assertEquals(1, participants.size());
    }

    @Test
    @DisplayName("Room ownership transfer promotes new owner and updates roles")
    void testTransferOwnership() {
        CreateRoomRequest req = new CreateRoomRequest("Base Camp", "Base", false, null, 10);
        RoomDto room = roomService.createRoom(user1Id, req);
        roomService.joinRoomByCode(user2Id, new JoinRoomRequest(room.code(), null));

        roomService.transferOwnership(user1Id, room.id(), user2Id);

        RoomDto updated = roomService.getRoomDetails(user2Id, room.id());
        assertEquals(user2Id, updated.ownerId());
        assertTrue(updated.isOwner());
    }

    @Test
    @DisplayName("Leaving a room removes participant; last member leaving deletes room")
    void testLeaveRoom() {
        CreateRoomRequest req = new CreateRoomRequest("Temporary Post", "Temp", false, null, 10);
        RoomDto room = roomService.createRoom(user1Id, req);
        roomService.joinRoomByCode(user2Id, new JoinRoomRequest(room.code(), null));

        // User 2 leaves
        roomService.leaveRoom(user2Id, room.id());
        List<RoomParticipantDto> participants = roomService.getRoomParticipants(user1Id, room.id());
        assertEquals(1, participants.size());

        // User 1 (sole remaining owner) leaves -> room auto-deleted
        roomService.leaveRoom(user1Id, room.id());
        assertTrue(roomService.getPublicRooms(user1Id).stream().noneMatch(r -> r.id().equals(room.id())));
    }
}
