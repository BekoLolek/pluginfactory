package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.build.BuildSessionRepository;
import com.bekololek.pluginfactory.build.BuildStatus;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BuildSessionRepository buildSessionRepository;

    /**
     * Build statuses that count against the user's monthly build quota.
     * Anything a user started and that either succeeded or is still
     * in-flight counts. FAILED and CANCELLED are deliberately excluded:
     * users shouldn't be punished with a wasted slot when the platform
     * (or their own cancel button) kills a build. Tokens already spent
     * are still charged separately via the TokenBudget ledger, so this
     * doesn't give anyone a free ride on real compute costs.
     */
    private static final List<BuildStatus> BILLABLE_STATUSES = List.of(
            BuildStatus.CHATTING,
            BuildStatus.PLANNING,
            BuildStatus.APPROVED,
            BuildStatus.BUILDING,
            BuildStatus.TESTING,
            BuildStatus.COMPLETED
    );

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
        if (getBillableBuildCount(subscription) >= tier.getMaxBuilds()) {
            return false;
        }
        return getRemainingMonthlyTokens(subscription) >= MIN_TOKENS_TO_START_BUILD;
    }

    /**
     * Live count of build sessions that count against the user's quota
     * for the current billing period. Single source of truth: queries
     * the build_sessions table directly rather than trusting the
     * denormalized {@code buildsUsedThisPeriod} counter, so the quota
     * always reflects reality even if a row was marked FAILED by a path
     * that bypassed {@code incrementBuildCount}/{@code refundBuildSlot}
     * (e.g. a pre-refund-wiring session, a direct DB edit, or a
     * recovery sweep on an older build of the API).
     */
    @Transactional(readOnly = true)
    public long getBillableBuildCount(UUID userId) {
        return getBillableBuildCount(getCurrentSubscription(userId));
    }

    private long getBillableBuildCount(Subscription subscription) {
        return buildSessionRepository.countByUserIdAndStatusInAndCreatedAtAfter(
                subscription.getUserId(),
                BILLABLE_STATUSES,
                periodStart(subscription)
        );
    }

    /**
     * Start of the current billing period for quota purposes. Paid
     * subscriptions have {@code currentPeriodStart} set by Stripe; free
     * tier rows don't, so we fall back to the first instant of the
     * current UTC calendar month — the same cadence the monthly cron
     * uses to reset counters.
     */
    private Instant periodStart(Subscription subscription) {
        if (subscription.getCurrentPeriodStart() != null) {
            return subscription.getCurrentPeriodStart();
        }
        return ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
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

    /**
     * Refunds a single build slot to the user's monthly quota. Called when a
     * build session terminates without producing a successful artifact
     * (FAILED or CANCELLED). Only the slot counter is refunded — tokens
     * already consumed are still charged via the TokenBudget ledger, so
     * users can't bypass real compute costs by farming failures, but they
     * also aren't punished for our flakiness or for cancelling a build they
     * no longer want. Idempotent: clamps at 0 so a double-refund (e.g.
     * FAILED → CANCELLED transition slipping through) can never give the
     * user free quota.
     */
    @Transactional
    public void refundBuildSlot(UUID userId) {
        Subscription subscription = getCurrentSubscription(userId);
        int current = subscription.getBuildsUsedThisPeriod();
        if (current <= 0) {
            return;
        }
        subscription.setBuildsUsedThisPeriod(current - 1);
        subscriptionRepository.save(subscription);
        log.info("Refunded build slot for user {} ({} → {})", userId, current, current - 1);
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
