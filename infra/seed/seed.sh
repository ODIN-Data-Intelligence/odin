#!/usr/bin/env bash
# ODIN Catalog — rich sample data loader (idempotent)
# Scenario: fictional investment bank "Meridian Capital" with capital markets data
# across Trading, Risk, Reference Data, and Compliance domains.
#
# Usage:
#   ./infra/seed/seed.sh               # seeds against localhost
#   BASE_URL=http://api.catalog.local ./infra/seed/seed.sh
#
# Prerequisites: curl, jq
# Idempotent: safe to run multiple times — existing records are reused.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
CATALOG_URL="${BASE_URL}:8001"
HARVEST_URL="${BASE_URL}:8002"
LINEAGE_URL="${BASE_URL}:8003"
API_KEY="${API_KEY:-dev-local}"

HEADER_AUTH="X-API-Key: ${API_KEY}"
HEADER_JSON="Content-Type: application/json"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()    { echo -e "${BLUE}[seed]${NC} $*"; }
success() { echo -e "${GREEN}[done]${NC} $*"; }
section() { echo -e "\n${YELLOW}━━━ $* ━━━${NC}"; }

# ── HTTP helpers ──────────────────────────────────────────────────────────────

post() {
  local url="$1" body="$2"
  curl -sf -H "${HEADER_AUTH}" -H "${HEADER_JSON}" -X POST "${url}" -d "${body}"
}

urlencode() { printf '%s' "$1" | jq -Rr @uri; }

# Find first matching .id in a JSON array where field==value
find_by() {
  local arr="$1" field="$2" value="$3"
  echo "${arr}" | jq -r --arg f "${field}" --arg v "${value}" \
    'if type == "array" then .[] else .content[] end | select(.[$f] == $v) | .id' \
    2>/dev/null | head -1
}

# ── Get-or-create helpers ─────────────────────────────────────────────────────

get_or_create_catalog() {
  local title="$1" body="$2"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/catalogs")
  existing=$(find_by "${list}" "title" "${title}")
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${CATALOG_URL}/api/v1/catalogs" "${body}" | jq -r '.id'
}

get_or_create_dataset() {
  local body="$1"
  local source_uri existing
  source_uri=$(echo "${body}" | jq -r '.sourceUri // empty')
  if [ -n "${source_uri}" ]; then
    existing=$(curl -sf -H "${HEADER_AUTH}" \
      "${CATALOG_URL}/api/v1/datasets?sourceUri=$(urlencode "${source_uri}")" \
      | jq -r '.content[0].id // empty')
    [ -n "${existing}" ] && { echo "${existing}"; return; }
  fi
  post "${CATALOG_URL}/api/v1/datasets" "${body}" | jq -r '.id'
}

get_or_create_data_product() {
  local title="$1" body="$2"
  local page existing
  page=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/data-products?size=100")
  existing=$(find_by "${page}" "title" "${title}")
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${CATALOG_URL}/api/v1/data-products" "${body}" | jq -r '.id'
}

get_or_create_harvest_source() {
  local name="$1" body="$2"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${HARVEST_URL}/api/v1/sources")
  existing=$(find_by "${list}" "name" "${name}")
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${HARVEST_URL}/api/v1/sources" "${body}" | jq -r '.id'
}

get_or_create_harvest_job() {
  local name="$1" body="$2"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${HARVEST_URL}/api/v1/jobs")
  existing=$(find_by "${list}" "name" "${name}")
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${HARVEST_URL}/api/v1/jobs" "${body}" | jq -r '.id'
}

get_or_create_logical_model() {
  local ds_id="$1" name="$2" body="$3"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/datasets/${ds_id}/logical-models")
  existing=$(find_by "${list}" "name" "${name}")
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${CATALOG_URL}/api/v1/datasets/${ds_id}/logical-models" "${body}" | jq -r '.id'
}

get_or_create_element() {
  local lm="$1" name="$2" label="$3" type="$4" ordinal="$5" required="$6" identifier="$7"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/logical-models/${lm}/elements")
  existing=$(find_by "${list}" "name" "${name}")
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${CATALOG_URL}/api/v1/logical-models/${lm}/elements" \
    "$(jq -n \
      --arg n "${name}" --arg l "${label}" --arg t "${type}" \
      --argjson o "${ordinal}" --argjson r "${required}" --argjson i "${identifier}" \
      '{name:$n,label:$l,logicalType:$t,ordinal:$o,isRequired:$r,isIdentifier:$i}')" \
    | jq -r '.id'
}

# ── Conditional-add helpers (skip if already present) ─────────────────────────

get_or_create_distribution() {
  local ds_id="$1" url_field="$2" url_val="$3" body="$4"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/datasets/${ds_id}/distributions")
  existing=$(echo "${list}" | jq -r --arg f "${url_field}" --arg v "${url_val}" \
    '.[] | select(.[$f] == $v) | .id' | head -1)
  [ -n "${existing}" ] && { echo "${existing}"; return; }
  post "${CATALOG_URL}/api/v1/datasets/${ds_id}/distributions" "${body}" | jq -r '.id'
}

add_vocab_profile_if_missing() {
  local ds_id="$1" vocab_id="$2" body="$3"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/datasets/${ds_id}/vocabulary-profiles")
  existing=$(echo "${list}" | jq -r --arg v "${vocab_id}" '.[] | select(.vocabularyId == $v) | .id' | head -1)
  [ -n "${existing}" ] && return
  post "${CATALOG_URL}/api/v1/datasets/${ds_id}/vocabulary-profiles" "${body}" > /dev/null
}

add_vocab_mapping_if_missing() {
  local elem="$1" vocab="$2" iri="$3" label="$4" match="${5:-exactMatch}"
  local list existing
  list=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/logical-data-elements/${elem}/vocab-mappings")
  existing=$(echo "${list}" | jq -r --arg iri "${iri}" '.[] | select(.conceptIri == $iri) | .id' | head -1)
  [ -n "${existing}" ] && return
  post "${CATALOG_URL}/api/v1/logical-data-elements/${elem}/vocab-mappings" \
    "$(jq -n \
      --arg v "${vocab}" --arg i "${iri}" --arg l "${label}" --arg m "${match}" \
      '{vocabularyId:$v,conceptIri:$i,conceptLabel:$l,matchType:$m}')" > /dev/null
}

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 1 — Fetch vocabulary IDs"
# ─────────────────────────────────────────────────────────────────────────────

VOCABS=$(curl -sf -H "${HEADER_AUTH}" "${CATALOG_URL}/api/v1/vocabularies")
VOCAB_SCHEMA=$(echo "${VOCABS}" | jq -r '.[] | select(.prefix=="schema") | .id')
VOCAB_FND=$(echo "${VOCABS}"   | jq -r '.[] | select(.prefix=="fibo-fnd") | .id')
VOCAB_FBC=$(echo "${VOCABS}"   | jq -r '.[] | select(.prefix=="fibo-fbc") | .id')
VOCAB_SEC=$(echo "${VOCABS}"   | jq -r '.[] | select(.prefix=="fibo-sec") | .id')
VOCAB_MD=$(echo "${VOCABS}"    | jq -r '.[] | select(.prefix=="fibo-md") | .id')
VOCAB_SKOS=$(echo "${VOCABS}"  | jq -r '.[] | select(.prefix=="skos") | .id')

info "schema.org : ${VOCAB_SCHEMA}"
info "FIBO FND   : ${VOCAB_FND}"
info "FIBO FBC   : ${VOCAB_FBC}"
info "FIBO SEC   : ${VOCAB_SEC}"
info "FIBO MD    : ${VOCAB_MD}"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 2 — Catalogs"
# ─────────────────────────────────────────────────────────────────────────────

info "Creating catalogs (idempotent by title)..."

CAT_TRADING=$(get_or_create_catalog "Trading Operations Catalog" '{
  "title": "Trading Operations Catalog",
  "description": "Authoritative catalog for all trading desks — equities, fixed income, derivatives, and FX. Contains executed trades, order books, position blotters, and P&L datasets produced by the trade lifecycle system.",
  "keywords": ["trading","equities","derivatives","fixed-income","fx"],
  "themes": ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  "language": ["en"],
  "license": "https://creativecommons.org/licenses/by/4.0/"
}')
success "Trading Operations Catalog: ${CAT_TRADING}"

CAT_RISK=$(get_or_create_catalog "Risk & Analytics Catalog" '{
  "title": "Risk & Analytics Catalog",
  "description": "Market risk, credit risk, and counterparty exposure datasets. Feeds regulatory capital calculations (Basel III/IV), VaR models, and stress-testing frameworks used by Risk Management.",
  "keywords": ["risk","var","basel","credit","market-risk","stress-testing"],
  "themes": ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  "language": ["en"]
}')
success "Risk & Analytics Catalog: ${CAT_RISK}"

CAT_REFDATA=$(get_or_create_catalog "Reference Data Catalog" '{
  "title": "Reference Data Catalog",
  "description": "Golden source for securities master, counterparty directories, currency pairs, and market calendars. All downstream systems join against these reference datasets.",
  "keywords": ["reference-data","securities","counterparty","currency","calendar"],
  "themes": ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  "language": ["en"]
}')
success "Reference Data Catalog: ${CAT_REFDATA}"

CAT_COMPLIANCE=$(get_or_create_catalog "Compliance & Regulatory Catalog" '{
  "title": "Compliance & Regulatory Catalog",
  "description": "Regulatory reporting outputs: MiFID II transaction reports, EMIR derivatives reporting, FinRep consolidated financial statements. Controlled access for compliance officers and regulators.",
  "keywords": ["compliance","mifid","emir","finrep","regulatory","reporting"],
  "themes": ["http://publications.europa.eu/resource/authority/data-theme/GOVE"],
  "language": ["en"],
  "license": "https://rightsstatements.org/vocab/InC/1.0/"
}')
success "Compliance & Regulatory Catalog: ${CAT_COMPLIANCE}"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 3 — Datasets"
# ─────────────────────────────────────────────────────────────────────────────

info "Creating datasets (idempotent by sourceUri)..."

DS_TRADES=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_TRADING}" '{
  title: "Executed Trades",
  description: "Complete record of every executed trade across all asset classes. Each row represents a single trade leg with counterparty, instrument, notional, price, and settlement details. Primary source of truth for all downstream risk and compliance feeds.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["trades","executions","otc","listed","equity","fixed-income","fx"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  license: "https://rightsstatements.org/vocab/InC/1.0/",
  version: "4.2.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/TRADING/TRADE_CAPTURE/EXECUTED_TRADES"
}')")
success "Executed Trades: ${DS_TRADES}"

DS_POSITIONS=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_TRADING}" '{
  title: "Daily Position Blotter",
  description: "End-of-day (EOD) consolidated positions per portfolio, instrument, and book. Aggregated from executed trades plus corporate actions. Feeds P&L attribution, risk engine, and regulatory capital calculations.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["positions","eod","blotter","portfolio","book"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "3.1.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/TRADING/POSITIONS/DAILY_BLOTTER"
}')")
success "Daily Position Blotter: ${DS_POSITIONS}"

DS_ORDERBOOK=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_TRADING}" '{
  title: "Intraday Order Book",
  description: "Real-time order book snapshots capturing all orders (pending, partial fills, cancelled, rejected) across all trading venues. Retained for 90 days for MiFID II best execution analysis.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/continuous",
  keywords: ["orders","order-book","intraday","best-execution","mifid"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "2.0.1",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/TRADING/ORDER_MANAGEMENT/INTRADAY_ORDER_BOOK"
}')")
success "Intraday Order Book: ${DS_ORDERBOOK}"

DS_VAR=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_RISK}" '{
  title: "Market Risk VaR",
  description: "Daily Value-at-Risk (VaR) estimates computed at 99% confidence interval using historical simulation (250-day window) at portfolio, desk, and firm level. Includes stressed VaR (sVaR) for Basel III capital requirements.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["var","svar","market-risk","basel","capital","confidence-interval"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "5.0.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/RISK/MARKET_RISK/VAR_DAILY"
}')")
success "Market Risk VaR: ${DS_VAR}"

DS_COUNTERPARTY=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_RISK}" '{
  title: "Counterparty Credit Exposure",
  description: "Current and potential future exposure (PFE) per counterparty and netting set. Includes credit valuation adjustment (CVA), wrong-way risk indicators, and ISDA master agreement references.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["credit-risk","cva","pfe","exposure","counterparty","netting","isda"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "2.3.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/RISK/CREDIT_RISK/COUNTERPARTY_EXPOSURE"
}')")
success "Counterparty Credit Exposure: ${DS_COUNTERPARTY}"

