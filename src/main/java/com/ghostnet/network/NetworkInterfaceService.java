package com.ghostnet.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Service discovering active host network interfaces and dynamically resolving
 * the primary reachable hotspot/Wi-Fi IPv4 address (Req 035-038, 060).
 */
@Service
public class NetworkInterfaceService {

    private static final Logger log = LoggerFactory.getLogger(NetworkInterfaceService.class);

    @Value("${ghostnet.network.preferred-interface:}")
    private String preferredInterfaceName;

    @Value("${server.port:8080}")
    private int serverPort;

    public List<NetworkInterfaceDto> getNetworkInterfaces() {
        List<NetworkInterfaceDto> result = new ArrayList<>();
        String primaryIp = getPrimaryLocalAddress();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                List<String> ipv4s = new ArrayList<>();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        ipv4s.add(addr.getHostAddress());
                    }
                }

                if (!ipv4s.isEmpty()) {
                    boolean isWireless = ni.getName().startsWith("wl") || ni.getName().startsWith("ap") || ni.getName().contains("wifi");
                    boolean isPrimary = ipv4s.contains(primaryIp);
                    result.add(new NetworkInterfaceDto(
                            ni.getName(),
                            ni.getDisplayName(),
                            ipv4s,
                            ni.isUp(),
                            isWireless,
                            isPrimary
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate network interfaces: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Detects the best reachable local IPv4 address for connected client devices.
     */
    public String getPrimaryLocalAddress() {
        try {
            // 1. If preferred interface is configured, check it first
            if (preferredInterfaceName != null && !preferredInterfaceName.isBlank()) {
                NetworkInterface preferredNi = NetworkInterface.getByName(preferredInterfaceName.trim());
                if (preferredNi != null && preferredNi.isUp()) {
                    Enumeration<InetAddress> addrs = preferredNi.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                            return a.getHostAddress();
                        }
                    }
                }
            }

            // 2. Scan all interfaces: prioritize wireless/hotspot, then typical private LAN subnets
            List<String> wirelessIps = new ArrayList<>();
            List<String> privateLanIps = new ArrayList<>();
            List<String> otherIps = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                String name = ni.getName().toLowerCase();
                boolean isWireless = name.startsWith("wl") || name.startsWith("ap") || name.contains("wifi");

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (isWireless) {
                            wirelessIps.add(ip);
                        } else if (ip.startsWith("192.168.") || ip.startsWith("10.") || (ip.startsWith("172.") && !name.contains("docker") && !name.contains("br-"))) {
                            privateLanIps.add(ip);
                        } else {
                            otherIps.add(ip);
                        }
                    }
                }
            }

            if (!wirelessIps.isEmpty()) return wirelessIps.get(0);
            if (!privateLanIps.isEmpty()) return privateLanIps.get(0);
            if (!otherIps.isEmpty()) return otherIps.get(0);

        } catch (Exception e) {
            log.warn("Error detecting primary network address: {}", e.getMessage());
        }

        return "127.0.0.1";
    }

    public int getServerPort() {
        return serverPort;
    }
}
