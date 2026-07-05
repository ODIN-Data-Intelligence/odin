CREATE TABLE dataset_semantic_tags (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id      UUID        NOT NULL REFERENCES datasets(resource_id) ON DELETE CASCADE,
    semantic_type   VARCHAR(255) NOT NULL,
    vocabulary_iri  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dst_dataset_id ON dataset_semantic_tags (dataset_id);
