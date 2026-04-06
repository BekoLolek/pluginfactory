# Oracle Cloud + Vercel Deployment Setup

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deploy PluginFactory with backend on Oracle Cloud Free Tier (Docker Compose) and frontend on Vercel, with SSL via Let's Encrypt.

**Architecture:** Frontend (React/Vite) deployed to Vercel with `VITE_API_URL` pointing to the backend. Backend runs the full Docker Compose stack (API, PostgreSQL, Redis, MinIO, Nginx, Docker socket proxy) on an Oracle Cloud ARM VM. Nginx terminates SSL via Let's Encrypt/Certbot and reverse-proxies to the Spring Boot API. CORS on the backend allows the Vercel frontend domain.

**Tech Stack:** Oracle Cloud Free Tier (ARM VM), Docker Compose, Certbot/Let's Encrypt, Nginx, Vercel, Vite

---

## Task 1: Create Frontend Environment Documentation

**Why:** The frontend uses `VITE_API_URL` but has no `.env.example` documenting this. Developers and Vercel deployments need to know what to set.

**Files:**
- Create: `web/.env.example`

**Step 1: Create the env example file**

```env
# Backend API URL (required in production)
# For local dev, the Vite dev server proxies /api to localhost:8080 automatically.
# For production (Vercel), set this to your backend URL:
VITE_API_URL=https://api.pluginfactory.bekololek.com
```

**Step 2: Verify frontend builds with env var**

Run: `cd web && npm run build`
Expected: Build succeeds (VITE_API_URL is optional at build time, only warns in console at runtime)

**Step 3: Commit**

```bash
cd web && git add .env.example && git commit -m "docs: add frontend .env.example with VITE_API_URL"
```

---

## Task 2: Update Vercel Config for Production Headers

**Why:** The existing `vercel.json` is functional but the CSP `connect-src` needs to also allow the API subdomain explicitly (not just wildcard). Also add a cache header for static assets.

**Files:**
- Modify: `web/vercel.json`

**Step 1: Update vercel.json**

Replace the entire file with:

```json
{
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "framework": "vite",
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ],
  "headers": [
    {
      "source": "/assets/(.*)",
      "headers": [
        { "key": "Cache-Control", "value": "public, max-age=31536000, immutable" }
      ]
    },
    {
      "source": "/(.*)",
      "headers": [
        {
          "key": "Content-Security-Policy",
          "value": "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' https://*.bekololek.com wss://*.bekololek.com; img-src 'self' data: https:; font-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        },
        { "key": "X-Frame-Options", "value": "DENY" },
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
        { "key": "Permissions-Policy", "value": "camera=(), microphone=(), geolocation=()" }
      ]
    }
  ]
}
```

Changes from existing:
- Added `Cache-Control` header for `/assets/*` (Vite hashed filenames are safe to cache forever)
- Added `https:` to `img-src` to allow external images (e.g., Discord avatars)

**Step 2: Commit**

```bash
cd web && git add vercel.json && git commit -m "feat: add asset caching headers to Vercel config"
```

---

## Task 3: Update Nginx for SSL with Let's Encrypt

**Why:** Production must serve over HTTPS. Nginx needs to terminate SSL and redirect HTTP to HTTPS. Certbot handles certificate provisioning.

**Files:**
- Modify: `infra/nginx/nginx.conf`

**Step 1: Replace nginx.conf with SSL-ready version**

