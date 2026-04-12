package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserDetail(
        UserInfo user,
        SubscriptionInfo subscription,
        List<BuildInfo> recentBuilds,
        List<TeamInfo> teams,
        List<PurchaseInfo> purchases,
        List<ApiKeyInfo> apiKeys
) {
    public record UserInfo(
            UUID id, String email, String displayName,
            String status, String role, Instant createdAt, Instant lastActiveAt
    ) {}

    public record SubscriptionInfo(
            String tier, String status, int buildsUsed, int tokensUsed,
            Instant periodStart, Instant periodEnd, String stripeCustomerId
    ) {}

    public record BuildInfo(
            UUID id, String status, Integer complexityScore, Instant createdAt
    ) {}

    public record TeamInfo(UUID id, String name, String role) {}

    public record PurchaseInfo(String listingTitle, int priceCents, Instant createdAt) {}

    public record ApiKeyInfo(String name, String lastFour, Instant lastUsedAt, boolean revoked) {}
}
