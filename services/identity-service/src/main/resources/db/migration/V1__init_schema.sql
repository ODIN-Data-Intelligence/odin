CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE organizations (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name         TEXT NOT NULL UNIQUE,
    display_name TEXT,
    description  TEXT,
    plan         VARCHAR(20) DEFAULT 'standard',
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE domains (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        UUID NOT NULL,
    name             TEXT NOT NULL,
    description      TEXT,
    parent_domain_id UUID REFERENCES domains(id),
    owner_id         UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE catalog_users (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id         UUID NOT NULL,
    email             TEXT NOT NULL UNIQUE,
    first_name        TEXT,
    last_name         TEXT,
    keycloak_user_id  TEXT UNIQUE,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    roles             TEXT[]  DEFAULT '{}',
    permissions       TEXT[]  DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_tenant ON catalog_users (tenant_id);

CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id    UUID NOT NULL,
    owner_id     UUID NOT NULL,
    key_hash     TEXT NOT NULL UNIQUE,
    description  TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at   TIMESTAMPTZ,
    scopes       TEXT[]  DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_apikeys_tenant ON api_keys (tenant_id);
CREATE INDEX idx_apikeys_hash   ON api_keys (key_hash);
