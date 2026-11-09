package com.ghostnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * GhostNet - Private offline-first networking and real-time communication platform.
 * Master application bootstrap and lifecycle coordinator.
 */
@SpringBootApplication
@EnableScheduling
public class GhostNetApplication {

    private static final Logger log = LoggerFactory.getLogger(GhostNetApplication.class);

    private final Environment environment;

    public GhostNetApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(GhostNetApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String port = environment.getProperty("server.port", "8080");
        String version = environment.getProperty("ghostnet.version", "1.0.0");

        StringBuilder ips = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                        ips.append("\n  » http://").append(addr.getHostAddress()).append(":").append(port)
                           .append(" (").append(ni.getName()).append(")");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not enumerate network interfaces: {}", e.getMessage());
        }

        if (ips.isEmpty()) {
            ips.append("\n  » http://127.0.0.1:").append(port).append(" (loopback)");
        }

        log.info("""
            
            ========================================================================
               ____ _   _  ___  ____ _____ _   _ _____ _____
              / ___| | | |/ _ \\/ ___|_   _| \\ | | ____|_   _|
             | |  _| |_| | | | \\___ \\ | | |  \\| |  _|   | |
             | |_| |  _  | |_| |___) || | | |\\  | |___  | |
              \\____|_| |_|\\___/|____/ |_| |_| \\_|_____| |_|
            
              GHOSTNET v{} — PRIVATE OFFLINE-FIRST PLATFORM
              YOUR NETWORK. YOUR PEOPLE. YOUR RULES.
            ========================================================================
              Access GhostNet from devices connected to your private Wi-Fi/Hotspot:{}
            ========================================================================
            """, version, ips);
    }
}
