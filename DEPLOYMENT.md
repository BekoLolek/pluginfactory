# BekoLolek Plugin Factory — Deployment Guide

This guide walks you through every step to get the platform running, from local development to full production deployment.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [External Service Setup](#2-external-service-setup)
3. [Local Development](#3-local-development)
4. [Environment Variables Reference](#4-environment-variables-reference)
5. [Frontend Deployment (Vercel)](#5-frontend-deployment-vercel)
6. [Production Server Setup](#6-production-server-setup)
7. [Production Deployment](#7-production-deployment)
8. [CI/CD Pipeline](#8-cicd-pipeline)
9. [DNS & SSL Configuration](#9-dns--ssl-configuration)
10. [Post-Deployment Verification](#10-post-deployment-verification)
11. [Monitoring & Maintenance](#11-monitoring--maintenance)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Prerequisites

### Local Machine

- **Java 17+** (JDK, for running backend tests — the project includes a Maven wrapper so no Maven install is needed)
- **Node.js 18+** and **npm 9+** (for frontend)
- **Docker** and **Docker Compose** (for running the full stack locally)
- **Git** (for version control)

### Production Server

- **Recommended:** Oracle Cloud Always Free ARM VM (4 OCPUs, 24GB RAM — always free)
- Alternatively, any Linux VPS (Ubuntu 22.04+, minimum 2 vCPUs, 4GB RAM)
- SSH access configured
- A domain name pointed to the server's IP
- Docker and Docker Compose (installed automatically by the bootstrap script)

---

## 2. External Service Setup

You need accounts and credentials from these services before deploying.

### 2.1 Discord Application (OAuth2 Login)

1. Go to https://discord.com/developers/applications
2. Click **New Application**, name it (e.g., "BekoLolek Plugin Factory")
3. Go to **OAuth2** > **General**
4. Copy the **Client ID** and **Client Secret**
5. Add redirect URIs:
   - Development: `http://localhost:5173/auth/callback`
   - Production: `https://yourdomain.com/auth/callback`
6. Under **OAuth2** > **URL Generator**, select scopes: `identify`, `email`

Save these values:
```
DISCORD_CLIENT_ID=<your-client-id>
DISCORD_CLIENT_SECRET=<your-client-secret>
DISCORD_REDIRECT_URI=https://yourdomain.com/auth/callback
```

### 2.2 Anthropic API (Claude AI)

1. Go to https://console.anthropic.com
2. Create an account and add billing
3. Go to **API Keys** and create a new key

Save:
```
ANTHROPIC_API_KEY=sk-ant-...
```

### 2.3 Stripe (Payments)

1. Go to https://dashboard.stripe.com and create an account
2. Get your **Secret Key** from **Developers** > **API Keys**
   - Use the test key (`sk_test_...`) for development
   - Use the live key (`sk_live_...`) for production
3. Create a **Webhook Endpoint**:
   - URL: `https://yourdomain.com/api/v1/webhooks/stripe`
   - Events to listen for:
     - `checkout.session.completed`
     - `customer.subscription.updated`
     - `customer.subscription.deleted`
     - `invoice.payment_failed`
4. Copy the **Webhook Signing Secret**
5. Create **Products and Prices** for each subscription tier:
   - BASIC: $9.99/month
   - PRO: $29.99/month
   - TEAM: $79.99/month

   **Tier limits** (defined in `api/src/main/java/com/bekololek/pluginfactory/subscription/Tier.java`):

   | Tier  | Builds/mo | Tokens/mo | Iterations | Max parallel | Commands | Listeners | Marketplace slots | JAR retention | Source code |
   |-------|-----------|-----------|------------|--------------|----------|-----------|-------------------|---------------|-------------|
   | FREE  | 1         | 30,000    | 1          | 0            | 5        | 3         | 0                 | 7 days        | No          |
   | BASIC | 5         | 300,000   | 3          | 0            | 15       | 10        | 2                 | 30 days       | No          |
   | PRO   | 20        | 900,000   | 5          | 5            | 50       | 30        | 5                 | 90 days       | Yes         |
   | TEAM  | 150       | 6,000,000 | 10         | 5            | 50       | 30        | 25                | 180 days      | Yes         |

   Tokens are a *pooled monthly budget* per subscription — a build is denied when either the build count cap or the token pool is reached (with at least 1k tokens of headroom required to start a build).

Save:
```
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

### 2.4 Discord Webhook (Deploy Notifications) — Optional

1. In your Discord server, go to a channel's settings > **Integrations** > **Webhooks**
2. Create a new webhook, copy the URL

Save:
```
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...
```

---

## 3. Local Development

### 3.1 Clone and Install

```bash
git clone <your-repo-url> plugin-factory
cd plugin-factory
```

### 3.2 Backend (Spring Boot with H2)

The backend runs in dev profile by default with an in-memory H2 database — no external database needed.

```bash
cd api
./mvnw clean compile      # Compile
./mvnw test               # Run all 93 tests
./mvnw spring-boot:run    # Start on port 8080
```

Verify:
```bash
curl http://localhost:8080/health
# → {"status":"UP","timestamp":"..."}

curl http://localhost:8080/api/v1/subscriptions/tiers
# → Returns 4 tiers (FREE, BASIC, PRO, TEAM)
```

### 3.3 Frontend (Vite Dev Server)

```bash
cd web
npm install
npm run lint              # Lint check
npm run build             # TypeScript check + production build
npm run dev               # Start dev server on port 5173
```

The frontend at `http://localhost:5173` will proxy API calls to `http://localhost:8080`.

### 3.4 Full Stack with Docker Compose (Local)

To run the full stack (API + PostgreSQL + Redis + MinIO + Nginx) locally:

```bash
# From project root
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

Or use the Makefile shortcut:
```bash
make dev
```

This starts:
- **API** on port 8080 (with debug port 5005)
- **PostgreSQL** on port 5432
- **Redis** on port 6379
- **MinIO** on port 9000 (console on 9001)
- **Nginx** on port 80

### 3.5 Build Container Images

Build the plugin compilation and test containers:

```bash
docker build -t pluginfactory-build:latest -f containers/Dockerfile.build containers/
docker build -t pluginfactory-test:latest -f containers/Dockerfile.test containers/
```

---

## 4. Environment Variables Reference

Create a `.env` file in the project root for Docker Compose. **Never commit this file.**

```env
# ─── Database ───────────────────────────────────────────────
POSTGRES_DB=pluginfactory
POSTGRES_USER=pluginfactory
POSTGRES_PASSWORD=<strong-random-password>

# ─── JWT ────────────────────────────────────────────────────
JWT_SECRET=<random-string-at-least-64-characters-long>

# ─── Discord OAuth ──────────────────────────────────────────
DISCORD_CLIENT_ID=<from-step-2.1>
DISCORD_CLIENT_SECRET=<from-step-2.1>
DISCORD_REDIRECT_URI=https://yourdomain.com/auth/callback

# ─── Anthropic (Claude AI) ─────────────────────────────────
ANTHROPIC_API_KEY=<from-step-2.2>

# ─── Stripe ─────────────────────────────────────────────────
STRIPE_API_KEY=<from-step-2.3>
STRIPE_WEBHOOK_SECRET=<from-step-2.3>

# ─── MinIO (Object Storage) ────────────────────────────────
MINIO_ACCESS_KEY=<random-access-key>
MINIO_SECRET_KEY=<random-secret-key>

# ─── CORS ───────────────────────────────────────────────────
CORS_ALLOWED_ORIGINS=https://yourdomain.com

# ─── Discord Webhook (optional) ────────────────────────────
DISCORD_WEBHOOK_URL=<from-step-2.4>
```

Generate secure random values:
```bash
# JWT secret (64 chars)
openssl rand -base64 48

# Database password
openssl rand -base64 24

# MinIO keys
openssl rand -hex 20   # access key
openssl rand -hex 40   # secret key
```

---

## 5. Frontend Deployment (Vercel)

The frontend is a static React SPA deployed independently to Vercel.

### 5.1 Connect to Vercel

1. Go to https://vercel.com and sign in with GitHub
2. Click **Import Project** and select the repository
3. Set the **Root Directory** to `web`
4. Vercel will auto-detect Vite — the `web/vercel.json` configures:
   - Build command: `npm run build`
   - Output directory: `dist`
   - SPA rewrites (all routes → `index.html`)

### 5.2 Environment Variables

In the Vercel project settings, add:

| Variable | Value |
|----------|-------|
| `VITE_API_URL` | `https://yourdomain.com` (your backend URL) |

### 5.3 Deploy

- **Production deploys** trigger on push to `main`
- **Preview deploys** trigger on pull requests
- Custom domain: Add your frontend domain in Vercel project settings > Domains

### 5.4 Update CORS

Make sure the backend's `CORS_ALLOWED_ORIGINS` includes your Vercel domain:
```env
CORS_ALLOWED_ORIGINS=https://pluginfactory.bekololek.com
```

---

## 6. Production Server Setup

### 6.0 Oracle Cloud Setup (Recommended)

Oracle Cloud Always Free tier provides a powerful ARM VM at no cost:

1. Go to Oracle Cloud Console > Compute > Instances > Create Instance
2. Choose **Ampere A1** shape (Always Free):
   - **Image:** Ubuntu 22.04 Minimal (aarch64)
   - **Shape:** VM.Standard.A1.Flex — 4 OCPUs, 24 GB RAM
   - **Boot volume:** 100 GB
3. Add your SSH public key
4. After creation, add **Ingress Rules** to the subnet security list:
   - Port 22 (SSH), Port 80 (HTTP), Port 443 (HTTPS) — all from 0.0.0.0/0

> **Important:** Oracle Cloud has TWO firewalls: the cloud security list AND the OS firewall (UFW). Both must allow the ports.

### 6.1 Initial Server Configuration

SSH into your server and use the automated bootstrap script:

```bash
ssh ubuntu@<VM_PUBLIC_IP>

# Clone the repo
sudo git clone <your-repo-url> /opt/pluginfactory
cd /opt/pluginfactory

# Run bootstrap (installs Docker, Docker Compose, UFW, fail2ban, swap)
sudo bash infra/scripts/server-bootstrap.sh
```

The bootstrap script automatically:
- Installs Docker and Docker Compose
- Creates a 4GB swap file (important for free-tier instances)
- Configures UFW firewall (ports 22, 80, 443)
- Enables fail2ban for SSH protection
- Creates `.env` from `.env.example`

### 6.2 Configure Environment

```bash
cd /opt/pluginfactory
nano .env
# Fill in all production values (see Section 4)
```

### 6.3 Build Plugin Containers

```bash
cd /opt/pluginfactory
docker build -t pluginfactory-build:latest -f containers/Dockerfile.build containers/
docker build -t pluginfactory-test:latest -f containers/Dockerfile.test containers/
```

### 6.4 Set Up SSH Keys for CI/CD

On your **local machine** (or in GitHub Actions), generate a deploy key:

```bash
ssh-keygen -t ed25519 -C "pluginfactory-deploy" -f deploy_key -N ""
```

Copy the public key to the server:
```bash
ssh-copy-id -i deploy_key.pub user@your-server-ip
```

Then add the **private key** as a GitHub Actions secret (see Section 8).

### 6.5 Firewall Configuration

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Only ports 22 (SSH), 80 (HTTP), and 443 (HTTPS) should be open. PostgreSQL, Redis, and MinIO ports should NOT be exposed externally — they communicate internally via Docker networking.

---

## 7. Production Deployment

### 7.1 First Deployment (Manual)

```bash
cd /opt/pluginfactory

# Build and start everything
docker compose build --no-cache
docker compose up -d

# Check status
docker compose ps

# Check API health
curl http://localhost:8080/actuator/health
```

### 7.2 Subsequent Deployments

Use the deploy script:
```bash
./infra/scripts/deploy.sh user@your-server main
```

Or via the Makefile:
```bash
make deploy-prod
```

The deploy script performs:
1. Pulls latest code from the specified branch
2. Builds new Docker images
3. Starts the updated stack
4. Waits for health check (up to 120s)
5. Automatically rolls back if health check fails
6. Cleans up old Docker images

### 7.3 View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f api
docker compose logs -f postgres
docker compose logs -f nginx
```

---

## 8. CI/CD Pipeline

The project has 4 GitHub Actions workflows. Configure these GitHub repository secrets and environments.

### 8.1 GitHub Secrets

Go to **Repository Settings** > **Secrets and variables** > **Actions** and add:

| Secret | Description |
|--------|-------------|
| `PRODUCTION_SSH_KEY` | Private SSH key for production server |
| `PRODUCTION_HOST_IP` | Production server IP address |
| `PRODUCTION_USER` | SSH username on production server |
| `STAGING_SSH_KEY` | Private SSH key for staging server |
| `STAGING_HOST_IP` | Staging server IP address |
| `STAGING_USER` | SSH username on staging server |
| `DISCORD_WEBHOOK_URL` | Discord webhook for deploy notifications (optional) |

### 8.2 GitHub Environments

Create two environments in **Repository Settings** > **Environments**:

- **staging** — auto-deploys on push to `develop`
- **production** — deploys on GitHub Release publish (add required reviewers for approval gating)

### 8.3 Workflow Overview

| Workflow | Trigger | What It Does |
|----------|---------|--------------|
| `backend-ci.yml` | Push/PR to `main`/`develop` (api/ changes) | Runs `./mvnw clean verify` (tests + JaCoCo coverage) |
| `frontend-ci.yml` | Push/PR to `main`/`develop` (web/ changes) | Runs TypeScript check, lint, and build |
| `deploy-staging.yml` | Push to `develop` | Deploys to staging via SSH |
| `deploy-production.yml` | GitHub Release published | Tests, deploys to production via SSH, health check, Discord notification |

### 8.4 Release Flow

```bash
# 1. Merge feature branches into develop
git checkout develop
git merge feature/my-feature
git push origin develop
# → Triggers staging deploy

# 2. When ready for production, merge to main and create a release
git checkout main
git merge develop
git push origin main

# 3. Create a release on GitHub (or via CLI)
gh release create v1.0.0 --title "v1.0.0" --notes "First production release"
# → Triggers production deploy
```

---

## 9. DNS & SSL Configuration

### 9.1 DNS Records

Set up these DNS records at your registrar:

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | `api.yourdomain.com` | `<server-ip>` | 300 |
| CNAME | `yourdomain.com` | `cname.vercel-dns.com` | 300 |

The backend runs on a subdomain (`api.`) while the frontend domain points to Vercel.

### 9.2 SSL with Let's Encrypt (Containerized)

SSL is handled automatically by the certbot container in `docker-compose.yml`. For the **initial certificate**, run the SSL init script:

```bash
cd /opt/pluginfactory
sudo bash infra/scripts/ssl-init.sh
```

This script solves the chicken-and-egg problem (nginx needs certs, certbot needs nginx):
1. Starts a temporary HTTP-only nginx
2. Requests the SSL certificate from Let's Encrypt
3. Stops the temporary nginx and starts the full stack with SSL

### 9.3 SSL Configuration

The nginx config (`infra/nginx/nginx.conf`) is already configured with:
- HTTP → HTTPS redirect
- TLS 1.2/1.3 with modern ciphers
- HSTS (1 year)
- OCSP stapling
- HTTP/2

The domain is hardcoded in the nginx SSL paths. If you use a different domain, update these lines in `infra/nginx/nginx.conf`:
```nginx
ssl_certificate /etc/letsencrypt/live/YOUR_DOMAIN/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/YOUR_DOMAIN/privkey.pem;
```

### 9.4 Auto-Renewal

The certbot container automatically attempts renewal every 12 hours. Certificates are valid for 90 days. No cron jobs or manual intervention needed.

---

## 10. Post-Deployment Verification

Run these checks after every production deployment:

```bash
# 1. API health
curl -s https://yourdomain.com/actuator/health | jq .
# Expected: {"status":"UP"}

# 2. Public endpoints
curl -s https://yourdomain.com/api/v1/subscriptions/tiers | jq .
# Expected: 4 tiers (FREE, BASIC, PRO, TEAM)

# 3. Auth flow
curl -s https://yourdomain.com/api/v1/auth/discord
# Expected: JSON with Discord authorization URL

# 4. Protected endpoint (should return 401)
curl -s -o /dev/null -w "%{http_code}" https://yourdomain.com/api/v1/users/me
# Expected: 401

# 5. Marketplace (should return 200)
curl -s -o /dev/null -w "%{http_code}" https://yourdomain.com/api/v1/marketplace/plugins
# Expected: 200

# 6. WebSocket endpoint
curl -s -o /dev/null -w "%{http_code}" https://yourdomain.com/ws/info
# Expected: 200

# 7. All Docker containers running
docker compose ps
# Expected: All services "Up" and healthy

# 8. Frontend loads
curl -s -o /dev/null -w "%{http_code}" https://yourdomain.com
# Expected: 200 (if served through same domain) or check your Vercel URL
```

You can also run the smoke test script:
```bash
./infra/scripts/smoke-test.sh https://yourdomain.com
```

---

## 11. Monitoring & Maintenance

### 11.1 Actuator Endpoints

The API exposes Spring Boot Actuator metrics:
- `GET /actuator/health` — health status
- `GET /actuator/info` — application info
- `GET /actuator/metrics` — JVM and application metrics
- `GET /actuator/prometheus` — Prometheus-format metrics

### 11.2 Database Backups

Set up automated PostgreSQL backups:

```bash
# Add to crontab
0 2 * * * docker exec pluginfactory-postgres pg_dump -U pluginfactory pluginfactory | gzip > /opt/backups/pluginfactory-$(date +\%Y\%m\%d).sql.gz

# Keep last 30 days
find /opt/backups -name "pluginfactory-*.sql.gz" -mtime +30 -delete
```

### 11.3 Log Rotation

Docker logs grow over time. Configure log rotation in `/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```

Restart Docker: `sudo systemctl restart docker`

### 11.4 Monthly Usage Reset

The backend automatically resets both `builds_used_this_period` and `tokens_used_this_period` on the 1st of each month at midnight UTC via `@Scheduled(cron = "0 0 0 1 * *")` in `SubscriptionService.resetUsageCounts()`. Token budgets are pooled monthly per subscription, not per build, and the pool refills on this schedule. No manual setup needed — just make sure `@EnableScheduling` is on the main application class (it is).

### 11.5 MinIO (Object Storage)

MinIO stores build artifacts (compiled JARs, source code bundles). Access the console at `http://your-server-ip:9001` (restricted to internal access only in production).

Create the required buckets on first deploy:
```bash
docker exec pluginfactory-minio mc alias set local http://localhost:9000 $MINIO_ACCESS_KEY $MINIO_SECRET_KEY
docker exec pluginfactory-minio mc mb local/plugin-artifacts
docker exec pluginfactory-minio mc mb local/source-bundles
```

---

## 12. Troubleshooting

### API won't start

```bash
# Check logs
docker compose logs api

# Common causes:
# - DATABASE_URL is wrong or PostgreSQL isn't ready
# - JWT_SECRET is missing or too short (must be 256+ bits for HS256)
# - Flyway migration conflict (check for V*.sql file issues)
```

### Database connection refused

```bash
# Verify PostgreSQL is running and healthy
docker compose ps postgres
docker compose logs postgres

# Test connection
docker exec pluginfactory-postgres pg_isready -U pluginfactory
```

### Frontend can't reach API (CORS errors)

1. Verify `CORS_ALLOWED_ORIGINS` matches your frontend URL exactly (including protocol, no trailing slash)
2. Check Nginx is proxying `/api` correctly
3. Check browser console for the exact CORS error

### Discord OAuth callback fails

1. Verify redirect URI matches exactly between Discord Developer Portal and your `.env`
2. Check that the callback page at `/auth/callback` is accessible
3. For local dev: redirect URI must be `http://localhost:5173/auth/callback`

### Stripe webhooks not received

1. Verify the webhook endpoint URL is correct in Stripe Dashboard
2. Check that `STRIPE_WEBHOOK_SECRET` matches the webhook's signing secret
3. Test with Stripe CLI: `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe`

### Build containers not starting

```bash
# Check Docker socket is mounted
docker compose exec api ls -la /var/run/docker.sock

# Check build images exist
docker images | grep pluginfactory

# Rebuild if missing
docker build -t pluginfactory-build:latest -f containers/Dockerfile.build containers/
docker build -t pluginfactory-test:latest -f containers/Dockerfile.test containers/
```

### Health check fails after deploy

The deploy script waits 120 seconds. If the API needs more time:
1. Check memory — the JVM may be OOM-killed (check `dmesg`)
2. Increase server RAM or tune JVM: add `-Xmx512m` to `JAVA_TOOL_OPTIONS`
3. Check if Flyway migrations are taking too long on large databases

### Rollback a bad deployment

The deploy script auto-rolls back on health check failure. For manual rollback:

```bash
cd /opt/pluginfactory
git log --oneline -5              # Find the last good commit
git checkout <good-commit-hash>
docker compose build --no-cache
docker compose up -d
```