DS_RISK_METRICS=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_RISK}" '{
  title: "Aggregated Market Risk Metrics",
  description: "Consolidated risk metrics aggregated from position blotter and market data: DV01, CS01, vega, delta, gamma per instrument class and desk. Input to daily risk reports distributed to the CRO.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["risk-metrics","dv01","delta","gamma","vega","greeks"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "1.8.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/RISK/MARKET_RISK/AGGREGATED_METRICS"
}')")
success "Aggregated Market Risk Metrics: ${DS_RISK_METRICS}"

DS_SECURITIES=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_REFDATA}" '{
  title: "Securities Master",
  description: "Golden source security reference data: ISIN, CUSIP, SEDOL, Bloomberg ticker, Reuters RIC, asset class, currency, country of risk, exchange listing, maturity, coupon. Sourced from Bloomberg BDS and Refinitiv. Updated daily.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["securities","isin","cusip","sedol","bloomberg","reference-data","instrument"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "12.0.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/REFDATA/SECURITIES/MASTER"
}')")
success "Securities Master: ${DS_SECURITIES}"

DS_COUNTERPARTY_DIR=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_REFDATA}" '{
  title: "Counterparty Directory",
  description: "Directory of all trading counterparties: legal entity identifier (LEI), BIC, SWIFT codes, jurisdiction, credit rating, and ISDA agreement status. Source of truth for counterparty onboarding and KYC.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/weekly",
  keywords: ["counterparty","lei","bic","swift","kyc","legal-entity"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "8.1.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/REFDATA/COUNTERPARTY/DIRECTORY"
}')")
success "Counterparty Directory: ${DS_COUNTERPARTY_DIR}"

DS_FX_RATES=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_REFDATA}" '{
  title: "FX Reference Rates",
  description: "Official end-of-day FX rates for 150+ currency pairs sourced from ECB, Bank of England, and FX fixing providers (WM/Reuters). Used for cross-currency revaluation, P&L attribution, and regulatory reporting.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["fx","currency","exchange-rate","ecb","wmreuters","fixing"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/ECON"],
  language: ["en"],
  version: "3.0.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/REFDATA/CALENDAR/FX_RATES"
}')")
success "FX Reference Rates: ${DS_FX_RATES}"

DS_MIFID=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_COMPLIANCE}" '{
  title: "MiFID II Transaction Reports",
  description: "Daily transaction reporting file submitted to the FCA/ESMA under MiFID II Article 26. Contains all reportable transactions in financial instruments: instrument details, counterparty, price, quantity, venue, and execution timestamp.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["mifid","transaction-reporting","fca","esma","regulatory","article26"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/GOVE"],
  language: ["en"],
  version: "2.1.0",
  license: "https://rightsstatements.org/vocab/InC/1.0/",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/COMPLIANCE/REPORTING/MIFID_II_REPORTS"
}')")
success "MiFID II Transaction Reports: ${DS_MIFID}"

DS_FINREP=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_COMPLIANCE}" '{
  title: "FinRep Consolidated Financial Statements",
  description: "EBA FinRep regulatory reporting templates (F01–F47) submitted quarterly to the PRA. Derived from general ledger and position data. Covers income statement, balance sheet, and off-balance-sheet items under IFRS 9.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/quarterly",
  keywords: ["finrep","eba","pra","ifrs9","balance-sheet","regulatory","quarterly"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/GOVE"],
  language: ["en"],
  version: "3.3.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/COMPLIANCE/REPORTING/FINREP_CONSOLIDATED"
}')")
success "FinRep Consolidated Financial Statements: ${DS_FINREP}"

DS_EMIR=$(get_or_create_dataset "$(jq -n --arg cat "${CAT_COMPLIANCE}" '{
  title: "EMIR Derivatives Reporting",
  description: "Trade repository reports for OTC derivatives under EMIR Refit. Includes UTI, LEI pairs, product classification, notional, collateral, and lifecycle events reported to DTCC/REGIS-TR. Double-sided reporting for EU counterparties.",
  catalogId: $cat,
  accrualPeriodicity: "http://purl.org/cld/freq/daily",
  keywords: ["emir","derivatives","uti","lei","trade-repository","dtcc","otc"],
  themes: ["http://publications.europa.eu/resource/authority/data-theme/GOVE"],
  language: ["en"],
  version: "2.0.0",
  sourceUri: "snowflake://meridian.snowflakecomputing.com/COMPLIANCE/REPORTING/EMIR_DERIVATIVES"
}')")
success "EMIR Derivatives Reporting: ${DS_EMIR}"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 4 — Distributions"
# ─────────────────────────────────────────────────────────────────────────────

info "Creating distributions (idempotent by access/download URL)..."

# Distribution IDs are stored so Phase 13 can attach per-distribution physical schemas.

DIST_TRADES_SF=$(get_or_create_distribution "${DS_TRADES}" "accessUrl" \
  "snowflake://meridian.snowflakecomputing.com/TRADING/TRADE_CAPTURE/EXECUTED_TRADES" \
  '{"title":"Snowflake Table — Executed Trades","description":"Live Snowflake table; query via Snowsight or JDBC with TRADING role.","accessUrl":"snowflake://meridian.snowflakecomputing.com/TRADING/TRADE_CAPTURE/EXECUTED_TRADES","mediaType":"application/vnd.snowflake.table","format":"Snowflake","availability":"available"}')

DIST_TRADES_PQ=$(get_or_create_distribution "${DS_TRADES}" "downloadUrl" \
  "s3://meridian-datalake/trading/executed_trades/" \
  '{"title":"Daily Parquet Snapshot — Executed Trades","description":"End-of-day Parquet export partitioned by trade_date stored in MinIO. Suitable for bulk analytics.","downloadUrl":"s3://meridian-datalake/trading/executed_trades/","mediaType":"application/parquet","format":"Parquet","byteSize":4294967296,"availability":"available"}')
success "Distributions: Executed Trades (Snowflake=${DIST_TRADES_SF:0:8}… Parquet=${DIST_TRADES_PQ:0:8}…)"

DIST_POSITIONS_SF=$(get_or_create_distribution "${DS_POSITIONS}" "accessUrl" \
  "snowflake://meridian.snowflakecomputing.com/TRADING/POSITIONS/DAILY_BLOTTER" \
  '{"title":"Snowflake Table — Daily Positions","accessUrl":"snowflake://meridian.snowflakecomputing.com/TRADING/POSITIONS/DAILY_BLOTTER","mediaType":"application/vnd.snowflake.table","format":"Snowflake","availability":"available"}')

DIST_POSITIONS_REST=$(get_or_create_distribution "${DS_POSITIONS}" "accessUrl" \
  "https://api.meridian.internal/v2/positions" \
  '{"title":"REST API — Position Snapshot","description":"JSON REST endpoint; returns current-day positions for a given book or portfolio.","accessUrl":"https://api.meridian.internal/v2/positions","mediaType":"application/json","format":"JSON","availability":"available"}')
success "Distributions: Daily Position Blotter (Snowflake=${DIST_POSITIONS_SF:0:8}… REST=${DIST_POSITIONS_REST:0:8}…)"

DIST_VAR_SF=$(get_or_create_distribution "${DS_VAR}" "accessUrl" \
  "snowflake://meridian.snowflakecomputing.com/RISK/MARKET_RISK/VAR_DAILY" \
  '{"title":"Snowflake Table — VaR Daily","accessUrl":"snowflake://meridian.snowflakecomputing.com/RISK/MARKET_RISK/VAR_DAILY","mediaType":"application/vnd.snowflake.table","format":"Snowflake","availability":"available"}')

DIST_VAR_CSV=$(get_or_create_distribution "${DS_VAR}" "downloadUrl" \
  "https://datahub.meridian.internal/risk/var/history.csv.gz" \
  '{"title":"CSV Export — VaR History (3 years)","description":"Three-year historical VaR timeseries, refreshed monthly.","downloadUrl":"https://datahub.meridian.internal/risk/var/history.csv.gz","mediaType":"text/csv","format":"CSV","byteSize":52428800,"availability":"available"}')
success "Distributions: Market Risk VaR (Snowflake=${DIST_VAR_SF:0:8}… CSV=${DIST_VAR_CSV:0:8}…)"

DIST_SECURITIES_SF=$(get_or_create_distribution "${DS_SECURITIES}" "accessUrl" \
  "snowflake://meridian.snowflakecomputing.com/REFDATA/SECURITIES/MASTER" \
  '{"title":"Snowflake Table — Securities Master","accessUrl":"snowflake://meridian.snowflakecomputing.com/REFDATA/SECURITIES/MASTER","mediaType":"application/vnd.snowflake.table","format":"Snowflake","availability":"available"}')

DIST_SECURITIES_REST=$(get_or_create_distribution "${DS_SECURITIES}" "accessUrl" \
  "https://api.meridian.internal/v1/securities" \
  '{"title":"REST API — Security Lookup","description":"Lookup security by ISIN, CUSIP, SEDOL, or Bloomberg ticker. Returns full reference attributes.","accessUrl":"https://api.meridian.internal/v1/securities","mediaType":"application/json","format":"JSON","availability":"available"}')

DIST_SECURITIES_KAFKA=$(get_or_create_distribution "${DS_SECURITIES}" "accessUrl" \
  "kafka://kafka.meridian.internal:9092/refdata.securities.changes" \
  '{"title":"Daily Delta Feed — Securities","description":"Kafka topic carrying incremental security updates (new listings, delistings, corporate actions).","accessUrl":"kafka://kafka.meridian.internal:9092/refdata.securities.changes","mediaType":"application/json","format":"Kafka","availability":"available"}')
success "Distributions: Securities Master (SF=${DIST_SECURITIES_SF:0:8}… REST=${DIST_SECURITIES_REST:0:8}… Kafka=${DIST_SECURITIES_KAFKA:0:8}…)"

DIST_MIFID_XML=$(get_or_create_distribution "${DS_MIFID}" "downloadUrl" \
  "s3://meridian-compliance/mifid2/" \
  '{"title":"XML Report — MiFID II Submission","description":"ISO 20022 XML file submitted to FCA ARM daily by T+1. Retained for 5 years.","downloadUrl":"s3://meridian-compliance/mifid2/","mediaType":"application/xml","format":"ISO 20022 XML","availability":"available"}')

DIST_MIFID_PQ=$(get_or_create_distribution "${DS_MIFID}" "downloadUrl" \
  "s3://meridian-datalake/compliance/mifid2/" \
  '{"title":"Parquet Archive — MiFID II","description":"Columnar archive for internal analytics and audit queries.","downloadUrl":"s3://meridian-datalake/compliance/mifid2/","mediaType":"application/parquet","format":"Parquet","availability":"available"}')
success "Distributions: MiFID II (XML=${DIST_MIFID_XML:0:8}… Parquet=${DIST_MIFID_PQ:0:8}…)"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 5 — Vocabulary Profiles"
# ─────────────────────────────────────────────────────────────────────────────

info "Linking datasets to FIBO vocabularies (idempotent by vocabulary ID)..."

for DS in "${DS_TRADES}" "${DS_POSITIONS}" "${DS_ORDERBOOK}"; do
  add_vocab_profile_if_missing "${DS}" "${VOCAB_SEC}" \
    "$(jq -n --arg v "${VOCAB_SEC}" '{"vocabularyId":$v,"isPrimary":true,"domainTags":["equity","fixed-income","derivatives"]}')"
  add_vocab_profile_if_missing "${DS}" "${VOCAB_FND}" \
    "$(jq -n --arg v "${VOCAB_FND}" '{"vocabularyId":$v,"isPrimary":false,"domainTags":["monetary-amount","currency"]}')"
done
success "Vocabulary profiles: Trading datasets → FIBO SEC + FND"

for DS in "${DS_VAR}" "${DS_COUNTERPARTY}" "${DS_RISK_METRICS}"; do
  add_vocab_profile_if_missing "${DS}" "${VOCAB_FND}" \
    "$(jq -n --arg v "${VOCAB_FND}" '{"vocabularyId":$v,"isPrimary":true,"domainTags":["risk","monetary-amount"]}')"
  add_vocab_profile_if_missing "${DS}" "${VOCAB_FBC}" \
    "$(jq -n --arg v "${VOCAB_FBC}" '{"vocabularyId":$v,"isPrimary":false,"domainTags":["financial-instruments"]}')"
done
success "Vocabulary profiles: Risk datasets → FIBO FND + FBC"

