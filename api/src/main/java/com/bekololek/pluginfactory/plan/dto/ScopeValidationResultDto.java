package com.bekololek.pluginfactory.plan.dto;

import java.util.List;

public record ScopeValidationResultDto(
        String status,
        List<String> violations
) {
}
