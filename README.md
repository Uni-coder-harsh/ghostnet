# GhostNet

> **YOUR NETWORK. YOUR PEOPLE. YOUR RULES.**

[![Java 21 LTS](https://img.shields.io/badge/Java-21%20LTS-00ffc8.svg?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-00b4d8.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![SQLite WAL](https://img.shields.io/badge/SQLite-WAL%20Mode-10b981.svg?style=flat-square&logo=sqlite)](https://www.sqlite.org/)
[![License MIT](https://img.shields.io/badge/License-MIT-f8fafc.svg?style=flat-square)](LICENSE)
[![Offline First](https://img.shields.io/badge/Network-100%25%20Offline%20First-fbbf24.svg?style=flat-square)](#)

---

## 1. Overview

**GhostNet** is a genuine, private, offline-first networking and real-time communication platform engineered in Java. It enables multiple laptops, smartphones, and tablets to communicate seamlessly across a locally hosted wireless network **without requiring Internet access, cellular data, or external cloud infrastructure**.

The host's laptop functions as the primary application server. Connected peers access GhostNet through ordinary mobile or desktop web browsers without installing any client applications.

---

## 2. Core Capabilities

- **Zero-Internet Operation:** Operates entirely within local Wi-Fi or ad-hoc hotspots without external DNS or third-party cloud dependencies.
- **Dynamic Interface & IP Discovery:** Automatically detects host network adapters (`wlan0`, `ap0`, `eth0`) and broadcasts reachable local IPv4 addresses.
- **Secure Authentication & Identities:** Account registration, BCrypt password hashing (strength 12), cryptographically secure session tokens, and automatic host administrator provisioning for the initial account.
- **Real-Time Direct Messaging:** 1-to-1 private messaging over WebSockets with delivery tracking (`SENT`, `DELIVERED`, `READ`), idempotency deduplication, and persistent SQLite storage.
- **Communication Rooms:** Public and private rooms, human-friendly short join codes (`GN-XXXX`), optional password protection, capacity limits, participant tracking, and owner moderation (kick members, transfer ownership, delete room).
- **In-Memory Multi-Device Presence:** Decouples user identity from physical connections, tracking real-time online/offline presence across laptops and smartphones.
- **Host Administration Dashboard (`/admin.html`):** Real-time telemetry (uptime, active WebSocket connections, online users, message counts, memory and CPU load), active session revocation, database snapshot creation, retention purge, and graceful shutdown.
- **Dark Futuristic Visual Identity:** Zero-dependency, responsive Single Page Application with dark slate/charcoal aesthetics, neon accents, and embedded vector icons (100% offline, zero CDN dependencies).
- **SQLite Concurrency & WAL Mode:** File-based embedded persistence with Write-Ahead Logging (`WAL`), `5000ms` busy timeout, foreign key enforcement, and non-blocking `VACUUM INTO` online snapshots.
- **Message Retention & Privacy:** Configurable retention policy (default 30 days) with automated purge. Message payloads are never exposed in standard server logs.

---

## 3. Architecture

```
                  ┌─────────────────────────────────────────┐
                  │              HOST LAPTOP                │
                  │                                         │
                  │  ┌───────────────────────────────────┐  │
                  │  │     Private Wi-Fi Hotspot         │  │
                  │  │       (No Internet Needed)        │  │
                  │  └─────────────────┬─────────────────┘  │
                  │                    │                    │
                  │  ┌─────────────────▼─────────────────┐  │
                  │  │       GhostNet Java Server        │  │
                  │  │         (Java 21 LTS)             │  │
                  │  │  ┌─────────────┐ ┌──────────────┐ │  │
                  │  │  │ Spring Boot │ │  WebSockets  │ │  │
                  │  │  │  REST APIs  │ │ Real-Time /ws│ │  │
                  │  │  └──────┬──────┘ └──────┬───────┘ │  │
                  │  │         │               │         │  │
                  │  │  ┌──────▼───────────────▼───────┐ │  │
                  │  │  │       Core Services          │ │  │
                  │  │  │ (Auth, Rooms, Messaging,     │ │  │
                  │  │  │  Presence, Network, Admin)   │ │  │
                  │  │  └──────────────┬───────────────┘ │  │
                  │  │                 │                 │  │
                  │  │  ┌──────────────▼───────────────┐ │  │
                  │  │  │     SQLite Database (WAL)    │ │  │
                  │  │  │      data/ghostnet.db        │ │  │
                  │  │  └──────────────────────────────┘ │  │
                  │  └───────────────────────────────────┘  │
                  └────────────────────▲────────────────────┘
                                       │ Local Wi-Fi (HTTP & WS)
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
     ┌────────┴────────┐      ┌────────┴────────┐      ┌────────┴────────┐
     │  Client Phone   │      │  Client Laptop  │      │  Client Tablet  │
     │ (Safari/Chrome) │      │ (Firefox/Chrome)│      │ (Browser Client)│
     └─────────────────┘      └─────────────────┘      └─────────────────┘
```

For complete architectural details, see [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md).

---

## 4. Quick Start

### 4.1 Prerequisites
- **OpenJDK 21 LTS** or newer (`java -version`).
- Linux, macOS, or Windows host laptop.

### 4.2 Build the Project
Clone the repository and build the standalone executable JAR:
```bash
./mvnw clean package
```

### 4.3 Run the Server
```bash
./scripts/start.sh
# Or directly:
java -jar target/ghostnet-1.0.0.jar
```

The server displays an ASCII startup banner showing reachable local addresses:
```
========================================================================
   ____ _   _  ___  ____ _____ _   _ _____ _____
  / ___| | | |/ _ \/ ___|_   _| \ | | ____|_   _|
 | |  _| |_| | | | \___ \ | | |  \| |  _|   | |
 | |_| |  _  | |_| |___) || | | |\  | |___  | |
  \____|_| |_|\___/|____/ |_| |_| \_|_____| |_|

  GHOSTNET v1.0.0 — PRIVATE OFFLINE-FIRST PLATFORM
  YOUR NETWORK. YOUR PEOPLE. YOUR RULES.
========================================================================
  Access GhostNet from devices connected to your private Wi-Fi/Hotspot:
  » http://192.168.12.1:8080 (wlan0)
  » http://127.0.0.1:8080 (loopback)
========================================================================
```

---

## 5. Setting Up a Private Hotspot

### Linux (NetworkManager):
```bash
# Using the helper script:
./scripts/create-hotspot.sh wlan0 "GhostNet" "ghostnet123"

# Or manually via nmcli:
nmcli device wifi hotspot ifname wlan0 ssid "GhostNet" password "ghostnet123"
```

### Connecting Client Devices:
1. On smartphones or laptops, join the Wi-Fi network **GhostNet**.
2. If Android or iOS prompts *"Network has no internet. Stay connected?"*, select **Yes / Stay Connected**.
3. Open any web browser and navigate to:
   ```
   http://<HOST-IP>:8080
   ```
   *(e.g., `http://192.168.12.1:8080`)*

For detailed hotspot troubleshooting and `hostapd` configurations, consult the [HOTSPOT_GUIDE.md](docs/networking/HOTSPOT_GUIDE.md).

---

## 6. Project Structure

```
ghostnet/
├── src/
│   ├── main/
│   │   ├── java/com/ghostnet/
│   │   │   ├── GhostNetApplication.java       # Bootstrap & IP banner
│   │   │   ├── admin/                         # Host administration & control
│   │   │   ├── common/                        # Error response & domain exceptions
│   │   │   ├── config/                        # Database, WebSocket & MVC configs
│   │   │   ├── messaging/                     # 1-to-1 DMs & room messages
│   │   │   ├── monitoring/                    # JVM & system telemetry metrics
│   │   │   ├── network/                       # Interface discovery & diagnostics
│   │   │   ├── persistence/                   # Migrations, backup & retention
│   │   │   ├── room/                          # Room lifecycles, codes & moderation
│   │   │   ├── security/                      # BCrypt, tokens, interceptor & rate limiting
│   │   │   ├── session/                       # Auth sessions & in-memory presence
│   │   │   └── user/                          # Users, profiles & authentication
│   │   └── resources/
│   │       ├── application.yml                # Configuration defaults
│   │       └── static/                        # Self-contained dark futuristic SPA
│   │           ├── index.html                 # Main chat client
│   │           ├── admin.html                 # Host admin dashboard
│   │           ├── css/ghostnet.css           # Futuristic dark visual theme
│   │           └── js/                        # Pure offline vector icons & scripts
│   └── test/java/com/ghostnet/                # JUnit 5 integration & unit test suite
├── docs/
│   ├── architecture/ARCHITECTURE.md           # System architecture specification
│   ├── networking/HOTSPOT_GUIDE.md            # Linux hotspot & network isolation guide
│   └── development/DEVELOPMENT.md             # Developer build reference
├── scripts/
│   ├── start.sh                               # Launch script
│   ├── create-hotspot.sh                      # NetworkManager hotspot spawner
│   ├── backup.sh                              # Database snapshot script
│   └── diagnostics.sh                         # Network connectivity probe
├── pom.xml                                    # Maven dependencies & plugins
├── LICENSE                                    # MIT License
└── README.md                                  # Project overview
```

---

## 7. Testing & Verification

GhostNet includes comprehensive automated JUnit 5 tests covering persistence, authentication, rate limiting, room joining, direct messaging, and telemetry:

```bash
./mvnw clean test
```

### Verified Test Scope:
- **Authentication & User Management:** Registration, case-insensitive uniqueness, first-user `ROLE_ADMIN` assignment, BCrypt password matching, profile updates, and password changes.
- **Presence & Sessions:** Multi-connection presence tracking, session revocation, and expiration cleanup.
- **Room Management:** Room creation, unique human-readable code generation (`GN-XXXX`), password validation, capacity enforcement, participant kick moderation, ownership transfer, and auto-deletion.
- **Messaging:** Direct messaging delivery status (`SENT`, `DELIVERED`, `READ`), idempotency caching, room broadcast isolation, and history pagination.
- **Database & Persistence:** SQLite WAL mode pragmas, schema migrations, online snapshots, and message retention purge.
- **Host Administration:** Network interface resolution, local vs external connectivity diagnostics, and telemetry metrics.

---

## 8. Scope Boundaries (Version 1.0)

As specified in the Master Specification:
- **Strictly Offline V1:** No AI or cloud LLMs, no remote Internet dependencies, no external CDN calls, no WebRTC/video calls, and no distributed multi-server federation.
- **Focus:** Complete reliability, architectural clarity, security, and rock-solid private Wi-Fi communication.

---

## 9. License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
