<p align="center">
  <img src="odin-catalog-logo-light.png" width="220" alt="ODIN Catalog" />
</p>

<h1 align="center">ODIN Catalog</h1>

<p align="center">
  Open-source data catalog built on W3C/OMG standards — DCAT 3.0, DPROD, CSV-W, OpenLineage, FIBO, and SKOS.
</p>

---

## Overview

ODIN Catalog is a metadata platform for discovering, governing, and understanding data assets across your organisation. It provides a three-tier conceptual → logical → physical metamodel:

- **Conceptual** — Data Products and Ports (DPROD)
- **Logical** — Datasets, Logical Models, and Vocabulary Mappings (DCAT + SKOS + FIBO)
- **Physical** — Distributions and harvested schemas (CSV-W + AWS Glue + Snowflake + Teradata)

Lineage is tracked using the OpenLineage standard via an Apache AGE graph database (Cypher over PostgreSQL).

---

## Key Capabilities

- **Role-based access** — the producer UI is protected by Keycloak OIDC login with four defined roles (Administrator, Data Governance, Data Owner, Data Steward) that gate both UI navigation and backend permissions.
- **Ownership governance** — datasets carry an owner, an ownership-transfer proposal workflow (propose → approve/reject), and an immutable audit history of every change.
- **AI-assisted semantics** — RAG chat over your metadata, AI element classification (suggests vocabulary concepts for logical elements), and dataset-level semantic-context recommendations.
- **Dataset semantic types** — domain types (e.g. `Customer`, `DebitCardAccount`) are derived from FIBO/schema.org vocabulary mappings, exposed as search facets and surfaced as badges in the consumer UI.
- **Human-readable metadata** — an IRI→label translation API renders controlled-vocabulary IRIs as friendly labels throughout the UI.

---

## Architecture

Six Spring Boot 3.3 / Java 21 microservices, each with its own database:

| Service | Port | Database | Responsibility |
|---------|------|----------|---------------|
| `inventory-service` | 8001 | PostgreSQL | Catalogs, Datasets, Distributions, Data Products, Logical Models, Vocabularies, Semantic Tags, Ownership Governance |
| `harvest-service` | 8002 | PostgreSQL + MinIO | Connector pipeline — DCAT HTTP, AWS Glue, Snowflake, Teradata |
| `lineage-service` | 8003 | PostgreSQL + Apache AGE | OpenLineage ingestion, DDL lineage, Cypher graph traversal |
| `search-service` | 8004 | OpenSearch | Full-text search, semantic type facets, FIBO concept facets |
| `ai-service` | 8005 | PostgreSQL + pgvector | RAG chat, semantic recommendations, element classification, embedding pipeline (Ollama / OpenAI) |
| `identity-service` | 8006 | PostgreSQL | Organisations, domains, users, roles, ABAC policies, API keys |

Two React 18 + TypeScript + Vite frontends:

| App | Port | Audience |
|-----|------|----------|
| `consumer` | 3001 | Analysts and data consumers — zero-navigation search and discovery |
| `producer` | 3000 | Data owners and stewards — data product management and governance |

Traefik routes `catalog.local/` → consumer and `manage.catalog.local/` → producer.

---

## Quick Start

**Prerequisites:** Docker 24+, Docker Compose v2, Java 21, Node 20.

```bash
# Clone
git clone https://github.com/odin-catalog/odin.git
cd odin-catalog

# Copy environment template
cp .env.example .env

# Start the full stack (infrastructure + all services)
make up

# Or with Ollama for local AI (requires ~8 GB RAM)
make up-ai
```

The stack takes about 60 seconds to reach healthy status. Check with:

```bash
docker compose ps
```

Once up:

| URL | What |
|-----|------|
| `http://localhost:3001` | Consumer (discovery) UI |
| `http://localhost:3000` | Producer (management) UI |
| `http://localhost:8001/swagger-ui.html` | inventory-service API docs |
| `http://localhost:8002/swagger-ui.html` | harvest-service API docs |
| `http://localhost:8003/swagger-ui.html` | lineage-service API docs |
| `http://localhost:8004/swagger-ui.html` | search-service API docs |
| `http://localhost:8005/swagger-ui.html` | ai-service API docs |
| `http://localhost:8006/swagger-ui.html` | identity-service API docs |
| `http://localhost:8180` | Keycloak admin console |
| `http://localhost:9000` | MinIO console |

---

## Development

### Backend (Java / Gradle)

```bash
# Build all services (skip tests)
make build-backend

# Run all tests
make test

# Test a single service
make test-svc svc=inventory-service

# Build and restart a service after code changes
docker compose build inventory-service && docker compose up -d inventory-service
```

### Frontend

