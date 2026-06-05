CREATE TABLE bookmark_collections (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bm_collections_user ON bookmark_collections(user_id, tenant_id);

CREATE TABLE bookmarks (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    dataset_id    UUID        NOT NULL,
    dataset_title TEXT        NOT NULL,
    collection_id UUID        REFERENCES bookmark_collections(id) ON DELETE SET NULL,
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, dataset_id)
);

CREATE INDEX idx_bookmarks_user       ON bookmarks(user_id, tenant_id);
CREATE INDEX idx_bookmarks_collection ON bookmarks(collection_id);
