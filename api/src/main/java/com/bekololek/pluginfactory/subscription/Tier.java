package com.bekololek.pluginfactory.subscription;

import lombok.Getter;

@Getter
public enum Tier {
    FREE(1, 50_000, 0, 0, 5, 3, 7, 0, false),
    BASIC(5, 200_000, 0, 2, 15, 10, 30, 1, false),
    PRO(20, 500_000, 5, 5, -1, -1, 90, 5, true),
    TEAM(-1, 1_000_000, 20, -1, -1, -1, -1, -1, true);

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
