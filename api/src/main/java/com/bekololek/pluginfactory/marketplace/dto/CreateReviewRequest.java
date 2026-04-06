package com.bekololek.pluginfactory.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 5000) String comment
) {
}