```nginx
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    # Logging
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for" '
                    'rt=$request_time';
    access_log /var/log/nginx/access.log main;

    # Performance
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    client_max_body_size 50m;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_min_length 256;
    gzip_types
        application/json
        application/javascript
        application/xml
        application/xml+rss
        text/css
        text/javascript
        text/plain
        text/xml;

    # Rate limiting zone: 10 requests per second per IP
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

    # Upstream for the Spring Boot API
    upstream api_backend {
        server api:8080;
        keepalive 32;
    }

    # HTTP server - redirect to HTTPS + ACME challenge
    server {
        listen 80;
        server_name _;

        # Let's Encrypt ACME challenge
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }

        # Health check for load balancers (over HTTP is fine)
        location /nginx-health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }

        # Redirect all other HTTP traffic to HTTPS
        location / {
            return 301 https://$host$request_uri;
        }
    }

    # HTTPS server
    server {
        listen 443 ssl;
        http2 on;
        server_name _;

        # SSL certificates (managed by Certbot)
        ssl_certificate /etc/nginx/ssl/fullchain.pem;
        ssl_certificate_key /etc/nginx/ssl/privkey.pem;

        # SSL settings
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
        ssl_prefer_server_ciphers off;
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 1d;
        ssl_session_tickets off;

        # OCSP stapling
        ssl_stapling on;
        ssl_stapling_verify on;
        resolver 1.1.1.1 8.8.8.8 valid=300s;

        # HSTS (1 year)
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

        # Security headers
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-XSS-Protection "1; mode=block" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;

        # API proxy
        location /api {
            limit_req zone=api_limit burst=20 nodelay;

            proxy_pass http://api_backend;
            proxy_http_version 1.1;

            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header X-Forwarded-Host $host;
            proxy_set_header X-Forwarded-Port $server_port;

            proxy_connect_timeout 30s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;

            proxy_buffering on;
            proxy_buffer_size 4k;
            proxy_buffers 8 4k;
        }

        # WebSocket proxy
        location /ws {
            proxy_pass http://api_backend;
            proxy_http_version 1.1;

            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            proxy_connect_timeout 7d;
            proxy_send_timeout 7d;
            proxy_read_timeout 7d;
        }

        # Actuator endpoints (restrict to internal networks)
        location /actuator {
            allow 10.0.0.0/8;
            allow 172.16.0.0/12;
            allow 192.168.0.0/16;
            allow 127.0.0.1;
            deny all;

            proxy_pass http://api_backend;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Deny access to hidden files
        location ~ /\. {
            deny all;
            access_log off;
            log_not_found off;
        }
    }
}
```

Key changes from existing:
- HTTP server now only handles ACME challenges and redirects to HTTPS
- HTTPS server with TLS 1.2/1.3, HSTS, OCSP stapling
- Actuator endpoints restricted to internal networks
- HTTP/2 enabled

**Step 2: Commit**

```bash
cd api && git add ../infra/nginx/nginx.conf && git commit -m "feat: enable SSL termination with Let's Encrypt in nginx"
```

---

## Task 4: Add Certbot to Docker Compose

**Why:** Certbot needs to run as a container to provision and renew Let's Encrypt certificates. Nginx needs access to the cert files and the ACME webroot.

**Files:**
- Modify: `docker-compose.yml`

**Step 1: Add certbot service and volumes to docker-compose.yml**

Add to the `services:` section (after `minio`):

```yaml
  certbot:
    image: certbot/certbot:latest
    container_name: pluginfactory-certbot
    volumes:
      - certbot-certs:/etc/letsencrypt
      - certbot-webroot:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew --webroot -w /var/www/certbot --quiet; sleep 12h & wait $${!}; done'"
    restart: unless-stopped
    networks:
      - pluginfactory
```

Update the `nginx` service to mount cert volumes:

```yaml
  nginx:
    image: nginx:alpine
    container_name: pluginfactory-nginx
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      api:
        condition: service_healthy
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - certbot-certs:/etc/nginx/ssl:ro
      - certbot-webroot:/var/www/certbot:ro
    restart: unless-stopped
    networks:
      - pluginfactory
```

Add to the `volumes:` section:

```yaml
  certbot-certs:
    driver: local
  certbot-webroot:
    driver: local
```

**Step 2: Add new env vars to `.env.example`**

Add at the bottom of the root `.env.example`:

```env
# --- Domain ---
DOMAIN=api.pluginfactory.bekololek.com
CERTBOT_EMAIL=your-email@example.com

# --- Application ---
APP_BASE_URL=https://api.pluginfactory.bekololek.com
```

**Step 3: Commit**

```bash
git add docker-compose.yml .env.example && git commit -m "feat: add certbot service for automated SSL certificate management"
```

---

## Task 5: Create Server Bootstrap Script

**Why:** A new Oracle Cloud VM needs Docker, Docker Compose, firewall, swap, and the project cloned. This script automates the entire server setup.

**Files:**
- Create: `infra/scripts/server-bootstrap.sh`

**Step 1: Create the bootstrap script**

