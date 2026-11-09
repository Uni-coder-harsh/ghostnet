# GhostNet — Offline Hotspot & Local Networking Guide

**Project Motto:** *YOUR NETWORK. YOUR PEOPLE. YOUR RULES.*  
**Release:** 1.0.0 (Foundational Release)

---

## 1. Overview

GhostNet is designed from the ground up for **zero-internet operation**. The host laptop functions as both the Wi-Fi Access Point (or local network node) and the central Java application server. Connected client devices (smartphones, tablets, and other laptops) connect to the host's Wi-Fi network and access GhostNet purely over local IPv4 routing through standard web browsers without needing to install client applications or have mobile data enabled.

---

## 2. Linux Hotspot Setup (Recommended)

### 2.1 Using NetworkManager (`nmcli`) — Primary Method

Most modern Linux distributions (Kali, Ubuntu, Debian, Fedora, Arch) manage networking via `NetworkManager`. You can create an offline Wi-Fi hotspot directly using `nmcli`:

#### Step 1: Identify your Wi-Fi interface
```bash
nmcli device status
# Example output: wlan0  wifi  disconnected
```

#### Step 2: Create and start the hotspot
```bash
# Replace 'wlan0' with your Wi-Fi interface name
nmcli device wifi hotspot ifname wlan0 ssid "GhostNet" password "ghostnet123"
```
*NetworkManager automatically configures a local DHCP server (typically assigning the host `192.168.12.1` or `10.42.0.1` and issuing IP addresses in that subnet to connected clients).*

#### Step 3: Verify without Internet / Mobile Data
GhostNet does **NOT** require Internet forwarding or tethering. If your host has no active Ethernet or mobile uplink, NetworkManager will still maintain the local Wi-Fi network and assign local IPv4 addresses.

#### Step 4: Stop the hotspot
```bash
nmcli connection down Hotspot
# Or delete if created as a named profile:
nmcli connection delete Hotspot
```

---

### 2.2 Using `hostapd` & `dnsmasq` — Headless / Advanced Fallback

For minimal Linux installations, servers without NetworkManager, or embedded devices (Raspberry Pi):

#### When is `hostapd` required?
- The host Wi-Fi adapter requires specialized driver parameters or 802.11ac/ax configurations not exposed by NetworkManager.
- Running on a server distribution without a desktop environment or NetworkManager daemon.
- You require custom static DHCP IP mappings or captive portal interception.

#### Configuration Steps:
1. **Install tools:**
   ```bash
   sudo apt install hostapd dnsmasq
   ```

2. **Configure `/etc/hostapd/hostapd.conf`:**
   ```ini
   interface=wlan0
   driver=nl80211
   ssid=GhostNet
   hw_mode=g
   channel=7
   wmm_enabled=0
   macaddr_acl=0
   auth_algs=1
   ignore_broadcast_ssid=0
   wpa=2
   wpa_passphrase=ghostnet123
   wpa_key_mgmt=WPA-PSK
   rsn_pairwise=CCMP
   ```

3. **Configure `/etc/dnsmasq.conf` (Local DHCP only):**
   ```ini
   interface=wlan0
   dhcp-range=192.168.4.2,192.168.4.50,255.255.255.0,24h
   address=/#/192.168.4.1
   ```

4. **Assign host IP and start services:**
   ```bash
   sudo ip link set wlan0 up
   sudo ip addr add 192.168.4.1/24 dev wlan0
   sudo systemctl start dnsmasq
   sudo systemctl start hostapd
   ```

---

## 3. Client Isolation (AP Isolation) Considerations

> [!WARNING]
> **Understanding Hotspot Client Isolation**  
> Client Isolation (also called AP Isolation or Wireless Isolation) is a security setting on some Wi-Fi routers and mobile hotspots that blocks connected devices from communicating directly with each other or with the host.

### Impact on GhostNet:
- GhostNet uses a **Host-and-Client Architecture**. Clients only need to communicate with the **Host Server** (port 8080).
- When the host's laptop creates the hotspot using `nmcli` or `hostapd`, the host is the Default Gateway. Communication between clients and the host is permitted by default.
- If you use an external physical portable router (e.g., travel router), verify that **"AP Isolation"** or **"Guest Network Isolation"** is turned **OFF** in the router admin panel so devices can reach the host IP.

---

## 4. Mobile Device Connection Guidelines (Android & iOS)

When client devices connect to a Wi-Fi network without Internet access, modern operating systems may attempt to fall back to mobile data or display a captive portal prompt.

### Android:
1. Connect to Wi-Fi SSID **"GhostNet"**.
2. When prompted: *"This network has no internet access. Stay connected?"* -> Select **"Yes" / "Stay Connected"**.
3. If pages fail to load, temporarily turn off **Mobile Data** on the smartphone so the browser routes all traffic through the GhostNet Wi-Fi connection.

### Apple iOS / iPadOS:
1. Connect to Wi-Fi SSID **"GhostNet"**.
2. If iOS displays a captive sheet with "Cancel", tap **"Cancel"** and select **"Use Without Internet"**.
3. Open Safari and navigate directly to `http://<HOST-IP>:8080` (e.g. `http://192.168.12.1:8080`).

---

## 5. Local Network HTTPS & WSS (Local TLS Setup)

For environments where browsers enforce secure contexts (such as Progressive Web Apps or camera/audio permissions in future extensions):

### Using `mkcert` (Local Trusted CA):
```bash
# 1. Install mkcert
sudo apt install libnss3-tools
curl -JLO "https://dl.filippo.io/mkcert/latest?for=linux/amd64"
chmod +x mkcert-v*-linux-amd64 && sudo mv mkcert-v*-linux-amd64 /usr/local/bin/mkcert

# 2. Generate certificate for the host local IP (e.g. 192.168.12.1)
mkcert -install
mkcert -pkcs12 -p12-file ghostnet-keystore.p12 localhost 127.0.0.1 192.168.12.1 10.42.0.1
```

### Configure in `application.yml`:
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:ghostnet-keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
```

---

## 6. Connectivity Diagnostic Probe

GhostNet includes a built-in diagnostic utility accessible via the UI or REST endpoint:
- **UI:** Click **"Diagnostics"** in the top navigation bar.
- **CLI / Terminal:**
  ```bash
  curl http://localhost:8080/api/admin/diagnostics
  ```
- **Probe Results verify:**
  1. Server is actively listening on local interface.
  2. Resolves primary host IP reachable by clients.
  3. Distinguishes local availability from Internet access (confirming air-gapped offline mode).
