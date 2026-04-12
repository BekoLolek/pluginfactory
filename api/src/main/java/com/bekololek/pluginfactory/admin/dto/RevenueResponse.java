package com.bekololek.pluginfactory.admin.dto;

import java.util.List;
import java.util.UUID;

public record RevenueResponse(
        long totalRevenueCents,
        long marketplaceRevenueCents,
        long subscriptionMrrCents,
        List<DayRevenue> timeline,
        List<TopListing> topSellingListings
) {
    public record DayRevenue(String date, long marketplaceCents) {}

    public record TopListing(UUID id, String title, long sales, long revenueCents) {}
}
