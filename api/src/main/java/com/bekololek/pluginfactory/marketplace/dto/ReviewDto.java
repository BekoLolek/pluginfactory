package com.bekololek.pluginfactory.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
        UUID id,
        UUID reviewerId,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt
) {
}
