#!/usr/bin/env bash
# ============================================================
# SSL Certificate Initialization for BekoLolek Plugin Factory
# ============================================================
# Solves the chicken-and-egg problem: nginx needs certs to start
# with SSL, but certbot needs nginx running to verify the domain.
#
# This script:
#   1. Starts nginx with a temporary HTTP-only config
#   2. Requests the SSL certificate from Let's Encrypt
#   3. Restarts nginx with the full SSL config
#
# Usage:
#   cd /opt/pluginfactory
#   sudo bash infra/scripts/ssl-init.sh
#
# Requires: .env file with DOMAIN and CERTBOT_EMAIL set.
# Requires: DNS A record for DOMAIN pointing to this server.
# ============================================================

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Load env vars
if [ ! -f .env ]; then
    log_error ".env file not found. Copy from .env.example and fill in values."
    exit 1
fi
export $(grep -v '^#' .env | grep -v '^\s*$' | xargs)

DOMAIN="${DOMAIN:?DOMAIN not set in .env}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:?CERTBOT_EMAIL not set in .env}"

log_info "Initializing SSL for domain: ${DOMAIN}"

# Ensure the Docker network exists
docker network create pluginfactory_pluginfactory 2>/dev/null || true

# Create volumes if they don't exist
docker volume create pluginfactory_certbot-certs 2>/dev/null || true
docker volume create pluginfactory_certbot-webroot 2>/dev/null || true

# Step 1: Stop any running containers that use port 80
log_info "Stopping existing services..."
docker compose down 2>/dev/null || true
docker rm -f nginx-temp 2>/dev/null || true

# Step 2: Create temporary HTTP-only nginx config
log_info "Creating temporary HTTP-only nginx config..."
TEMP_CONF=$(mktemp)
cat > "${TEMP_CONF}" << 'NGINXEOF'
worker_processes auto;
events { worker_connections 1024; }
http {
    server {
        listen 80;
        server_name _;
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }
        location / {
            return 200 "Waiting for SSL setup...\n";
            add_header Content-Type text/plain;
        }
    }
}
NGINXEOF

# Step 3: Start nginx in HTTP-only mode
log_info "Starting temporary nginx on port 80..."
docker run -d --name nginx-temp \
    -p 80:80 \
    -v "${TEMP_CONF}:/etc/nginx/nginx.conf:ro" \
    -v pluginfactory_certbot-webroot:/var/www/certbot \
    nginx:alpine

sleep 3

# Verify nginx is responding
HTTP_CHECK=$(curl -s -o /dev/null -w '%{http_code}' http://localhost/.well-known/acme-challenge/test 2>/dev/null || echo "000")
if [ "${HTTP_CHECK}" = "000" ]; then
    log_error "Temporary nginx is not responding on port 80. Check firewall settings."
    docker rm -f nginx-temp
    rm -f "${TEMP_CONF}"
    exit 1
fi
log_info "Temporary nginx is running."

# Step 4: Request the certificate
log_info "Requesting SSL certificate from Let's Encrypt..."
docker run --rm \
    -v pluginfactory_certbot-certs:/etc/letsencrypt \
    -v pluginfactory_certbot-webroot:/var/www/certbot \
    certbot/certbot certonly \
    --webroot -w /var/www/certbot \
    -d "${DOMAIN}" \
    --email "${CERTBOT_EMAIL}" \
    --agree-tos \
    --non-interactive

CERT_EXIT=$?

# Step 5: Clean up temporary nginx
log_info "Stopping temporary nginx..."
docker rm -f nginx-temp
rm -f "${TEMP_CONF}"

if [ ${CERT_EXIT} -ne 0 ]; then
    log_error "Certbot failed. Check that:"
    log_error "  1. DNS A record for ${DOMAIN} points to this server's IP"
    log_error "  2. Port 80 is open in both cloud firewall and OS firewall"
    log_error "  3. The domain is not behind a proxy (Cloudflare orange cloud)"
    exit 1
fi

log_info "SSL certificate obtained successfully!"

# Step 6: Start the full stack
log_info "Starting the full stack with SSL..."
docker compose up -d

log_info "Waiting for services to start..."
sleep 10

# Step 7: Verify
HTTP_STATUS=$(curl -sk -o /dev/null -w '%{http_code}' "https://${DOMAIN}/nginx-health" --max-time 15 2>/dev/null || echo "000")

if [ "${HTTP_STATUS}" = "200" ]; then
    echo ""
    log_info "SSL setup complete! https://${DOMAIN} is live."
    echo ""
else
    log_warn "HTTPS returned status ${HTTP_STATUS}. The stack may still be starting."
    log_warn "Wait a minute and check:"
    log_warn "  curl -s https://${DOMAIN}/nginx-health"
    log_warn "  docker compose logs nginx --tail 20"
fi
