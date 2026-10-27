#!/usr/bin/env bash
# deploy.sh - build images locally, ship them to EC2 over SSH, restart the stack.
# Usage: ./deploy.sh   (needs Docker locally and the "cs203" SSH alias)
set -euo pipefail

HOST="${DEPLOY_HOST:-cs203}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() { printf '\033[1;34m[deploy]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[deploy]\033[0m %s\n' "$*" >&2; exit 1; }

# Clerk publishable key is baked into the web image; take it from env or frontend/.env.local.
KEY="${VITE_CLERK_PUBLISHABLE_KEY:-$(grep -hs '^VITE_CLERK_PUBLISHABLE_KEY=' frontend/.env.local frontend/.env | head -1 | cut -d= -f2-)}"
[[ "$KEY" == pk_* ]] || die "VITE_CLERK_PUBLISHABLE_KEY not set (env or frontend/.env.local)."

# Server secrets live only in ~/app/.env on EC2; this script never uploads any .env.
ssh "$HOST" 'grep -q "^CLERK_ISSUER_URI=https" ~/app/.env && grep -q "^DB_PASSWORD=." ~/app/.env' \
  || die "~/app/.env on $HOST needs DB_PASSWORD and CLERK_ISSUER_URI (see .env.example)."

[ -z "$(git status --porcelain)" ] || log "Warning: uncommitted changes will be deployed."

log "Building images ($(git rev-parse --short HEAD))..."
docker build -t itinerary-backend:latest backend
docker build -t itinerary-web:latest --build-arg VITE_CLERK_PUBLISHABLE_KEY="$KEY" frontend

log "Shipping images to $HOST..."
docker save itinerary-backend:latest itinerary-web:latest | gzip | ssh "$HOST" 'gunzip | docker load'

log "Updating Compose and Caddy configuration..."

ssh "$HOST" '
  cp ~/app/docker-compose.yml ~/app/docker-compose.yml.bak
  if [ -f ~/app/Caddyfile ]; then
    cp ~/app/Caddyfile ~/app/Caddyfile.bak
  fi
'

scp docker-compose.yml "$HOST":~/app/docker-compose.yml
scp Caddyfile "$HOST":~/app/Caddyfile

log "Restarting stack..."
ssh "$HOST" 'cd ~/app && docker compose up -d && docker image prune -f >/dev/null'

# 401 on /api/trips through nginx = web, backend, DB and auth all up.
log "Waiting for HTTPS and the backend..."

ssh "$HOST" '
  for i in $(seq 1 60); do
    code=$(curl -sS -o /dev/null -w "%{http_code}" \
      --max-time 5 https://agiletrips.live/api/trips || true)

    [ "$code" = "401" ] && exit 0
    sleep 2
  done
  exit 1
' || die "HTTPS/backend check failed. Check Caddy and backend logs."