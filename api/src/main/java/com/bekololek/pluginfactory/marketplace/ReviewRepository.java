package com.bekololek.pluginfactory.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByListingId(UUID listingId);

    Optional<Review> findByListingIdAndReviewerId(UUID listingId, UUID reviewerId);

    boolean existsByListingIdAndReviewerId(UUID listingId, UUID reviewerId);
}
