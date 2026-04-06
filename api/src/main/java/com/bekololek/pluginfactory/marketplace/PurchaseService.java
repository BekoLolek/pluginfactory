package com.bekololek.pluginfactory.marketplace;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final MarketplaceService marketplaceService;
    private final UserRepository userRepository;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Transactional
    public Purchase purchaseFree(UUID userId, UUID listingId) {
        MarketplaceListing listing = marketplaceService.getListing(listingId);

        if (listing.getPriceCents() != 0) {
            throw new ValidationException("Plugin is not free");
        }

        if (purchaseRepository.existsByListingIdAndBuyerId(listingId, userId)) {
            throw new ValidationException("Already purchased");
        }

        Purchase purchase = new Purchase();
        purchase.setListingId(listingId);
        purchase.setBuyerId(userId);
        purchase.setPriceCents(0);
        purchase.setStatus("COMPLETED");

        Purchase saved = purchaseRepository.save(purchase);

        listing.setDownloadCount(listing.getDownloadCount() + 1);

        return saved;
    }

    @Transactional(readOnly = true)
    public boolean hasPurchased(UUID userId, UUID listingId) {
        return purchaseRepository.existsByListingIdAndBuyerId(listingId, userId);
    }

    @Transactional(readOnly = true)
    public List<Purchase> getMyPurchases(UUID userId) {
        return purchaseRepository.findByBuyerId(userId);
    }

    /**
     * Creates a Stripe Checkout Session for a paid marketplace plugin purchase.
     * Returns the checkout URL for the frontend to redirect the user.
     * The purchase record is created upon Stripe webhook confirmation.
     */
    public String createPaidCheckoutUrl(UUID userId, UUID listingId) throws StripeException {
        MarketplaceListing listing = marketplaceService.getListing(listingId);

        if (listing.getPriceCents() == 0) {
            throw new ValidationException("Plugin is free — use the free purchase endpoint");
        }

        if (purchaseRepository.existsByListingIdAndBuyerId(listingId, userId)) {
            throw new ValidationException("Already purchased");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(user.getEmail())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount((long) listing.getPriceCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(listing.getTitle())
                                        .setDescription(listing.getShortDescription())
                                        .build())
                                .build())
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(baseUrl + "/dashboard/marketplace/my-purchases?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/dashboard/marketplace/" + listingId)
                .putMetadata("userId", userId.toString())
                .putMetadata("listingId", listingId.toString())
                .putMetadata("priceCents", String.valueOf(listing.getPriceCents()))
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe checkout session {} for user {} purchasing listing {}", session.getId(), userId, listingId);
        return session.getUrl();
    }

    /**
     * Called by Stripe webhook on successful payment to complete the purchase.
     */
    @Transactional
    public Purchase completePaidPurchase(UUID userId, UUID listingId, int priceCents) {
        if (purchaseRepository.existsByListingIdAndBuyerId(listingId, userId)) {
            return purchaseRepository.findByBuyerId(userId).stream()
                    .filter(p -> p.getListingId().equals(listingId))
                    .findFirst()
                    .orElseThrow();
        }

        MarketplaceListing listing = marketplaceService.getListing(listingId);

        Purchase purchase = new Purchase();
        purchase.setListingId(listingId);
        purchase.setBuyerId(userId);
        purchase.setPriceCents(priceCents);
        purchase.setStatus("COMPLETED");

        Purchase saved = purchaseRepository.save(purchase);
        listing.setDownloadCount(listing.getDownloadCount() + 1);

        log.info("Completed paid purchase for user {} on listing {}", userId, listingId);
        return saved;
    }
}
