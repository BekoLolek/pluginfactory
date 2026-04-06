package com.bekololek.pluginfactory.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record MarketplaceListingDto(
        UUID id,
        UUID sellerId,
        String sellerName,
        UUID artifactId,
        String title,
        String description,
        String shortDescription,
        String category,
        String minecraftVersion,
        int priceCents,
        int downloadCount,
        double averageRating,
        int reviewCount,
        String status,
        Instant createdAt
) {
}
