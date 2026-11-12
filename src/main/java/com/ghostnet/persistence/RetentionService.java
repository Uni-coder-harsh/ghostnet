package com.ghostnet.persistence;

import com.ghostnet.files.AttachmentService;
import com.ghostnet.guest.GuestService;
import com.ghostnet.messaging.DirectMessageRepository;
import com.ghostnet.messaging.RoomMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Message & attachment retention service responsible for executing configurable retention policies,
 * purging expired temporary guest files and identities (Req 143, 193, 194, 197).
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final DirectMessageRepository directMessageRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final AttachmentService attachmentService;
    private final GuestService guestService;

    @Value("${ghostnet.retention.enabled:true}")
    private boolean retentionEnabled;

    @Value("${ghostnet.retention.message-retention-days:30}")
    private int retentionDays;

    public RetentionService(DirectMessageRepository directMessageRepository,
                            RoomMessageRepository roomMessageRepository,
                            AttachmentService attachmentService,
                            GuestService guestService) {
        this.directMessageRepository = directMessageRepository;
        this.roomMessageRepository = roomMessageRepository;
        this.attachmentService = attachmentService;
        this.guestService = guestService;
    }

    /**
     * Executes retention purge according to configured policies.
     * Scheduled to run periodically (or on manual trigger).
     */
    @Scheduled(cron = "0 0 */6 * * ?") // Every 6 hours
    public RetentionReport cleanupRetention() {
        int attachmentsCleaned = attachmentService.cleanupExpiredAttachments();
        int guestsCleaned = guestService.cleanupExpiredGuests();

        if (!retentionEnabled || retentionDays <= 0) {
            log.info("Message retention policy is disabled or set to keep indefinitely (retentionDays={})", retentionDays);
            return new RetentionReport(0, 0, attachmentsCleaned, guestsCleaned, Instant.now());
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Executing retention cleanup for messages older than {} days (cutoff: {})", retentionDays, cutoff);

        int directDeleted = directMessageRepository.deleteOlderThan(cutoff);
        int roomDeleted = roomMessageRepository.deleteOlderThan(cutoff);

        log.info("Retention cleanup completed: deleted {} direct messages, {} room messages, {} expired attachments, {} expired guests.",
                directDeleted, roomDeleted, attachmentsCleaned, guestsCleaned);
        return new RetentionReport(directDeleted, roomDeleted, attachmentsCleaned, guestsCleaned, cutoff);
    }

    /**
     * Explicitly deletes all message history for a specific room.
     */
    public void deleteRoomHistory(String roomId) {
        log.info("Purging message history for room '{}'", roomId);
        roomMessageRepository.deleteByRoomId(roomId);
    }

    /**
     * Explicitly deletes conversation history between two users.
     */
    public void deleteDirectConversation(String user1, String user2) {
        log.info("Purging direct conversation history between user '{}' and user '{}'", user1, user2);
        directMessageRepository.deleteConversation(user1, user2);
    }

    public record RetentionReport(
            int directMessagesPurged,
            int roomMessagesPurged,
            int attachmentsPurged,
            int guestsPurged,
            Instant cutoff
    ) {}
}
