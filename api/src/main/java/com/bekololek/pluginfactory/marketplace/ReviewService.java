package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final MarketplaceService marketplaceService;

    @Transactional
    public Review submitReview(UUID userId, UUID listingId, int rating, String comment) {
        // Verify listing exists
        MarketplaceListing listing = marketplaceService.getListing(listingId);

        // Check user hasn't already reviewed
        if (reviewRepository.existsByListingIdAndReviewerId(listingId, userId)) {
            throw new ValidationException("Already reviewed");
        }

        Review review = new Review();
        review.setListingId(listingId);
        review.setReviewerId(userId);
        review.setRating(rating);
        review.setComment(comment);

        Review saved = reviewRepository.save(review);

        recalculateRating(listingId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Review> getReviews(UUID listingId) {
        return reviewRepository.findByListingId(listingId);
    }

    @Transactional
    public void recalculateRating(UUID listingId) {
        MarketplaceListing listing = marketplaceService.getListing(listingId);
        List<Review> reviews = reviewRepository.findByListingId(listingId);

        if (reviews.isEmpty()) {
            listing.setAverageRating(0.0);
            listing.setReviewCount(0);
        } else {
            double average = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);
            listing.setAverageRating(average);
            listing.setReviewCount(reviews.size());
        }
    }
}
