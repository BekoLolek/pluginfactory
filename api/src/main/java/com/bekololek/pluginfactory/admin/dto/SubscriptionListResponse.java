package com.bekololek.pluginfactory.admin.dto;

import java.util.Map;

public record SubscriptionListResponse(
        java.util.List<AdminSubscriptionSummary> content,
        long totalElements,
        int totalPages,
        SubscriptionSummary summary
) {
    public record SubscriptionSummary(
            Map<String, Long> byTier,
            Map<String, Long> byStatus,
            long mrrCents
    ) {}
}
