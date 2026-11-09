package com.ghostnet.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;

/**
 * Diagnostic utility verifying local server accessibility and explicitly distinguishing
 * offline application availability from external Internet connectivity (Req 056-059).
 */
@Service
public class ConnectivityDiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityDiagnosticService.class);

    private final NetworkInterfaceService networkInterfaceService;

    @Value("${server.port:8080}")
    private int port;

    public ConnectivityDiagnosticService(NetworkInterfaceService networkInterfaceService) {
        this.networkInterfaceService = networkInterfaceService;
    }

    public DiagnosticReport runDiagnostics() {
        String primaryIp = networkInterfaceService.getPrimaryLocalAddress();
        List<NetworkInterfaceDto> interfaces = networkInterfaceService.getNetworkInterfaces();

        boolean localReachable = testSocketConnection("127.0.0.1", port, 500);
        boolean primaryIpReachable = testSocketConnection(primaryIp, port, 500);

        // Distinguish application availability from external internet reachability (Req 058)
        boolean internetReachable = testExternalReachability("1.1.1.1", 53, 200);

        String operatingMode = internetReachable ? "HYBRID_CONNECTED_MODE" : "AIR_GAPPED_OFFLINE_MODE";
        String accessUrl = "http://" + primaryIp + ":" + port;

        StringBuilder summary = new StringBuilder();
        if (localReachable || primaryIpReachable) {
            summary.append("GhostNet server is active and reachable locally on port ").append(port).append(". ");
        } else {
            summary.append("Warning: Local socket port test failed. ");
        }

        if (internetReachable) {
            summary.append("External Internet connection detected. GhostNet operates self-contained without external dependencies.");
        } else {
            summary.append("Air-gapped offline operation confirmed (No external Internet connection required).");
        }

        return new DiagnosticReport(
                localReachable || primaryIpReachable,
                primaryIp,
                port,
                accessUrl,
                internetReachable,
                operatingMode,
                summary.toString(),
                interfaces,
                Instant.now()
        );
    }

    private boolean testSocketConnection(String host, int targetPort, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, targetPort), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testExternalReachability(String externalHost, int externalPort, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(externalHost, externalPort), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public record DiagnosticReport(
            boolean serverReachableLocally,
            String primaryLocalIp,
            int port,
            String accessUrl,
            boolean internetReachable,
            String operatingMode,
            String summary,
            List<NetworkInterfaceDto> detectedInterfaces,
            Instant timestamp
    ) {}
}
