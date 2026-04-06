package com.bekololek.pluginfactory.subscription.dto;

public record TierDto(
        String name,
        int maxBuilds,
        int tokenBudget,
        int maxParallel,
        int maxIterations,
        int maxCommands,
        int maxEventListeners,
        int jarRetentionDays,
        int marketplaceSlots,
        boolean sourceCodeAccess
) {}
