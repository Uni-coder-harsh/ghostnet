# GhostNet

> **YOUR NETWORK. YOUR PEOPLE. YOUR RULES.**

[![Java 21 LTS](https://img.shields.io/badge/Java-21%20LTS-00ffc8.svg?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-00b4d8.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![SQLite WAL](https://img.shields.io/badge/SQLite-WAL%20Mode-10b981.svg?style=flat-square&logo=sqlite)](https://www.sqlite.org/)
[![License MIT](https://img.shields.io/badge/License-MIT-f8fafc.svg?style=flat-square)](LICENSE)
[![Offline First](https://img.shields.io/badge/Network-100%25%20Offline%20Air--Gapped-fbbf24.svg?style=flat-square)](#)

---

## 1. Overview

**GhostNet** is a genuine, private, offline-first networking and operational communication platform engineered in Java 21 LTS. It turns any laptop into a self-hosted local communication hub accessible to smartphones, tablets, and computers across a private wireless hotspot **without requiring Internet connectivity, cellular mobile data, or external cloud infrastructure**.

Connected clients communicate through ordinary web browsers without installing native apps or browser extensions.

---

## 2. Core Capabilities

- **Zero-Internet Operation:** Operates entirely within local Wi-Fi or ad-hoc hotspots without external DNS or third-party cloud dependencies.
- **Three Access Tiers:**
  1. **Registered Operatives:** Permanent accounts, profile notes, 1-on-1 direct transmissions, communication enclaves, file sharing, and zero-knowledge private encrypted vaults.
  2. **Ephemeral Guests:** Instant onboarding with only a temporary display name. Ephemeral guests receive a browser device credential (`gdev_...`) to seamlessly restore identity across browser disconnects. Guest uploads expire in 24 hours. Vault access is strictly denied.
  3. **Host Administrator:** The host laptop administrator (`admin` / `ghostadmin`) has total control over network configuration, storage limits, active sessions, guest identity revocations, and audit telemetry.
- **Conversation File Sharing:** Upload and download files in 1-on-1 DMs and multi-user room enclaves with drag-and-drop, real-time progress bars, MIME type validation, and SHA-256 integrity checksum badges.
- **Individual Zero-Knowledge Private Vaults:** Individual encrypted vaults for registered operatives using client-side **WebCrypto AES-256-GCM** with keys derived locally from user PINs using **PBKDF2 (600,000 iterations)**. Ciphertext is streamed to isolated host storage paths (`data/vaults/<user-id>/`). The host server never possesses or sees user PINs, plaintext data, or decryption keys (zero host backdoors).
- **Physical Storage Segregation:** Secure filesystem layout segregating `vaults/`, `chat-files/`, `temporary/guest-uploads/`, `temporary/transfer-buffers/`, and `backups/`, with hardened path traversal defense and disk quota enforcement.
- **Engineering Network Console:** 9 integrated operational sections:
  - `[01] Overview:` Network telemetry, reachable host IP/port, connected clients, free disk gauge.
  - `[02] Messages:` Direct 1-on-1 private transmissions with file attachments.
  - `[03] Rooms:` Multi-user enclaves and `#General Mesh` (`GN-MAIN`) with code joining and file sharing.
  - `[04] File Transfers:` Live Transfer Center tracking active/completed uploads, speeds, and SHA-256 hashes.
  - `[05] Private Vault:` Client-side encrypted storage with folder breadcrumbs, search, and decrypt actions.
  - `[06] Network Members:` Local operative & guest directory with online presence indicators.
  - `[07] Activity Log:` Real-time chronological audit trail of connections and transmissions.
  - `[08] Settings:` Profile configuration, PIN management, and guest device credential reset.
  - `[09] Host Admin:` Central host control dashboard for quotas, guests, and storage maintenance.

---

## 3. Host Administrator Credentials

On startup, GhostNet automatically provisions the default host administrator:

```
Web Client URL:        http://<host-ip>:8080/
Host Admin URL:        http://<host-ip>:8080/admin.html

Default Username:      admin
Default Password:      ghostadmin

Default Public Room:   #General Mesh (Code: GN-MAIN)
```
*(Any user account created with `ROLE_ADMIN` also has full administrator access).*

---

## 4. Architecture

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
                  │  │  │       Core Platform          │ │  │
                  │  │  │ (Auth, Guests, Rooms, Files, │ │  │
                  │  │  │  Vaults, Transfers, Admin)   │ │  │
                  │  │  └──────────────┬───────────────┘ │  │
                  │  │                 │                 │  │
                  │  │  ┌──────────────▼───────────────┐ │  │
                  │  │  │       Storage & Persistence  │ │  │
                  │  │  │ • SQLite WAL (ghostnet.db)   │ │  │
                  │  │  │ • data/vaults/<user-id>/     │ │  │
                  │  │  │ • data/chat-files/<conv>/    │ │  │
                  │  │  │ • data/temporary/guest/      │ │  │
                  │  └───────────────────────────────────┘  │
                  └────────────────────┬────────────────────┘
                                       │
                      HTTP / WebSockets on Local Wi-Fi
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            │                          │                          │
  ┌─────────▼────────┐       ┌─────────▼────────┐       ┌─────────▼────────┐
  │  Client Laptop   │       │    Smartphone    │       │     Tablet       │
  │ (Chrome/Firefox) │       │  (Safari/Chrome) │       │ (Mobile Browser) │
  └──────────────────┘       └──────────────────┘       └──────────────────┘
```

---

## 5. Quick Start

### Prerequisites
- Linux, macOS, or Windows
- OpenJDK 21 LTS or higher (`java -version`)
- Maven wrapper included (`./mvnw`)

### Build
```bash
./mvnw clean package
```

### Run
```bash
java -jar target/ghostnet-1.0.0.jar
```
Or run directly in development mode:
```bash
./mvnw spring-boot:run
```

GhostNet will display the reachable local IP address on startup:
```
========================================================================
  Access GhostNet from devices connected to your private Wi-Fi/Hotspot:
  » http://192.168.1.50:8080 (wlan0)
========================================================================
```

---

## 6. Creating an Offline Hotspot on Linux

### Option A: NetworkManager (nmcli) — Recommended
```bash
# Create and start private wireless hotspot
nmcli device wifi hotspot ifname wlan0 ssid GhostNet password "ghostnet123"

# Verify IP assigned to host (usually 10.42.0.1 or 192.168.x.x)
ip addr show wlan0
```

### Option B: Linux Wi-Fi Hotspot GUI
1. Open **Settings** → **Wi-Fi** → Click **Turn On Wi-Fi Hotspot...**
2. Set Network Name to `GhostNet` and enter a password.
3. Connected devices can now navigate to `http://<host-ip>:8080/`.

---

## 7. Testing & Verification

GhostNet includes comprehensive test suites covering unit logic, integration flows, and the **Mandatory Testing Scenarios A through H**:

```bash
# Run all 52 platform tests
./mvnw clean test

# Run Mandatory Scenarios A–H specifically
./mvnw test -Dtest=PlatformScenariosTests
```

### Verified Scenarios:
- **Scenario A:** Direct message file sharing between two registered operatives with third-party isolation.
- **Scenario B:** Room message file sharing with non-member access denial.
- **Scenario C:** Private vault isolation (User A cannot see or access User B's vault files).
- **Scenario D:** Ephemeral guest temporary access, file sharing, and temporary directory segregation.
- **Scenario E:** Guest vault rejection (guests attempting vault access receive `403 Forbidden`).
- **Scenario F:** Returning guest device recognition and identity restoration via random device credential.
- **Scenario G:** Storage limits and quota enforcement.
- **Scenario H:** Path traversal security defense (`../` neutralization and storage root enforcement).
