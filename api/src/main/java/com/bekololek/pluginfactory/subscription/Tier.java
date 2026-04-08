package com.bekololek.pluginfactory.subscription;

import lombok.Getter;

@Getter
public enum Tier {
    // tokenBudget is now a MONTHLY token pool, not a per-build cap.
    // (maxBuilds, monthlyTokenPool, maxParallel, maxIterations, maxCommands,
    //  maxEventListeners, jarRetentionDays, marketplaceSlots, sourceCodeAccess)
    FREE(1, 30_000, 0, 1, 5, 3, 7, 0, false),
    BASIC(5, 300_000, 0, 3, 15, 10, 30, 2, false),
    PRO(20, 900_000, 5, 5, 50, 30, 90, 5, true),
    TEAM(150, 6_000_000, 5, 10, 50, 30, 180, 25, true);

    private final int maxBuilds;
    private final int tokenBudget;
    private final int maxParallel;
    private final int maxIterations;
    private final int maxCommands;
    private final int maxEventListeners;
    private final int jarRetentionDays;
    private final int marketplaceSlots;
    private final boolean sourceCodeAccess;

    Tier(int maxBuilds, int tokenBudget, int maxParallel, int maxIterations,
         int maxCommands, int maxEventListeners, int jarRetentionDays,
         int marketplaceSlots, boolean sourceCodeAccess) {
        this.maxBuilds = maxBuilds;
        this.tokenBudget = tokenBudget;
        this.maxParallel = maxParallel;
        this.maxIterations = maxIterations;
        this.maxCommands = maxCommands;
        this.maxEventListeners = maxEventListeners;
        this.jarRetentionDays = jarRetentionDays;
        this.marketplaceSlots = marketplaceSlots;
        this.sourceCodeAccess = sourceCodeAccess;
    }

    public boolean isUnlimited(int value) {
        return value == -1;
    }
}
