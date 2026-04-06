package com.bekololek.pluginfactory.user.dto;

public record UsageStatsDto(
        int buildsUsed,
        int buildsLimit,
        String tier
) {}
