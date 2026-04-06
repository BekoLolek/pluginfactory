package com.bekololek.pluginfactory.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record PurchaseDto(
        UUID id,
        UUID listingId,
        int priceCents,
        String status,
        Instant createdAt
) {
}