```bash
# Install dependencies
cd frontend && npm install

# Run consumer dev server (hot reload, port 3001)
make dev-consumer

# Run producer dev server (hot reload, port 3000)
make dev-producer

# Build both apps for production
make build-frontend
```

### Database migrations

Flyway migrations run automatically on service startup. To run them manually:

```bash
make migrate
```

### Seed data

```bash
# Seed system vocabularies (schema.org, FIBO, SKOS)
make seed-vocab

# Load the Meridian Capital sample dataset (investment bank scenario)
make seed
```

---

## Standards

| Standard | Role |
|----------|------|
| [DCAT 3.0](https://www.w3.org/TR/vocab-dcat-3/) | Dataset and Distribution metadata; catalog export as JSON-LD |
| [DPROD](https://www.w3.org/TR/dprod/) | Data Product, Port, and DataService model |
| [CSV-W](https://www.w3.org/TR/tabular-data-primer/) | Physical schema descriptor (columns, datatypes, constraints) |
| [OpenLineage](https://openlineage.io/) | Job/Run/Dataset lineage events; compatible with Marquez and dbt |
| [SKOS](https://www.w3.org/TR/skos-reference/) | Vocabulary mapping properties (exactMatch, closeMatch, …) |
| [FIBO](https://spec.edmcouncil.org/fibo/) | Financial industry business ontology for semantic annotation |
| [schema.org](https://schema.org/) | General-purpose vocabulary for non-financial datasets |

---

## Kubernetes / Helm Deployment

A single Helm chart covers the full stack. Requires Helm 3.x and a running Kubernetes cluster.

### Install

```bash
# Add to your cluster in a dedicated namespace
helm install odin infra/helm/charts/odin-catalog/ \
  --namespace odin-catalog \
  --create-namespace

# Watch pods come up
kubectl get pods -n odin-catalog -w
```

Two post-install Jobs run automatically:

- **kafka-init** — creates all 10 Kafka topics with correct partition counts and cleanup policies
- **opensearch-init** — creates the `catalog_entities` index with the English analyzer mapping

Both Jobs complete in under 30 seconds and are deleted on success.

### Configure

Override values with `--set` or a values file:

```bash
# Minimal production overrides
helm install odin infra/helm/charts/odin-catalog/ \
  --namespace odin-catalog --create-namespace \
  --set postgres.inventory.password=<secret> \
  --set postgres.harvest.password=<secret> \
  --set postgres.lineage.password=<secret> \
  --set postgres.identity.password=<secret> \
  --set postgres.ai.password=<secret> \
  --set keycloak.adminPassword=<secret> \
  --set keycloak.clientSecret=<secret> \
  --set minio.rootPassword=<secret> \
  --set ingress.consumerHost=catalog.example.com \
  --set ingress.producerHost=manage.catalog.example.com \
  --set ingress.apiHost=api.catalog.example.com
```

Or use a values file:

```bash
cp infra/helm/charts/odin-catalog/values.yaml my-values.yaml
# edit my-values.yaml
helm install odin infra/helm/charts/odin-catalog/ -f my-values.yaml -n odin-catalog --create-namespace
```

### Ingress

The chart creates three Ingress resources (requires an nginx ingress controller):

| Ingress | Default host | Routes to |
|---------|-------------|-----------|
| consumer | `catalog.local` | Consumer (discovery) frontend |
| producer | `manage.catalog.local` | Producer (management) frontend |
| api | `api.catalog.local` | API gateway — `/inventory/`, `/harvest/`, `/lineage/`, `/search/`, `/ai/`, `/identity/` |

Disable ingress with `--set ingress.enabled=false` and use `kubectl port-forward` instead.

### Upgrade and uninstall

```bash
# Apply chart changes or value updates
helm upgrade odin infra/helm/charts/odin-catalog/ -n odin-catalog -f my-values.yaml

# Remove all resources (PVCs are retained by default)
helm uninstall odin -n odin-catalog
```

### Resource customisation

Default resource requests are conservative (250 m CPU / 512 Mi for backends). Override per environment:

```yaml
# values-prod.yaml
resources:
  backend:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "2",  memory: 2Gi }
  opensearch:
    requests: { cpu: "1",  memory: 2Gi }
    limits:   { cpu: "2",  memory: 4Gi }
  kafka:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1",  memory: 2Gi }
```

---

## Monorepo Layout

```
data-catalog/
├── services/
│   ├── inventory-service/    # Catalog, Dataset, DataProduct, Logical Model APIs
│   ├── harvest-service/      # Connectors, Spring Batch pipeline, Quartz scheduler
│   ├── lineage-service/      # OpenLineage ingestion, Apache AGE graph
│   ├── search-service/       # OpenSearch indexing and query
│   ├── ai-service/           # RAG chat, embeddings, semantic search
│   └── identity-service/     # Users, roles, ABAC, API keys, Keycloak integration
├── shared/
│   ├── shared-models/        # Java records for DCAT, DPROD, CSV-W, Kafka envelopes
│   ├── kafka-client/         # KafkaEventPublisher, KafkaEventConsumer, topic constants
│   └── auth-common/          # Spring Security JWT filter, @RequiresPermission
├── frontend/
│   ├── consumer/             # Discovery app (port 3001)
│   ├── producer/             # Management app (port 3000)
│   └── shared/               # TypeScript types and typed API clients
├── infra/
│   ├── traefik/              # Traefik routing config
│   ├── kafka/                # Topic definitions
│   ├── keycloak/             # Realm export
│   ├── opensearch/           # Index mappings
│   └── helm/charts/odin-catalog/  # Helm chart (52 Kubernetes resources)
├── marketing/                # Marketing landing page
├── docker-compose.yml
├── Makefile
└── gradle/libs.versions.toml # Gradle version catalog
```

---

## Authentication & Access Control

### Producer UI login

The producer (management) UI is protected by **Keycloak OIDC login** — opening it redirects to
the Keycloak login page (`login-required`). It authenticates against the `catalog-frontend`
public PKCE client in the `datacatalog` realm. The issued Bearer token carries `tenant_id` and
`permissions` claims, which drive multi-tenancy and authorization on every backend call. The
consumer (discovery) UI is read-only and unauthenticated.

### Backend authentication

All backend endpoints accept either:

- `Authorization: Bearer <JWT>` — Keycloak-issued OIDC token
- `X-API-Key: <key>` — long-lived API key (managed by identity-service)

JWTs are validated against Keycloak's JWKS (`jwk-set-uri`), so a token works regardless of the
issuer hostname (e.g. `localhost` in the browser vs. `keycloak` inside the Docker network).
Authorization is enforced from the token's `permissions` claim: `GET` requests need
`catalog:read`; mutations need `catalog:write`; `catalog:admin` grants both.

### Roles

| Role (Keycloak) | `permissions` claim | Producer UI access |
|-----------------|---------------------|--------------------|
| Administrator (`administrator`) | `catalog:read`, `catalog:write`, `catalog:admin` | Everything, incl. Admin › Harvest, Domains, Users, Settings |
| Data Governance (`data-governance`) | `catalog:read`, `catalog:write` | All content + Admin › Domains |
| Data Owner (`data-owner`) | `catalog:read`, `catalog:write` | All content; no Admin section |
| Data Steward (`data-steward`) | `catalog:read`, `catalog:write` | All content; no Admin section |

### Default users

The realm seeds one account per role plus five additional data owners for development and testing (change these before any non-local deployment):

| Username | Name | Role | Password |
|----------|------|------|----------|
| `admin@datacatalog.local` | Catalog Admin | Administrator | `admin` |
| `governance@datacatalog.local` | Grace Governance | Data Governance | `password` |
| `owner@datacatalog.local` | Owen Owner | Data Owner | `password` |
| `steward@datacatalog.local` | Sam Steward | Data Steward | `password` |
| `trading.owner@datacatalog.local` | Alice Chen | Data Owner | `password` |
| `risk.owner@datacatalog.local` | Marcus Webb | Data Owner | `password` |
| `refdata.owner@datacatalog.local` | Priya Nair | Data Owner | `password` |
| `compliance.owner@datacatalog.local` | David Park | Data Owner | `password` |
| `finance.owner@datacatalog.local` | Sofia Reyes | Data Owner | `password` |

Manage users via the producer **Admin › Users** page (administrator only) or the identity-service
endpoints (backed by the Keycloak Admin API) — invite, list, and deactivate.

### Local development

For local development you can bypass JWT validation with a dev API key — any key starting with
`dev-` (e.g. `X-API-Key: dev-local`) is accepted and granted read/write/admin on the default
tenant. **Never enable this path outside local development.**

---

## Infrastructure Services

| Service | Port | Purpose |
|---------|------|---------|
| postgres-inventory | 5433 | inventory-service DB |
| postgres-harvest | 5434 | harvest-service DB |
| postgres-lineage | 5435 | lineage-service DB (Apache AGE) |
| postgres-identity | 5436 | identity-service DB |
| postgres-ai | 5437 | ai-service DB (pgvector) |
| Kafka (KRaft) | 9092 | Event bus |
| Apicurio Registry | 8085 | Kafka schema registry |
| OpenSearch | 9200 | Search engine |
| MinIO | 9000 | Object store (harvest snapshots, DDL files) |
| Redis | 6379 | Quartz scheduler lock store |
| Keycloak | 8180 | Identity provider (OAuth2/OIDC) |
| Traefik | 80 | API gateway and routing |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
