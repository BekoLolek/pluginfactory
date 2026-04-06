package com.bekololek.pluginfactory.build.dto;

import java.time.Instant;
import java.util.UUID;

public record BuildIterationDto(
        UUID id,
        UUID sessionId,
        int iterationNumber,
        String status,
        String trigger,
        Instant startedAt,
        Instant completedAt
) {
}
