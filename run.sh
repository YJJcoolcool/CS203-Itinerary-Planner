#!/usr/bin/env bash
#
# run.sh - start the Itinerary Planner development stack.
#
# Starts the Spring Boot backend (http://localhost:8080) and the Vite
# frontend (http://localhost:5173) together and stops both on Ctrl+C.
#
# Usage:
#   ./run.sh                # backend + frontend
#   ./run.sh backend        # backend only
#   ./run.sh frontend       # frontend only
#   ./run.sh --install      # force a dependency reinstall first
#   ./run.sh --help
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

RUN_BACKEND=1
RUN_FRONTEND=1
FORCE_INSTALL=0

log()  { printf '\033[1;34m[run]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[run]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[run]\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: ./run.sh [backend|frontend] [--install]

  (no target)   Start backend and frontend together
  backend       Start the Spring Boot backend only  (http://localhost:8080)
  frontend      Start the Vite frontend only        (http://localhost:5173)
  --install     Force reinstall of dependencies before starting
  -h, --help    Show this help

Environment overrides:
  BACKEND_PORT   Backend port  (default 8080)
  FRONTEND_PORT  Frontend port (default 5173)
  SPRING_PROFILES_ACTIVE  Backend profile (default dev; needs the SSH tunnel and DB_PASSWORD)
EOF
}

# ---------------------------------------------------------------- arguments
for arg in "$@"; do
  case "$arg" in
    backend)    RUN_FRONTEND=0 ;;
    frontend)   RUN_BACKEND=0 ;;
    all)        RUN_BACKEND=1; RUN_FRONTEND=1 ;;
    --install)  FORCE_INSTALL=1 ;;
    -h|--help)  usage; exit 0 ;;
    *)          die "Unknown argument: $arg (try --help)" ;;
  esac
done

[ "$RUN_BACKEND" -eq 1 ] || [ "$RUN_FRONTEND" -eq 1 ] || die "Nothing to run."

# ------------------------------------------------------------ prerequisites
command -v java >/dev/null 2>&1 || die "java not found. Install a JDK 21+."
command -v node >/dev/null 2>&1 || die "node not found. Install Node.js 20+."
if [ "$RUN_BACKEND" -eq 1 ]; then
  command -v curl >/dev/null 2>&1 || die "curl not found (needed for readiness checks)."
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F'"' '/version/{print $2}' | awk -F. '{print ($1=="1") ? $2 : $1}')"
if [ -n "${JAVA_MAJOR:-}" ] && [ "$JAVA_MAJOR" -lt 21 ] 2>/dev/null; then
  warn "Detected Java $JAVA_MAJOR; the backend targets Java 21+."
fi

# -------------------------------------------------------------- env files
# Backend reads backend/.env; Vite reads frontend/.env. Create them from the
# checked-in examples on first run so the app can boot without manual setup.
if [ "$RUN_BACKEND" -eq 1 ] && [ ! -f "$BACKEND_DIR/.env" ] && [ -f "$BACKEND_DIR/.env.example" ]; then
  cp "$BACKEND_DIR/.env.example" "$BACKEND_DIR/.env"
  warn "Created backend/.env from backend/.env.example - add real Clerk keys for authenticated endpoints."
fi
if [ "$RUN_FRONTEND" -eq 1 ] && [ ! -f "$FRONTEND_DIR/.env" ] && [ -f "$FRONTEND_DIR/.env.example" ]; then
  cp "$FRONTEND_DIR/.env.example" "$FRONTEND_DIR/.env"
  warn "Created frontend/.env from .env.example."
fi

