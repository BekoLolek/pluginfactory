package com.bekololek.pluginfactory.admin.dto;

import java.util.List;

public record MarketplaceListResponse(
        List<AdminMarketplaceSummary> content,
        long totalElements,
        int totalPages,
        MarketplaceSummaryStats summary
) {
    public record MarketplaceSummaryStats(
            long totalListings,
            long totalPurchases,
            long totalRevenueCents
    ) {}
}
