package com.bekololek.pluginfactory.build.dto;

import jakarta.validation.constraints.NotBlank;

public record IterationRequest(
        @NotBlank String feedback
) {
}
