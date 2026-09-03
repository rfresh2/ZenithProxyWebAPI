package dev.zenith.web.api.model;

public record WebSocketCommandRequest(
    String type,
    String requestId,
    String command
) { }