for DS in "${DS_SECURITIES}" "${DS_FX_RATES}" "${DS_COUNTERPARTY_DIR}"; do
  add_vocab_profile_if_missing "${DS}" "${VOCAB_SEC}" \
    "$(jq -n --arg v "${VOCAB_SEC}" '{"vocabularyId":$v,"isPrimary":true,"domainTags":["securities","identifier"]}')"
  add_vocab_profile_if_missing "${DS}" "${VOCAB_MD}" \
    "$(jq -n --arg v "${VOCAB_MD}" '{"vocabularyId":$v,"isPrimary":false,"domainTags":["market-data","price"]}')"
done
success "Vocabulary profiles: Reference data → FIBO SEC + MD"

for DS in "${DS_MIFID}" "${DS_FINREP}" "${DS_EMIR}"; do
  add_vocab_profile_if_missing "${DS}" "${VOCAB_FBC}" \
    "$(jq -n --arg v "${VOCAB_FBC}" '{"vocabularyId":$v,"isPrimary":true,"domainTags":["compliance","regulatory"]}')"
  add_vocab_profile_if_missing "${DS}" "${VOCAB_SCHEMA}" \
    "$(jq -n --arg v "${VOCAB_SCHEMA}" '{"vocabularyId":$v,"isPrimary":false,"domainTags":["date","identifier"]}')"
done
success "Vocabulary profiles: Compliance datasets → FIBO FBC + schema.org"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 6 — Logical Models with FIBO/schema.org Mappings"
# ─────────────────────────────────────────────────────────────────────────────

info "Building logical model: Executed Trades..."

LM_TRADES=$(get_or_create_logical_model "${DS_TRADES}" "Trade Capture Logical Model" \
  '{"name":"Trade Capture Logical Model","description":"Business-oriented view of a single trade leg. FIBO-annotated to enable cross-system semantic integration.","version":"2.0","status":"published"}')

EL_TRADE_ID=$(  get_or_create_element "${LM_TRADES}" "tradeId"         "Trade Identifier"        "Identifier"     1 true  true)
EL_ISIN=$(       get_or_create_element "${LM_TRADES}" "isin"            "ISIN"                    "Identifier"     2 true  false)
EL_NOTIONAL=$(   get_or_create_element "${LM_TRADES}" "notionalAmount"  "Notional Amount"         "MonetaryAmount" 3 true  false)
EL_CURRENCY=$(   get_or_create_element "${LM_TRADES}" "settleCurrency"  "Settlement Currency"     "Currency"       4 true  false)
EL_PRICE=$(      get_or_create_element "${LM_TRADES}" "tradePrice"      "Trade Price"             "Price"          5 true  false)
EL_DATE=$(       get_or_create_element "${LM_TRADES}" "tradeDate"       "Trade Date"              "Date"           6 true  false)
EL_DIRECTION=$(  get_or_create_element "${LM_TRADES}" "direction"       "Buy / Sell Indicator"    "Code"           7 true  false)
EL_COUNTERPARTY=$(get_or_create_element "${LM_TRADES}" "counterpartyLei" "Counterparty LEI"       "Identifier"     8 true  false)
EL_VENUE=$(      get_or_create_element "${LM_TRADES}" "executionVenue"  "Execution Venue (MIC)"   "Code"           9 false false)
EL_TRADER=$(     get_or_create_element "${LM_TRADES}" "traderId"        "Trader Identifier"       "Identifier"    10 true  false)

add_vocab_mapping_if_missing "${EL_TRADE_ID}"     "${VOCAB_FBC}"    "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/FinancialServicesEntities/TradeIdentifier" "TradeIdentifier"
add_vocab_mapping_if_missing "${EL_ISIN}"         "${VOCAB_SEC}"    "https://spec.edmcouncil.org/fibo/ontology/SEC/Securities/SecuritiesIdentificationSchemes/InternationalSecuritiesIdentificationNumber" "ISIN"
add_vocab_mapping_if_missing "${EL_NOTIONAL}"     "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount"
add_vocab_mapping_if_missing "${EL_CURRENCY}"     "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/ISO4217-CurrencyCodes/Currency" "Currency"
add_vocab_mapping_if_missing "${EL_PRICE}"        "${VOCAB_MD}"     "https://spec.edmcouncil.org/fibo/ontology/MD/TemporalCore/SecurityMarketConcepts/MarketPrice" "MarketPrice"
add_vocab_mapping_if_missing "${EL_DATE}"         "${VOCAB_SCHEMA}" "https://schema.org/startDate" "startDate"
add_vocab_mapping_if_missing "${EL_COUNTERPARTY}" "${VOCAB_FBC}"    "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/RegistrationAuthorities/LegalEntityIdentifier" "LegalEntityIdentifier"
add_vocab_mapping_if_missing "${EL_VENUE}"        "${VOCAB_FBC}"    "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/FinancialServicesEntities/MarketIdentifierCode" "MarketIdentifierCode"
success "Logical model (published): Executed Trades — 10 elements, FIBO-mapped"

info "Building logical model: Daily Position Blotter..."
LM_POSITIONS=$(get_or_create_logical_model "${DS_POSITIONS}" "Position Blotter Logical Model" \
  '{"name":"Position Blotter Logical Model","description":"EOD position record per portfolio and instrument. Semantically aligned to FIBO SEC and FND.","version":"1.5","status":"published"}')

EL_POS_PORTFOLIO=$(get_or_create_element "${LM_POSITIONS}" "portfolioId"  "Portfolio Identifier" "Identifier"     1 true true)
EL_POS_ISIN=$(      get_or_create_element "${LM_POSITIONS}" "isin"         "ISIN"                 "Identifier"     2 true false)
EL_POS_QTY=$(       get_or_create_element "${LM_POSITIONS}" "netQuantity"  "Net Quantity"          "Quantity"       3 true false)
EL_POS_MV=$(        get_or_create_element "${LM_POSITIONS}" "marketValue"  "Market Value"          "MonetaryAmount" 4 true false)
EL_POS_PNL=$(       get_or_create_element "${LM_POSITIONS}" "dailyPnl"     "Daily P&L"             "MonetaryAmount" 5 true false)
EL_POS_CCY=$(       get_or_create_element "${LM_POSITIONS}" "baseCurrency" "Base Currency"         "Currency"       6 true false)
EL_POS_DATE=$(      get_or_create_element "${LM_POSITIONS}" "positionDate" "Position Date"         "Date"           7 true false)

add_vocab_mapping_if_missing "${EL_POS_ISIN}" "${VOCAB_SEC}" "https://spec.edmcouncil.org/fibo/ontology/SEC/Securities/SecuritiesIdentificationSchemes/InternationalSecuritiesIdentificationNumber" "ISIN"
add_vocab_mapping_if_missing "${EL_POS_MV}"   "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount"
add_vocab_mapping_if_missing "${EL_POS_PNL}"  "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount" "closeMatch"
add_vocab_mapping_if_missing "${EL_POS_CCY}"  "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/ISO4217-CurrencyCodes/Currency" "Currency"
add_vocab_mapping_if_missing "${EL_POS_DATE}" "${VOCAB_SCHEMA}" "https://schema.org/startDate" "startDate"
success "Logical model (published): Daily Position Blotter — 7 elements"

info "Building logical model: Securities Master..."
LM_SECURITIES=$(get_or_create_logical_model "${DS_SECURITIES}" "Security Reference Logical Model" \
  '{"name":"Security Reference Logical Model","description":"Canonical representation of a financial instrument identifier set, fully aligned to FIBO SEC and FBC.","version":"3.0","status":"published"}')

EL_SEC_ISIN=$(     get_or_create_element "${LM_SECURITIES}" "isin"              "ISIN"                       "Identifier" 1 true  true)
EL_SEC_CUSIP=$(    get_or_create_element "${LM_SECURITIES}" "cusip"             "CUSIP"                      "Identifier" 2 false false)
EL_SEC_SEDOL=$(    get_or_create_element "${LM_SECURITIES}" "sedol"             "SEDOL"                      "Identifier" 3 false false)
EL_SEC_TICKER=$(   get_or_create_element "${LM_SECURITIES}" "bloombergTicker"   "Bloomberg Ticker"           "Identifier" 4 false false)
EL_SEC_CLASS=$(    get_or_create_element "${LM_SECURITIES}" "assetClass"        "Asset Class"                "Code"       5 true  false)
EL_SEC_CCY=$(      get_or_create_element "${LM_SECURITIES}" "denominatedCurrency" "Denomination Currency"    "Currency"   6 true  false)
EL_SEC_ISSUER=$(   get_or_create_element "${LM_SECURITIES}" "issuerLei"         "Issuer LEI"                 "Identifier" 7 false false)
EL_SEC_MATURITY=$( get_or_create_element "${LM_SECURITIES}" "maturityDate"      "Maturity Date"              "Date"       8 false false)

add_vocab_mapping_if_missing "${EL_SEC_ISIN}"    "${VOCAB_SEC}" "https://spec.edmcouncil.org/fibo/ontology/SEC/Securities/SecuritiesIdentificationSchemes/InternationalSecuritiesIdentificationNumber" "ISIN"
add_vocab_mapping_if_missing "${EL_SEC_CUSIP}"   "${VOCAB_SEC}" "https://spec.edmcouncil.org/fibo/ontology/SEC/Securities/SecuritiesIdentificationSchemes/CommitteeOnUniformSecuritiesIdentificationProceduresNumber" "CUSIP"
add_vocab_mapping_if_missing "${EL_SEC_CLASS}"   "${VOCAB_FBC}" "https://spec.edmcouncil.org/fibo/ontology/FBC/FinancialInstruments/FinancialInstruments/FinancialInstrument" "FinancialInstrument"
add_vocab_mapping_if_missing "${EL_SEC_CCY}"     "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/ISO4217-CurrencyCodes/Currency" "Currency"
add_vocab_mapping_if_missing "${EL_SEC_ISSUER}"  "${VOCAB_FBC}" "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/RegistrationAuthorities/LegalEntityIdentifier" "LegalEntityIdentifier"
add_vocab_mapping_if_missing "${EL_SEC_MATURITY}" "${VOCAB_FBC}" "https://spec.edmcouncil.org/fibo/ontology/FBC/FinancialInstruments/FinancialInstruments/hasMaturityDate" "hasMaturityDate"
success "Logical model (published): Securities Master — 8 elements"

info "Building logical model: Market Risk VaR (draft)..."
LM_VAR=$(get_or_create_logical_model "${DS_VAR}" "VaR Measure Logical Model" \
  '{"name":"VaR Measure Logical Model","description":"Draft logical model for the VaR output dataset. Pending final sign-off from the risk quants team.","version":"0.3","status":"draft"}')

EL_VAR_PORTFOLIO=$(get_or_create_element "${LM_VAR}" "portfolioId"    "Portfolio Identifier"  "Identifier"     1 true true)
EL_VAR_VALUE=$(    get_or_create_element "${LM_VAR}" "varValue"        "VaR Value (USD)"       "MonetaryAmount" 2 true false)
EL_VAR_SVAR=$(     get_or_create_element "${LM_VAR}" "stressedVar"     "Stressed VaR (USD)"    "MonetaryAmount" 3 true false)
EL_VAR_CONF=$(     get_or_create_element "${LM_VAR}" "confidenceLevel" "Confidence Level"      "Percentage"     4 true false)
EL_VAR_HORIZON=$(  get_or_create_element "${LM_VAR}" "holdingPeriod"   "Holding Period (days)" "Integer"        5 true false)
EL_VAR_DATE=$(     get_or_create_element "${LM_VAR}" "valuationDate"   "Valuation Date"        "Date"           6 true false)

add_vocab_mapping_if_missing "${EL_VAR_VALUE}" "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount"
add_vocab_mapping_if_missing "${EL_VAR_SVAR}"  "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount" "closeMatch"
add_vocab_mapping_if_missing "${EL_VAR_DATE}"  "${VOCAB_SCHEMA}" "https://schema.org/startDate" "startDate"
success "Logical model (draft): Market Risk VaR — 6 elements"

info "Building logical model: MiFID II Transaction Reports..."
LM_MIFID=$(get_or_create_logical_model "${DS_MIFID}" "MiFID II Report Logical Model" \
  '{"name":"MiFID II Report Logical Model","description":"Logical structure of a MiFID II Article 26 transaction report field set. ESMA field numbering in labels.","version":"2.0","status":"published"}')

