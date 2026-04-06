package com.bekololek.pluginfactory.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    List<Purchase> findByBuyerId(UUID buyerId);

    boolean existsByListingIdAndBuyerId(UUID listingId, UUID buyerId);
}
