package com.bekololek.pluginfactory.team.dto;

import com.bekololek.pluginfactory.team.TeamRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddTeamMemberRequest(
        @NotNull UUID userId,
        @NotNull TeamRole role
) {
}
