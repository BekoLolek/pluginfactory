package com.bekololek.pluginfactory.build.dto;

import java.time.Instant;
import java.util.UUID;

public record BuildSessionDto(
        UUID id,
        UUID userId,
        String status,
        String currentPhase,
        Integer complexityScore,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {}
