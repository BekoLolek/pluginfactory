package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenBudgetService {

    private final TokenBudgetRepository tokenBudgetRepository;

    @Transactional
    public TokenBudget allocateBudget(UUID sessionId, Tier tier) {
        TokenBudget budget = new TokenBudget();
        budget.setSessionId(sessionId);
        budget.setAllocatedTokens(tier.getTokenBudget());
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
