package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private MarketplaceService marketplaceService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void submitReview_happyPath() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setSellerId(UUID.randomUUID());
        listing.setAverageRating(0.0);
        listing.setReviewCount(0);

        when(marketplaceService.getListing(listingId)).thenReturn(listing);
        when(reviewRepository.existsByListingIdAndReviewerId(listingId, userId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        // For recalculateRating
        Review savedReview = new Review();
        savedReview.setRating(4);
        savedReview.setListingId(listingId);
        when(reviewRepository.findByListingId(listingId)).thenReturn(List.of(savedReview));

        Review result = reviewService.submitReview(userId, listingId, 4, "Great plugin!");

        assertThat(result.getRating()).isEqualTo(4);
        assertThat(result.getComment()).isEqualTo("Great plugin!");
        assertThat(result.getListingId()).isEqualTo(listingId);
        assertThat(result.getReviewerId()).isEqualTo(userId);
    }

    @Test
    void submitReview_duplicatePrevention() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        when(marketplaceService.getListing(listingId)).thenReturn(listing);
        when(reviewRepository.existsByListingIdAndReviewerId(listingId, userId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submitReview(userId, listingId, 5, "Duplicate"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Already reviewed");
    }

    @Test
    void recalculateRating_computesAverage() {
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setAverageRating(0.0);
        listing.setReviewCount(0);
        when(marketplaceService.getListing(listingId)).thenReturn(listing);

        Review r1 = new Review();
        r1.setRating(5);
        Review r2 = new Review();
        r2.setRating(3);
        Review r3 = new Review();
        r3.setRating(4);

        when(reviewRepository.findByListingId(listingId)).thenReturn(List.of(r1, r2, r3));

        reviewService.recalculateRating(listingId);

        assertThat(listing.getAverageRating()).isEqualTo(4.0);
        assertThat(listing.getReviewCount()).isEqualTo(3);
    }

    @Test
    void getReviews_returnsList() {
        UUID listingId = UUID.randomUUID();

        Review r1 = new Review();
        r1.setRating(5);
        Review r2 = new Review();
        r2.setRating(3);

        when(reviewRepository.findByListingId(listingId)).thenReturn(List.of(r1, r2));

        List<Review> result = reviewService.getReviews(listingId);

        assertThat(result).hasSize(2);
    }
}
