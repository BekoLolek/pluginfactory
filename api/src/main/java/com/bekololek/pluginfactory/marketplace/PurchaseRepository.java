package com.bekololek.pluginfactory.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    List<Purchase> findByBuyerId(UUID buyerId);

    boolean existsByListingIdAndBuyerId(UUID listingId, UUID buyerId);

    @Query("SELECT COALESCE(SUM(p.priceCents), 0) FROM Purchase p WHERE p.status = 'COMPLETED'")
    long sumRevenueCents();

    List<Purchase> findByListingId(UUID listingId);
}
