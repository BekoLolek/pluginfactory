# Public API smoke tests — endpoints that require no authentication.
#
# Run against any environment. Use the {{base}} variable to target it:
#   hurl --variable base=https://api.pluginfactory.org public.hurl
#   hurl --variable base=http://localhost:8080 public.hurl
#
# These tests are safe to run against production: they only do GETs on
# read-only public endpoints and assert structural shape, not specific
# data values that might change.

# ─────────────────────────────────────────────────────────────────────
# Health
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/health
HTTP 200
[Asserts]
jsonpath "$.status" == "UP"
jsonpath "$.timestamp" exists

GET {{base}}/health/ready
HTTP 200
[Asserts]
jsonpath "$.status" == "UP"
jsonpath "$.database" == "connected"

# ─────────────────────────────────────────────────────────────────────
# Subscription tiers — public catalog
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/subscriptions/tiers
HTTP 200
[Asserts]
jsonpath "$" isCollection
jsonpath "$" count >= 4
jsonpath "$[?(@.name=='FREE')]" exists
jsonpath "$[?(@.name=='BASIC')]" exists
jsonpath "$[?(@.name=='PRO')]" exists
jsonpath "$[?(@.name=='TEAM')]" exists
jsonpath "$[0].maxBuilds" isInteger
jsonpath "$[0].tokenBudget" isInteger
jsonpath "$[0].sourceCodeAccess" isBoolean

# ─────────────────────────────────────────────────────────────────────
# Marketplace — public plugin catalog
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/marketplace/plugins
HTTP 200
[Asserts]
jsonpath "$.content" isCollection
jsonpath "$.totalElements" isInteger
jsonpath "$.totalPages" isInteger
jsonpath "$.pageable.pageSize" isInteger
jsonpath "$.first" isBoolean

GET {{base}}/api/v1/marketplace/plugins?page=0&size=5
HTTP 200
[Asserts]
jsonpath "$.pageable.pageSize" == 5
jsonpath "$.pageable.pageNumber" == 0

# ─────────────────────────────────────────────────────────────────────
# Discord OAuth entry point — public, returns the auth URL
# ─────────────────────────────────────────────────────────────────────

GET {{base}}/api/v1/auth/discord
HTTP 200
[Asserts]
jsonpath "$.url" exists
jsonpath "$.url" matches "^https://discord\\.com/"
# state is embedded in the returned URL as a query param, not a top-level field
jsonpath "$.url" contains "state="
