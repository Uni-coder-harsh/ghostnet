package com.ghostnet.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled monitor executing heartbeat checks and purging dead WebSocket connections (Req 047, 048).
 */
@Component
public class WebSocketHeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHeartbeatMonitor.class);

    private final WebSocketConnectionManager connectionManager;

    @Value("${ghostnet.network.client-timeout-seconds:45}")
    private int clientTimeoutSeconds;

    public WebSocketHeartbeatMonitor(WebSocketConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Scheduled(fixedRateString = "${ghostnet.network.heartbeat-interval-seconds:15}000")
    public void monitorConnections() {
        long timeoutMillis = clientTimeoutSeconds * 1000L;
        int closed = connectionManager.closeStaleConnections(timeoutMillis);
        if (closed > 0) {
            log.info("Closed {} timed out WebSocket connection(s)", closed);
        }
    }
}
