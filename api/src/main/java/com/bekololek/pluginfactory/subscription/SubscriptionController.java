package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import com.bekololek.pluginfactory.subscription.dto.CheckoutRequest;
import com.bekololek.pluginfactory.subscription.dto.SubscriptionDto;
import com.bekololek.pluginfactory.subscription.dto.TierDto;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;

    @GetMapping("/tiers")
    public ResponseEntity<List<TierDto>> listTiers() {
        List<TierDto> tiers = Arrays.stream(Tier.values())
                .map(tier -> new TierDto(
                        tier.name(),
                        tier.getMaxBuilds(),
                        tier.getTokenBudget(),
                        tier.getMaxParallel(),
                        tier.getMaxIterations(),
                        tier.getMaxCommands(),
                        tier.getMaxEventListeners(),
                        tier.getJarRetentionDays(),
                        tier.getMarketplaceSlots(),
                        tier.isSourceCodeAccess()
                ))
                .toList();
        return ResponseEntity.ok(tiers);
    }

    @GetMapping("/current")
    public ResponseEntity<SubscriptionDto> getCurrentSubscription() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        Subscription subscription = subscriptionService.getCurrentSubscription(userId);
        SubscriptionDto dto = new SubscriptionDto(
                subscription.getId(),
                subscription.getTier().name(),
                subscription.getStatus().name(),
                subscription.getBuildsUsedThisPeriod(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCreatedAt()
        );
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            @Valid @RequestBody CheckoutRequest request) throws StripeException {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        Tier targetTier;
        try {
            targetTier = Tier.valueOf(request.tier().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid subscription tier: " + request.tier());
        }
        String url = stripeService.createCheckoutSession(userId, targetTier);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/portal")
    public ResponseEntity<Map<String, String>> createPortalSession() throws StripeException {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        String url = stripeService.createCustomerPortalSession(userId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
