package com.ghostnet.transfers;

import java.util.List;
import java.util.Optional;

public interface TransferRepository {
    void save(TransferRecord transfer);
    void updateProgress(String id, long bytesTransferred, String status, long speedBytesPerSec, String errorMessage);
    Optional<TransferRecord> findById(String id);
    List<TransferRecord> findRecentByUserId(String userId, int limit);
    List<TransferRecord> findAllRecent(int limit);
}
