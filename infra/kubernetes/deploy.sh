#!/usr/bin/env bash
# Deploy ODIN Data Catalog to Kubernetes using raw manifests (no Helm).
#
# Usage:
#   IMAGE_REGISTRY=localhost:32000/ ./infra/kubernetes/deploy.sh [--dry-run] [--delete]
#
# Environment variables:
#   IMAGE_REGISTRY   Registry prefix for ODIN service images, with trailing slash.
#                    Default: empty (images are pulled as odin/<service>:latest).
#                    MicroK8s: IMAGE_REGISTRY=localhost:32000/
#                    Example:  IMAGE_REGISTRY=registry.example.com/myorg/
#
# Flags:
#   --dry-run   Print rendered manifests without applying them
#   --delete    Tear down the deployment (does NOT delete PVCs)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DRY_RUN=false
DELETE=false
export IMAGE_REGISTRY="${IMAGE_REGISTRY:-}"

# ─── Parse flags ──────────────────────────────────────────────────────────────
for arg in "$@"; do
  case $arg in
    --dry-run) DRY_RUN=true ;;
    --delete)  DELETE=true ;;
    *) echo "Unknown flag: $arg"; exit 1 ;;
  esac
done

# ─── Helpers ──────────────────────────────────────────────────────────────────
kubectl_apply() {
  local file=$1
  if $DRY_RUN; then
    echo "--- [dry-run] $file ---"
    envsubst < "$file"
  else
    envsubst < "$file" | kubectl apply -f -
  fi
}

kubectl_delete() {
  local file=$1
  envsubst < "$file" | kubectl delete --ignore-not-found -f -
}

# ─── Delete mode ──────────────────────────────────────────────────────────────
if $DELETE; then
  echo "==> Deleting ODIN Data Catalog resources (PVCs preserved)..."
  for f in $(ls "$SCRIPT_DIR"/*.yaml | sort -r); do
    kubectl_delete "$f"
  done
  echo ""
  echo "Resources deleted. PVCs in namespace odin-catalog were NOT removed."
  echo "To also remove all data: kubectl delete namespace odin-catalog"
  exit 0
fi

# ─── Pre-flight checks ────────────────────────────────────────────────────────
echo "==> Checking cluster connectivity..."
kubectl cluster-info --request-timeout=10s

if [[ -n "$IMAGE_REGISTRY" ]]; then
  echo "==> Using image registry: ${IMAGE_REGISTRY}"
else
  echo "==> IMAGE_REGISTRY not set — images will be pulled as odin/<service>:latest"
fi

# ─── Apply manifests in dependency order ──────────────────────────────────────
MANIFESTS=(
  00-namespace.yaml
  01-serviceaccount.yaml
  02-secrets.yaml
  03-configmaps.yaml
  10-postgres.yaml
  11-kafka.yaml
  12-opensearch.yaml
  13-minio.yaml
  14-redis.yaml
  15-keycloak.yaml
  20-backend-services.yaml
  21-frontends.yaml
  22-ingress.yaml
  30-jobs.yaml
)

for manifest in "${MANIFESTS[@]}"; do
  echo "==> Applying $manifest..."
  kubectl_apply "$SCRIPT_DIR/$manifest"
done

if $DRY_RUN; then
  echo ""
  echo "[dry-run] No resources were applied."
  exit 0
fi

# ─── Wait for infrastructure to be ready ─────────────────────────────────────
echo ""
echo "==> Waiting for StatefulSets to become ready..."
for sts in kafka opensearch postgres-inventory postgres-harvest postgres-lineage postgres-identity postgres-ai; do
  kubectl -n odin-catalog rollout status statefulset/odin-catalog-$sts --timeout=300s
done

echo ""
echo "==> Waiting for infrastructure Deployments to become ready..."
for dep in minio redis keycloak; do
  kubectl -n odin-catalog rollout status deployment/odin-catalog-$dep --timeout=300s
done

echo ""
echo "==> Waiting for Jobs to complete (Kafka topics + OpenSearch index)..."
kubectl -n odin-catalog wait job/odin-catalog-kafka-init    --for=condition=complete --timeout=120s
kubectl -n odin-catalog wait job/odin-catalog-opensearch-init --for=condition=complete --timeout=120s

echo ""
echo "==> Waiting for backend service Deployments..."
for svc in inventory-service harvest-service lineage-service search-service ai-service identity-service; do
  kubectl -n odin-catalog rollout status deployment/odin-catalog-$svc --timeout=300s
done

echo ""
echo "==> Waiting for frontend Deployments..."
for fe in frontend-producer frontend-consumer; do
  kubectl -n odin-catalog rollout status deployment/odin-catalog-$fe --timeout=120s
done

# ─── Summary ─────────────────────────────────────────────────────────────────
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null || echo "<node-ip>")

echo ""
echo "==> Deployment complete."
echo ""
echo "    Add these lines to /etc/hosts if not already present:"
echo "      $NODE_IP  catalog.local"
echo "      $NODE_IP  manage.catalog.local"
echo "      $NODE_IP  api.catalog.local"
echo ""
echo "    Access URLs:"
echo "      Consumer UI   http://catalog.local"
echo "      Producer UI   http://manage.catalog.local"
echo "      API health    http://api.catalog.local/inventory/actuator/health"
echo ""
echo "    Keycloak admin (port-forward):"
echo "      kubectl -n odin-catalog port-forward svc/odin-catalog-keycloak 8180:8180"
echo "      http://localhost:8180  —  admin / admin"
echo ""
