package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminBuildSummary(
        UUID id,
        String userEmail,
        String status,
        String currentPhase,
        Integer complexityScore,
        int tokensConsumed,
        int iterationCount,
        Instant createdAt,
        Instant completedAt
) {}
