#!/usr/bin/env bash
# ============================================================
# Oracle Cloud VM Bootstrap Script for BekoLolek Plugin Factory
# ============================================================
# Run as root on a fresh Ubuntu 22.04+ ARM instance.
#
# Usage:
#   sudo bash server-bootstrap.sh
#
# After running, you must:
#   1. Clone your repo to /opt/pluginfactory (if not already done)
#   2. Copy .env.example to .env and fill in real values
#   3. Run infra/scripts/ssl-init.sh to provision SSL certificates
#   4. Start the stack with docker compose up -d
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

# ---- Swap (important for free-tier instances) ----
if ! swapon --show | grep -q '/swapfile'; then
    log_info "Creating ${SWAP_SIZE} swap file..."
    fallocate -l ${SWAP_SIZE} /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
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
ufw allow 80/tcp    # HTTP (ACME + redirect)
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
    log_info "Repository already exists at ${DEPLOY_DIR}."
else
    log_warn "No git repo found at ${DEPLOY_DIR}."
    log_warn "You need to clone your repository:"
    log_warn "  git clone <your-repo-url> ${DEPLOY_DIR}"
fi

# ---- Create .env from example if not exists ----
if [ -f "${DEPLOY_DIR}/.env.example" ] && [ ! -f "${DEPLOY_DIR}/.env" ]; then
    cp "${DEPLOY_DIR}/.env.example" "${DEPLOY_DIR}/.env"
    log_warn "Created .env from .env.example — edit it with real values:"
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
echo "  2. Edit .env with real production values:"
echo "     nano ${DEPLOY_DIR}/.env"
echo ""
echo "  3. Generate secure secrets:"
echo "     openssl rand -base64 32   # JWT secret"
echo "     openssl rand -base64 24   # passwords"
echo ""
echo "  4. Initialize SSL certificate:"
echo "     cd ${DEPLOY_DIR} && sudo bash infra/scripts/ssl-init.sh"
echo ""
echo "  5. Start the full stack:"
echo "     cd ${DEPLOY_DIR} && docker compose up -d"
echo ""
echo "  6. Verify:"
echo "     docker compose ps"
echo "     curl -s https://\$DOMAIN/actuator/health"
echo ""
echo "============================================================"
