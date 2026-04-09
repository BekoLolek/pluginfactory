# Negative auth tests — endpoints that should reject unauthenticated requests.
#
# These verify the security boundary is intact: every protected endpoint must
# return 401 (not 403, not 200, not 500) when no Authorization header is sent.
# A regression where one of these starts returning 200 would mean the route
# is accidentally exposed.
#
# Run against any environment:
#   hurl --variable base=https://api.pluginfactory.org auth-negative.hurl
#
# Safe for production: only sends GETs and unauth POSTs that the server
# rejects before touching any state.

# ─────────────────────────────────────────────────────────────────────
# User endpoints
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/users/me
HTTP 401

GET {{base}}/api/v1/users/me/usage
HTTP 401

GET {{base}}/api/v1/users/me/api-keys
HTTP 401

POST {{base}}/api/v1/users/me/api-keys
{"name": "smoke-test"}
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Build endpoints
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/builds
HTTP 401

GET {{base}}/api/v1/builds?page=0&size=5
HTTP 401

POST {{base}}/api/v1/builds
{"prompt": "smoke test"}
HTTP 401

GET {{base}}/api/v1/builds/00000000-0000-0000-0000-000000000000
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Subscription endpoints (only the auth-required ones; tiers is public)
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/subscriptions/current
HTTP 401

POST {{base}}/api/v1/subscriptions/checkout
{"tier": "BASIC"}
HTTP 401

POST {{base}}/api/v1/subscriptions/portal
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Team endpoints
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/teams
HTTP 401

POST {{base}}/api/v1/teams
{"name": "smoke-test-team"}
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Marketplace — write endpoints require auth (reads are public)
# ─────────────────────────────────────────────────────────────────────

POST {{base}}/api/v1/marketplace/plugins
{"name": "smoke", "description": "smoke", "price": 0}
HTTP 401

GET {{base}}/api/v1/marketplace/my/listings
HTTP 401

GET {{base}}/api/v1/marketplace/my/purchases
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Source code requests
# ─────────────────────────────────────────────────────────────────────

POST {{base}}/api/v1/source-code/request
{"artifactId": "00000000-0000-0000-0000-000000000000"}
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Admin endpoints — must reject anonymous AND non-admin tokens.
# Anonymous case here; the role-based 403 case needs a real non-admin
# token, which lives in the authenticated suite.
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/admin/containers
HTTP 401

POST {{base}}/api/v1/admin/containers/scale
{"tier": "BASIC", "count": 1}
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Auth endpoints — refresh with bogus token must fail, not 500
# ─────────────────────────────────────────────────────────────────────

# Bogus refresh token surfaces as IllegalArgumentException → 400
POST {{base}}/api/v1/auth/refresh
Content-Type: application/json
{"refreshToken": "not-a-real-token"}
HTTP 400

POST {{base}}/api/v1/auth/logout
HTTP 401

# ─────────────────────────────────────────────────────────────────────
# Stripe webhook — must fail signature verification with 400, NOT 500.
# Stripe retries indefinitely on 5xx but stops on 4xx, so a 500 here
# would cause a retry storm in production. Two cases worth covering:
# ─────────────────────────────────────────────────────────────────────

# 1. Missing Stripe-Signature header → 400 (MissingRequestHeaderException)
POST {{base}}/api/v1/webhooks/stripe
Content-Type: application/json
{"type": "checkout.session.completed", "id": "evt_smoke"}
HTTP 400

# 2. Present-but-invalid signature header → 400 (signature verification fails)
POST {{base}}/api/v1/webhooks/stripe
Content-Type: application/json
Stripe-Signature: t=0,v1=invalid
{"type": "checkout.session.completed", "id": "evt_smoke"}
HTTP 400
