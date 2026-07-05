package com.odin.catalog.shared.kafka.topics;

public final class CatalogTopics {

    private CatalogTopics() {}

    public static final String DATA_PRODUCTS_CHANGES    = "inventory.data-products.changes";
    public static final String DATASETS_CHANGES         = "inventory.datasets.changes";
    public static final String DISTRIBUTIONS_CHANGES    = "inventory.distributions.changes";

    public static final String HARVEST_RUNS_EVENTS      = "harvest.runs.events";
    public static final String HARVEST_ENTITIES         = "harvest.entities.discovered";
    public static final String HARVEST_DDL              = "harvest.ddl.discovered";

    public static final String LINEAGE_RUN_EVENTS       = "lineage.run-events.received";
    public static final String LINEAGE_GRAPH_UPDATED    = "lineage.graph.updated";

    public static final String AI_EMBEDDINGS_REQUESTED  = "ai.embeddings.requested";

    public static final String IDENTITY_USERS_CHANGES   = "identity.users.changes";

    public static final String POLICY_RECORDS_CHANGES        = "policy.records.changes";
    public static final String POLICY_EVALUATIONS_COMPLETED  = "policy.evaluations.completed";
}
