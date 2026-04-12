package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminMarketplaceSummary(
        UUID id,
        String title,
        String sellerEmail,
        String category,
        int priceCents,
        int downloadCount,
        double averageRating,
        int reviewCount,
        String status,
        Instant createdAt
) {}