```bash
#!/usr/bin/env bash
# ============================================================
# Oracle Cloud VM Bootstrap Script for BekoLolek Plugin Factory
# ============================================================
# Run as root on a fresh Ubuntu 22.04+ ARM instance.
#
# Usage:
#   curl -sSL <raw-url> | sudo bash
#   # or
#   sudo bash server-bootstrap.sh
#
# After running, you must:
#   1. Copy .env.example to .env and fill in real values
#   2. Run the initial SSL cert provisioning
#   3. Start the stack with docker compose up -d
# ============================================================

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

DEPLOY_DIR="/opt/pluginfactory"
SWAP_SIZE="4G"

# ---- System Updates ----
log_info "Updating system packages..."
apt-get update -qq && apt-get upgrade -y -qq

# ---- Install Dependencies ----
log_info "Installing dependencies..."
apt-get install -y -qq \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    jq \
    ufw \
    fail2ban

# ---- Install Docker ----
if ! command -v docker &>/dev/null; then
    log_info "Installing Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
    log_info "Docker installed: $(docker --version)"
else
    log_info "Docker already installed: $(docker --version)"
fi

# ---- Docker Compose (plugin) ----
if ! docker compose version &>/dev/null; then
    log_info "Installing Docker Compose plugin..."
    apt-get install -y -qq docker-compose-plugin
fi
log_info "Docker Compose: $(docker compose version)"

# ---- Swap (important for free-tier low-RAM instances) ----
if ! swapon --show | grep -q '/swapfile'; then
    log_info "Creating ${SWAP_SIZE} swap file..."
    fallocate -l ${SWAP_SIZE} /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    # Optimize swap usage
    sysctl vm.swappiness=10
    echo 'vm.swappiness=10' >> /etc/sysctl.conf
    log_info "Swap enabled."
else
    log_info "Swap already configured."
fi

# ---- Firewall (UFW) ----
log_info "Configuring firewall..."
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # HTTP (for ACME + redirect)
ufw allow 443/tcp   # HTTPS
ufw --force enable
log_info "Firewall enabled: SSH(22), HTTP(80), HTTPS(443)"

# ---- Fail2Ban ----
log_info "Configuring fail2ban..."
systemctl enable fail2ban
systemctl start fail2ban

# ---- Create deploy directory ----
log_info "Setting up project directory at ${DEPLOY_DIR}..."
mkdir -p ${DEPLOY_DIR}

# ---- Clone or update repo ----
if [ -d "${DEPLOY_DIR}/.git" ] || [ -d "${DEPLOY_DIR}/api/.git" ]; then
    log_info "Repository already exists. Pulling latest..."
    cd ${DEPLOY_DIR}
    git pull origin main || true
else
    log_warn "No git repo found at ${DEPLOY_DIR}."
    log_warn "You need to clone your repository manually:"
    log_warn "  git clone <your-repo-url> ${DEPLOY_DIR}"
    log_warn "  OR scp your project files to ${DEPLOY_DIR}/"
fi

# ---- Create .env from example if not exists ----
if [ -f "${DEPLOY_DIR}/.env.example" ] && [ ! -f "${DEPLOY_DIR}/.env" ]; then
    cp "${DEPLOY_DIR}/.env.example" "${DEPLOY_DIR}/.env"
    log_warn "Created .env from .env.example. EDIT IT with real values:"
    log_warn "  nano ${DEPLOY_DIR}/.env"
fi

# ---- Print next steps ----
echo ""
echo "============================================================"
log_info "Server bootstrap complete!"
echo "============================================================"
echo ""
echo "Next steps:"
echo ""
echo "  1. Clone your repo (if not already done):"
echo "     git clone <your-repo-url> ${DEPLOY_DIR}"
echo ""
echo "  2. Edit the .env file with real production values:"
echo "     nano ${DEPLOY_DIR}/.env"
echo ""
echo "  3. Provision the initial SSL certificate:"
echo "     export \$(grep -v '^#' ${DEPLOY_DIR}/.env | xargs)"
echo "     docker run --rm \\"
echo "       -v pluginfactory_certbot-certs:/etc/letsencrypt \\"
echo "       -v pluginfactory_certbot-webroot:/var/www/certbot \\"
echo "       certbot/certbot certonly --webroot \\"
echo "       -w /var/www/certbot \\"
echo "       -d \$DOMAIN \\"
echo "       --email \$CERTBOT_EMAIL \\"
echo "       --agree-tos --non-interactive"
echo ""
echo "     NOTE: For the initial cert, nginx must be running on port 80"
echo "     without SSL first. See infra/scripts/ssl-init.sh for automation."
echo ""
echo "  4. Start the full stack:"
echo "     cd ${DEPLOY_DIR} && docker compose up -d"
echo ""
echo "  5. Check health:"
echo "     curl -s https://\$DOMAIN/actuator/health"
echo ""
echo "============================================================"
```

