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
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private MarketplaceService marketplaceService;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void purchaseFree_happyPath() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setPriceCents(0);
        listing.setDownloadCount(5);

        when(marketplaceService.getListing(listingId)).thenReturn(listing);
        when(purchaseRepository.existsByListingIdAndBuyerId(listingId, userId)).thenReturn(false);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        Purchase result = purchaseService.purchaseFree(userId, listingId);

        assertThat(result.getListingId()).isEqualTo(listingId);
        assertThat(result.getBuyerId()).isEqualTo(userId);
        assertThat(result.getPriceCents()).isEqualTo(0);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(listing.getDownloadCount()).isEqualTo(6);
    }

    @Test
    void purchaseFree_alreadyPurchased() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setPriceCents(0);

        when(marketplaceService.getListing(listingId)).thenReturn(listing);
        when(purchaseRepository.existsByListingIdAndBuyerId(listingId, userId)).thenReturn(true);

        assertThatThrownBy(() -> purchaseService.purchaseFree(userId, listingId))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Already purchased");
    }

    @Test
    void purchaseFree_notFree() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        MarketplaceListing listing = new MarketplaceListing();
        listing.setPriceCents(999);

        when(marketplaceService.getListing(listingId)).thenReturn(listing);

        assertThatThrownBy(() -> purchaseService.purchaseFree(userId, listingId))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Plugin is not free");
    }

    @Test
    void hasPurchased_returnsTrue() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        when(purchaseRepository.existsByListingIdAndBuyerId(listingId, userId)).thenReturn(true);

        assertThat(purchaseService.hasPurchased(userId, listingId)).isTrue();
    }

    @Test
    void getMyPurchases_returnsList() {
        UUID userId = UUID.randomUUID();

        Purchase p1 = new Purchase();
        p1.setListingId(UUID.randomUUID());
        Purchase p2 = new Purchase();
        p2.setListingId(UUID.randomUUID());

        when(purchaseRepository.findByBuyerId(userId)).thenReturn(List.of(p1, p2));

        List<Purchase> result = purchaseService.getMyPurchases(userId);

        assertThat(result).hasSize(2);
    }
}
