package com.bekololek.pluginfactory.build.dto;

import java.time.Instant;
import java.util.UUID;

public record ArtifactDto(
        UUID id,
        UUID sessionId,
        UUID iterationId,
        String fileHash,
        Long fileSizeBytes,
        String pluginVersion,
        boolean securityPassed,
        Instant createdAt
) {
}
