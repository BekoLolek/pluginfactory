package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.BudgetExhaustedException;
import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IterationService {

    private final BuildSessionService buildSessionService;
    private final BuildIterationRepository buildIterationRepository;
    private final SubscriptionService subscriptionService;
    private final TokenBudgetService tokenBudgetService;
    private final ChatMessageService chatMessageService;
    private final BuildLauncher buildLauncher;

    public BuildIteration requestIteration(UUID sessionId, UUID userId, String feedback) {
        // 1. Load session, verify ownership, verify status == COMPLETED
        BuildSession session = buildSessionService.getSession(sessionId, userId);
        if (session.getStatus() != BuildStatus.COMPLETED) {
            throw new ForbiddenException("Session must be in COMPLETED status to iterate");
        }

        // 2. Get tier, check iterations used vs maxIterations
        Tier tier = subscriptionService.getTierForUser(userId);
        List<BuildIteration> existingIterations = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId);
        int iterationsUsed = existingIterations.size();

        int maxIterations = tier.getMaxIterations();
        if (!tier.isUnlimited(maxIterations) && iterationsUsed >= maxIterations) {
            throw new ForbiddenException("Iteration limit reached for your tier");
        }

        // 3. Check token budget remaining - throw BudgetExhaustedException if < 20%
        TokenBudget budget = tokenBudgetService.getRemainingBudget(sessionId);
        int remaining = budget.getAllocatedTokens() - budget.getConsumedTokens();
        if (remaining <= budget.getAllocatedTokens() * 0.2) {
            throw new BudgetExhaustedException("Insufficient token budget for iteration");
        }

        // 4. Add feedback as user message to chat history
        chatMessageService.addMessage(sessionId, "user", feedback, null, 0);

        // 5. Update session status back to BUILDING
        buildSessionService.updateStatus(sessionId, BuildStatus.BUILDING);

        // 6. Kick off the async pipeline. startBuild creates the
        // BuildIteration row synchronously and hands off to the
        // async worker, so we can return the iteration immediately
        // without racing the worker on the DB.
        return buildLauncher.startBuild(sessionId, "MANUAL_ITERATION");
    }

    public List<BuildIteration> listIterations(UUID sessionId) {
        return buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId);
    }
}
