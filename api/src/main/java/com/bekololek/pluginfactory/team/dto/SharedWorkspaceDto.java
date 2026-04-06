package com.bekololek.pluginfactory.team.dto;

import java.time.Instant;
import java.util.UUID;

public record SharedWorkspaceDto(
        UUID id,
        String name,
        String description,
        UUID teamId,
        UUID createdById,
        Instant createdAt
) {
}
