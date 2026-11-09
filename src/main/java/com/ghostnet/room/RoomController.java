package com.ghostnet.room;

import com.ghostnet.security.RequireAuth;
import com.ghostnet.security.SecurityContext;
import com.ghostnet.user.UserRecord;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for room lifecycle, discovery, joining, and moderation (Req 151-175).
 */
@RestController
@RequestMapping("/api/rooms")
@RequireAuth
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomDto> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        UserRecord current = SecurityContext.requireUser();
        RoomDto created = roomService.createRoom(current.id(), request);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/join")
    public ResponseEntity<RoomDto> joinRoom(@Valid @RequestBody JoinRoomRequest request) {
        UserRecord current = SecurityContext.requireUser();
        RoomDto joined = roomService.joinRoomByCode(current.id(), request);
        return ResponseEntity.ok(joined);
    }

    @GetMapping("/public")
    public ResponseEntity<List<RoomDto>> getPublicRooms() {
        UserRecord current = SecurityContext.requireUser();
        return ResponseEntity.ok(roomService.getPublicRooms(current.id()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<RoomDto>> getMyRooms() {
        UserRecord current = SecurityContext.requireUser();
        return ResponseEntity.ok(roomService.getMyRooms(current.id()));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto> getRoomDetails(@PathVariable String roomId) {
        UserRecord current = SecurityContext.requireUser();
        return ResponseEntity.ok(roomService.getRoomDetails(current.id(), roomId));
    }

    @GetMapping("/{roomId}/participants")
    public ResponseEntity<List<RoomParticipantDto>> getRoomParticipants(@PathVariable String roomId) {
        UserRecord current = SecurityContext.requireUser();
        return ResponseEntity.ok(roomService.getRoomParticipants(current.id(), roomId));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId) {
        UserRecord current = SecurityContext.requireUser();
        roomService.leaveRoom(current.id(), roomId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/participants/{targetUserId}")
    public ResponseEntity<Void> kickMember(@PathVariable String roomId, @PathVariable String targetUserId) {
        UserRecord current = SecurityContext.requireUser();
        roomService.kickMember(current.id(), roomId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(@PathVariable String roomId,
                                                  @Valid @RequestBody TransferOwnershipRequest request) {
        UserRecord current = SecurityContext.requireUser();
        roomService.transferOwnership(current.id(), roomId, request.newOwnerUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        UserRecord current = SecurityContext.requireUser();
        roomService.deleteRoom(current.id(), roomId);
        return ResponseEntity.noContent().build();
    }
}
