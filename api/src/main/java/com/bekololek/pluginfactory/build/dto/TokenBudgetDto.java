package com.bekololek.pluginfactory.build.dto;

import java.util.UUID;

public record TokenBudgetDto(
        UUID sessionId,
        int allocatedTokens,
        int consumedTokens,
        int planningTokens,
        int implementationTokens,
        int testingTokens,
        String thresholdStatus,
        int remainingTokens
) {}
