package com.bekololek.pluginfactory.team.dto;

import com.bekololek.pluginfactory.team.TeamRole;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberDto(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        TeamRole role,
        Instant joinedAt
) {
}
