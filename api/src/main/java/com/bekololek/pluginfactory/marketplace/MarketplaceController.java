package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import com.bekololek.pluginfactory.marketplace.dto.CreateListingRequest;
import com.bekololek.pluginfactory.marketplace.dto.CreateReviewRequest;
import com.bekololek.pluginfactory.marketplace.dto.MarketplaceListingDto;
import com.bekololek.pluginfactory.marketplace.dto.PurchaseDto;
import com.bekololek.pluginfactory.marketplace.dto.ReviewDto;
import com.bekololek.pluginfactory.marketplace.dto.UpdateListingRequest;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final ReviewService reviewService;
    private final PurchaseService purchaseService;
    private final UserRepository userRepository;

    @GetMapping("/plugins")
    public ResponseEntity<Page<MarketplaceListingDto>> listPlugins(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<MarketplaceListing> listings = marketplaceService.listPublicListings(
                category, version, search, sort, PageRequest.of(page, Math.min(size, 100)));

        // Collect all seller IDs and fetch users in batch
        List<UUID> sellerIds = listings.getContent().stream()
                .map(MarketplaceListing::getSellerId)
                .distinct()
                .toList();
        Map<UUID, User> userMap = userRepository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Page<MarketplaceListingDto> dtos = listings.map(l -> toDto(l, userMap));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/plugins/{id}")
    public ResponseEntity<MarketplaceListingDto> getPlugin(@PathVariable UUID id) {
        MarketplaceListing listing = marketplaceService.getListing(id);
        User seller = userRepository.findById(listing.getSellerId()).orElse(null);
        return ResponseEntity.ok(toDto(listing, seller));
    }

    @GetMapping("/plugins/{id}/reviews")
    public ResponseEntity<List<ReviewDto>> getReviews(@PathVariable UUID id) {
        List<Review> reviews = reviewService.getReviews(id);

        List<UUID> reviewerIds = reviews.stream()
                .map(Review::getReviewerId)
                .distinct()
                .toList();
        Map<UUID, User> userMap = userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ReviewDto> dtos = reviews.stream()
                .map(r -> toReviewDto(r, userMap))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/plugins")
    public ResponseEntity<MarketplaceListingDto> createPlugin(
            @Valid @RequestBody CreateListingRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        MarketplaceListing listing = marketplaceService.createListing(userId, request);
        User seller = userRepository.findById(userId).orElse(null);
        return ResponseEntity.ok(toDto(listing, seller));
    }

    @PutMapping("/plugins/{id}")
    public ResponseEntity<MarketplaceListingDto> updatePlugin(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateListingRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        MarketplaceListing listing = marketplaceService.updateListing(userId, id, request);
        User seller = userRepository.findById(userId).orElse(null);
        return ResponseEntity.ok(toDto(listing, seller));
    }

    @DeleteMapping("/plugins/{id}")
    public ResponseEntity<Void> deletePlugin(@PathVariable UUID id) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        marketplaceService.deleteListing(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plugins/{id}/reviews")
    public ResponseEntity<ReviewDto> submitReview(
            @PathVariable UUID id,
            @Valid @RequestBody CreateReviewRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        Review review = reviewService.submitReview(userId, id, request.rating(), request.comment());
        User reviewer = userRepository.findById(userId).orElse(null);
        return ResponseEntity.ok(toReviewDto(review, reviewer));
    }

    @PostMapping("/plugins/{id}/purchase")
    public ResponseEntity<PurchaseDto> purchasePlugin(@PathVariable UUID id) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        Purchase purchase = purchaseService.purchaseFree(userId, id);
        return ResponseEntity.ok(toPurchaseDto(purchase));
    }

    @PostMapping("/plugins/{id}/purchase/checkout")
    public ResponseEntity<Map<String, String>> createPaidCheckout(@PathVariable UUID id) throws Exception {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        String checkoutUrl = purchaseService.createPaidCheckoutUrl(userId, id);
        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
    }

    @GetMapping("/my/listings")
    public ResponseEntity<List<MarketplaceListingDto>> getMyListings() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        List<MarketplaceListing> listings = marketplaceService.getMyListings(userId);

        User seller = userRepository.findById(userId).orElse(null);
        List<MarketplaceListingDto> dtos = listings.stream()
                .map(l -> toDto(l, seller))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my/purchases")
    public ResponseEntity<List<PurchaseDto>> getMyPurchases() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        List<Purchase> purchases = purchaseService.getMyPurchases(userId);
        List<PurchaseDto> dtos = purchases.stream()
                .map(this::toPurchaseDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private MarketplaceListingDto toDto(MarketplaceListing listing, Map<UUID, User> userMap) {
        User seller = userMap.get(listing.getSellerId());
        return toDto(listing, seller);
    }

    private MarketplaceListingDto toDto(MarketplaceListing listing, User seller) {
        return new MarketplaceListingDto(
                listing.getId(),
                listing.getSellerId(),
                seller != null ? seller.getDisplayName() : null,
                listing.getArtifactId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getShortDescription(),
                listing.getCategory(),
                listing.getMinecraftVersion(),
                listing.getPriceCents(),
                listing.getDownloadCount(),
                listing.getAverageRating(),
                listing.getReviewCount(),
                listing.getStatus(),
                listing.getCreatedAt()
        );
    }

    private ReviewDto toReviewDto(Review review, Map<UUID, User> userMap) {
        User reviewer = userMap.get(review.getReviewerId());
        return toReviewDto(review, reviewer);
    }

    private ReviewDto toReviewDto(Review review, User reviewer) {
        return new ReviewDto(
                review.getId(),
                review.getReviewerId(),
                reviewer != null ? reviewer.getDisplayName() : null,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    private PurchaseDto toPurchaseDto(Purchase purchase) {
        return new PurchaseDto(
                purchase.getId(),
                purchase.getListingId(),
                purchase.getPriceCents(),
                purchase.getStatus(),
                purchase.getCreatedAt()
        );
    }
}
