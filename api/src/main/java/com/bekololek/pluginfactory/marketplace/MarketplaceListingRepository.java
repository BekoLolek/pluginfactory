package com.bekololek.pluginfactory.marketplace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID>,
        JpaSpecificationExecutor<MarketplaceListing> {

    List<MarketplaceListing> findBySellerIdAndStatus(UUID sellerId, String status);

    Page<MarketplaceListing> findByStatus(String status, Pageable pageable);

    long countBySellerId(UUID sellerId);
}