EL_MIF_UTI=$(      get_or_create_element "${LM_MIFID}" "uti"               "UTI (Field 1)"                 "Identifier" 1 true  true)
EL_MIF_ISIN=$(     get_or_create_element "${LM_MIFID}" "instrumentIsin"    "Instrument ISIN (Field 41)"    "Identifier" 2 true  false)
EL_MIF_PRICE=$(    get_or_create_element "${LM_MIFID}" "price"             "Price (Field 33)"              "Price"      3 true  false)
EL_MIF_QTY=$(      get_or_create_element "${LM_MIFID}" "quantity"          "Quantity (Field 30)"           "Quantity"   4 true  false)
EL_MIF_CCY=$(      get_or_create_element "${LM_MIFID}" "priceCurrency"     "Price Currency (Field 34)"     "Currency"   5 true  false)
EL_MIF_BUY_LEI=$(  get_or_create_element "${LM_MIFID}" "buyerLei"          "Buyer LEI (Field 15)"          "Identifier" 6 true  false)
EL_MIF_SELL_LEI=$( get_or_create_element "${LM_MIFID}" "sellerLei"         "Seller LEI (Field 16)"         "Identifier" 7 true  false)
EL_MIF_VENUE=$(    get_or_create_element "${LM_MIFID}" "tradingVenueMic"   "Trading Venue MIC (Field 36)"  "Code"       8 true  false)
EL_MIF_TS=$(       get_or_create_element "${LM_MIFID}" "executionTimestamp" "Execution Timestamp (Field 28)" "DateTime" 9 true  false)

add_vocab_mapping_if_missing "${EL_MIF_ISIN}"     "${VOCAB_SEC}" "https://spec.edmcouncil.org/fibo/ontology/SEC/Securities/SecuritiesIdentificationSchemes/InternationalSecuritiesIdentificationNumber" "ISIN"
add_vocab_mapping_if_missing "${EL_MIF_PRICE}"    "${VOCAB_MD}"  "https://spec.edmcouncil.org/fibo/ontology/MD/TemporalCore/SecurityMarketConcepts/MarketPrice" "MarketPrice"
add_vocab_mapping_if_missing "${EL_MIF_CCY}"      "${VOCAB_FND}" "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/ISO4217-CurrencyCodes/Currency" "Currency"
add_vocab_mapping_if_missing "${EL_MIF_BUY_LEI}"  "${VOCAB_FBC}" "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/RegistrationAuthorities/LegalEntityIdentifier" "LegalEntityIdentifier"
add_vocab_mapping_if_missing "${EL_MIF_SELL_LEI}" "${VOCAB_FBC}" "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/RegistrationAuthorities/LegalEntityIdentifier" "LegalEntityIdentifier"
add_vocab_mapping_if_missing "${EL_MIF_VENUE}"    "${VOCAB_FBC}" "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/FinancialServicesEntities/MarketIdentifierCode" "MarketIdentifierCode"
add_vocab_mapping_if_missing "${EL_MIF_TS}"       "${VOCAB_SCHEMA}" "https://schema.org/startDate" "startDate" "closeMatch"
success "Logical model (published): MiFID II — 9 elements, fully mapped"

info "Building logical model: Counterparty Credit Exposure..."
LM_COUNTERPARTY=$(get_or_create_logical_model "${DS_COUNTERPARTY}" "Counterparty Credit Exposure Model" \
  '{"name":"Counterparty Credit Exposure Model","description":"Business-oriented view of counterparty credit risk exposure. Covers current exposure, PFE, CVA/DVA, netting sets, and ISDA agreement references. FIBO-annotated for regulatory and semantic interoperability.","version":"1.0","status":"draft"}')

EL_CE_EXPOSURE_ID=$(  get_or_create_element "${LM_COUNTERPARTY}" "exposureId"               "Exposure Record ID"              "Identifier"     1 true  true)
EL_CE_CP_ID=$(        get_or_create_element "${LM_COUNTERPARTY}" "counterpartyId"            "Counterparty Internal ID"        "Identifier"     2 true  false)
EL_CE_CP_LEI=$(       get_or_create_element "${LM_COUNTERPARTY}" "counterpartyLei"           "Counterparty LEI"                "Identifier"     3 true  false)
EL_CE_CP_NAME=$(      get_or_create_element "${LM_COUNTERPARTY}" "counterpartyName"          "Counterparty Legal Name"         "Text"           4 true  false)
EL_CE_NETTING=$(      get_or_create_element "${LM_COUNTERPARTY}" "nettingSetId"              "Netting Set Identifier"          "Identifier"     5 true  false)
EL_CE_AS_OF=$(        get_or_create_element "${LM_COUNTERPARTY}" "asOfDate"                  "As-Of Date"                      "Date"           6 true  false)
EL_CE_CCY=$(          get_or_create_element "${LM_COUNTERPARTY}" "baseCurrency"              "Base Currency"                   "Currency"       7 true  false)
EL_CE_CURRENT=$(      get_or_create_element "${LM_COUNTERPARTY}" "currentExposure"           "Current Exposure (CE)"           "MonetaryAmount" 8 true  false)
EL_CE_PFE=$(          get_or_create_element "${LM_COUNTERPARTY}" "potentialFutureExposure"   "Potential Future Exposure (PFE)" "MonetaryAmount" 9 true  false)
EL_CE_EE=$(           get_or_create_element "${LM_COUNTERPARTY}" "expectedExposure"          "Expected Exposure (EE)"          "MonetaryAmount" 10 true  false)
EL_CE_PEAK=$(         get_or_create_element "${LM_COUNTERPARTY}" "peakExposure"              "Peak Exposure (EPE)"             "MonetaryAmount" 11 false false)
EL_CE_CVA=$(          get_or_create_element "${LM_COUNTERPARTY}" "creditValuationAdjustment" "Credit Valuation Adjustment"     "MonetaryAmount" 12 true  false)
EL_CE_DVA=$(          get_or_create_element "${LM_COUNTERPARTY}" "debitValuationAdjustment"  "Debit Valuation Adjustment"      "MonetaryAmount" 13 false false)
EL_CE_COL_POST=$(     get_or_create_element "${LM_COUNTERPARTY}" "collateralPostedAmount"    "Collateral Posted"               "MonetaryAmount" 14 false false)
EL_CE_COL_RECV=$(     get_or_create_element "${LM_COUNTERPARTY}" "collateralReceivedAmount"  "Collateral Received"             "MonetaryAmount" 15 false false)
EL_CE_NET=$(          get_or_create_element "${LM_COUNTERPARTY}" "netExposure"               "Net Exposure"                    "MonetaryAmount" 16 true  false)
EL_CE_WWR_FLAG=$(     get_or_create_element "${LM_COUNTERPARTY}" "wrongWayRiskFlag"          "Wrong-Way Risk Flag"             "Boolean"        17 true  false)
EL_CE_WWR_SCORE=$(    get_or_create_element "${LM_COUNTERPARTY}" "wrongWayRiskScore"         "Wrong-Way Risk Score"            "Decimal"        18 false false)
EL_CE_ISDA=$(         get_or_create_element "${LM_COUNTERPARTY}" "isdaAgreementId"           "ISDA Master Agreement ID"        "Identifier"     19 false false)
EL_CE_RATING=$(       get_or_create_element "${LM_COUNTERPARTY}" "creditRating"              "Credit Rating"                   "Code"           20 false false)
EL_CE_THRESHOLD=$(    get_or_create_element "${LM_COUNTERPARTY}" "thresholdAmount"           "Threshold Amount"                "MonetaryAmount" 21 false false)
EL_CE_MTA=$(          get_or_create_element "${LM_COUNTERPARTY}" "minimumTransferAmount"     "Minimum Transfer Amount"         "MonetaryAmount" 22 false false)
EL_CE_CALC_METHOD=$(  get_or_create_element "${LM_COUNTERPARTY}" "calculationMethod"         "Calculation Method"              "Code"           23 true  false)
EL_CE_CONF_LVL=$(     get_or_create_element "${LM_COUNTERPARTY}" "confidenceLevel"           "Confidence Level"                "Decimal"        24 true  false)
EL_CE_HORIZON=$(      get_or_create_element "${LM_COUNTERPARTY}" "timeHorizonDays"           "Time Horizon (days)"             "Integer"        25 true  false)

add_vocab_mapping_if_missing "${EL_CE_CP_LEI}"   "${VOCAB_FBC}"    "https://spec.edmcouncil.org/fibo/ontology/FBC/FunctionalEntities/RegistrationAuthorities/LegalEntityIdentifier" "LegalEntityIdentifier"
add_vocab_mapping_if_missing "${EL_CE_AS_OF}"    "${VOCAB_SCHEMA}" "https://schema.org/startDate" "startDate"
add_vocab_mapping_if_missing "${EL_CE_CCY}"      "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/ISO4217-CurrencyCodes/Currency" "Currency"
add_vocab_mapping_if_missing "${EL_CE_CURRENT}"  "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount"
add_vocab_mapping_if_missing "${EL_CE_PFE}"      "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount" "closeMatch"
add_vocab_mapping_if_missing "${EL_CE_EE}"       "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount" "closeMatch"
add_vocab_mapping_if_missing "${EL_CE_CVA}"      "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount" "closeMatch"
add_vocab_mapping_if_missing "${EL_CE_NET}"      "${VOCAB_FND}"    "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount" "MonetaryAmount" "closeMatch"
success "Logical model (draft): Counterparty Credit Exposure — 25 elements, FIBO-mapped"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 7 — Data Products"
# ─────────────────────────────────────────────────────────────────────────────

info "Creating data products (idempotent by title)..."

DP_TRADE_ANALYTICS=$(get_or_create_data_product "Trade Analytics Data Product" '{
  "title": "Trade Analytics Data Product",
  "description": "Self-service analytics surface for the executed trades dataset. Pre-joined with securities master and counterparty directory. Published to the internal analytics marketplace as a governed product with SLA.",
  "lifecycleStatus": "Consume",
  "purpose": "Enable quants, PMs, and risk teams to query normalised trade data without needing raw Snowflake access.",
  "informationSensitivity": "Confidential",
  "keywords": ["trades","analytics","self-service"],
  "themes": ["trading"]
}')
success "Data Product (Consume): Trade Analytics"

DP_RISK_REPORT=$(get_or_create_data_product "Daily Risk Report Data Product" '{
  "title": "Daily Risk Report Data Product",
  "description": "Curated risk dashboard data product: VaR, sVaR, Greeks, and CCR metrics pre-aggregated for the morning risk report. Published to BI tools via a governed REST API.",
  "lifecycleStatus": "Deploy",
  "purpose": "Deliver a consistent, auditable risk data set to risk officers and senior management dashboards.",
  "informationSensitivity": "Restricted",
  "keywords": ["risk","var","greeks","dashboard"],
  "themes": ["risk"]
}')
success "Data Product (Deploy): Daily Risk Report"

DP_SECURITIES_GOLDEN=$(get_or_create_data_product "Securities Golden Source Data Product" '{
  "title": "Securities Golden Source Data Product",
  "description": "The single source of truth for security reference data, offered as a data product. Includes ISIN cross-referencing, asset class taxonomy, and issuer hierarchy. Consumed by 40+ downstream systems.",
  "lifecycleStatus": "Consume",
  "purpose": "Eliminate per-system security master divergence; provide a governed, versioned securities reference.",
  "informationSensitivity": "Internal",
  "keywords": ["securities","golden-source","isin","reference"],
  "themes": ["reference-data"]
}')
success "Data Product (Consume): Securities Golden Source"

DP_MIFID_REPORTING=$(get_or_create_data_product "MiFID II Reporting Data Product" '{
  "title": "MiFID II Reporting Data Product",
  "description": "Regulatory reporting data product that packages trade data enriched with the static reference fields required for MiFID II Article 26 submission. Under active build to add EMIR Refit fields.",
  "lifecycleStatus": "Build",
  "purpose": "Centralise regulatory enrichment logic to avoid duplicated compliance plumbing in individual trading systems.",
  "informationSensitivity": "Restricted",
  "keywords": ["mifid","emir","regulatory","compliance"],
  "themes": ["compliance"]
}')
success "Data Product (Build): MiFID II Reporting"

DP_PNL_ATTRIBUTION=$(get_or_create_data_product "P&L Attribution Prototype" '{
  "title": "P&L Attribution Prototype",
  "description": "Early-stage data product exploring automated P&L attribution using risk factor sensitivities. Currently in design — seeking data ownership agreement with Finance and Risk teams.",
  "lifecycleStatus": "Design",
  "purpose": "Replace manual Excel-based P&L explain with a governed, automated data product.",
  "informationSensitivity": "Confidential",
  "keywords": ["pnl","attribution","risk-factors","prototype"],
  "themes": ["risk","trading"]
}')
success "Data Product (Design): P&L Attribution Prototype"