**Step 2: Commit**

```bash
git add infra/scripts/server-bootstrap.sh && git commit -m "feat: add Oracle Cloud server bootstrap script"
```

---

## Task 6: Create SSL Initialization Script

**Why:** Certbot can't issue a cert when nginx requires SSL certs that don't exist yet. This chicken-and-egg problem needs a script that starts nginx in HTTP-only mode, gets the cert, then restarts with SSL.

**Files:**
- Create: `infra/scripts/ssl-init.sh`

**Step 1: Create the SSL init script**

```bash
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
# Requires: .env file with DOMAIN and CERTBOT_EMAIL set
# ============================================================

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
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

# Step 1: Create a temporary HTTP-only nginx config
log_info "Creating temporary HTTP-only nginx config..."
cat > /tmp/nginx-http-only.conf << 'NGINXEOF'
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

# Step 2: Start nginx in HTTP-only mode
log_info "Starting nginx in HTTP-only mode..."
docker compose down nginx certbot 2>/dev/null || true

docker run -d --name nginx-temp \
    -p 80:80 \
    -v /tmp/nginx-http-only.conf:/etc/nginx/nginx.conf:ro \
    -v pluginfactory_certbot-webroot:/var/www/certbot \
    --network pluginfactory_pluginfactory \
    nginx:alpine

sleep 2

# Step 3: Request the certificate
log_info "Requesting SSL certificate from Let's Encrypt..."
docker run --rm \
    -v pluginfactory_certbot-certs:/etc/letsencrypt \
    -v pluginfactory_certbot-webroot:/var/www/certbot \
    certbot/certbot certonly \
    --webroot -w /var/www/certbot \
    -d "${DOMAIN}" \
    --email "${CERTBOT_EMAIL}" \
    --agree-tos \
    --non-interactive \
    --force-renewal

# Step 4: Stop the temporary nginx
log_info "Stopping temporary nginx..."
docker rm -f nginx-temp

# Step 5: Create symlinks so nginx finds certs at expected path
log_info "Setting up certificate symlinks..."
CERT_VOLUME_PATH=$(docker volume inspect pluginfactory_certbot-certs --format '{{ .Mountpoint }}')
# The certs are at /etc/letsencrypt/live/${DOMAIN}/ inside the volume

# Step 6: Start the full stack
log_info "Starting the full stack with SSL..."
docker compose up -d

sleep 5

# Step 7: Verify
log_info "Verifying SSL..."
HTTP_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "https://${DOMAIN}/nginx-health" --max-time 10 2>/dev/null || echo "000")

if [ "${HTTP_STATUS}" = "200" ]; then
    log_info "SSL setup complete! https://${DOMAIN} is live."
else
    log_error "SSL verification returned HTTP ${HTTP_STATUS}. Check logs:"
    log_error "  docker compose logs nginx"
    log_error "  docker compose logs certbot"
    exit 1
fi
```

**Step 2: Create a symlink-aware nginx SSL path**

The certbot volume maps certificates to `/etc/nginx/ssl/`. But certbot stores them at `/etc/letsencrypt/live/<domain>/`. We need to update the nginx config SSL paths and docker-compose volume mapping.

Update the `nginx` service volumes in `docker-compose.yml`:

```yaml
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - certbot-certs:/etc/letsencrypt:ro
      - certbot-webroot:/var/www/certbot:ro
```

And update the nginx.conf SSL cert paths:

```nginx
        ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;
```

Since nginx can't use env vars natively, we'll use a fixed domain or a template. Simpler approach: use the `ssl-placeholder.conf` pattern with a known domain.

