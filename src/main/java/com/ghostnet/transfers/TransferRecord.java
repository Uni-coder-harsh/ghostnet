package com.ghostnet.transfers;

import java.time.Instant;

/**
 * Domain record tracking file transfer activity for the Transfer Center (Req Section 7).
 */
public record TransferRecord(
        String id,
        String transferType, // 'UPLOAD' or 'DOWNLOAD'
        String category,     // 'ATTACHMENT' or 'VAULT'
        String userId,
        String filename,
        long fileSize,
        long bytesTransferred,
        String status,       // 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'
        long speedBytesPerSec,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {}
