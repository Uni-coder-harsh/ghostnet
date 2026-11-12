package com.ghostnet.vault;

public record VaultStatusDto(
        boolean isInitialized,
        String salt,
        String authVerifier,
        long quotaBytes,
        long usedBytes,
        double usagePercentage
) {
    public static VaultStatusDto of(VaultSettingsRecord record) {
        if (record == null) {
            return new VaultStatusDto(false, null, null, 100 * 1024 * 1024L, 0L, 0.0);
        }
        double percentage = record.quotaBytes() > 0 
                ? (double) record.usedBytes() / record.quotaBytes() * 100.0 
                : 0.0;
        return new VaultStatusDto(
                record.isInitialized(),
                record.salt(),
                record.authVerifier(),
                record.quotaBytes(),
                record.usedBytes(),
                Math.round(percentage * 100.0) / 100.0
        );
    }
}
