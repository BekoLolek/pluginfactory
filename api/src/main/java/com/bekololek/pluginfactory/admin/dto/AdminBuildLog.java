package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** One captured build-step log (compile / runtime / functional) for the dashboard. */
public record AdminBuildLog(
        UUID id,
        UUID iterationId,
        String phase,
        Integer exitCode,
        String content,
        Instant createdAt
) {
}