info "Linking datasets to data products (backend deduplicates by dataset ID)..."
link_dataset() {
  # Backend already deduplicates — POST is safe to repeat
  post "${CATALOG_URL}/api/v1/data-products/${1}/datasets" "{\"datasetId\":\"${2}\"}" > /dev/null
}
link_dataset "${DP_TRADE_ANALYTICS}"   "${DS_TRADES}"
link_dataset "${DP_TRADE_ANALYTICS}"   "${DS_POSITIONS}"
link_dataset "${DP_TRADE_ANALYTICS}"   "${DS_ORDERBOOK}"
link_dataset "${DP_RISK_REPORT}"       "${DS_VAR}"
link_dataset "${DP_RISK_REPORT}"       "${DS_COUNTERPARTY}"
link_dataset "${DP_RISK_REPORT}"       "${DS_RISK_METRICS}"
link_dataset "${DP_SECURITIES_GOLDEN}" "${DS_SECURITIES}"
link_dataset "${DP_SECURITIES_GOLDEN}" "${DS_COUNTERPARTY_DIR}"
link_dataset "${DP_SECURITIES_GOLDEN}" "${DS_FX_RATES}"
link_dataset "${DP_MIFID_REPORTING}"   "${DS_MIFID}"
link_dataset "${DP_MIFID_REPORTING}"   "${DS_EMIR}"
success "Datasets linked to data products"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 8 — OpenLineage Multi-Hop Lineage Graph"
# ─────────────────────────────────────────────────────────────────────────────
# OpenLineage handler upserts jobs/runs/datasets by namespace+name/runId,
# so re-submitting the same runId is safe.
# ─────────────────────────────────────────────────────────────────────────────

info "Submitting OpenLineage events (idempotent by fixed runId)..."

ol_event() {
  local runId="$1" jobNs="$2" jobName="$3" eventType="$4" inputs="$5" outputs="$6"
  post "${LINEAGE_URL}/api/v1/lineage" "$(jq -n \
    --arg rid "${runId}" --arg jns "${jobNs}" --arg jn "${jobName}" \
    --arg et "${eventType}" --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --argjson ins "${inputs}" --argjson outs "${outputs}" \
    '{eventType:$et,eventTime:$ts,run:{runId:$rid,facets:{}},job:{namespace:$jns,name:$jn,facets:{}},inputs:$ins,outputs:$outs,producer:"meridian/trade-lifecycle",schemaURL:"https://openlineage.io/spec/1-0-5/OpenLineage.json"}')" > /dev/null
}

ds_node() { jq -n --arg ns "$1" --arg n "$2" '{namespace:$ns,name:$n,facets:{}}'; }

RUN_ENRICH="aaaaaaaa-0001-0001-0001-000000000001"
ol_event "${RUN_ENRICH}" "trading" "trade_enrichment_job" "START" \
  "[$(ds_node "reference" "securities_master"), $(ds_node "reference" "fx_reference_rates")]" \
  "[$(ds_node "trading" "executed_trades")]"
ol_event "${RUN_ENRICH}" "trading" "trade_enrichment_job" "COMPLETE" \
  "[$(ds_node "reference" "securities_master"), $(ds_node "reference" "fx_reference_rates")]" \
  "[$(ds_node "trading" "executed_trades")]"
success "Lineage Job 1: trade_enrichment_job (securities + fx → executed_trades)"

RUN_POSITIONS="aaaaaaaa-0002-0002-0002-000000000002"
ol_event "${RUN_POSITIONS}" "trading" "position_aggregation_job" "START" \
  "[$(ds_node "trading" "executed_trades")]" "[$(ds_node "trading" "daily_position_blotter")]"
ol_event "${RUN_POSITIONS}" "trading" "position_aggregation_job" "COMPLETE" \
  "[$(ds_node "trading" "executed_trades")]" "[$(ds_node "trading" "daily_position_blotter")]"
success "Lineage Job 2: position_aggregation_job (executed_trades → daily_positions)"

RUN_RISK="aaaaaaaa-0003-0003-0003-000000000003"
ol_event "${RUN_RISK}" "risk" "risk_metrics_computation_job" "START" \
  "[$(ds_node "trading" "daily_position_blotter"), $(ds_node "reference" "securities_master")]" \
  "[$(ds_node "risk" "aggregated_market_risk_metrics")]"
ol_event "${RUN_RISK}" "risk" "risk_metrics_computation_job" "COMPLETE" \
  "[$(ds_node "trading" "daily_position_blotter"), $(ds_node "reference" "securities_master")]" \
  "[$(ds_node "risk" "aggregated_market_risk_metrics")]"
success "Lineage Job 3: risk_metrics_computation_job (positions + securities → risk_metrics)"

RUN_VAR="aaaaaaaa-0004-0004-0004-000000000004"
ol_event "${RUN_VAR}" "risk" "var_calculation_job" "START" \
  "[$(ds_node "trading" "daily_position_blotter")]" "[$(ds_node "risk" "market_risk_var")]"
ol_event "${RUN_VAR}" "risk" "var_calculation_job" "COMPLETE" \
  "[$(ds_node "trading" "daily_position_blotter")]" "[$(ds_node "risk" "market_risk_var")]"
success "Lineage Job 4: var_calculation_job (positions → var_daily)"

RUN_FINREP="aaaaaaaa-0005-0005-0005-000000000005"
ol_event "${RUN_FINREP}" "compliance" "finrep_generation_job" "START" \
  "[$(ds_node "risk" "aggregated_market_risk_metrics"), $(ds_node "risk" "market_risk_var")]" \
  "[$(ds_node "compliance" "finrep_consolidated_financial_statements")]"
ol_event "${RUN_FINREP}" "compliance" "finrep_generation_job" "COMPLETE" \
  "[$(ds_node "risk" "aggregated_market_risk_metrics"), $(ds_node "risk" "market_risk_var")]" \
  "[$(ds_node "compliance" "finrep_consolidated_financial_statements")]"
success "Lineage Job 5: finrep_generation_job (risk_metrics + var → finrep)"

RUN_MIFID="aaaaaaaa-0006-0006-0006-000000000006"
ol_event "${RUN_MIFID}" "compliance" "mifid_report_job" "START" \
  "[$(ds_node "trading" "executed_trades"), $(ds_node "reference" "counterparty_directory")]" \
  "[$(ds_node "compliance" "mifid_ii_transaction_reports")]"
ol_event "${RUN_MIFID}" "compliance" "mifid_report_job" "COMPLETE" \
  "[$(ds_node "trading" "executed_trades"), $(ds_node "reference" "counterparty_directory")]" \
  "[$(ds_node "compliance" "mifid_ii_transaction_reports")]"
success "Lineage Job 6: mifid_report_job (trades + counterparties → mifid)"

RUN_EMIR="aaaaaaaa-0007-0007-0007-000000000007"
ol_event "${RUN_EMIR}" "compliance" "emir_reporting_job" "START" \
  "[$(ds_node "trading" "executed_trades"), $(ds_node "reference" "counterparty_directory")]" \
  "[$(ds_node "compliance" "emir_derivatives_reporting")]"
ol_event "${RUN_EMIR}" "compliance" "emir_reporting_job" "COMPLETE" \
  "[$(ds_node "trading" "executed_trades"), $(ds_node "reference" "counterparty_directory")]" \
  "[$(ds_node "compliance" "emir_derivatives_reporting")]"
success "Lineage Job 7: emir_reporting_job (trades + counterparties → emir)"

RUN_OB="aaaaaaaa-0008-0008-0008-000000000008"
ol_event "${RUN_OB}" "trading" "order_book_fill_matcher" "START" \
  "[$(ds_node "trading" "intraday_order_book")]" "[$(ds_node "trading" "executed_trades")]"
ol_event "${RUN_OB}" "trading" "order_book_fill_matcher" "COMPLETE" \
  "[$(ds_node "trading" "intraday_order_book")]" "[$(ds_node "trading" "executed_trades")]"
success "Lineage Job 8: order_book_fill_matcher (order_book → executed_trades)"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 9 — DDL Lineage Views"
# ─────────────────────────────────────────────────────────────────────────────
# AGE uses MERGE — already idempotent.
# ─────────────────────────────────────────────────────────────────────────────

info "Submitting DDL lineage for derived views (idempotent via AGE MERGE)..."

post "${LINEAGE_URL}/api/v1/ddl/submit" '{
  "namespace": "risk",
  "dialect": "SNOWFLAKE",
  "ddl": "CREATE OR REPLACE VIEW risk.v_counterparty_exposure AS SELECT t.counterparty_lei, t.notional_amount, t.settle_currency, c.credit_rating, c.netting_set_id FROM trading.executed_trades t JOIN reference.counterparty_directory c ON t.counterparty_lei = c.lei WHERE t.trade_date = CURRENT_DATE"
}' > /dev/null
success "DDL lineage: v_counterparty_exposure (trades JOIN counterparties)"

post "${LINEAGE_URL}/api/v1/ddl/submit" '{
  "namespace": "compliance",
  "dialect": "SNOWFLAKE",
  "ddl": "CREATE OR REPLACE VIEW compliance.v_mifid_enriched AS SELECT t.trade_id, t.isin, t.trade_price, t.notional_amount, t.settle_currency, t.execution_venue, t.counterparty_lei, s.asset_class, s.bloomberg_ticker, fx.rate AS usd_rate FROM trading.executed_trades t JOIN reference.securities_master s ON t.isin = s.isin LEFT JOIN reference.fx_reference_rates fx ON t.settle_currency = fx.from_currency AND fx.to_currency = '\''USD'\'' AND fx.rate_date = t.trade_date"
}' > /dev/null
success "DDL lineage: v_mifid_enriched (trades JOIN securities JOIN fx_rates)"

post "${LINEAGE_URL}/api/v1/ddl/submit" '{
  "namespace": "reporting",
  "dialect": "ANSI",
  "ddl": "CREATE VIEW reporting.v_daily_risk_summary AS SELECT p.portfolio_id, p.position_date, p.market_value, p.daily_pnl, r.var_value, r.stressed_var, r.confidence_level FROM trading.daily_position_blotter p JOIN risk.market_risk_var r ON p.portfolio_id = r.portfolio_id AND p.position_date = r.valuation_date"
}' > /dev/null
success "DDL lineage: v_daily_risk_summary (positions JOIN var)"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 10 — Catalog ↔ Lineage Identity Links"
# ─────────────────────────────────────────────────────────────────────────────
# PUT is idempotent — safe to re-run.
# ─────────────────────────────────────────────────────────────────────────────

info "Linking catalog dataset IDs to lineage identities (idempotent PUT)..."

catalog_link() {
  local ns="$1" name="$2" catalog_id="$3"
  curl -sf -X PUT "${LINEAGE_URL}/api/v1/datasets/${ns}/${name}/catalog-link" \
    -H "${HEADER_AUTH}" -H "${HEADER_JSON}" \
    -d "{\"catalogResourceId\":\"${catalog_id}\"}" > /dev/null
}

catalog_link "trading"    "executed_trades"                          "${DS_TRADES}"
catalog_link "trading"    "daily_position_blotter"                   "${DS_POSITIONS}"
catalog_link "trading"    "intraday_order_book"                      "${DS_ORDERBOOK}"
catalog_link "risk"       "market_risk_var"                          "${DS_VAR}"
catalog_link "risk"       "aggregated_market_risk_metrics"           "${DS_RISK_METRICS}"
catalog_link "reference"  "securities_master"                        "${DS_SECURITIES}"
catalog_link "reference"  "counterparty_directory"                   "${DS_COUNTERPARTY_DIR}"
catalog_link "reference"  "fx_reference_rates"                       "${DS_FX_RATES}"
catalog_link "compliance" "mifid_ii_transaction_reports"             "${DS_MIFID}"
catalog_link "compliance" "finrep_consolidated_financial_statements" "${DS_FINREP}"
catalog_link "compliance" "emir_derivatives_reporting"               "${DS_EMIR}"

success "Catalog ↔ lineage identity links registered (11 datasets)"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 11 — Harvest Sources & Jobs"
# ─────────────────────────────────────────────────────────────────────────────

info "Creating harvest sources (idempotent by name)..."

