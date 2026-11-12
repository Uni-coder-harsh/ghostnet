package com.ghostnet.files;

import com.ghostnet.common.ResourceNotFoundException;
import com.ghostnet.common.ValidationException;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.room.RoomRecord;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.storage.FileStorageService;
import com.ghostnet.storage.StorageLayoutService;
import com.ghostnet.transfers.TransferRecord;
import com.ghostnet.transfers.TransferRepository;
import com.ghostnet.user.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * High-level service coordinating conversation file uploads, downloads,
 * permission enforcement, and expiration cleanup (Req Section 4).
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final ChatAttachmentRepository chatAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final StorageLayoutService storageLayoutService;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final TransferRepository transferRepository;

    public AttachmentService(ChatAttachmentRepository chatAttachmentRepository,
                             FileStorageService fileStorageService,
                             StorageLayoutService storageLayoutService,
                             RoomRepository roomRepository,
                             RoomMembershipRepository roomMembershipRepository,
                             TransferRepository transferRepository) {
        this.chatAttachmentRepository = chatAttachmentRepository;
        this.fileStorageService = fileStorageService;
        this.storageLayoutService = storageLayoutService;
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional
    public AttachmentDto uploadAttachment(UserRecord uploader, MultipartFile file, String conversationType, String conversationId) {
        String cleanType = conversationType.trim().toUpperCase();
        if (!"ROOM".equals(cleanType) && !"DIRECT".equals(cleanType)) {
            throw new ValidationException("Invalid conversation type: " + conversationType);
        }

        String targetConvId = conversationId;
        // 1. Verify authorization
        if ("ROOM".equals(cleanType)) {
            targetConvId = resolveRoomId(conversationId);
            if (!roomMembershipRepository.isMember(targetConvId, uploader.id())) {
                throw new UnauthorizedException("You must be an active member of the room to share files");
            }
        }

        // 2. Stream to disk and hash
        FileStorageService.StoredFileResult result = fileStorageService.storeChatAttachment(uploader, file, targetConvId);

        String attachmentId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = uploader.isGuest()
                ? now.plus(Duration.ofHours(storageLayoutService.getGuestExpirationHours()))
                : null;

        ChatAttachmentRecord record = new ChatAttachmentRecord(
                attachmentId,
                null,
                uploader.id(),
                uploader.role().name(),
                cleanType,
                targetConvId,
                result.originalFilename(),
                result.storedFilename(),
                result.relativeStoragePath(),
                result.mimeType(),
                result.fileSize(),
                result.checksumSha256(),
                now,
                expiresAt,
                0
        );

        chatAttachmentRepository.save(record);

        // Audit transfer
        transferRepository.save(new TransferRecord(
                UUID.randomUUID().toString(),
                "UPLOAD",
                "ATTACHMENT",
                uploader.id(),
                result.originalFilename(),
                result.fileSize(),
                result.fileSize(),
                "COMPLETED",
                0,
                null,
                now,
                now
        ));

        log.info("Chat attachment uploaded: '{}' (ID: {}, Size: {} bytes) by '{}'",
                result.originalFilename(), attachmentId, result.fileSize(), uploader.displayName());

        return AttachmentDto.from(record);
    }

    public DownloadResource getAttachmentForDownload(UserRecord requester, String attachmentId) {
        ChatAttachmentRecord attachment = chatAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        if (attachment.isExpired()) {
            throw new ResourceNotFoundException("Attachment has expired and is no longer available");
        }

        verifyAccess(requester, attachment);

        Path physicalPath = fileStorageService.resolvePath(attachment.storagePath());
        if (!Files.exists(physicalPath)) {
            throw new ResourceNotFoundException("Physical file not found on host disk");
        }

        chatAttachmentRepository.incrementDownloadCount(attachmentId);

        // Audit transfer
        transferRepository.save(new TransferRecord(
                UUID.randomUUID().toString(),
                "DOWNLOAD",
                "ATTACHMENT",
                requester.id(),
                attachment.originalFilename(),
                attachment.fileSize(),
                attachment.fileSize(),
                "COMPLETED",
                0,
                null,
                Instant.now(),
                Instant.now()
        ));

        return new DownloadResource(
                new FileSystemResource(physicalPath),
                attachment.originalFilename(),
                attachment.mimeType(),
                attachment.fileSize()
        );
    }

    public DownloadResource getAttachmentForPreview(UserRecord requester, String attachmentId) {
        ChatAttachmentRecord attachment = chatAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        if (attachment.isExpired()) {
            throw new ResourceNotFoundException("Attachment has expired");
        }

        verifyAccess(requester, attachment);

        Path physicalPath = fileStorageService.resolvePath(attachment.storagePath());
        if (!Files.exists(physicalPath)) {
            throw new ResourceNotFoundException("Physical file not found");
        }

        return new DownloadResource(
                new FileSystemResource(physicalPath),
                attachment.originalFilename(),
                attachment.mimeType(),
                attachment.fileSize()
        );
    }

    public AttachmentDto getAttachmentInfo(UserRecord requester, String attachmentId) {
        ChatAttachmentRecord attachment = chatAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        verifyAccess(requester, attachment);
        return AttachmentDto.from(attachment);
    }

    public void linkAttachmentToMessage(String attachmentId, String messageId) {
        chatAttachmentRepository.linkToMessage(attachmentId, messageId);
    }

    @Transactional
    public int cleanupExpiredAttachments() {
        Instant now = Instant.now();
        List<ChatAttachmentRecord> expired = chatAttachmentRepository.findExpired(now);
        int count = 0;
        for (ChatAttachmentRecord a : expired) {
            fileStorageService.deletePhysicalFile(a.storagePath());
            chatAttachmentRepository.deleteById(a.id());
            count++;
        }
        if (count > 0) {
            log.info("Purged {} expired temporary guest attachments", count);
        }
        return count;
    }

    private String resolveRoomId(String idOrCode) {
        if (idOrCode == null || idOrCode.isBlank()) return idOrCode;
        return roomRepository.findByCode(idOrCode.trim().toUpperCase())
                .map(RoomRecord::id)
                .orElse(idOrCode);
    }

    private void verifyAccess(UserRecord requester, ChatAttachmentRecord attachment) {
        // Admin always has audit access
        if (requester.isAdmin()) {
            return;
        }

        if ("ROOM".equals(attachment.conversationType())) {
            if (!roomMembershipRepository.isMember(attachment.conversationId(), requester.id())) {
                throw new UnauthorizedException("Unauthorized: You are not a member of the room where this file was shared");
            }
        } else if ("DIRECT".equals(attachment.conversationType())) {
            boolean isSender = requester.id().equals(attachment.uploaderId());
            boolean isRecipient = requester.id().equals(attachment.conversationId());
            if (!isSender && !isRecipient) {
                throw new UnauthorizedException("Unauthorized: You do not have permission to access this private conversation attachment");
            }
        }
    }

    public record DownloadResource(Resource resource, String filename, String mimeType, long contentLength) {}
}
