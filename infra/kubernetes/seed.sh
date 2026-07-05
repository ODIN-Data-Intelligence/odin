#!/usr/bin/env bash
# Load sample data into ODIN Catalog running on Kubernetes.
#
# Sets up kubectl port-forwards for each service, waits for them to be
# healthy, then delegates all data creation to infra/seed/seed.sh (which
# contains the full Meridian Capital scenario: 4 catalogs, 12 datasets,
# 5 data products, 8 lineage jobs, 5 harvest sources, and more).
#
# Usage:
#   ./infra/kubernetes/seed.sh [OPTIONS]
#
# Options:
#   --namespace NS   Kubernetes namespace (default: odin-catalog)
#   --api-key KEY    X-API-Key header value (default: dev-local)
#   --context CTX    kubectl context to use (default: current context)
#   --timeout N      Seconds to wait for each service to become healthy (default: 120)
#
# Prerequisites: kubectl, curl, jq
#
# Notes:
#   - Ports 8001-8004 are used for port-forwarding. Stop any local Docker Compose
#     stack beforehand if those ports are already in use.
#   - The seed is idempotent; running it multiple times will not create duplicates.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_SCRIPT="${SCRIPT_DIR}/../seed/seed.sh"

NAMESPACE="${NAMESPACE:-odin-catalog}"
API_KEY="${API_KEY:-dev-local}"
KUBE_CONTEXT="${KUBE_CONTEXT:-}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()    { echo -e "${BLUE}[k8s-seed]${NC} $*"; }
success() { echo -e "${GREEN}[done]${NC} $*"; }
warn()    { echo -e "${YELLOW}[warn]${NC} $*"; }
die()     { echo -e "${RED}[error]${NC} $*" >&2; exit 1; }

# ─── Parse args ───────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case $1 in
    --namespace) NAMESPACE="$2";    shift 2 ;;
    --api-key)   API_KEY="$2";      shift 2 ;;
    --context)   KUBE_CONTEXT="$2"; shift 2 ;;
    --timeout)   HEALTH_TIMEOUT="$2"; shift 2 ;;
    *) die "Unknown option: $1" ;;
  esac
done

KUBECTL="kubectl"
[[ -n "${KUBE_CONTEXT}" ]] && KUBECTL="kubectl --context=${KUBE_CONTEXT}"

# ─── Prerequisites ────────────────────────────────────────────────────────────
for cmd in kubectl curl jq; do
  command -v "$cmd" &>/dev/null || die "Required tool not found: $cmd"
done

[[ -f "${SEED_SCRIPT}" ]] || die "Seed script not found at: ${SEED_SCRIPT}"

# ─── Port-forward management ──────────────────────────────────────────────────
PF_PIDS=()

cleanup() {
  if [[ ${#PF_PIDS[@]} -gt 0 ]]; then
    info "Stopping port-forwards..."
    for pid in "${PF_PIDS[@]}"; do
      kill "$pid" 2>/dev/null || true
    done
  fi
}
trap cleanup EXIT

start_portforward() {
  local svc="$1" local_port="$2" remote_port="$3"
  local svc_full="svc/odin-catalog-${svc}"

  # Warn if port is already in use
  if lsof -i ":${local_port}" &>/dev/null 2>&1; then
    warn "Port ${local_port} already in use — port-forward for ${svc} may fail."
    warn "Stop any local Docker Compose stack before running this script."
  fi

  $KUBECTL -n "${NAMESPACE}" port-forward "${svc_full}" \
    "${local_port}:${remote_port}" --address 127.0.0.1 \
    >/tmp/pf-${svc}.log 2>&1 &
  PF_PIDS+=($!)
  info "Port-forward started: ${svc} → localhost:${local_port} (PID ${PF_PIDS[-1]})"
}

wait_healthy() {
  local name="$1" url="$2" timeout="${HEALTH_TIMEOUT}"
  local deadline=$(( $(date +%s) + timeout ))
  info "Waiting for ${name} at ${url} ..."
  while true; do
    if curl -sf --connect-timeout 2 "${url}" >/dev/null 2>&1; then
      success "${name} is healthy"
      return 0
    fi
    if [[ $(date +%s) -ge $deadline ]]; then
      die "Timed out waiting for ${name} after ${timeout}s. Check: kubectl -n ${NAMESPACE} get pods"
    fi
    sleep 3
  done
}

# ─── Verify cluster is reachable ──────────────────────────────────────────────
info "Checking cluster connectivity..."
$KUBECTL cluster-info --request-timeout=10s >/dev/null \
  || die "Cannot reach Kubernetes cluster. Check your kubeconfig / context."

info "Checking that all service pods are running..."
for svc in inventory-service harvest-service lineage-service search-service; do
  READY=$($KUBECTL -n "${NAMESPACE}" get deployment "odin-catalog-${svc}" \
    -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
  if [[ "${READY}" != "1" ]]; then
    warn "odin-catalog-${svc} is not ready (readyReplicas=${READY})."
    warn "Wait for the deployment to be ready before seeding:"
    warn "  kubectl -n ${NAMESPACE} rollout status deployment/odin-catalog-${svc}"
  fi
done

# ─── Start port-forwards ──────────────────────────────────────────────────────
echo ""
info "Starting port-forwards (localhost:8001-8004)..."

start_portforward "inventory-service" 8001 8001
start_portforward "harvest-service"   8002 8002
start_portforward "lineage-service"   8003 8003
start_portforward "search-service"    8004 8004

# Give port-forwards a moment to establish
sleep 3

# ─── Wait for each service health endpoint ────────────────────────────────────
wait_healthy "inventory-service" "http://localhost:8001/actuator/health/readiness"
wait_healthy "harvest-service"   "http://localhost:8002/actuator/health/readiness"
wait_healthy "lineage-service"   "http://localhost:8003/actuator/health/readiness"
wait_healthy "search-service"    "http://localhost:8004/actuator/health/readiness"

# ─── Run seed script ──────────────────────────────────────────────────────────
echo ""
info "All services reachable. Delegating to infra/seed/seed.sh ..."
echo ""

export BASE_URL="http://localhost"
export API_KEY="${API_KEY}"

bash "${SEED_SCRIPT}"

# ─── Trigger search re-index ──────────────────────────────────────────────────
echo ""
info "Triggering OpenSearch re-index via port-forward..."
REINDEX=$(curl -sf \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -X POST "http://localhost:8004/api/v1/admin/reindex" 2>/dev/null \
  || echo '{"note":"reindex skipped or unavailable"}')
echo "${REINDEX}" | jq . 2>/dev/null || echo "${REINDEX}"
success "Re-index triggered"

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN} Kubernetes seed complete${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  Namespace : ${NAMESPACE}"
echo "  Cluster   : $($KUBECTL config current-context 2>/dev/null || echo 'unknown')"
echo ""
echo "  Access URLs (add these to /etc/hosts if not already present):"
NODE_IP=$($KUBECTL get nodes \
  -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' \
  2>/dev/null || echo "<node-ip>")
echo "    ${NODE_IP}  catalog.local"
echo "    ${NODE_IP}  manage.catalog.local"
echo "    ${NODE_IP}  api.catalog.local"
echo ""
echo "  Try it:"
echo "    Search UI  → http://catalog.local"
echo "    Manage UI  → http://manage.catalog.local"
echo "    Search API → curl 'http://api.catalog.local/search/api/v1/search?q=trades'"
echo ""