SRC_SNOWFLAKE_TRADING=$(get_or_create_harvest_source "Snowflake — Trading & Positions" '{
  "name": "Snowflake — Trading & Positions",
  "sourceType": "snowflake",
  "baseUrl": "https://meridian.snowflakecomputing.com",
  "region": "eu-west-1",
  "databaseName": "TRADING",
  "schemaFilter": ["TRADE_CAPTURE", "POSITIONS", "ORDER_MANAGEMENT"],
  "credentialRef": "vault://secret/snowflake/trading"
}')
success "Harvest source: Snowflake Trading (${SRC_SNOWFLAKE_TRADING})"

SRC_SNOWFLAKE_RISK=$(get_or_create_harvest_source "Snowflake — Risk & Compliance" '{
  "name": "Snowflake — Risk & Compliance",
  "sourceType": "snowflake",
  "baseUrl": "https://meridian.snowflakecomputing.com",
  "region": "eu-west-1",
  "databaseName": "RISK",
  "schemaFilter": ["MARKET_RISK", "CREDIT_RISK", "COMPLIANCE"],
  "credentialRef": "vault://secret/snowflake/risk"
}')
success "Harvest source: Snowflake Risk (${SRC_SNOWFLAKE_RISK})"

SRC_SNOWFLAKE_REFDATA=$(get_or_create_harvest_source "Snowflake — Reference Data" '{
  "name": "Snowflake — Reference Data",
  "sourceType": "snowflake",
  "baseUrl": "https://meridian.snowflakecomputing.com",
  "region": "eu-west-1",
  "databaseName": "REFDATA",
  "schemaFilter": ["SECURITIES", "COUNTERPARTY", "CALENDAR"],
  "credentialRef": "vault://secret/snowflake/refdata"
}')
success "Harvest source: Snowflake Reference Data (${SRC_SNOWFLAKE_REFDATA})"

SRC_GLUE=$(get_or_create_harvest_source "AWS Glue — Data Lake Catalog" '{
  "name": "AWS Glue — Data Lake Catalog",
  "sourceType": "aws_glue",
  "region": "eu-west-1",
  "credentialRef": "vault://secret/aws/glue-readonly"
}')
success "Harvest source: AWS Glue (${SRC_GLUE})"

SRC_DCAT=$(get_or_create_harvest_source "ECB DCAT Data Portal" '{
  "name": "ECB DCAT Data Portal",
  "sourceType": "dcat_http",
  "baseUrl": "https://data.ecb.europa.eu/api/data",
  "credentialRef": ""
}')
success "Harvest source: ECB DCAT (${SRC_DCAT})"

info "Creating harvest jobs (idempotent by name)..."

get_or_create_harvest_job "Daily Trading Schema Harvest" \
  "$(jq -n --arg sid "${SRC_SNOWFLAKE_TRADING}" \
    '{"sourceId":$sid,"name":"Daily Trading Schema Harvest","scheduleCron":"0 1 * * *","fullRefresh":false,"enabled":true}')" > /dev/null
success "Harvest job: Daily Trading Schema Harvest (01:00 daily)"

get_or_create_harvest_job "Daily Risk Schema Harvest" \
  "$(jq -n --arg sid "${SRC_SNOWFLAKE_RISK}" \
    '{"sourceId":$sid,"name":"Daily Risk Schema Harvest","scheduleCron":"0 1 * * *","fullRefresh":false,"enabled":true}')" > /dev/null
success "Harvest job: Daily Risk Schema Harvest (01:00 daily)"

get_or_create_harvest_job "Weekly Reference Data Full Refresh" \
  "$(jq -n --arg sid "${SRC_SNOWFLAKE_REFDATA}" \
    '{"sourceId":$sid,"name":"Weekly Reference Data Full Refresh","scheduleCron":"0 2 * * 0","fullRefresh":true,"enabled":true}')" > /dev/null
success "Harvest job: Weekly Reference Data Full Refresh (Sunday 02:00)"

get_or_create_harvest_job "AWS Glue Data Lake Harvest" \
  "$(jq -n --arg sid "${SRC_GLUE}" \
    '{"sourceId":$sid,"name":"AWS Glue Data Lake Harvest","scheduleCron":"0 3 * * *","fullRefresh":false,"enabled":true}')" > /dev/null
success "Harvest job: AWS Glue Data Lake Harvest (03:00 daily)"

get_or_create_harvest_job "ECB Reference Rates DCAT Harvest" \
  "$(jq -n --arg sid "${SRC_DCAT}" \
    '{"sourceId":$sid,"name":"ECB Reference Rates DCAT Harvest","scheduleCron":"30 16 * * 1-5","fullRefresh":false,"enabled":true}')" > /dev/null
success "Harvest job: ECB DCAT Harvest (16:30 weekdays)"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 12 — Trigger Search Re-index"
# ─────────────────────────────────────────────────────────────────────────────

info "Triggering OpenSearch re-index to pick up all new datasets..."
REINDEX_RESULT=$(curl -sf -H "${HEADER_AUTH}" -H "${HEADER_JSON}" \
  -X POST "http://localhost:8004/api/v1/admin/reindex" 2>/dev/null || echo '{"note":"reindex endpoint not available, search will sync via Kafka"}')
echo "${REINDEX_RESULT}" | python3 -m json.tool 2>/dev/null || echo "${REINDEX_RESULT}"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 13 — Physical Schemas (one per distribution, format-appropriate naming)"
# ─────────────────────────────────────────────────────────────────────────────
# Naming conventions:
#   Snowflake  : UPPER_SNAKE_CASE, abbreviated (TRD_ID, NOTL_AMT, CPTY_LEI)
#   Parquet/CSV: camelCase, full words (tradeId, notionalAmount, counterpartyLei)
#   REST/Kafka : camelCase, full words — JSON field names match lake column names
#   XML/ISO 20022: abbreviated PascalCase XML element names (TxId, TradQty, BuyrId)
#
# Column count per distribution == logical element count for that dataset.

# Load (replace) physical schema for a distribution — always posts to ensure
# correct column definitions. The endpoint does bulk delete + insert.
load_physical_schema() {
  local dist_id="$1" name="$2" body="$3"
  local code count
  code=$(curl -s -o /tmp/_schema_resp.json -w "%{http_code}" -X POST \
    -H "${HEADER_AUTH}" -H "${HEADER_JSON}" \
    "${CATALOG_URL}/api/v1/distributions/${dist_id}/physical-schema" --data "${body}")
  count=$(jq 'length' /tmp/_schema_resp.json 2>/dev/null || echo 0)
  success "Physical schema: ${name} — ${count} columns [HTTP ${code}]"
}

info "Loading physical schemas for 11 distributions (5 datasets × 2-3 formats)..."

# ── Executed Trades — Snowflake (abbreviated UPPER_SNAKE_CASE, NUMBER/CHAR types)
load_physical_schema "${DIST_TRADES_SF}" "Executed Trades / Snowflake" "$(jq -n '[
  {"name":"TRD_ID",        "datatype":"VARCHAR(64)",  "description":"Unique trade identifier",                       "required":true},
  {"name":"INSTMT_ISIN",   "datatype":"CHAR(12)",     "description":"Traded instrument ISIN",                        "required":true},
  {"name":"NOTL_AMT",      "datatype":"NUMBER(18,4)", "description":"Trade notional amount",                         "required":true},
  {"name":"SETL_CCY",      "datatype":"CHAR(3)",      "description":"Settlement currency ISO 4217",                  "required":true},
  {"name":"TRD_PRC",       "datatype":"NUMBER(18,8)", "description":"Executed trade price",                          "required":true},
  {"name":"TRD_DT",        "datatype":"DATE",         "description":"Trade execution date",                          "required":true},
  {"name":"BUY_SELL_IND",  "datatype":"CHAR(1)",      "description":"Direction: B=Buy S=Sell",                       "required":true},
  {"name":"CPTY_LEI",      "datatype":"CHAR(20)",     "description":"Counterparty Legal Entity Identifier",          "required":true},
  {"name":"EXEC_VEN_MIC",  "datatype":"CHAR(4)",      "description":"Execution venue Market Identifier Code",        "required":false},
  {"name":"EXEC_TRDR_ID",  "datatype":"VARCHAR(32)",  "description":"Executing trader identifier",                   "required":true}
]')"

# ── Executed Trades — Parquet lake (camelCase, full words, Parquet types)
load_physical_schema "${DIST_TRADES_PQ}" "Executed Trades / Parquet" "$(jq -n '[
  {"name":"tradeId",            "datatype":"string",        "description":"Unique trade identifier",                  "required":true},
  {"name":"instrumentIsin",     "datatype":"string",        "description":"Traded instrument ISIN",                   "required":true},
  {"name":"notionalAmount",     "datatype":"decimal(18,4)", "description":"Trade notional amount",                    "required":true},
  {"name":"settlementCurrency", "datatype":"string",        "description":"Settlement currency ISO 4217",             "required":true},
  {"name":"tradePrice",         "datatype":"decimal(18,8)", "description":"Executed trade price",                     "required":true},
  {"name":"tradeDate",          "datatype":"date",          "description":"Trade execution date",                     "required":true},
  {"name":"buySellIndicator",   "datatype":"string",        "description":"Direction: BUY or SELL",                   "required":true},
  {"name":"counterpartyLei",    "datatype":"string",        "description":"Counterparty Legal Entity Identifier",     "required":true},
  {"name":"executionVenueMic",  "datatype":"string",        "description":"Execution venue Market Identifier Code",   "required":false},
  {"name":"executingTraderId",  "datatype":"string",        "description":"Executing trader identifier",              "required":true}
]')"

# ── Daily Position Blotter — Snowflake (7 cols = 7 elements)
load_physical_schema "${DIST_POSITIONS_SF}" "Daily Position Blotter / Snowflake" "$(jq -n '[
  {"name":"PORTF_ID",    "datatype":"VARCHAR(16)",  "description":"Portfolio identifier",                  "required":true},
  {"name":"INSTMT_ISIN", "datatype":"CHAR(12)",     "description":"Held instrument ISIN",                  "required":true},
  {"name":"NET_QTY",     "datatype":"NUMBER(18,0)", "description":"Net quantity held (long positive)",     "required":true},
  {"name":"MKT_VAL",     "datatype":"NUMBER(18,4)", "description":"Market value in base currency",         "required":true},
  {"name":"DLY_PNL",     "datatype":"NUMBER(18,4)", "description":"Daily profit and loss",                 "required":true},
  {"name":"BASE_CCY",    "datatype":"CHAR(3)",      "description":"Base currency for valuations",          "required":true},
  {"name":"POS_DT",      "datatype":"DATE",         "description":"Position snapshot date",                "required":true}
]')"

# ── Daily Position Blotter — REST API (camelCase JSON field names, 7 cols)
load_physical_schema "${DIST_POSITIONS_REST}" "Daily Position Blotter / REST API" "$(jq -n '[
  {"name":"portfolioId",   "datatype":"string",  "description":"Portfolio identifier",                     "required":true},
  {"name":"instrumentIsin","datatype":"string",  "description":"Held instrument ISIN",                     "required":true},
  {"name":"netQuantity",   "datatype":"integer", "description":"Net quantity held (long positive)",        "required":true},
  {"name":"marketValue",   "datatype":"number",  "description":"Market value in base currency",            "required":true},
  {"name":"dailyPnl",      "datatype":"number",  "description":"Daily profit and loss",                   "required":true},
  {"name":"baseCurrency",  "datatype":"string",  "description":"Base currency ISO 4217",                  "required":true},
  {"name":"positionDate",  "datatype":"string",  "description":"Position snapshot date (ISO 8601 date)",  "required":true}
]')"

# ── Market Risk VaR — Snowflake (6 cols = 6 elements)
load_physical_schema "${DIST_VAR_SF}" "Market Risk VaR / Snowflake" "$(jq -n '[
  {"name":"PORTF_ID",      "datatype":"VARCHAR(16)",  "description":"Portfolio identifier",                "required":true},
  {"name":"VAR_VAL",       "datatype":"NUMBER(18,4)", "description":"Value at Risk in USD",               "required":true},
  {"name":"SVAR_VAL",      "datatype":"NUMBER(18,4)", "description":"Stressed VaR in USD",                "required":true},
  {"name":"CONF_LVL",      "datatype":"NUMBER(5,4)",  "description":"Confidence level e.g. 0.9900",       "required":true},
  {"name":"HLDG_PRD_DAYS", "datatype":"NUMBER(3,0)",  "description":"Holding period in calendar days",   "required":true},
  {"name":"VAL_DT",        "datatype":"DATE",         "description":"Risk valuation date",                "required":true}
]')"

