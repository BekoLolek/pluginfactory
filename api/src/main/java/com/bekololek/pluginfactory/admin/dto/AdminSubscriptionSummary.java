package com.bekololek.pluginfactory.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubscriptionSummary(
        UUID userId,
        String userEmail,
        String tier,
        String status,
        int buildsUsed,
        int tokensUsed,
        Instant periodEnd,
        String stripeCustomerId
) {}
