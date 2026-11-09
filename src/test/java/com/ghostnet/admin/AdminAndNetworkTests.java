package com.ghostnet.admin;

import com.ghostnet.monitoring.MetricsService;
import com.ghostnet.monitoring.SystemMetricsDto;
import com.ghostnet.network.ConnectivityDiagnosticService;
import com.ghostnet.network.NetworkInterfaceDto;
import com.ghostnet.network.NetworkInterfaceService;
import com.ghostnet.security.UnauthorizedException;
import com.ghostnet.session.AuthSessionService;
import com.ghostnet.session.SessionRecord;
import com.ghostnet.user.RegisterRequest;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRole;
import com.ghostnet.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdminAndNetworkTests {

    @Autowired
    private NetworkInterfaceService networkInterfaceService;

    @Autowired
    private ConnectivityDiagnosticService diagnosticService;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM direct_messages;");
        jdbcTemplate.execute("DELETE FROM room_messages;");
        jdbcTemplate.execute("DELETE FROM room_members;");
        jdbcTemplate.execute("DELETE FROM rooms;");
        jdbcTemplate.execute("DELETE FROM user_sessions;");
        jdbcTemplate.execute("DELETE FROM user_preferences;");
        jdbcTemplate.execute("DELETE FROM users;");
    }

    @Test
    @DisplayName("Network interface discovery resolves valid local IPv4 address")
    void testNetworkInterfaceDiscovery() {
        String primaryIp = networkInterfaceService.getPrimaryLocalAddress();
        assertNotNull(primaryIp);
        assertFalse(primaryIp.isBlank());

        List<NetworkInterfaceDto> interfaces = networkInterfaceService.getNetworkInterfaces();
        assertNotNull(interfaces);
        // On standard host, at least loopback or wlan0/eth0/docker is present
        assertTrue(!interfaces.isEmpty() || primaryIp.equals("127.0.0.1"));
    }

    @Test
    @DisplayName("Connectivity diagnostic verifies local server availability and distinguishes internet status")
    void testConnectivityDiagnostics() {
        ConnectivityDiagnosticService.DiagnosticReport report = diagnosticService.runDiagnostics();

        assertNotNull(report);
        assertNotNull(report.primaryLocalIp());
        assertEquals(8080, report.port());
        assertNotNull(report.accessUrl());
        assertTrue(report.accessUrl().startsWith("http://"));
        assertNotNull(report.operatingMode());
        assertNotNull(report.summary());
    }

    @Test
    @DisplayName("Metrics service calculates system telemetry and resource metrics")
    void testMetricsService() {
        // Register sample user
        userService.register(new RegisterRequest("host_admin", "adminPass999", "Host"), "127.0.0.1", "test");

        SystemMetricsDto metrics = metricsService.getMetrics();

        assertEquals("ONLINE", metrics.status());
        assertEquals("1.0.0", metrics.version());
        assertNotNull(metrics.hostIp());
        assertEquals(8080, metrics.port());
        assertTrue(metrics.registeredUsersCount() >= 1);
        assertNotNull(metrics.formattedUptime());
        assertTrue(metrics.memoryUsedMb() >= 0);
        assertTrue(metrics.memoryMaxMb() > 0);
        assertTrue(metrics.cpuProcessors() >= 1);
    }

    @Test
    @DisplayName("Admin session management lists active sessions and allows revocation")
    void testAdminSessionManagement() {
        var hostRes = userService.register(new RegisterRequest("master_host", "pass1234", "Host"), "127.0.0.1", "test");
        var clientRes = userService.register(new RegisterRequest("client_device", "pass1234", "Client"), "192.168.1.42", "mobile");

        List<AdminService.AdminSessionDto> sessions = adminService.getActiveSessions();
        assertEquals(2, sessions.size());

        // Revoke client session
        adminService.revokeSession(clientRes.token());

        List<AdminService.AdminSessionDto> updatedSessions = adminService.getActiveSessions();
        assertEquals(1, updatedSessions.size());
        assertEquals("master_host", updatedSessions.get(0).username());
    }
}
