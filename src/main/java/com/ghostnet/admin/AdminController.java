package com.ghostnet.admin;

import com.ghostnet.monitoring.MetricsService;
import com.ghostnet.monitoring.SystemMetricsDto;
import com.ghostnet.network.ConnectivityDiagnosticService;
import com.ghostnet.network.NetworkInterfaceDto;
import com.ghostnet.network.NetworkInterfaceService;
import com.ghostnet.persistence.DatabaseBackupService;
import com.ghostnet.persistence.RetentionService;
import com.ghostnet.security.RequireAdmin;
import com.ghostnet.security.SecurityContext;
import com.ghostnet.user.UserRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Host Administration, System Telemetry, Network Diagnostics,
 * and Server Lifecycle Management (Req 246-261).
 * Strictly requires ROLE_ADMIN privileges.
 */
@RestController
@RequestMapping("/api/admin")
@RequireAdmin
public class AdminController {

    private final AdminService adminService;
    private final MetricsService metricsService;
    private final NetworkInterfaceService networkInterfaceService;
    private final ConnectivityDiagnosticService diagnosticService;

    public AdminController(AdminService adminService,
                           MetricsService metricsService,
                           NetworkInterfaceService networkInterfaceService,
                           ConnectivityDiagnosticService diagnosticService) {
        this.adminService = adminService;
        this.metricsService = metricsService;
        this.networkInterfaceService = networkInterfaceService;
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<SystemMetricsDto> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetrics());
    }

    @GetMapping("/network")
    public ResponseEntity<List<NetworkInterfaceDto>> getNetworkInterfaces() {
        return ResponseEntity.ok(networkInterfaceService.getNetworkInterfaces());
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<ConnectivityDiagnosticService.DiagnosticReport> runDiagnostics() {
        return ResponseEntity.ok(diagnosticService.runDiagnostics());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AdminService.AdminSessionDto>> getActiveSessions() {
        return ResponseEntity.ok(adminService.getActiveSessions());
    }

    @GetMapping("/guests")
    public ResponseEntity<List<com.ghostnet.guest.GuestIdentityRecord>> getActiveGuests() {
        return ResponseEntity.ok(adminService.getActiveGuests());
    }

    @DeleteMapping("/guests/{guestId}")
    public ResponseEntity<Void> revokeGuest(@PathVariable String guestId) {
        adminService.revokeGuest(guestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{token}")
    public ResponseEntity<Void> revokeSession(@PathVariable String token) {
        adminService.revokeSession(token);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rooms/{roomId}/participants/{targetUserId}")
    public ResponseEntity<Void> kickRoomParticipant(@PathVariable String roomId,
                                                    @PathVariable String targetUserId) {
        UserRecord currentAdmin = SecurityContext.requireUser();
        adminService.removeRoomParticipant(currentAdmin.id(), roomId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/retention/run")
    public ResponseEntity<RetentionService.RetentionReport> runRetentionCleanup() {
        return ResponseEntity.ok(adminService.runRetentionCleanup());
    }

    @PostMapping("/backup/create")
    public ResponseEntity<DatabaseBackupService.BackupResult> createBackup() {
        return ResponseEntity.ok(adminService.createBackup());
    }

    @GetMapping("/backup/list")
    public ResponseEntity<List<DatabaseBackupService.BackupInfo>> listBackups() {
        return ResponseEntity.ok(adminService.listBackups());
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> shutdownServer() {
        adminService.initiateGracefulShutdown();
        return ResponseEntity.ok(Map.of(
                "status", "SHUTTING_DOWN",
                "message", "GhostNet server is shutting down gracefully."
        ));
    }
}