**Updated nginx.conf SSL lines** (replace the ssl_certificate lines):

```nginx
        # SSL certificates (managed by Certbot)
        # These paths match certbot's default output structure
        ssl_certificate /etc/letsencrypt/live/api.pluginfactory.bekololek.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/api.pluginfactory.bekololek.com/privkey.pem;
```

**Step 3: Commit**

```bash
git add infra/scripts/ssl-init.sh && git commit -m "feat: add SSL initialization script for Let's Encrypt"
```

---

## Task 7: Update Root .env.example with All Production Variables

**Why:** The current `.env.example` is missing some vars needed for deployment (DOMAIN, CERTBOT_EMAIL, REDIS_PASSWORD). Consolidate everything.

**Files:**
- Modify: `.env.example`

**Step 1: Update .env.example**

```env
# ============================================================
# BekoLolek Plugin Factory - Environment Variables
# ============================================================
# Copy this file to .env and fill in the values.
#   cp .env.example .env
# ============================================================

# --- Domain & SSL ---
DOMAIN=api.pluginfactory.bekololek.com
CERTBOT_EMAIL=your-email@example.com
APP_BASE_URL=https://api.pluginfactory.bekololek.com

# --- Database ---
POSTGRES_DB=pluginfactory
POSTGRES_USER=pluginfactory
POSTGRES_PASSWORD=change-me-in-production

# --- Redis ---
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=change-me-in-production

# --- JWT ---
JWT_SECRET=replace-with-a-secure-256-bit-secret-key-for-production

# --- AI / Anthropic ---
ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxxxxxxxxxx

# --- Payments / Stripe ---
STRIPE_API_KEY=sk_test_xxxxxxxxxxxxxxxxxxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxxxxxxxxx

# --- Discord OAuth ---
DISCORD_CLIENT_ID=your-discord-client-id
DISCORD_CLIENT_SECRET=your-discord-client-secret
DISCORD_REDIRECT_URI=https://api.pluginfactory.bekololek.com/api/v1/auth/discord/callback

# --- MinIO Object Storage ---
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=change-me-in-production

# --- CORS ---
CORS_ALLOWED_ORIGINS=https://pluginfactory.bekololek.com

# --- Monitoring (optional) ---
GRAFANA_ADMIN_PASSWORD=change-me-in-production

# --- Discord Alerts (optional) ---
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/your-webhook-id/your-webhook-token
```

**Step 2: Commit**

```bash
git add .env.example && git commit -m "docs: consolidate all production env vars in .env.example"
```

---

## Task 8: Update docker-compose.yml with Certbot and SSL Volumes

**Why:** Bring together all the Docker Compose changes: certbot service, updated nginx volumes, cert volume definitions.

**Files:**
- Modify: `docker-compose.yml`

**Step 1: Apply all changes to docker-compose.yml**

The full updated `docker-compose.yml`:

```yaml
services:
  api:
    build:
      context: ./api
      dockerfile: Dockerfile
    container_name: pluginfactory-api
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      docker-proxy:
        condition: service_started
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}?user=${POSTGRES_USER}&password=${POSTGRES_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      STRIPE_API_KEY: ${STRIPE_API_KEY}
      STRIPE_WEBHOOK_SECRET: ${STRIPE_WEBHOOK_SECRET}
      DISCORD_CLIENT_ID: ${DISCORD_CLIENT_ID}
      DISCORD_CLIENT_SECRET: ${DISCORD_CLIENT_SECRET}
      DISCORD_REDIRECT_URI: ${DISCORD_REDIRECT_URI}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      DOCKER_HOST: tcp://docker-proxy:2375
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
      APP_BASE_URL: ${APP_BASE_URL}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    restart: unless-stopped
    networks:
      - pluginfactory

  docker-proxy:
    image: tecnativa/docker-socket-proxy:latest
    container_name: pluginfactory-docker-proxy
    environment:
      CONTAINERS: 1
      EXEC: 1
      IMAGES: 1
      POST: 1
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
    restart: unless-stopped
    networks:
      - pluginfactory

  postgres:
    image: postgres:16-alpine
    container_name: pluginfactory-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    restart: unless-stopped
    networks:
      - pluginfactory

  redis:
    image: redis:7-alpine
    container_name: pluginfactory-redis
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 5s
    restart: unless-stopped
    networks:
      - pluginfactory

  nginx:
    image: nginx:alpine
    container_name: pluginfactory-nginx
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      api:
        condition: service_healthy
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - certbot-certs:/etc/letsencrypt:ro
      - certbot-webroot:/var/www/certbot:ro
    restart: unless-stopped
    networks:
      - pluginfactory

  certbot:
    image: certbot/certbot:latest
    container_name: pluginfactory-certbot
    volumes:
      - certbot-certs:/etc/letsencrypt
      - certbot-webroot:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew --webroot -w /var/www/certbot --quiet; sleep 12h & wait $${!}; done'"
    restart: unless-stopped
    networks:
      - pluginfactory

  minio:
    image: minio/minio:latest
    container_name: pluginfactory-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 10s
    restart: unless-stopped
    networks:
      - pluginfactory

volumes:
  pgdata:
    driver: local
  redis-data:
    driver: local
  minio-data:
    driver: local
  certbot-certs:
    driver: local
  certbot-webroot:
    driver: local

networks:
  pluginfactory:
    driver: bridge
```

