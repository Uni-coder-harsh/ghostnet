# GhostNet — Developer Reference & Build Guide

**Project Motto:** *YOUR NETWORK. YOUR PEOPLE. YOUR RULES.*  
**Version:** 1.0.0

---

## 1. Prerequisites

- **Java Development Kit (JDK):** OpenJDK 21 LTS or higher.
  ```bash
  java -version
  # openjdk version "21.0.8" ...
  ```
- **Build Tool:** Apache Maven 3.9+ (or use the included `./mvnw` wrapper).

---

## 2. Quick Commands

### Compile & Run Tests
```bash
./mvnw clean test
```

### Build Standalone Executable JAR
```bash
./mvnw clean package
# Produces: target/ghostnet-1.0.0.jar
```

### Launch the GhostNet Server
```bash
java -jar target/ghostnet-1.0.0.jar
# Or with virtual threads explicitly enabled:
java --enable-preview -jar target/ghostnet-1.0.0.jar
```

---

## 3. Configuration Properties Reference

GhostNet is fully configurable via `src/main/resources/application.yml` or standard environment variables:

| Environment Variable | YAML Property | Default | Description |
| :--- | :--- | :--- | :--- |
| `PORT` | `server.port` | `8080` | Port for HTTP server and WebSocket endpoint. |
| `GHOSTNET_DB_PATH` | `ghostnet.database.path` | `data/ghostnet.db` | Local path for SQLite persistent database file. |
| `GHOSTNET_INTERFACE` | `ghostnet.network.preferred-interface` | `""` | Optional network interface name to bind / prioritize (e.g., `wlan0`). |
| - | `ghostnet.security.cookie-name` | `GHOSTNET_SESSION` | Name of the HttpOnly session cookie. |
| - | `ghostnet.security.session-duration-hours`| `72` | Session lifetime before re-authentication is required. |
| - | `ghostnet.security.rate-limit-per-minute` | `120` | Max requests per minute per IP / session before throttling. |
| - | `ghostnet.security.max-message-length` | `4096` | Max allowable byte length for a single message payload. |
| - | `ghostnet.retention.enabled` | `true` | Enables scheduled message retention purging. |
| - | `ghostnet.retention.message-retention-days`| `30` | Number of days to retain direct and room messages before auto-purge. |
| - | `ghostnet.network.heartbeat-interval-seconds`| `15` | Interval in seconds between heartbeat ping/pong checks. |
| - | `ghostnet.network.client-timeout-seconds` | `45` | Idle timeout before disconnected/stale sockets are terminated. |

---

## 4. Package & Class Inventory

- `com.ghostnet.GhostNetApplication`: Main bootstrap class, prints ASCII banner and host reachability URLs.
- `com.ghostnet.config`:
  - `DatabaseConfig`: Configures HikariCP DataSource, WAL mode, busy timeouts, and SQLite pragmas.
  - `WebSocketConfig`: Binds WebSocket handler to `/ws` with permissive CORS for dynamic local IPs.
  - `WebMvcConfig`: Registers `SessionAuthInterceptor` and static file routing.
- `com.ghostnet.network`:
  - `NetworkInterfaceService`: Enumerate network adapters, resolve primary Wi-Fi / hotspot address.
  - `ConnectivityDiagnosticService`: Probe local socket and distinguish offline mode from public internet.
- `com.ghostnet.user`:
  - `UserService`: User registration, authentication, profile updates, and role assignment.
  - `JdbcUserRepository`: Parameterized SQL repository for user accounts.
  - `AuthController`: REST endpoints for `/api/auth/register`, `/login`, `/logout`, `/me`.
  - `UserController`: Profile updates and user discovery.
- `com.ghostnet.session`:
  - `AuthSessionService`: Session lifecycle, cryptographically secure token issuance, and expiration.
  - `PresenceService`: In-memory thread-safe user presence registry.
- `com.ghostnet.room`:
  - `RoomService`: Room creation, joining codes (`GN-XXXX`), password verification, moderation, and capacity checks.
  - `RoomController`: REST endpoints for room discovery, joining, leaving, and participant moderation.
- `com.ghostnet.messaging`:
  - `MessagingService`: 1-to-1 direct messaging, room broadcasts, delivery status, and history pagination.
  - `MessageController`: REST endpoints for message history and transmission fallback.
- `com.ghostnet.websocket`:
  - `GhostNetWebSocketHandler`: Real-time text frame processor, event dispatch, and connection lifecycle.
  - `GhostNetHandshakeInterceptor`: Verifies session authentication prior to handshake.
  - `WebSocketConnectionManager`: Thread-safe connection registry with synchronized frame delivery.
  - `WebSocketHeartbeatMonitor`: Scheduled stale connection purger.
- `com.ghostnet.persistence`:
  - `DatabaseMigrationService`: Schema initialization and index creation on startup.
  - `DatabaseBackupService`: Online atomic backups using SQLite `VACUUM INTO`.
  - `RetentionService`: Automated daily message retention cleanup.
- `com.ghostnet.security`:
  - `PasswordService`: BCrypt password hashing.
  - `TokenGenerator`: Cryptographically secure tokens and human-friendly room codes.
  - `SessionAuthInterceptor`: Role-based route protection (`@RequireAuth`, `@RequireAdmin`).
  - `TokenBucketRateLimiter`: Thread-safe rate limiter protecting against flooding.
  - `RateLimitingFilter`: Servlet filter enforcing request quotas.
- `com.ghostnet.admin`:
  - `AdminService`: Host telemetry aggregation, session revocation, and graceful shutdown.
  - `AdminController`: Administrative endpoints.
- `com.ghostnet.monitoring`:
  - `MetricsService`: Collects JVM memory, CPU load, uptime, and communication statistics.
