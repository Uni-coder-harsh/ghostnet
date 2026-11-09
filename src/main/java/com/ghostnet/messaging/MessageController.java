package com.ghostnet.messaging;

import com.ghostnet.security.RequireAuth;
import com.ghostnet.security.SecurityContext;
import com.ghostnet.user.UserRecord;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for direct messaging and room messaging history & sending (Req 131-150, 164-166).
 */
@RestController
@RequestMapping("/api/messages")
@RequireAuth
public class MessageController {

    private final MessagingService messagingService;

    public MessageController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @PostMapping("/direct")
    public ResponseEntity<DirectMessageDto> sendDirectMessage(@Valid @RequestBody SendDirectMessageRequest request) {
        UserRecord current = SecurityContext.requireUser();
        DirectMessageDto sent = messagingService.sendDirectMessage(current.id(), request);
        return ResponseEntity.ok(sent);
    }

    @GetMapping("/direct/{otherUserId}")
    public ResponseEntity<List<DirectMessageDto>> getDirectMessageHistory(
            @PathVariable String otherUserId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UserRecord current = SecurityContext.requireUser();
        List<DirectMessageDto> history = messagingService.getDirectMessageHistory(current.id(), otherUserId, limit, offset);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/direct/{otherUserId}/read")
    public ResponseEntity<Void> markDirectConversationRead(@PathVariable String otherUserId) {
        UserRecord current = SecurityContext.requireUser();
        messagingService.markConversationRead(current.id(), otherUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/room", "/room/{roomId}"})
    public ResponseEntity<RoomMessageDto> sendRoomMessage(
            @PathVariable(required = false) String roomId,
            @Valid @RequestBody SendRoomMessageRequest request) {
        UserRecord current = SecurityContext.requireUser();
        String effectiveRoomId = (roomId != null && !roomId.isBlank()) ? roomId : request.roomId();
        SendRoomMessageRequest effectiveReq = new SendRoomMessageRequest(
                effectiveRoomId,
                request.content(),
                request.clientMessageId()
        );
        RoomMessageDto sent = messagingService.sendRoomMessage(current.id(), effectiveReq);
        return ResponseEntity.ok(sent);
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<RoomMessageDto>> getRoomMessageHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UserRecord current = SecurityContext.requireUser();
        List<RoomMessageDto> history = messagingService.getRoomMessageHistory(current.id(), roomId, limit, offset);
        return ResponseEntity.ok(history);
    }
}
