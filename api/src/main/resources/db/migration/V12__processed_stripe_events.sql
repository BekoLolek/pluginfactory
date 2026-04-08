-- Track Stripe event IDs we've already fully processed so that webhook retries
-- (Stripe retries on any non-2xx) don't double-apply subscription changes.
CREATE TABLE processed_stripe_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    event_type   VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processed_stripe_events_processed_at
    ON processed_stripe_events (processed_at);
