CREATE TABLE marketplace_listings (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES users(id),
    artifact_id UUID NOT NULL REFERENCES artifacts(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    short_description VARCHAR(500),
    category VARCHAR(50) NOT NULL,
    minecraft_version VARCHAR(20),
    price_cents INT NOT NULL DEFAULT 0,
    download_count INT NOT NULL DEFAULT 0,
    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    review_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_marketplace_seller ON marketplace_listings(seller_id);
CREATE INDEX idx_marketplace_category ON marketplace_listings(category);
CREATE INDEX idx_marketplace_status ON marketplace_listings(status);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES marketplace_listings(id),
    reviewer_id UUID NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_review_per_user_listing UNIQUE (listing_id, reviewer_id)
);
CREATE INDEX idx_reviews_listing ON reviews(listing_id);

CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES marketplace_listings(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    price_cents INT NOT NULL,
    stripe_payment_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_purchases_buyer ON purchases(buyer_id);
CREATE INDEX idx_purchases_listing ON purchases(listing_id);
