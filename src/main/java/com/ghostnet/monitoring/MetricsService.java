package com.ghostnet.monitoring;

import com.ghostnet.messaging.DirectMessageRepository;
import com.ghostnet.messaging.RoomMessageRepository;
import com.ghostnet.network.NetworkInterfaceService;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.session.PresenceService;
import com.ghostnet.user.UserRepository;
import com.ghostnet.websocket.WebSocketConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.Instant;

/**
 * Monitoring service collecting live server performance, resource metrics,
 * and communication telemetry for host administration (Req 246-255).
 */
@Service
public class MetricsService {

    private final Instant startTime = Instant.now();

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final DirectMessageRepository directMessageRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final PresenceService presenceService;
    private final WebSocketConnectionManager wsConnectionManager;
    private final NetworkInterfaceService networkInterfaceService;

    @Value("${ghostnet.version:1.0.0}")
    private String version;

    public MetricsService(UserRepository userRepository,
                          RoomRepository roomRepository,
                          DirectMessageRepository directMessageRepository,
                          RoomMessageRepository roomMessageRepository,
                          PresenceService presenceService,
                          WebSocketConnectionManager wsConnectionManager,
                          NetworkInterfaceService networkInterfaceService) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.directMessageRepository = directMessageRepository;
        this.roomMessageRepository = roomMessageRepository;
        this.presenceService = presenceService;
        this.wsConnectionManager = wsConnectionManager;
        this.networkInterfaceService = networkInterfaceService;
    }

    public SystemMetricsDto getMetrics() {
        Duration uptime = Duration.between(startTime, Instant.now());
        String formattedUptime = formatDuration(uptime);

        long registeredUsers = userRepository.count();
        int onlineUsers = presenceService.countOnlineUsers();
        int activeWsConnections = wsConnectionManager.getActiveConnectionCount();
        long activeRooms = roomRepository.count();

        long dmCount = directMessageRepository.countTotal();
        long roomMsgCount = roomMessageRepository.countTotal();
        long totalMessages = dmCount + roomMsgCount;

        // JVM Memory
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        double memPercent = maxMem > 0 ? ((double) usedMem / maxMem) * 100.0 : 0.0;

        // System / CPU
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int processors = osBean.getAvailableProcessors();
        double systemLoad = osBean.getSystemLoadAverage();

        String primaryIp = networkInterfaceService.getPrimaryLocalAddress();
        int port = networkInterfaceService.getServerPort();

        return new SystemMetricsDto(
                "ONLINE",
                version,
                primaryIp,
                port,
                "http://" + primaryIp + ":" + port,
                startTime,
                formattedUptime,
                uptime.toSeconds(),
                registeredUsers,
                onlineUsers,
                activeWsConnections,
                activeRooms,
                dmCount,
                roomMsgCount,
                totalMessages,
                usedMem,
                freeMem,
                maxMem,
                Math.round(memPercent * 10.0) / 10.0,
                processors,
                systemLoad >= 0 ? Math.round(systemLoad * 100.0) / 100.0 : 0.0
        );
    }

    private String formatDuration(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();

        if (days > 0) {
            return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        } else {
            return String.format("%dm %02ds", minutes, seconds);
        }
    }
}
