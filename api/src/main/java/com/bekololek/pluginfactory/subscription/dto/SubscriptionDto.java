package com.bekololek.pluginfactory.subscription.dto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionDto(
        UUID id,
        String tier,
        String status,
        int buildsUsedThisPeriod,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant createdAt
) {}
