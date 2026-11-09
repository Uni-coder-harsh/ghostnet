package com.ghostnet.persistence;

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
 * Message retention service responsible for executing configurable retention policies
 * and authorized deletion of message histories without logging message contents (Req 143, 193, 194, 197).
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final DirectMessageRepository directMessageRepository;
    private final RoomMessageRepository roomMessageRepository;

    @Value("${ghostnet.retention.enabled:true}")
    private boolean retentionEnabled;

    @Value("${ghostnet.retention.message-retention-days:30}")
    private int retentionDays;

    public RetentionService(DirectMessageRepository directMessageRepository,
                            RoomMessageRepository roomMessageRepository) {
        this.directMessageRepository = directMessageRepository;
        this.roomMessageRepository = roomMessageRepository;
    }

    /**
     * Executes retention purge according to configured days.
     * Scheduled to run once per day (or on manual trigger).
     */
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
    public RetentionReport cleanupRetention() {
        if (!retentionEnabled || retentionDays <= 0) {
            log.info("Message retention policy is disabled or set to keep indefinitely (retentionDays={})", retentionDays);
            return new RetentionReport(0, 0, Instant.now());
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Executing retention cleanup for messages older than {} days (cutoff: {})", retentionDays, cutoff);

        int directDeleted = directMessageRepository.deleteOlderThan(cutoff);
        int roomDeleted = roomMessageRepository.deleteOlderThan(cutoff);

        log.info("Retention cleanup completed: deleted {} direct messages and {} room messages.", directDeleted, roomDeleted);
        return new RetentionReport(directDeleted, roomDeleted, cutoff);
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

    public record RetentionReport(int directMessagesPurged, int roomMessagesPurged, Instant cutoff) {}
}
