package com.ghostnet.network;

import java.util.List;

/**
 * DTO representing an inspected physical or virtual network interface.
 */
public record NetworkInterfaceDto(
        String name,
        String displayName,
        List<String> ipv4Addresses,
        boolean up,
        boolean wireless,
        boolean primary
) {
}
