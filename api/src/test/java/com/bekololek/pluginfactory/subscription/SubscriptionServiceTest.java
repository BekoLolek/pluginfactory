package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.build.BuildSessionRepository;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BuildSessionRepository buildSessionRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private void stubBillableCount(UUID userId, long count) {
        when(buildSessionRepository.countByUserIdAndStatusInAndCreatedAtAfter(
                eq(userId), anyList(), any(Instant.class))).thenReturn(count);
    }

    @Test
    void canBuild_underLimit() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.BASIC); // maxBuilds = 5
        subscription.setTokensUsedThisPeriod(0);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        stubBillableCount(userId, 3);

        assertThat(subscriptionService.canBuild(userId)).isTrue();
    }

    @Test
    void canBuild_atBuildLimit() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE); // maxBuilds = 1

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        stubBillableCount(userId, 1);

        assertThat(subscriptionService.canBuild(userId)).isFalse();
    }

    @Test
    void canBuild_failedBuildsDoNotCount() {
        // Regression guard: a user who has burned through maxBuilds worth
        // of FAILED sessions must still be able to start another build.
        // The live-count query excludes FAILED/CANCELLED, so the billable
        // count stays at 0 and the user is under the limit.
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE); // maxBuilds = 1

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        stubBillableCount(userId, 0); // 5 FAILED sessions exist but none are billable

        assertThat(subscriptionService.canBuild(userId)).isTrue();
    }

    @Test
    void canBuild_atTokenLimit() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.PRO);
        // Leave less than the 1k minimum-to-start headroom in the monthly pool.
        subscription.setTokensUsedThisPeriod(Tier.PRO.getTokenBudget() - 500);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        stubBillableCount(userId, 0);

        assertThat(subscriptionService.canBuild(userId)).isFalse();
    }

    @Test
    void canBuild_largeTeamTier() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.TEAM);
        subscription.setTokensUsedThisPeriod(0);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        stubBillableCount(userId, 100); // well under the 150 cap

        assertThat(subscriptionService.canBuild(userId)).isTrue();
    }

    @Test
    void incrementBuildCount() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE);
        subscription.setBuildsUsedThisPeriod(0);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        subscriptionService.incrementBuildCount(userId);

        assertThat(subscription.getBuildsUsedThisPeriod()).isEqualTo(1);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void refundBuildSlot_decrementsCounter() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.BASIC);
        subscription.setBuildsUsedThisPeriod(3);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        subscriptionService.refundBuildSlot(userId);

        assertThat(subscription.getBuildsUsedThisPeriod()).isEqualTo(2);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void refundBuildSlot_clampsAtZero() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE);
        subscription.setBuildsUsedThisPeriod(0);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        subscriptionService.refundBuildSlot(userId);

        // No-op refund — counter cannot go negative and we don't waste a save call.
        assertThat(subscription.getBuildsUsedThisPeriod()).isEqualTo(0);
        verify(subscriptionRepository, org.mockito.Mockito.never()).save(subscription);
    }

    @Test
    void getTierForUser_success() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.PRO);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        assertThat(subscriptionService.getTierForUser(userId)).isEqualTo(Tier.PRO);
    }

    @Test
    void getTierForUser_notFound() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getTierForUser(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Subscription not found");
    }

    @Test
    void resetUsageCounts() {
        Subscription sub1 = new Subscription();
        sub1.setBuildsUsedThisPeriod(5);
        sub1.setTokensUsedThisPeriod(123_000);
        Subscription sub2 = new Subscription();
        sub2.setBuildsUsedThisPeriod(10);
        sub2.setTokensUsedThisPeriod(456_000);

        when(subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(sub1, sub2));

        subscriptionService.resetUsageCounts();

        assertThat(sub1.getBuildsUsedThisPeriod()).isEqualTo(0);
        assertThat(sub1.getTokensUsedThisPeriod()).isEqualTo(0);
        assertThat(sub2.getBuildsUsedThisPeriod()).isEqualTo(0);
        assertThat(sub2.getTokensUsedThisPeriod()).isEqualTo(0);
        verify(subscriptionRepository).saveAll(List.of(sub1, sub2));
    }

    @Test
    void recordTokenUsage_addsToPool() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.PRO);
        subscription.setTokensUsedThisPeriod(10_000);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        subscriptionService.recordTokenUsage(userId, 2_500);

        assertThat(subscription.getTokensUsedThisPeriod()).isEqualTo(12_500);
        verify(subscriptionRepository).save(subscription);
    }
}
