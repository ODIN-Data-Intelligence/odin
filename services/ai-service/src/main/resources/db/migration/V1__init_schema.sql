CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE conversations (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id  UUID NOT NULL,
    user_id    UUID,
    title      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_messages (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL CHECK (role IN ('user','assistant','system','tool')),
    content          TEXT NOT NULL,
    token_count      INT,
    model_used       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation ON conversation_messages (conversation_id);

-- pgvector embedding table (Spring AI PgVectorStore creates its own table,
-- but we define this schema for reference)
CREATE TABLE embedding_documents (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID,
    entity_type VARCHAR(30),
    entity_id   UUID,
    chunk_index INT,
    content     TEXT,
    embedding   VECTOR(768),
    model_name  TEXT,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (entity_id, chunk_index, model_name)
);

CREATE INDEX ON embedding_documents USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
