package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void canBuild_underLimit() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.BASIC); // maxBuilds = 5
        subscription.setBuildsUsedThisPeriod(3);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        assertThat(subscriptionService.canBuild(userId)).isTrue();
    }

    @Test
    void canBuild_atLimit() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE); // maxBuilds = 1
        subscription.setBuildsUsedThisPeriod(1);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        assertThat(subscriptionService.canBuild(userId)).isFalse();
    }

    @Test
    void canBuild_unlimitedTier() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.TEAM); // maxBuilds = -1 (unlimited)
        subscription.setBuildsUsedThisPeriod(1000);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

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
    void resetBuildCounts() {
        Subscription sub1 = new Subscription();
        sub1.setBuildsUsedThisPeriod(5);
        Subscription sub2 = new Subscription();
        sub2.setBuildsUsedThisPeriod(10);

        when(subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(sub1, sub2));

        subscriptionService.resetBuildCounts();

        assertThat(sub1.getBuildsUsedThisPeriod()).isEqualTo(0);
        assertThat(sub2.getBuildsUsedThisPeriod()).isEqualTo(0);
        verify(subscriptionRepository).saveAll(List.of(sub1, sub2));
    }
}
