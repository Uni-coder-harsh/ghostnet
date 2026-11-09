package com.ghostnet.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard WebSocket JSON message frame format (Req 041, 042).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsMessage(
        String type,
        Object payload,
        String requestId,
        Instant timestamp
) {
    public WsMessage(String type, Object payload, String requestId) {
        this(type, payload, requestId, Instant.now());
    }

    public static WsMessage of(String type, Object payload) {
        return new WsMessage(type, payload, null, Instant.now());
    }

    public static WsMessage error(String message, String requestId) {
        return new WsMessage("ERROR", message, requestId, Instant.now());
    }
}
