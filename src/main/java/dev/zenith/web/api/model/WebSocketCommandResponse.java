package dev.zenith.web.api.model;

import java.util.List;

public record WebSocketCommandResponse(
    String type,
    String requestId,
    String embed,
    String embedComponent,
    List<String> multiLineOutput
) {
    public WebSocketCommandResponse(
        final String requestId,
        final String embed,
        final String embedComponent,
        final List<String> multiLineOutput
    ) {
        this("commandResult", requestId, embed, embedComponent, multiLineOutput);
    }
}
