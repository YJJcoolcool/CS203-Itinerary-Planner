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

log "Updating compose file (previous kept as docker-compose.yml.bak)..."
ssh "$HOST" 'cp ~/app/docker-compose.yml ~/app/docker-compose.yml.bak'
scp docker-compose.yml "$HOST":~/app/docker-compose.yml

log "Restarting stack..."
ssh "$HOST" 'cd ~/app && docker compose up -d && docker image prune -f >/dev/null'

# 401 on /api/trips through nginx = web, backend, DB and auth all up.
log "Waiting for the backend..."
ssh "$HOST" 'for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w "%{http_code}" localhost/api/trips)
  [ "$code" = 401 ] && exit 0; sleep 2; done; exit 1' \
  || die "Backend not answering. Check: ssh $HOST 'cd ~/app && docker compose logs backend'"
log "Deployed. Site: http://$(ssh -G "$HOST" | awk '/^hostname /{print $2}')"