# Export the backend env so Spring placeholders (${CLERK_ISSUER_URI}) resolve
# even when the app is launched from a different working directory.
if [ -f "$BACKEND_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$BACKEND_DIR/.env" 2>/dev/null || warn "Could not source backend/.env; continuing with current environment."
  set +a
fi

# The backend has no datasource without a profile; default to dev (EC2 via the SSH tunnel).
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

# Treat the example placeholders (and an empty value) as "not configured":
# drop CLERK_ISSUER_URI before launching so Spring starts the backend without
# authentication instead of pointing JWT validation at a non-existent Clerk
# instance. Set real credentials in backend/.env to enable authentication.
if [ "$RUN_BACKEND" -eq 1 ]; then
  case "${CLERK_ISSUER_URI:-}" in
    ""|*replace_me*|*your-instance*)
      unset CLERK_ISSUER_URI
      warn "No real Clerk issuer configured. Starting the backend WITHOUT authentication; JWT-protected endpoints are open until backend/.env is filled in." ;;
  esac
fi

# ------------------------------------------------------------- install deps
if [ "$RUN_FRONTEND" -eq 1 ]; then
  # Reinstall when node_modules is missing OR stale. npm records the last
  # install in node_modules/.package-lock.json, so a newer package.json or
  # package-lock.json means dependencies were added/changed since the last
  # install (e.g. a newly declared package such as leaflet) and Vite would
  # otherwise fail to resolve them.
  FE_STAMP="$FRONTEND_DIR/node_modules/.package-lock.json"
  if [ "$FORCE_INSTALL" -eq 1 ] \
     || [ ! -d "$FRONTEND_DIR/node_modules" ] \
     || [ ! -f "$FE_STAMP" ] \
     || [ "$FRONTEND_DIR/package.json" -nt "$FE_STAMP" ] \
     || [ "$FRONTEND_DIR/package-lock.json" -nt "$FE_STAMP" ]; then
    log "Installing frontend dependencies..."
    ( cd "$FRONTEND_DIR" && npm ci )
  else
    log "Frontend dependencies are up to date."
  fi
fi

if [ "$RUN_BACKEND" -eq 1 ] && [ "$FORCE_INSTALL" -eq 1 ]; then
  log "Resolving backend dependencies..."
  ( cd "$BACKEND_DIR" && sh ./mvnw -q -DskipTests dependency:resolve )
fi

# --------------------------------------------------------- process control
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  trap - INT TERM EXIT
  printf '\n'
  log "Shutting down..."
  for pid in "$FRONTEND_PID" "$BACKEND_PID"; do
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      pkill -TERM -P "$pid" 2>/dev/null || true
      kill -TERM "$pid" 2>/dev/null || true
    fi
  done
  sleep 1
  for pid in "$FRONTEND_PID" "$BACKEND_PID"; do
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      pkill -KILL -P "$pid" 2>/dev/null || true
      kill -KILL "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
  log "Stopped."
}
trap cleanup INT TERM EXIT

wait_for_http() {
  local port="$1" name="$2" tries="${3:-90}"
  for _ in $(seq 1 "$tries"); do
    if curl -s -o /dev/null "http://localhost:$port/" 2>/dev/null; then
      log "$name is up on http://localhost:$port"
      return 0
    fi
    sleep 1
  done
  warn "$name did not respond on port $port within ${tries}s (it may still be starting)."
  return 1
}

# --------------------------------------------------------------- launch
if [ "$RUN_BACKEND" -eq 1 ]; then
  log "Starting backend (Spring Boot) on http://localhost:$BACKEND_PORT ..."
  ( cd "$BACKEND_DIR" && exec sh ./mvnw -q spring-boot:run -Dspring-boot.run.fork=false ) &
  BACKEND_PID=$!
  wait_for_http "$BACKEND_PORT" "Backend" 90 || true
fi

if [ "$RUN_FRONTEND" -eq 1 ]; then
  log "Starting frontend (Vite) on http://localhost:$FRONTEND_PORT ..."
  ( cd "$FRONTEND_DIR" && exec ./node_modules/.bin/vite --port "$FRONTEND_PORT" ) &
  FRONTEND_PID=$!
  wait_for_http "$FRONTEND_PORT" "Frontend" 60 || true
fi

log "Press Ctrl+C to stop."
wait
