package com.bekololek.pluginfactory.team.dto;

import java.time.Instant;
import java.util.UUID;

public record TeamDto(
        UUID id,
        String name,
        UUID ownerId,
        int maxMembers,
        int memberCount,
        Instant createdAt
) {
}
