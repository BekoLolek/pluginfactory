-- Fix average_rating column type mismatch.
-- The entity uses Java `double` (mapped to Types#FLOAT) but the V6 migration
-- created the column as DECIMAL(3,2) (mapped to Types#NUMERIC), causing
-- Hibernate schema validation to fail on strict PostgreSQL.
ALTER TABLE marketplace_listings
    ALTER COLUMN average_rating TYPE DOUBLE PRECISION
    USING average_rating::DOUBLE PRECISION;
