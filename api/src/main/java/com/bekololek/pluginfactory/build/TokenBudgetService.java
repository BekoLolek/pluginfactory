package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenBudgetService {

    private final TokenBudgetRepository tokenBudgetRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public TokenBudget allocateBudget(UUID sessionId, UUID userId, Tier tier) {
        TokenBudget budget = new TokenBudget();
        budget.setSessionId(sessionId);
        budget.setUserId(userId);
        // Per-session ceiling = whatever remains in the user's monthly pool.
        // This lets a single build use the full remaining budget if needed,
        // while still preventing a runaway build from going past the monthly cap.
        int remaining = userId != null
                ? subscriptionService.getRemainingMonthlyTokens(userId)
                : tier.getTokenBudget();
        budget.setAllocatedTokens(remaining);
        budget.setConsumedTokens(0);
        budget.setPlanningTokens(0);
        budget.setImplementationTokens(0);
        budget.setTestingTokens(0);
        budget.setThresholdStatus(ThresholdStatus.NORMAL);
        return tokenBudgetRepository.save(budget);
    }

    @Transactional
    public TokenBudget consumeTokens(UUID sessionId, String phase, int amount) {
        TokenBudget budget = tokenBudgetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Token budget not found"));

        budget.setConsumedTokens(budget.getConsumedTokens() + amount);
        if (budget.getUserId() != null) {
            subscriptionService.recordTokenUsage(budget.getUserId(), amount);
        }

        switch (phase.toLowerCase()) {
            case "planning" -> budget.setPlanningTokens(budget.getPlanningTokens() + amount);
            case "implementation" -> budget.setImplementationTokens(budget.getImplementationTokens() + amount);
            case "testing" -> budget.setTestingTokens(budget.getTestingTokens() + amount);
        }

        double ratio = (double) budget.getConsumedTokens() / budget.getAllocatedTokens();
        if (ratio >= 1.0) {
            budget.setThresholdStatus(ThresholdStatus.EXHAUSTED);
        } else if (ratio >= 0.95) {
            budget.setThresholdStatus(ThresholdStatus.CRITICAL);
        } else if (ratio >= 0.8) {
            budget.setThresholdStatus(ThresholdStatus.WARNING);
        } else {
            budget.setThresholdStatus(ThresholdStatus.NORMAL);
        }

        return tokenBudgetRepository.save(budget);
    }

    @Transactional(readOnly = true)
    public TokenBudget getRemainingBudget(UUID sessionId) {
        return tokenBudgetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Token budget not found"));
    }

    @Transactional(readOnly = true)
    public boolean hasBudget(UUID sessionId, int estimatedTokens) {
        TokenBudget budget = tokenBudgetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Token budget not found"));
        return budget.getConsumedTokens() + estimatedTokens <= budget.getAllocatedTokens();
    }
}
