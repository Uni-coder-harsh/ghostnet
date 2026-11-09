package com.ghostnet.monitoring;

import java.time.Instant;

/**
 * Structured system telemetry DTO for host administration dashboard (Req 246-255).
 */
public record SystemMetricsDto(
        String status,
        String version,
        String hostIp,
        int port,
        String accessUrl,
        Instant startedAt,
        String formattedUptime,
        long uptimeSeconds,
        long registeredUsersCount,
        int onlineUsersCount,
        int activeWebSocketConnections,
        long activeRoomsCount,
        long directMessagesCount,
        long roomMessagesCount,
        long totalMessagesProcessed,
        long memoryUsedMb,
        long memoryFreeMb,
        long memoryMaxMb,
        double memoryUsagePercent,
        int cpuProcessors,
        double cpuSystemLoadAverage
) {
}
