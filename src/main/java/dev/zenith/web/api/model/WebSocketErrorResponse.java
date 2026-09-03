package dev.zenith.web.api.model;

public record WebSocketErrorResponse(
    String type,
    String requestId,
    String reason
) {
    public WebSocketErrorResponse(final String requestId, final String reason) {
        this("error", requestId, reason);
    }
}
