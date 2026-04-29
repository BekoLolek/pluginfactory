-- Track admin-initiated token refunds at the per-session level.
-- Refunds are idempotent: refunded_at being set means "we have already
-- decremented this user's monthly pool for this session, do not do it
-- again." The amount is recorded so an audit trail and a UI display
-- (e.g. "1,250 tokens refunded by admin on 2026-04-29") are possible
-- without joining against any other table.
ALTER TABLE token_budgets ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE token_budgets ADD COLUMN refunded_amount INTEGER;