Changes: removed deprecated `version: "3.9"`, added certbot service, updated nginx volumes for SSL, added certbot volumes.

**Step 2: Commit**

```bash
git add docker-compose.yml && git commit -m "feat: add certbot service and SSL volumes to production compose"
```

---

## Task 9: Create Deployment Guide

**Why:** Consolidate all deployment steps into a single reference document for setting up Oracle Cloud + Vercel from scratch.

**Files:**
- Create: `DEPLOYMENT.md`

**Step 1: Write the deployment guide**

```markdown
# Deployment Guide: Oracle Cloud + Vercel

## Architecture

```
[Users] → [Vercel CDN] → React SPA (frontend)
                ↓ (API calls via VITE_API_URL)
         [Oracle Cloud VM]
              ↓
         [Nginx :443] → SSL termination
              ↓
         [Spring Boot API :8080]
              ↓
    [PostgreSQL] [Redis] [MinIO] [Docker Proxy]
```

## Prerequisites

- Oracle Cloud account (Always Free tier)
- Vercel account (free tier)
- Domain with DNS access (e.g., `bekololek.com`)
- Discord OAuth app configured
- Stripe account (test or live keys)
- Anthropic API key

## Part 1: Oracle Cloud VM Setup

### 1.1 Create the VM

1. Go to Oracle Cloud Console → Compute → Instances → Create Instance
2. Choose **Ampere A1** shape (Always Free eligible):
   - **Image:** Ubuntu 22.04 Minimal (aarch64)
   - **Shape:** VM.Standard.A1.Flex
   - **OCPUs:** 4 (max free)
   - **Memory:** 24 GB (max free)
   - **Boot volume:** 100 GB (free tier allows up to 200 GB)
3. Add your SSH public key
4. Create the instance and note the **public IP address**

### 1.2 Configure DNS

Create an A record pointing your API subdomain to the VM's public IP:

```
api.pluginfactory.bekololek.com → <VM_PUBLIC_IP>
```

Wait for DNS propagation (check with `dig api.pluginfactory.bekololek.com`).

### 1.3 Oracle Cloud Network Security

In the Oracle Cloud Console, add **Ingress Rules** to the VM's subnet security list:

| Port | Protocol | Source    | Description |
|------|----------|-----------|-------------|
| 22   | TCP      | 0.0.0.0/0 | SSH         |
| 80   | TCP      | 0.0.0.0/0 | HTTP        |
| 443  | TCP      | 0.0.0.0/0 | HTTPS       |

**Important:** Oracle Cloud has TWO firewalls - the cloud security list AND the OS firewall (iptables/UFW). Both must allow the ports.

### 1.4 Bootstrap the Server

SSH into the VM and run the bootstrap script:

```bash
ssh ubuntu@<VM_PUBLIC_IP>

# Clone the repo
sudo git clone <YOUR_REPO_URL> /opt/pluginfactory
cd /opt/pluginfactory