# ── Market Risk VaR — CSV export (camelCase, full words, 6 cols)
load_physical_schema "${DIST_VAR_CSV}" "Market Risk VaR / CSV" "$(jq -n '[
  {"name":"portfolioId",      "datatype":"string",  "description":"Portfolio identifier",                  "required":true},
  {"name":"varValue",         "datatype":"decimal", "description":"Value at Risk in USD",                  "required":true},
  {"name":"stressedVarValue", "datatype":"decimal", "description":"Stressed VaR in USD",                  "required":true},
  {"name":"confidenceLevel",  "datatype":"decimal", "description":"Confidence level e.g. 0.9900",         "required":true},
  {"name":"holdingPeriodDays","datatype":"integer", "description":"Holding period in calendar days",       "required":true},
  {"name":"valuationDate",    "datatype":"date",    "description":"Risk valuation date (ISO 8601)",        "required":true}
]')"

# ── Securities Master — Snowflake (8 cols = 8 elements)
load_physical_schema "${DIST_SECURITIES_SF}" "Securities Master / Snowflake" "$(jq -n '[
  {"name":"ISIN",        "datatype":"CHAR(12)",     "description":"International Securities Identification Number","required":true},
  {"name":"CUSIP",       "datatype":"CHAR(9)",      "description":"CUSIP identifier",                              "required":false},
  {"name":"SEDOL",       "datatype":"CHAR(7)",      "description":"SEDOL identifier",                              "required":false},
  {"name":"BBG_TICKER",  "datatype":"VARCHAR(32)",  "description":"Bloomberg ticker symbol",                       "required":false},
  {"name":"ASST_CLS",    "datatype":"VARCHAR(32)",  "description":"Asset class (Equity, FixedIncome, FX, Deriv)", "required":true},
  {"name":"DENOM_CCY",   "datatype":"CHAR(3)",      "description":"Denomination currency ISO 4217",               "required":true},
  {"name":"ISSUER_LEI",  "datatype":"CHAR(20)",     "description":"Issuing entity Legal Entity Identifier",       "required":false},
  {"name":"MAT_DT",      "datatype":"DATE",         "description":"Instrument maturity date (null for equities)", "required":false}
]')"

# ── Securities Master — REST API (camelCase JSON, 8 cols)
load_physical_schema "${DIST_SECURITIES_REST}" "Securities Master / REST API" "$(jq -n '[
  {"name":"isin",                "datatype":"string", "description":"International Securities Identification Number","required":true},
  {"name":"cusip",               "datatype":"string", "description":"CUSIP identifier",                              "required":false},
  {"name":"sedol",               "datatype":"string", "description":"SEDOL identifier",                              "required":false},
  {"name":"bloombergTicker",     "datatype":"string", "description":"Bloomberg ticker symbol",                       "required":false},
  {"name":"assetClass",          "datatype":"string", "description":"Asset class (Equity, FixedIncome, FX, Deriv)", "required":true},
  {"name":"denominatedCurrency", "datatype":"string", "description":"Denomination currency ISO 4217",               "required":true},
  {"name":"issuerLei",           "datatype":"string", "description":"Issuing entity Legal Entity Identifier",       "required":false},
  {"name":"maturityDate",        "datatype":"string", "description":"Maturity date ISO 8601 (null for equities)",   "required":false}
]')"

# ── Securities Master — Kafka (camelCase JSON Avro, 8 cols — same names as REST)
load_physical_schema "${DIST_SECURITIES_KAFKA}" "Securities Master / Kafka" "$(jq -n '[
  {"name":"isin",                "datatype":"string", "description":"International Securities Identification Number","required":true},
  {"name":"cusip",               "datatype":"string", "description":"CUSIP identifier",                              "required":false},
  {"name":"sedol",               "datatype":"string", "description":"SEDOL identifier",                              "required":false},
  {"name":"bloombergTicker",     "datatype":"string", "description":"Bloomberg ticker symbol",                       "required":false},
  {"name":"assetClass",          "datatype":"string", "description":"Asset class (Equity, FixedIncome, FX, Deriv)", "required":true},
  {"name":"denominatedCurrency", "datatype":"string", "description":"Denomination currency ISO 4217",               "required":true},
  {"name":"issuerLei",           "datatype":"string", "description":"Issuing entity Legal Entity Identifier",       "required":false},
  {"name":"maturityDate",        "datatype":"string", "description":"Maturity date ISO 8601 (null for equities)",   "required":false}
]')"

# ── MiFID II — ISO 20022 XML (abbreviated PascalCase element names, 9 cols = 9 elements)
load_physical_schema "${DIST_MIFID_XML}" "MiFID II / ISO 20022 XML" "$(jq -n '[
  {"name":"TxId",        "datatype":"xs:string",   "description":"Unique Transaction Identifier (RTS 22 Field 1)",     "required":true},
  {"name":"FinInstrmId", "datatype":"xs:string",   "description":"Financial instrument ISIN (Field 41)",               "required":true},
  {"name":"TxPrc",       "datatype":"xs:decimal",  "description":"Transaction price (Field 33)",                       "required":true},
  {"name":"TradQty",     "datatype":"xs:decimal",  "description":"Transaction quantity (Field 30)",                    "required":true},
  {"name":"PrcCcy",      "datatype":"xs:string",   "description":"Price currency ISO 4217 (Field 34)",                 "required":true},
  {"name":"BuyrId",      "datatype":"xs:string",   "description":"Buyer Legal Entity Identifier (Field 15)",           "required":true},
  {"name":"SellrId",     "datatype":"xs:string",   "description":"Seller Legal Entity Identifier (Field 16)",          "required":true},
  {"name":"TradgVenuId", "datatype":"xs:string",   "description":"Trading venue MIC (Field 36)",                       "required":true},
  {"name":"ExctnTmStmp", "datatype":"xs:dateTime", "description":"Execution timestamp UTC (Field 28)",                 "required":true}
]')"

# ── MiFID II — Parquet lake (camelCase, full words, 9 cols)
load_physical_schema "${DIST_MIFID_PQ}" "MiFID II / Parquet" "$(jq -n '[
  {"name":"uniqueTransactionIdentifier","datatype":"string",        "description":"Unique Transaction Identifier (RTS 22 Field 1)","required":true},
  {"name":"instrumentIsin",             "datatype":"string",        "description":"Financial instrument ISIN (Field 41)",           "required":true},
  {"name":"price",                      "datatype":"decimal(18,8)", "description":"Transaction price (Field 33)",                   "required":true},
  {"name":"tradeQuantity",              "datatype":"decimal(18,4)", "description":"Transaction quantity (Field 30)",                "required":true},
  {"name":"priceCurrency",              "datatype":"string",        "description":"Price currency ISO 4217 (Field 34)",             "required":true},
  {"name":"buyerLei",                   "datatype":"string",        "description":"Buyer Legal Entity Identifier (Field 15)",       "required":true},
  {"name":"sellerLei",                  "datatype":"string",        "description":"Seller Legal Entity Identifier (Field 16)",      "required":true},
  {"name":"tradingVenueMic",            "datatype":"string",        "description":"Trading venue MIC (Field 36)",                   "required":true},
  {"name":"executionTimestamp",         "datatype":"timestamp",     "description":"Execution timestamp UTC (Field 28)",             "required":true}
]')"

# ─────────────────────────────────────────────────────────────────────────────
section "Phase 14 — Bind Physical Columns to Logical Model Elements"
# ─────────────────────────────────────────────────────────────────────────────

# Look up a physical column ID by name from a distribution's schema.
get_dist_col_id() {
  local dist_id="$1" col_name="$2"
  curl -sf -H "${HEADER_AUTH}" \
    "${CATALOG_URL}/api/v1/distributions/${dist_id}/physical-schema" \
    | jq -r --arg n "${col_name}" '.[] | select(.name == $n) | .id' | head -1
}

# Bind a physical column to a logical element.
# Idempotent: the bind API does UPDATE SET, so re-running is safe.
bind_column() {
  local elem_id="$1" dist_id="$2" col_name="$3" label="$4"
  local col_id code
  col_id=$(get_dist_col_id "${dist_id}" "${col_name}")
  if [ -z "${col_id}" ]; then
    info "WARNING: column not found: ${col_name} in distribution ${dist_id}"; return
  fi
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "${HEADER_AUTH}" -H "${HEADER_JSON}" \
    "${CATALOG_URL}/api/v1/logical-data-elements/${elem_id}/bind" \
    --data "$(jq -n --arg cid "${col_id}" '{"physicalColumnId":$cid}')")
  success "Bound: ${label} [HTTP ${code}]"
}

info "Binding logical elements to physical columns across all distributions..."
info "Each element binds to its column in every format (Snowflake + lake/REST/XML)."

# ── Executed Trades — Snowflake (10 bindings: abbreviated names)
bind_column "${EL_TRADE_ID}"     "${DIST_TRADES_SF}" "TRD_ID"       "tradeId → TRD_ID (SF)"
bind_column "${EL_ISIN}"         "${DIST_TRADES_SF}" "INSTMT_ISIN"  "isin → INSTMT_ISIN (SF)"
bind_column "${EL_NOTIONAL}"     "${DIST_TRADES_SF}" "NOTL_AMT"     "notionalAmount → NOTL_AMT (SF)"
bind_column "${EL_CURRENCY}"     "${DIST_TRADES_SF}" "SETL_CCY"     "settleCurrency → SETL_CCY (SF)"
bind_column "${EL_PRICE}"        "${DIST_TRADES_SF}" "TRD_PRC"      "tradePrice → TRD_PRC (SF)"
bind_column "${EL_DATE}"         "${DIST_TRADES_SF}" "TRD_DT"       "tradeDate → TRD_DT (SF)"
bind_column "${EL_DIRECTION}"    "${DIST_TRADES_SF}" "BUY_SELL_IND" "direction → BUY_SELL_IND (SF)"
bind_column "${EL_COUNTERPARTY}" "${DIST_TRADES_SF}" "CPTY_LEI"     "counterpartyLei → CPTY_LEI (SF)"
bind_column "${EL_VENUE}"        "${DIST_TRADES_SF}" "EXEC_VEN_MIC" "executionVenue → EXEC_VEN_MIC (SF)"
bind_column "${EL_TRADER}"       "${DIST_TRADES_SF}" "EXEC_TRDR_ID" "traderId → EXEC_TRDR_ID (SF)"

# ── Executed Trades — Parquet lake (10 bindings: camelCase full words)
bind_column "${EL_TRADE_ID}"     "${DIST_TRADES_PQ}" "tradeId"            "tradeId → tradeId (PQ)"
bind_column "${EL_ISIN}"         "${DIST_TRADES_PQ}" "instrumentIsin"     "isin → instrumentIsin (PQ)"
bind_column "${EL_NOTIONAL}"     "${DIST_TRADES_PQ}" "notionalAmount"     "notionalAmount → notionalAmount (PQ)"
bind_column "${EL_CURRENCY}"     "${DIST_TRADES_PQ}" "settlementCurrency" "settleCurrency → settlementCurrency (PQ)"
bind_column "${EL_PRICE}"        "${DIST_TRADES_PQ}" "tradePrice"         "tradePrice → tradePrice (PQ)"
bind_column "${EL_DATE}"         "${DIST_TRADES_PQ}" "tradeDate"          "tradeDate → tradeDate (PQ)"
bind_column "${EL_DIRECTION}"    "${DIST_TRADES_PQ}" "buySellIndicator"   "direction → buySellIndicator (PQ)"
bind_column "${EL_COUNTERPARTY}" "${DIST_TRADES_PQ}" "counterpartyLei"    "counterpartyLei → counterpartyLei (PQ)"
bind_column "${EL_VENUE}"        "${DIST_TRADES_PQ}" "executionVenueMic"  "executionVenue → executionVenueMic (PQ)"
bind_column "${EL_TRADER}"       "${DIST_TRADES_PQ}" "executingTraderId"  "traderId → executingTraderId (PQ)"

# ── Daily Position Blotter — Snowflake (7 bindings)
bind_column "${EL_POS_PORTFOLIO}" "${DIST_POSITIONS_SF}" "PORTF_ID"    "portfolioId → PORTF_ID (SF)"
bind_column "${EL_POS_ISIN}"      "${DIST_POSITIONS_SF}" "INSTMT_ISIN" "isin → INSTMT_ISIN (SF)"
bind_column "${EL_POS_QTY}"       "${DIST_POSITIONS_SF}" "NET_QTY"     "netQuantity → NET_QTY (SF)"
bind_column "${EL_POS_MV}"        "${DIST_POSITIONS_SF}" "MKT_VAL"     "marketValue → MKT_VAL (SF)"
bind_column "${EL_POS_PNL}"       "${DIST_POSITIONS_SF}" "DLY_PNL"     "dailyPnl → DLY_PNL (SF)"
bind_column "${EL_POS_CCY}"       "${DIST_POSITIONS_SF}" "BASE_CCY"    "baseCurrency → BASE_CCY (SF)"
bind_column "${EL_POS_DATE}"      "${DIST_POSITIONS_SF}" "POS_DT"      "positionDate → POS_DT (SF)"

