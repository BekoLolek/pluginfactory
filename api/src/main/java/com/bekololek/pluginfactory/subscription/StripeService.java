package com.bekololek.pluginfactory.subscription;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Value("${stripe.api-key:}")
    private String apiKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    private static final Map<Tier, String> TIER_PRICE_IDS = Map.of(
            Tier.BASIC, "price_basic_monthly",
            Tier.PRO, "price_pro_monthly",
            Tier.TEAM, "price_team_monthly"
    );

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
        }
    }

    public String createCheckoutSession(UUID userId, Tier targetTier) throws StripeException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String priceId = TIER_PRICE_IDS.get(targetTier);
        if (priceId == null) {
            throw new IllegalArgumentException("No price configured for tier: " + targetTier);
        }

        com.stripe.param.checkout.SessionCreateParams params =
                com.stripe.param.checkout.SessionCreateParams.builder()
                        .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomerEmail(user.getEmail())
                        .addLineItem(LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build())
                        .setSuccessUrl(baseUrl + "/subscription/success?session_id={CHECKOUT_SESSION_ID}")
                        .setCancelUrl(baseUrl + "/subscription/cancel")
                        .putMetadata("userId", userId.toString())
                        .putMetadata("targetTier", targetTier.name())
                        .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    public String createCustomerPortalSession(UUID userId) throws StripeException {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));

        if (subscription.getStripeCustomerId() == null) {
            throw new IllegalStateException("No Stripe customer ID found for user");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(subscription.getStripeCustomerId())
                .setReturnUrl(baseUrl + "/subscription")
                .build();

        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature", e);
            throw new IllegalArgumentException("Invalid signature");
        }

        String eventType = event.getType();
        log.info("Processing Stripe event: {}", eventType);

        switch (eventType) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.info("Unhandled event type: {}", eventType);
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (session == null) {
            log.error("Could not deserialize checkout session");
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        UUID userId = UUID.fromString(metadata.get("userId"));
        Tier targetTier = Tier.valueOf(metadata.get("targetTier"));

        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));

        subscription.setTier(targetTier);
        subscription.setStripeCustomerId(session.getCustomer());
        subscription.setStripeSubscriptionId(session.getSubscription());
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setBuildsUsedThisPeriod(0);
        subscriptionRepository.save(subscription);

        log.info("Upgraded user {} to tier {}", userId, targetTier);
    }

    private void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSubscription =
                (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
        if (stripeSubscription == null) {
            return;
        }

        subscriptionRepository.findByUserId(
                        findUserIdByStripeSubscriptionId(stripeSubscription.getId()))
                .ifPresent(subscription -> {
                    String status = stripeSubscription.getStatus();
                    if ("past_due".equals(status)) {
                        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
                    } else if ("active".equals(status)) {
                        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                    }
                    subscriptionRepository.save(subscription);
                });
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSubscription =
                (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
        if (stripeSubscription == null) {
            return;
        }

        subscriptionRepository.findByUserId(
                        findUserIdByStripeSubscriptionId(stripeSubscription.getId()))
                .ifPresent(subscription -> {
                    subscription.setTier(Tier.FREE);
                    subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
                    subscription.setStripeSubscriptionId(null);
                    subscriptionRepository.save(subscription);
                });
    }

    private void handlePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (invoice == null) {
            return;
        }

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId != null) {
            subscriptionRepository.findByUserId(
                            findUserIdByStripeSubscriptionId(subscriptionId))
                    .ifPresent(subscription -> {
                        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
                        subscriptionRepository.save(subscription);
                    });
        }
    }

    private UUID findUserIdByStripeSubscriptionId(String stripeSubscriptionId) {
        return subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .map(Subscription::getUserId)
                .orElseThrow(() -> new NotFoundException(
                        "No subscription found for Stripe subscription: " + stripeSubscriptionId));
    }
}
