#!/bin/bash
set -e

BOOTSTRAP=kafka:9092
KAFKA_BIN=/opt/kafka/bin/kafka-topics.sh

create() {
  local name=$1 partitions=$2 cleanup_policy=${3:-delete}
  echo "Creating topic: $name (partitions=$partitions, cleanup=$cleanup_policy)"
  $KAFKA_BIN --bootstrap-server $BOOTSTRAP --create --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config cleanup.policy="$cleanup_policy" \
    --config retention.ms=604800000
}

# Inventory — compacted (entity changelog)
create inventory.data-products.changes  12 compact
create inventory.datasets.changes       12 compact
create inventory.distributions.changes  6  compact

# Harvest — delete (event log)
create harvest.runs.events            6
create harvest.entities.discovered    12
create harvest.ddl.discovered         3

# Lineage
create lineage.run-events.received    12
create lineage.graph.updated          6

# AI
create ai.embeddings.requested        6

# Identity
create identity.users.changes         3  compact

echo "All topics created."
