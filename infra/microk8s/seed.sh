#!/usr/bin/env bash
# Load sample data into ODIN Catalog running on MicroK8s.
#
# Routes all API calls through the nginx ingress at api.catalog.local — no
# kubectl port-forwards required. Requires api.catalog.local to resolve
# (add it to /etc/hosts as printed by ./infra/microk8s/deploy.sh).
#
# Usage:
#   ./infra/microk8s/seed.sh [OPTIONS]
#
# Options:
#   --api-host URL   Ingress base URL (default: http://api.catalog.local)
#   --api-key  KEY   X-API-Key header value (default: dev-local)
#   --timeout  N     Seconds to wait for each service to become healthy (default: 120)
#
# Prerequisites: curl, jq
# The seed is idempotent; running it multiple times will not create duplicates.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_SCRIPT="${SCRIPT_DIR}/../seed/seed.sh"

API_HOST="${API_HOST:-http://api.catalog.local}"
API_KEY="${API_KEY:-dev-local}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()    { echo -e "${BLUE}[mk8s-seed]${NC} $*"; }
success() { echo -e "${GREEN}[done]${NC} $*"; }
warn()    { echo -e "${YELLOW}[warn]${NC} $*"; }
die()     { echo -e "${RED}[error]${NC} $*" >&2; exit 1; }

# ─── Parse args ───────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case $1 in
    --api-host) API_HOST="$2"; shift 2 ;;
    --api-key)  API_KEY="$2";  shift 2 ;;
    --timeout)  HEALTH_TIMEOUT="$2"; shift 2 ;;
    *) die "Unknown option: $1" ;;
  esac
done

# ─── Prerequisites ────────────────────────────────────────────────────────────
for cmd in curl jq; do
  command -v "$cmd" &>/dev/null || die "Required tool not found: $cmd"
done

[[ -f "${SEED_SCRIPT}" ]] || die "Seed script not found at: ${SEED_SCRIPT}"

# Verify the ingress hostname is resolvable before waiting 120 s for a timeout
INGRESS_HOSTNAME="${API_HOST#http://}"
INGRESS_HOSTNAME="${INGRESS_HOSTNAME#https://}"
INGRESS_HOSTNAME="${INGRESS_HOSTNAME%%/*}"
if ! getent hosts "${INGRESS_HOSTNAME}" >/dev/null 2>&1; then
  die "${INGRESS_HOSTNAME} is not resolvable. Add it to /etc/hosts (see deploy.sh output)."
fi

# ─── Health check helper ──────────────────────────────────────────────────────
wait_healthy() {
  local name="$1" url="$2"
  local deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
  info "Waiting for ${name} at ${url} ..."
  while true; do
    if curl -sf --max-time 5 "${url}" >/dev/null 2>&1; then
      success "${name} is healthy"
      return 0
    fi
    if [[ $(date +%s) -ge $deadline ]]; then
      die "Timed out waiting for ${name} after ${HEALTH_TIMEOUT}s."
    fi
    sleep 3
  done
}

# ─── Wait for each service via the ingress ────────────────────────────────────
echo ""
info "Checking service health via ${API_HOST} ..."

wait_healthy "inventory-service" "${API_HOST}/inventory/actuator/health/readiness"
wait_healthy "harvest-service"   "${API_HOST}/harvest/actuator/health/readiness"
wait_healthy "lineage-service"   "${API_HOST}/lineage/actuator/health/readiness"
wait_healthy "search-service"    "${API_HOST}/search/actuator/health/readiness"

# ─── Run seed script ──────────────────────────────────────────────────────────
echo ""
info "All services reachable. Delegating to infra/seed/seed.sh ..."
echo ""

export CATALOG_URL="${API_HOST}/inventory"
export HARVEST_URL="${API_HOST}/harvest"
export LINEAGE_URL="${API_HOST}/lineage"
export SEARCH_URL="${API_HOST}/search"
export BASE_URL="${API_HOST}"
export API_KEY="${API_KEY}"

bash "${SEED_SCRIPT}"

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN} MicroK8s seed complete${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  Try it:"
echo "    Consumer UI  → http://catalog.local"
echo "    Producer UI  → http://manage.catalog.local"
echo "    Search API   → curl '${API_HOST}/search/api/v1/search?q=trades'"
echo ""