# ── Daily Position Blotter — REST API (7 bindings)
bind_column "${EL_POS_PORTFOLIO}" "${DIST_POSITIONS_REST}" "portfolioId"    "portfolioId → portfolioId (REST)"
bind_column "${EL_POS_ISIN}"      "${DIST_POSITIONS_REST}" "instrumentIsin" "isin → instrumentIsin (REST)"
bind_column "${EL_POS_QTY}"       "${DIST_POSITIONS_REST}" "netQuantity"    "netQuantity → netQuantity (REST)"
bind_column "${EL_POS_MV}"        "${DIST_POSITIONS_REST}" "marketValue"    "marketValue → marketValue (REST)"
bind_column "${EL_POS_PNL}"       "${DIST_POSITIONS_REST}" "dailyPnl"       "dailyPnl → dailyPnl (REST)"
bind_column "${EL_POS_CCY}"       "${DIST_POSITIONS_REST}" "baseCurrency"   "baseCurrency → baseCurrency (REST)"
bind_column "${EL_POS_DATE}"      "${DIST_POSITIONS_REST}" "positionDate"   "positionDate → positionDate (REST)"

# ── Market Risk VaR — Snowflake (6 bindings)
bind_column "${EL_VAR_PORTFOLIO}" "${DIST_VAR_SF}" "PORTF_ID"      "portfolioId → PORTF_ID (SF)"
bind_column "${EL_VAR_VALUE}"     "${DIST_VAR_SF}" "VAR_VAL"       "varValue → VAR_VAL (SF)"
bind_column "${EL_VAR_SVAR}"      "${DIST_VAR_SF}" "SVAR_VAL"      "stressedVar → SVAR_VAL (SF)"
bind_column "${EL_VAR_CONF}"      "${DIST_VAR_SF}" "CONF_LVL"      "confidenceLevel → CONF_LVL (SF)"
bind_column "${EL_VAR_HORIZON}"   "${DIST_VAR_SF}" "HLDG_PRD_DAYS" "holdingPeriod → HLDG_PRD_DAYS (SF)"
bind_column "${EL_VAR_DATE}"      "${DIST_VAR_SF}" "VAL_DT"        "valuationDate → VAL_DT (SF)"

# ── Market Risk VaR — CSV (6 bindings)
bind_column "${EL_VAR_PORTFOLIO}" "${DIST_VAR_CSV}" "portfolioId"       "portfolioId → portfolioId (CSV)"
bind_column "${EL_VAR_VALUE}"     "${DIST_VAR_CSV}" "varValue"          "varValue → varValue (CSV)"
bind_column "${EL_VAR_SVAR}"      "${DIST_VAR_CSV}" "stressedVarValue"  "stressedVar → stressedVarValue (CSV)"
bind_column "${EL_VAR_CONF}"      "${DIST_VAR_CSV}" "confidenceLevel"   "confidenceLevel → confidenceLevel (CSV)"
bind_column "${EL_VAR_HORIZON}"   "${DIST_VAR_CSV}" "holdingPeriodDays" "holdingPeriod → holdingPeriodDays (CSV)"
bind_column "${EL_VAR_DATE}"      "${DIST_VAR_CSV}" "valuationDate"     "valuationDate → valuationDate (CSV)"

# ── Securities Master — Snowflake (8 bindings)
bind_column "${EL_SEC_ISIN}"     "${DIST_SECURITIES_SF}" "ISIN"       "isin → ISIN (SF)"
bind_column "${EL_SEC_CUSIP}"    "${DIST_SECURITIES_SF}" "CUSIP"      "cusip → CUSIP (SF)"
bind_column "${EL_SEC_SEDOL}"    "${DIST_SECURITIES_SF}" "SEDOL"      "sedol → SEDOL (SF)"
bind_column "${EL_SEC_TICKER}"   "${DIST_SECURITIES_SF}" "BBG_TICKER" "bloombergTicker → BBG_TICKER (SF)"
bind_column "${EL_SEC_CLASS}"    "${DIST_SECURITIES_SF}" "ASST_CLS"   "assetClass → ASST_CLS (SF)"
bind_column "${EL_SEC_CCY}"      "${DIST_SECURITIES_SF}" "DENOM_CCY"  "denominatedCurrency → DENOM_CCY (SF)"
bind_column "${EL_SEC_ISSUER}"   "${DIST_SECURITIES_SF}" "ISSUER_LEI" "issuerLei → ISSUER_LEI (SF)"
bind_column "${EL_SEC_MATURITY}" "${DIST_SECURITIES_SF}" "MAT_DT"     "maturityDate → MAT_DT (SF)"

# ── Securities Master — REST API (8 bindings)
bind_column "${EL_SEC_ISIN}"     "${DIST_SECURITIES_REST}" "isin"                "isin → isin (REST)"
bind_column "${EL_SEC_CUSIP}"    "${DIST_SECURITIES_REST}" "cusip"               "cusip → cusip (REST)"
bind_column "${EL_SEC_SEDOL}"    "${DIST_SECURITIES_REST}" "sedol"               "sedol → sedol (REST)"
bind_column "${EL_SEC_TICKER}"   "${DIST_SECURITIES_REST}" "bloombergTicker"     "bloombergTicker → bloombergTicker (REST)"
bind_column "${EL_SEC_CLASS}"    "${DIST_SECURITIES_REST}" "assetClass"          "assetClass → assetClass (REST)"
bind_column "${EL_SEC_CCY}"      "${DIST_SECURITIES_REST}" "denominatedCurrency" "denominatedCurrency → denominatedCurrency (REST)"
bind_column "${EL_SEC_ISSUER}"   "${DIST_SECURITIES_REST}" "issuerLei"           "issuerLei → issuerLei (REST)"
bind_column "${EL_SEC_MATURITY}" "${DIST_SECURITIES_REST}" "maturityDate"        "maturityDate → maturityDate (REST)"

# ── Securities Master — Kafka (8 bindings — same camelCase names as REST)
bind_column "${EL_SEC_ISIN}"     "${DIST_SECURITIES_KAFKA}" "isin"                "isin → isin (Kafka)"
bind_column "${EL_SEC_CUSIP}"    "${DIST_SECURITIES_KAFKA}" "cusip"               "cusip → cusip (Kafka)"
bind_column "${EL_SEC_SEDOL}"    "${DIST_SECURITIES_KAFKA}" "sedol"               "sedol → sedol (Kafka)"
bind_column "${EL_SEC_TICKER}"   "${DIST_SECURITIES_KAFKA}" "bloombergTicker"     "bloombergTicker → bloombergTicker (Kafka)"
bind_column "${EL_SEC_CLASS}"    "${DIST_SECURITIES_KAFKA}" "assetClass"          "assetClass → assetClass (Kafka)"
bind_column "${EL_SEC_CCY}"      "${DIST_SECURITIES_KAFKA}" "denominatedCurrency" "denominatedCurrency → denominatedCurrency (Kafka)"
bind_column "${EL_SEC_ISSUER}"   "${DIST_SECURITIES_KAFKA}" "issuerLei"           "issuerLei → issuerLei (Kafka)"
bind_column "${EL_SEC_MATURITY}" "${DIST_SECURITIES_KAFKA}" "maturityDate"        "maturityDate → maturityDate (Kafka)"

# ── MiFID II — ISO 20022 XML (9 bindings: abbreviated XML element names)
bind_column "${EL_MIF_UTI}"      "${DIST_MIFID_XML}" "TxId"        "uti → TxId (XML)"
bind_column "${EL_MIF_ISIN}"     "${DIST_MIFID_XML}" "FinInstrmId" "instrumentIsin → FinInstrmId (XML)"
bind_column "${EL_MIF_PRICE}"    "${DIST_MIFID_XML}" "TxPrc"       "price → TxPrc (XML)"
bind_column "${EL_MIF_QTY}"      "${DIST_MIFID_XML}" "TradQty"     "quantity → TradQty (XML)"
bind_column "${EL_MIF_CCY}"      "${DIST_MIFID_XML}" "PrcCcy"      "priceCurrency → PrcCcy (XML)"
bind_column "${EL_MIF_BUY_LEI}"  "${DIST_MIFID_XML}" "BuyrId"      "buyerLei → BuyrId (XML)"
bind_column "${EL_MIF_SELL_LEI}" "${DIST_MIFID_XML}" "SellrId"     "sellerLei → SellrId (XML)"
bind_column "${EL_MIF_VENUE}"    "${DIST_MIFID_XML}" "TradgVenuId" "tradingVenueMic → TradgVenuId (XML)"
bind_column "${EL_MIF_TS}"       "${DIST_MIFID_XML}" "ExctnTmStmp" "executionTimestamp → ExctnTmStmp (XML)"

# ── MiFID II — Parquet lake (9 bindings: camelCase full words)
bind_column "${EL_MIF_UTI}"      "${DIST_MIFID_PQ}" "uniqueTransactionIdentifier" "uti → uniqueTransactionIdentifier (PQ)"
bind_column "${EL_MIF_ISIN}"     "${DIST_MIFID_PQ}" "instrumentIsin"              "instrumentIsin → instrumentIsin (PQ)"
bind_column "${EL_MIF_PRICE}"    "${DIST_MIFID_PQ}" "price"                       "price → price (PQ)"
bind_column "${EL_MIF_QTY}"      "${DIST_MIFID_PQ}" "tradeQuantity"               "quantity → tradeQuantity (PQ)"
bind_column "${EL_MIF_CCY}"      "${DIST_MIFID_PQ}" "priceCurrency"               "priceCurrency → priceCurrency (PQ)"
bind_column "${EL_MIF_BUY_LEI}"  "${DIST_MIFID_PQ}" "buyerLei"                    "buyerLei → buyerLei (PQ)"
bind_column "${EL_MIF_SELL_LEI}" "${DIST_MIFID_PQ}" "sellerLei"                   "sellerLei → sellerLei (PQ)"
bind_column "${EL_MIF_VENUE}"    "${DIST_MIFID_PQ}" "tradingVenueMic"             "tradingVenueMic → tradingVenueMic (PQ)"
bind_column "${EL_MIF_TS}"       "${DIST_MIFID_PQ}" "executionTimestamp"          "executionTimestamp → executionTimestamp (PQ)"

# ─────────────────────────────────────────────────────────────────────────────
section "Seed Complete"
# ─────────────────────────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN} ODIN Catalog seed data loaded — Meridian Capital scenario${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  Catalogs   :  4 (Trading, Risk, Reference Data, Compliance)"
echo "  Datasets   : 12 (trades, positions, orders, VAR, CCR, risk metrics,"
echo "                securities, counterparties, FX rates, MiFID, FinRep, EMIR)"
echo "  Distrib.   : 14 (Snowflake, Parquet, REST, Kafka, XML)"
echo "  Phys.schema: 11 (one per distribution — Snowflake abbreviated, lake/REST/Kafka camelCase, XML ISO 20022)"
echo "  Vocab      : FIBO (FND/FBC/SEC/MD) + schema.org profiles on all datasets"
echo "  Log.models :  6 (Trades, Positions, Securities, VaR, MiFID, CCR) — 65 elements"
echo "  Phys.binds : 88 (each element bound to its column in every distribution format)"
echo "  FIBO maps  : 25+ concept mappings (ISIN, LEI, MonetaryAmount, Currency, ...)"
echo "  Data Prods :  5 (Consume×2, Deploy, Build, Design lifecycle stages)"
echo "  Lineage    :  8 OpenLineage jobs — 4-hop pipeline (securities→finrep)"
echo "  DDL views  :  3 (v_counterparty_exposure, v_mifid_enriched, v_daily_risk_summary)"
echo "  Harvest    :  5 sources (Snowflake×3, AWS Glue, ECB DCAT) + 5 scheduled jobs"
echo ""
echo "  All phases are idempotent — safe to re-run without creating duplicates."
echo ""
echo "  Try it:"
echo "    Search  → http://localhost:3001"
echo "    Manage  → http://localhost:3000"
echo "    Search API → curl 'http://localhost:8004/api/v1/search?q=trades'"
echo "    Lineage → curl 'http://localhost:8003/api/v1/datasets/trading/executed_trades/lineage?direction=upstream&depth=3'"
echo ""
