package com.bekololek.pluginfactory.subscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);

    long countByTier(Tier tier);

    long countByStatusAndTierNot(Subscription.SubscriptionStatus status, Tier tier);

    @Query("SELECT s FROM Subscription s WHERE " +
            "(CAST(:tier AS text) IS NULL OR s.tier = :tier) AND " +
            "(CAST(:status AS text) IS NULL OR s.status = :status)")
    Page<Subscription> findWithFilters(
            @Param("tier") Tier tier,
            @Param("status") Subscription.SubscriptionStatus status,
            Pageable pageable);
}