# Run bootstrap
sudo bash infra/scripts/server-bootstrap.sh
```

### 1.5 Configure Environment

```bash
cd /opt/pluginfactory
sudo nano .env
```

Fill in all values. Generate secure secrets:

```bash
# Generate JWT secret (256-bit)
openssl rand -base64 32

# Generate passwords
openssl rand -base64 24
```

### 1.6 Initialize SSL

```bash
cd /opt/pluginfactory
sudo bash infra/scripts/ssl-init.sh
```

### 1.7 Start the Stack

```bash
cd /opt/pluginfactory
sudo docker compose up -d
```

Verify:

```bash
# Check all containers are running
docker compose ps

# Check API health
curl -s https://api.pluginfactory.bekololek.com/actuator/health

# Check logs if something is wrong
docker compose logs api --tail 50
docker compose logs nginx --tail 50
```

## Part 2: Vercel Frontend Setup

### 2.1 Connect Repository

1. Go to [vercel.com](https://vercel.com) and sign in
2. Click "Add New Project"
3. Import your Git repository
4. Set the **Root Directory** to `web`
5. Vercel auto-detects Vite — verify these settings:
   - **Framework:** Vite
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`

### 2.2 Configure Environment Variables

In Vercel Project Settings → Environment Variables, add:

| Variable | Value | Environment |
|----------|-------|-------------|
| `VITE_API_URL` | `https://api.pluginfactory.bekololek.com` | Production |

### 2.3 Configure Domain

In Vercel Project Settings → Domains:
- Add `pluginfactory.bekololek.com`
- Follow Vercel's DNS instructions (CNAME or A record)

### 2.4 Deploy

Push to your main branch or click "Deploy" in Vercel. The `vercel.json` in `web/` handles SPA routing and security headers automatically.

## Part 3: Post-Deployment Checklist

- [ ] `https://api.pluginfactory.bekololek.com/actuator/health` returns `{"status":"UP"}`
- [ ] `https://pluginfactory.bekololek.com` loads the React app
- [ ] Discord OAuth login works (redirect URI matches DISCORD_REDIRECT_URI in .env)
- [ ] Stripe webhooks configured to point to `https://api.pluginfactory.bekololek.com/api/v1/webhooks/stripe`
- [ ] SSL certificate is valid (`curl -vI https://api.pluginfactory.bekololek.com` shows valid cert)
- [ ] WebSocket connection works for build chat

## Ongoing Maintenance

### SSL Certificate Renewal
Certbot auto-renews every 12 hours (via the certbot container). Certificates are valid for 90 days. No manual action needed.

### Updates
```bash
cd /opt/pluginfactory
git pull origin main
docker compose build --no-cache api
docker compose up -d --remove-orphans
```

Or use the CI/CD pipeline by creating a GitHub release (triggers `deploy-production.yml`).

### Monitoring (optional)
```bash
cd /opt/pluginfactory
docker compose -f infra/monitoring/docker-compose.monitoring.yml up -d
# Grafana: http://<VM_IP>:3000 (internal only, not exposed via nginx)
```

### Logs
```bash
docker compose logs api --tail 100 -f     # API logs
docker compose logs nginx --tail 100 -f   # Nginx access/error logs
docker compose logs postgres --tail 50    # Database logs
```
```

**Step 2: Commit**

```bash
git add DEPLOYMENT.md && git commit -m "docs: add comprehensive Oracle Cloud + Vercel deployment guide"
```

---

## Summary of All Tasks

| Task | What | Files Changed |
|------|------|---------------|
| 1 | Frontend env documentation | `web/.env.example` |
| 2 | Vercel config update (caching) | `web/vercel.json` |
| 3 | Nginx SSL config | `infra/nginx/nginx.conf` |
| 4 | Certbot Docker service | `docker-compose.yml`, `.env.example` |
| 5 | Server bootstrap script | `infra/scripts/server-bootstrap.sh` |
| 6 | SSL initialization script | `infra/scripts/ssl-init.sh` |
| 7 | Consolidated .env.example | `.env.example` |
| 8 | Docker Compose final assembly | `docker-compose.yml` |
| 9 | Deployment guide | `DEPLOYMENT.md` |

Tasks 1-2 are frontend (web/ repo). Tasks 3-9 are backend/infra (api/ repo or root).
