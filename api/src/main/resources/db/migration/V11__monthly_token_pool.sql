-- Switch from per-build token budgets to a monthly token pool tracked on subscriptions.
-- The per-session token_budgets table is still used for in-flight build accounting,
-- but the *cap* is now derived from the user's remaining monthly pool, not a fixed
-- per-build amount. We add user_id to token_budgets so the consume path can credit
-- usage back to the right subscription without needing to look up the build session.
ALTER TABLE subscriptions
    ADD COLUMN tokens_used_this_period INTEGER NOT NULL DEFAULT 0;

ALTER TABLE token_budgets
    ADD COLUMN user_id UUID;
