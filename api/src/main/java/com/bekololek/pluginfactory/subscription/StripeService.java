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
    private final ProcessedStripeEventRepository processedStripeEventRepository;

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
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Fail closed: without a configured secret we can't prove the
            // request came from Stripe, so do not process anything.
            log.error("Stripe webhook secret is not configured; rejecting event");
            throw new IllegalArgumentException("Webhook secret not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // Real Stripe webhooks always send a header of the form
            // "t=<unix>,v1=<hex>[,v0=<hex>]". If we can't even parse that
            // out, the request almost certainly isn't from Stripe — it's
            // junk traffic (bot scan, misrouted health check, someone
            // probing the endpoint with curl). Log those at WARN without
            // a stack trace so they don't look like a real failure.
            //
            // A well-formed header that doesn't match our secret, on the
            // other hand, is worth logging at ERROR: it could be a
            // rotated-secret misconfiguration, a replay, or a real
            // tampering attempt, and an operator should actually look.
            if (isMalformedSignatureHeader(sigHeader)) {
                log.warn(
                        "Rejected webhook with malformed Stripe-Signature header " +
                                "(likely junk traffic, not a real Stripe call): {}",
                        e.getMessage()
                );
            } else {
                log.error(
                        "Stripe webhook signature verification failed — " +
                                "header was well-formed but did not match the configured secret: {}",
                        e.getMessage()
                );
            }
            throw new IllegalArgumentException("Invalid signature");
        }

        String eventId = event.getId();
        String eventType = event.getType();

        // Idempotency: Stripe retries any non-2xx response (and sometimes on
        // 2xx, if its client times out), so the same event id can arrive
        // multiple times. Short-circuit if we've already fully processed it.
        if (eventId != null && processedStripeEventRepository.existsByEventId(eventId)) {
            log.info("Stripe event {} ({}) already processed; skipping", eventId, eventType);
            return;
        }

        log.info("Processing Stripe event {} ({})", eventId, eventType);

        switch (eventType) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.info("Unhandled Stripe event type: {}", eventType);
        }

        if (eventId != null) {
            ProcessedStripeEvent marker = new ProcessedStripeEvent();
            marker.setEventId(eventId);
            marker.setEventType(eventType);
            processedStripeEventRepository.save(marker);
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
        // Fresh billing period → reset both usage counters so the user gets the
        // full new quota immediately (not leftover pool from the previous tier).
        subscription.setBuildsUsedThisPeriod(0);
        subscription.setTokensUsedThisPeriod(0);
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

    /**
     * Returns true if the Stripe-Signature header is missing the structural
     * bits that the Stripe SDK's parser requires (a {@code t=} timestamp
     * and at least one {@code v1=} signature). Anything failing this check
     * can't possibly be a real Stripe webhook — Stripe always emits a
     * well-formed header — so we log those at WARN instead of ERROR.
     */
    private static boolean isMalformedSignatureHeader(String sigHeader) {
        if (sigHeader == null || sigHeader.isBlank()) {
            return true;
        }
        return !sigHeader.contains("t=") || !sigHeader.contains("v1=");
    }
}
