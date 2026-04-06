package com.bekololek.pluginfactory.build;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RetryPolicy {

    private final TokenBudgetService tokenBudgetService;

    public boolean shouldRetry(UUID sessionId, ErrorClassifier.ErrorCategory category, int currentRetryCount) {
        if (category == ErrorClassifier.ErrorCategory.SECURITY) {
            return false;
        }
        if (category == ErrorClassifier.ErrorCategory.STRUCTURAL) {
            return false;
        }
        if (currentRetryCount >= 3) {
            return false;
        }

        TokenBudget budget = tokenBudgetService.getRemainingBudget(sessionId);
        int remaining = budget.getAllocatedTokens() - budget.getConsumedTokens();
        return remaining > budget.getAllocatedTokens() * 0.2;
    }
}
