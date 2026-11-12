package com.ghostnet.network;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public REST controller for network interface discovery and connectivity diagnostics (Req 035-039, 056-059).
 * Accessible without authentication so connected devices can immediately resolve host coordinates.
 */
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NetworkInterfaceService networkInterfaceService;
    private final ConnectivityDiagnosticService diagnosticService;

    @Value("${server.port:8080}")
    private int serverPort;

    public NetworkController(NetworkInterfaceService networkInterfaceService,
                             ConnectivityDiagnosticService diagnosticService) {
        this.networkInterfaceService = networkInterfaceService;
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/addresses")
    public ResponseEntity<Map<String, Object>> getAddresses() {
        String primary = networkInterfaceService.getPrimaryLocalAddress();
        List<NetworkInterfaceDto> interfaces = networkInterfaceService.getNetworkInterfaces();
        String accessUrl = "http://" + primary + ":" + serverPort + "/";

        return ResponseEntity.ok(Map.of(
                "primaryAddress", primary,
                "port", serverPort,
                "accessUrl", accessUrl,
                "interfaces", interfaces
        ));
    }

    @GetMapping("/interfaces")
    public ResponseEntity<List<NetworkInterfaceDto>> getInterfaces() {
        return ResponseEntity.ok(networkInterfaceService.getNetworkInterfaces());
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<ConnectivityDiagnosticService.DiagnosticReport> getDiagnostics() {
        return ResponseEntity.ok(diagnosticService.runDiagnostics());
    }
}
