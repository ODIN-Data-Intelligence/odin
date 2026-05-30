-- Seed the four default Keycloak realm users into catalog_users so that the
-- user management API returns results on a fresh install.
-- These match the accounts in infra/keycloak/datacatalog-realm.json.

INSERT INTO catalog_users (id, tenant_id, email, first_name, last_name, active, roles, permissions)
VALUES
  ('a0000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000001',
   'admin@datacatalog.local', 'Catalog', 'Admin', TRUE,
   ARRAY['administrator'], ARRAY['catalog:read','catalog:write','catalog:admin']),

  ('a0000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000001',
   'governance@datacatalog.local', 'Grace', 'Governance', TRUE,
   ARRAY['data-governance'], ARRAY['catalog:read','catalog:write']),

  ('a0000000-0000-0000-0000-000000000003',
   '00000000-0000-0000-0000-000000000001',
   'owner@datacatalog.local', 'Owen', 'Owner', TRUE,
   ARRAY['data-owner'], ARRAY['catalog:read','catalog:write']),

  ('a0000000-0000-0000-0000-000000000004',
   '00000000-0000-0000-0000-000000000001',
   'steward@datacatalog.local', 'Sam', 'Steward', TRUE,
   ARRAY['data-steward'], ARRAY['catalog:read','catalog:write'])

ON CONFLICT (email) DO NOTHING;
