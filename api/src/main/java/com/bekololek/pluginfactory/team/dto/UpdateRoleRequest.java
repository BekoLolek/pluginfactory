package com.bekololek.pluginfactory.team.dto;

import com.bekololek.pluginfactory.team.TeamRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull TeamRole role
) {
}
