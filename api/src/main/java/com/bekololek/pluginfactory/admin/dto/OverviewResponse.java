package com.bekololek.pluginfactory.admin.dto;

public record OverviewResponse(
        long totalUsers,
        long activeUsersLast24h,
        long activeUsersLast7d,
        long newUsersToday,
        long totalBuilds,
        long buildsToday,
        double buildSuccessRate,
        long activePaidSubscriptions,
        long mrrCents,
        long totalRevenueCents,
        long activeTeams
) {}
