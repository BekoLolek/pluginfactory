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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock
    private MarketplaceListingRepository listingRepository;

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private BuildSessionService buildSessionService;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private MarketplaceService marketplaceService;

    @Test
    void createListing_happyPath() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(sessionId);

        BuildSession session = new BuildSession();
        session.setUserId(userId);

        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));
        when(buildSessionService.getSessionById(sessionId)).thenReturn(session);
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);
        when(listingRepository.countBySellerId(userId)).thenReturn(2L);
        when(listingRepository.save(any(MarketplaceListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateListingRequest req = new CreateListingRequest(
                "My Plugin", "A great plugin", "Short desc",
                "UTILITY", "1.20.4", 0, artifactId);

        MarketplaceListing result = marketplaceService.createListing(userId, req);

        assertThat(result.getTitle()).isEqualTo("My Plugin");
        assertThat(result.getSellerId()).isEqualTo(userId);
        assertThat(result.getArtifactId()).isEqualTo(artifactId);
        assertThat(result.getCategory()).isEqualTo("UTILITY");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createListing_slotLimitExceeded() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(sessionId);

        BuildSession session = new BuildSession();
        session.setUserId(userId);

        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));
        when(buildSessionService.getSessionById(sessionId)).thenReturn(session);
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.FREE);
        when(listingRepository.countBySellerId(userId)).thenReturn(0L);

        CreateListingRequest req = new CreateListingRequest(
                "My Plugin", "desc", "short",
                "UTILITY", "1.20.4", 0, artifactId);

        assertThatThrownBy(() -> marketplaceService.createListing(userId, req))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Marketplace slot limit reached for your tier");
    }

    @Test
    void getListing_notFound() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marketplaceService.getListing(listingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Listing not found");
    }

    @Test
    void getListing_found() {
        UUID listingId = UUID.randomUUID();
        MarketplaceListing listing = new MarketplaceListing();
        listing.setTitle("Test");
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        MarketplaceListing result = marketplaceService.getListing(listingId);

        assertThat(result.getTitle()).isEqualTo("Test");
    }

    @SuppressWarnings("unchecked")
    @Test
    void listPublicListings_withFilters() {
        MarketplaceListing listing = new MarketplaceListing();
        listing.setTitle("Test Plugin");
        listing.setCategory("UTILITY");
        listing.setStatus("ACTIVE");

        Page<MarketplaceListing> page = new PageImpl<>(List.of(listing));
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<MarketplaceListing> result = marketplaceService.listPublicListings(
                "UTILITY", "1.20.4", "Test", "newest", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Plugin");
    }

    @Test
    void deleteListing_ownershipCheck() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setSellerId(otherUserId);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> marketplaceService.deleteListing(userId, listingId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You do not own this listing");
    }

    @Test
    void deleteListing_success() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setSellerId(userId);
        listing.setStatus("ACTIVE");
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(MarketplaceListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        marketplaceService.deleteListing(userId, listingId);

        verify(listingRepository).save(any(MarketplaceListing.class));
    }

    @Test
    void updateListing_success() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setSellerId(userId);
        listing.setTitle("Old Title");
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(MarketplaceListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateListingRequest req = new UpdateListingRequest(
                "New Title", null, null, null, null);

        MarketplaceListing result = marketplaceService.updateListing(userId, listingId, req);

        assertThat(result.getTitle()).isEqualTo("New Title");
    }
}
