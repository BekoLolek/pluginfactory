package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public Subscription getCurrentSubscription(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
    }

    @Transactional(readOnly = true)
    public Tier getTierForUser(UUID userId) {
        return getCurrentSubscription(userId).getTier();
    }

    /**
     * Minimum tokens required to start a new build. Prevents users from starting
     * a build when only a handful of tokens remain in the monthly pool.
     */
    private static final int MIN_TOKENS_TO_START_BUILD = 1_000;

    @Transactional(readOnly = true)
    public boolean canBuild(UUID userId) {
        Subscription subscription = getCurrentSubscription(userId);
        Tier tier = subscription.getTier();
        if (subscription.getBuildsUsedThisPeriod() >= tier.getMaxBuilds()) {
            return false;
        }
        return getRemainingMonthlyTokens(subscription) >= MIN_TOKENS_TO_START_BUILD;
    }

    @Transactional(readOnly = true)
    public int getRemainingMonthlyTokens(UUID userId) {
        return getRemainingMonthlyTokens(getCurrentSubscription(userId));
    }

    private int getRemainingMonthlyTokens(Subscription subscription) {
        int remaining = subscription.getTier().getTokenBudget() - subscription.getTokensUsedThisPeriod();
        return Math.max(0, remaining);
    }

    @Transactional
    public void incrementBuildCount(UUID userId) {
        Subscription subscription = getCurrentSubscription(userId);
        subscription.setBuildsUsedThisPeriod(subscription.getBuildsUsedThisPeriod() + 1);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void recordTokenUsage(UUID userId, int tokens) {
        if (tokens <= 0) {
            return;
        }
        Subscription subscription = getCurrentSubscription(userId);
        subscription.setTokensUsedThisPeriod(subscription.getTokensUsedThisPeriod() + tokens);
        subscriptionRepository.save(subscription);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetUsageCounts() {
        log.info("Resetting build and token counts for all active subscriptions");
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findByStatus(Subscription.SubscriptionStatus.ACTIVE);
        for (Subscription subscription : activeSubscriptions) {
            subscription.setBuildsUsedThisPeriod(0);
            subscription.setTokensUsedThisPeriod(0);
        }
        subscriptionRepository.saveAll(activeSubscriptions);
        log.info("Reset usage counts for {} subscriptions", activeSubscriptions.size());
    }
}
