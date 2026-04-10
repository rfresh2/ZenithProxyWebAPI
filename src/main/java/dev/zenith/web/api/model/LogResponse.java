package dev.zenith.web.api.model;

import java.util.List;

public record LogResponse(
    long baseIndex,
    long fromIndex,
    long nextIndex,
    int retained,
    List<String> lines
) { }
