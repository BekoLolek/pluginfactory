#!/usr/bin/env bash
# ============================================================
# Zero-Downtime Deploy Script for BekoLolek Plugin Factory
# ============================================================
# Deploys the production stack to a remote server via SSH.
#
# Usage:
#   ./deploy.sh [user@host] [branch]
#
# Environment:
#   DEPLOY_HOST     - SSH target (e.g., user@server.com), overridden by $1
#   DEPLOY_BRANCH   - Git branch to deploy (default: main), overridden by $2
#   DEPLOY_DIR      - Remote directory (default: /opt/pluginfactory)
#   COMPOSE_FILE    - Compose file path (default: docker-compose.yml)
# ============================================================

set -euo pipefail

DEPLOY_HOST="${1:-${DEPLOY_HOST:-}}"
DEPLOY_BRANCH="${2:-${DEPLOY_BRANCH:-main}}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/pluginfactory}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Validate required parameters
if [[ -z "$DEPLOY_HOST" ]]; then
    log_error "Deploy host is required. Pass as argument or set DEPLOY_HOST."
    echo "Usage: $0 [user@host] [branch]"
    exit 1
fi

log_info "Starting deployment to $DEPLOY_HOST"
log_info "Branch: $DEPLOY_BRANCH"
log_info "Remote directory: $DEPLOY_DIR"

# Function to run commands on the remote server
remote_exec() {
    ssh -o StrictHostKeyChecking=accept-new "$DEPLOY_HOST" "$@"
}

# Step 1: Pull latest code
log_info "Pulling latest code from $DEPLOY_BRANCH..."
remote_exec "cd $DEPLOY_DIR && git fetch origin && git checkout $DEPLOY_BRANCH && git pull origin $DEPLOY_BRANCH"

# Step 2: Build new images
log_info "Building new Docker images..."
remote_exec "cd $DEPLOY_DIR && docker compose -f $COMPOSE_FILE build --no-cache"

# Step 3: Store current container IDs for cleanup
log_info "Storing current container IDs for cleanup..."
OLD_CONTAINERS=$(remote_exec "cd $DEPLOY_DIR && docker compose -f $COMPOSE_FILE ps -q" 2>/dev/null || echo "")

# Step 4: Start the new stack
log_info "Starting updated stack..."
remote_exec "cd $DEPLOY_DIR && docker compose -f $COMPOSE_FILE up -d --remove-orphans"

# Step 5: Wait for health check
log_info "Waiting for API health check (timeout: ${HEALTH_TIMEOUT}s)..."
SECONDS_WAITED=0
HEALTH_OK=false

while [[ $SECONDS_WAITED -lt $HEALTH_TIMEOUT ]]; do
    HTTP_STATUS=$(remote_exec "curl -s -o /dev/null -w '%{http_code}' $HEALTH_URL" 2>/dev/null || echo "000")

    if [[ "$HTTP_STATUS" == "200" ]]; then
        HEALTH_OK=true
        break
    fi

    sleep 5
    SECONDS_WAITED=$((SECONDS_WAITED + 5))
    log_info "  Waiting... (${SECONDS_WAITED}s elapsed, HTTP status: $HTTP_STATUS)"
done

if [[ "$HEALTH_OK" != "true" ]]; then
    log_error "Health check failed after ${HEALTH_TIMEOUT}s. Rolling back..."

    # Rollback: restore old containers
    remote_exec "cd $DEPLOY_DIR && git checkout HEAD~1 && docker compose -f $COMPOSE_FILE up -d"

    log_error "Rollback complete. Deployment FAILED."
    exit 1
fi

log_info "Health check passed after ${SECONDS_WAITED}s."

# Step 6: Clean up old images
log_info "Cleaning up old Docker images..."
remote_exec "docker image prune -f" 2>/dev/null || true

# Step 7: Verify all services are running
log_info "Verifying all services..."
remote_exec "cd $DEPLOY_DIR && docker compose -f $COMPOSE_FILE ps"

log_info "Deployment to $DEPLOY_HOST completed successfully!"
