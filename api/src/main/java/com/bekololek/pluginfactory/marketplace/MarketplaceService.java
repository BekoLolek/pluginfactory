package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.build.Artifact;
import com.bekololek.pluginfactory.build.ArtifactRepository;
import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.marketplace.dto.CreateListingRequest;
import com.bekololek.pluginfactory.marketplace.dto.UpdateListingRequest;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final MarketplaceListingRepository listingRepository;
    private final ArtifactRepository artifactRepository;
    private final BuildSessionService buildSessionService;
    private final SubscriptionService subscriptionService;

    @Transactional
    public MarketplaceListing createListing(UUID userId, CreateListingRequest req) {
        // Validate seller owns the artifact
        Artifact artifact = artifactRepository.findById(req.artifactId())
                .orElseThrow(() -> new NotFoundException("Artifact not found"));

        BuildSession session = buildSessionService.getSessionById(artifact.getSessionId());
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not own this artifact");
        }

        // Check marketplace slot limit
        Tier tier = subscriptionService.getTierForUser(userId);
        int slots = tier.getMarketplaceSlots();
        if (!tier.isUnlimited(slots)) {
            long currentCount = listingRepository.countBySellerId(userId);
            if (currentCount >= slots) {
                throw new ValidationException("Marketplace slot limit reached for your tier");
            }
        }

        MarketplaceListing listing = new MarketplaceListing();
        listing.setSellerId(userId);
        listing.setArtifactId(req.artifactId());
        listing.setTitle(req.title());
        listing.setDescription(req.description());
        listing.setShortDescription(req.shortDescription());
        listing.setCategory(req.category());
        listing.setMinecraftVersion(req.minecraftVersion());
        listing.setPriceCents(req.priceCents());

        return listingRepository.save(listing);
    }

    @Transactional(readOnly = true)
    public MarketplaceListing getListing(UUID listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing not found"));
    }

    @Transactional(readOnly = true)
    public Page<MarketplaceListing> listPublicListings(String category, String minecraftVersion,
                                                        String search, String sortBy, Pageable pageable) {
        Specification<MarketplaceListing> spec = MarketplaceListingSpecification.isActive();

        if (category != null && !category.isBlank()) {
            spec = spec.and(MarketplaceListingSpecification.hasCategory(category));
        }

        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            spec = spec.and(MarketplaceListingSpecification.hasMinecraftVersion(minecraftVersion));
        }

        if (search != null && !search.isBlank()) {
            spec = spec.and(MarketplaceListingSpecification.searchTitleOrDescription(search));
        }

        Sort sort = resolveSort(sortBy);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return listingRepository.findAll(spec, sortedPageable);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceListing> getMyListings(UUID userId) {
        return listingRepository.findBySellerIdAndStatus(userId, "ACTIVE");
    }

    @Transactional
    public MarketplaceListing updateListing(UUID userId, UUID listingId, UpdateListingRequest req) {
        MarketplaceListing listing = getListing(listingId);
        verifyOwnership(listing, userId);

        if (req.title() != null) {
            listing.setTitle(req.title());
        }
        if (req.description() != null) {
            listing.setDescription(req.description());
        }
        if (req.shortDescription() != null) {
            listing.setShortDescription(req.shortDescription());
        }
        if (req.category() != null) {
            listing.setCategory(req.category());
        }
        if (req.priceCents() != null) {
            listing.setPriceCents(req.priceCents());
        }

        return listingRepository.save(listing);
    }

    @Transactional
    public void deleteListing(UUID userId, UUID listingId) {
        MarketplaceListing listing = getListing(listingId);
        verifyOwnership(listing, userId);
        listing.setStatus("REMOVED");
        listingRepository.save(listing);
    }

    private void verifyOwnership(MarketplaceListing listing, UUID userId) {
        if (!listing.getSellerId().equals(userId)) {
            throw new ForbiddenException("You do not own this listing");
        }
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sortBy) {
            case "rating" -> Sort.by(Sort.Direction.DESC, "averageRating");
            case "downloads" -> Sort.by(Sort.Direction.DESC, "downloadCount");
            case "price" -> Sort.by(Sort.Direction.ASC, "priceCents");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
