#!/usr/bin/env bash
# Deploy ODIN Data Catalog to a local MicroK8s cluster.
#
# Usage:
#   ./infra/microk8s/deploy.sh [--reduced-resources] [--upgrade] [--dry-run]
#
# Flags:
#   --reduced-resources   Layer infra/microk8s/reduced-resources.yaml (for < 16 GB RAM)
#   --upgrade             Run `helm upgrade` instead of `helm install`
#   --dry-run             Pass --dry-run to Helm (renders manifests without applying)
#
# Prerequisites:
#   - MicroK8s installed with addons: dns storage ingress registry
#   - All service images pushed to localhost:32000 (see docs/microk8s-deployment.md §3)
#   - Helm 3 available as `helm` or `microk8s helm3`
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CHART="$REPO_ROOT/infra/helm/charts/odin-catalog"
NAMESPACE="odin-catalog"
RELEASE="odin"
EXTRA_VALUES=""
HELM_CMD="helm"
ACTION="install"
DRY_RUN=""

# ─── Parse flags ──────────────────────────────────────────────────────────────
for arg in "$@"; do
  case $arg in
    --reduced-resources) EXTRA_VALUES="-f $SCRIPT_DIR/reduced-resources.yaml" ;;
    --upgrade)           ACTION="upgrade" ;;
    --dry-run)           DRY_RUN="--dry-run" ;;
    *) echo "Unknown flag: $arg"; exit 1 ;;
  esac
done

# ─── Prefer microk8s helm3 if system helm is absent ───────────────────────────
if ! command -v helm &>/dev/null; then
  if command -v microk8s &>/dev/null; then
    HELM_CMD="microk8s helm3"
  else
    echo "ERROR: helm (or microk8s helm3) not found." >&2
    exit 1
  fi
fi

# ─── Verify cluster reachability ──────────────────────────────────────────────
echo "==> Checking cluster..."
if command -v microk8s &>/dev/null; then
  microk8s status --wait-ready
else
  kubectl cluster-info --request-timeout=10s
fi

# ─── Ensure namespace exists with correct pod-security labels ─────────────────
echo "==> Ensuring namespace $NAMESPACE..."
kubectl get namespace "$NAMESPACE" &>/dev/null || kubectl create namespace "$NAMESPACE"

kubectl label namespace "$NAMESPACE" \
  pod-security.kubernetes.io/enforce=privileged \
  pod-security.kubernetes.io/warn=privileged \
  --overwrite

# ─── Run Helm ────────────────────────────────────────────────────────────────
echo "==> Running: helm $ACTION $RELEASE ..."

# shellcheck disable=SC2086
$HELM_CMD $ACTION "$RELEASE" "$CHART" \
  --namespace "$NAMESPACE" \
  -f "$SCRIPT_DIR/values.yaml" \
  $EXTRA_VALUES \
  $DRY_RUN \
  --timeout 10m \
  --wait

# ─── Post-install: print access information ───────────────────────────────────
if [[ -z "$DRY_RUN" ]]; then
  NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')

  echo ""
  echo "==> Deployment complete."
  echo ""
  echo "    Add these entries to /etc/hosts if not already present:"
  echo "      $NODE_IP  catalog.local"
  echo "      $NODE_IP  manage.catalog.local"
  echo "      $NODE_IP  api.catalog.local"
  echo ""
  echo "    Access URLs:"
  echo "      Consumer UI   http://catalog.local"
  echo "      Producer UI   http://manage.catalog.local"
  echo "      API gateway   http://api.catalog.local/inventory/actuator/health"
  echo ""
  echo "    Keycloak admin (port-forward):"
  echo "      kubectl -n $NAMESPACE port-forward svc/$RELEASE-catalog-keycloak 8180:8180"
  echo "      open http://localhost:8180  (admin / admin)"
  echo ""
fi
