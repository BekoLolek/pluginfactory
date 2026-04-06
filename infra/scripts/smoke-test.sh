#!/usr/bin/env bash
# ============================================================
# Smoke Test Script for BekoLolek Plugin Factory
# ============================================================
# Starts the full stack, runs basic health and connectivity
# tests, then tears everything down.
#
# Usage:
#   ./smoke-test.sh [compose-file]
#
# Environment:
#   COMPOSE_FILE    - Compose file path (default: docker-compose.yml)
#   API_BASE_URL    - Base URL for API (default: http://localhost:8080)
#   HEALTH_TIMEOUT  - Seconds to wait for health (default: 120)
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="${1:-${COMPOSE_FILE:-docker-compose.yml}}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"
TESTS_PASSED=0
TESTS_FAILED=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_test()  { echo -e "${GREEN}[PASS]${NC} $*"; }
log_fail()  { echo -e "${RED}[FAIL]${NC} $*"; }

# Cleanup function - always tear down the stack
cleanup() {
    log_info "Tearing down the stack..."
    cd "$PROJECT_ROOT" && docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
    log_info "Cleanup complete."
}
trap cleanup EXIT

# Test helper: assert HTTP status
assert_status() {
    local url="$1"
    local expected="$2"
    local description="$3"

    local actual
    actual=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")

    if [[ "$actual" == "$expected" ]]; then
        log_test "$description (HTTP $actual)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "$description (expected HTTP $expected, got HTTP $actual)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Test helper: assert response body contains string
assert_contains() {
    local url="$1"
    local expected="$2"
    local description="$3"

    local body
    body=$(curl -s "$url" 2>/dev/null || echo "")

    if echo "$body" | grep -q "$expected"; then
        log_test "$description"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        log_fail "$description (response did not contain '$expected')"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# ============================================================
# Start the stack
# ============================================================
log_info "Starting the stack with $COMPOSE_FILE..."
cd "$PROJECT_ROOT"
docker compose -f "$COMPOSE_FILE" up -d --build

# ============================================================
# Wait for health endpoint
# ============================================================
log_info "Waiting for API health endpoint (timeout: ${HEALTH_TIMEOUT}s)..."
SECONDS_WAITED=0
HEALTH_OK=false

while [[ $SECONDS_WAITED -lt $HEALTH_TIMEOUT ]]; do
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API_BASE_URL/actuator/health" 2>/dev/null || echo "000")

    if [[ "$HTTP_STATUS" == "200" ]]; then
        HEALTH_OK=true
        break
    fi

    sleep 5
    SECONDS_WAITED=$((SECONDS_WAITED + 5))
    log_info "  Waiting... (${SECONDS_WAITED}s elapsed, HTTP status: $HTTP_STATUS)"
done

if [[ "$HEALTH_OK" != "true" ]]; then
    log_error "API did not become healthy within ${HEALTH_TIMEOUT}s."
    log_error "Container logs:"
    docker compose -f "$COMPOSE_FILE" logs --tail=50 api
    exit 1
fi

log_info "API is healthy after ${SECONDS_WAITED}s."

# ============================================================
# Run smoke tests
# ============================================================
log_info "Running smoke tests..."

# Test 1: Health endpoint returns 200
assert_status "$API_BASE_URL/actuator/health" "200" "Health endpoint returns 200"

# Test 2: Health response contains UP status
assert_contains "$API_BASE_URL/actuator/health" "UP" "Health response contains UP status"

# Test 3: Info endpoint is accessible
assert_status "$API_BASE_URL/actuator/info" "200" "Info endpoint returns 200"

# Test 4: Prometheus metrics endpoint is accessible
assert_status "$API_BASE_URL/actuator/prometheus" "200" "Prometheus metrics endpoint returns 200"

# Test 5: API base responds (expect 401 for unauthenticated requests, not 500)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_BASE_URL/api/auth/me" 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "401" || "$HTTP_CODE" == "403" ]]; then
    log_test "Unauthenticated API request returns $HTTP_CODE (expected auth error, not server error)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
elif [[ "$HTTP_CODE" == "5"* ]]; then
    log_fail "API returned server error $HTTP_CODE for unauthenticated request"
    TESTS_FAILED=$((TESTS_FAILED + 1))
else
    log_test "API returned $HTTP_CODE for unauthenticated request"
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi

# Test 6: Nginx health endpoint (if nginx is exposed on port 80)
assert_status "http://localhost:80/nginx-health" "200" "Nginx health endpoint returns 200"

# Test 7: Nginx proxies /api requests
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:80/actuator/health" 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "200" ]]; then
    log_test "Nginx proxies actuator health endpoint (HTTP $HTTP_CODE)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    log_fail "Nginx proxy to actuator health failed (HTTP $HTTP_CODE)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ============================================================
# Results summary
# ============================================================
echo ""
echo "============================================"
echo "  Smoke Test Results"
echo "============================================"
echo -e "  Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "  Failed: ${RED}${TESTS_FAILED}${NC}"
echo "============================================"

if [[ $TESTS_FAILED -gt 0 ]]; then
    log_error "$TESTS_FAILED test(s) failed."
    log_info "Container logs for debugging:"
    docker compose -f "$COMPOSE_FILE" logs --tail=30
    exit 1
else
    log_info "All smoke tests passed!"
    exit 0
fi
