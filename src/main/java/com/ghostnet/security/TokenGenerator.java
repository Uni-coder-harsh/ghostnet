package com.ghostnet.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Cryptographically secure random token and room-code generator (Req 123, 153, 213).
 */
@Component
public class TokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();
    private static final String ROOM_CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // exclude 0, 1, I, O for human readability

    /**
     * Generates a 256-bit cryptographically secure session token in hex (64 chars).
     */
    public String generateSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Generates a human-friendly unique room join code, e.g. "GN-7K4X".
     */
    public String generateRoomCode() {
        StringBuilder sb = new StringBuilder("GN-");
        for (int i = 0; i < 4; i++) {
            int index = secureRandom.nextInt(ROOM_CODE_CHARS.length());
            sb.append(ROOM_CODE_CHARS.charAt(index));
        }
        return sb.toString();
    }
}
