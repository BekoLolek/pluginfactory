package com.bekololek.pluginfactory.plan.dto;

import jakarta.validation.constraints.NotBlank;

public record PlanRevisionRequest(
        @NotBlank String feedback
) {
}
