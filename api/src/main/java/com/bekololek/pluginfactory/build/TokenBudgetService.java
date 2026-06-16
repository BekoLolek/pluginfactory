package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
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

    /**
     * Refund the tokens consumed by a single session back to the user's
     * monthly pool. Idempotent: a session that has already been refunded
     * (TokenBudget.refundedAt non-null) returns the original refund
     * amount without touching the user's usage counter again.
     *
     * <p>Returns the number of tokens credited back. Zero is a valid
     * result if the session never consumed any tokens (e.g. failed
     * during CLARIFICATION before any LLM call).
     */
    @Transactional
    public int refundSessionTokens(UUID sessionId) {
        TokenBudget budget = tokenBudgetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Token budget not found"));

        // Credit only what hasn't already been refunded. This stays idempotent
        // against double-refunding the same tokens (a repeat call with no new
        // consumption credits 0), but still tops up when a session consumed more
        // AFTER an earlier refund — e.g. an admin refunded mid-outage and the
        // build then burned more tokens before ultimately failing.
        int consumed = budget.getConsumedTokens();
        int alreadyRefunded = budget.getRefundedAmount() != null ? budget.getRefundedAmount() : 0;
        int delta = Math.max(0, consumed - alreadyRefunded);

        if (delta > 0 && budget.getUserId() != null) {
            subscriptionService.refundTokens(budget.getUserId(), delta);
        }

        // Stamp when there's something new to record: a credit (delta > 0) or a
        // first-ever refund of a zero-consumed session (so a later call doesn't
        // keep retrying). A repeat call with nothing new is a true no-op.
        boolean firstStamp = budget.getRefundedAt() == null;
        if (delta > 0 || firstStamp) {
            budget.setRefundedAt(Instant.now());
            budget.setRefundedAmount(Math.max(consumed, alreadyRefunded));
            tokenBudgetRepository.save(budget);
        }
        if (delta > 0) {
            log.info("Refunded {} tokens for session {} (total refunded now {})",
                    delta, sessionId, Math.max(consumed, alreadyRefunded));
        }
        return delta;
    }
}
