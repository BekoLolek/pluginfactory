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

    @Transactional(readOnly = true)
    public boolean canBuild(UUID userId) {
        Subscription subscription = getCurrentSubscription(userId);
        int maxBuilds = subscription.getTier().getMaxBuilds();
        if (maxBuilds == -1) {
            return true;
        }
        return subscription.getBuildsUsedThisPeriod() < maxBuilds;
    }

    @Transactional
    public void incrementBuildCount(UUID userId) {
        Subscription subscription = getCurrentSubscription(userId);
        subscription.setBuildsUsedThisPeriod(subscription.getBuildsUsedThisPeriod() + 1);
        subscriptionRepository.save(subscription);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetBuildCounts() {
        log.info("Resetting build counts for all active subscriptions");
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findByStatus(Subscription.SubscriptionStatus.ACTIVE);
        for (Subscription subscription : activeSubscriptions) {
            subscription.setBuildsUsedThisPeriod(0);
        }
        subscriptionRepository.saveAll(activeSubscriptions);
        log.info("Reset build counts for {} subscriptions", activeSubscriptions.size());
    }
}
