-- Five additional data owner accounts for development and testing.
-- These match the accounts added to infra/keycloak/datacatalog-realm.json.

INSERT INTO catalog_users (id, tenant_id, email, first_name, last_name, active, roles, permissions)
VALUES
  ('a0000000-0000-0000-0000-000000000005',
   '00000000-0000-0000-0000-000000000001',
   'trading.owner@datacatalog.local', 'Alice', 'Chen', TRUE,
   ARRAY['data-owner'], ARRAY['catalog:read','catalog:write']),

  ('a0000000-0000-0000-0000-000000000006',
   '00000000-0000-0000-0000-000000000001',
   'risk.owner@datacatalog.local', 'Marcus', 'Webb', TRUE,
   ARRAY['data-owner'], ARRAY['catalog:read','catalog:write']),

  ('a0000000-0000-0000-0000-000000000007',
   '00000000-0000-0000-0000-000000000001',
   'refdata.owner@datacatalog.local', 'Priya', 'Nair', TRUE,
   ARRAY['data-owner'], ARRAY['catalog:read','catalog:write']),

  ('a0000000-0000-0000-0000-000000000008',
   '00000000-0000-0000-0000-000000000001',
   'compliance.owner@datacatalog.local', 'David', 'Park', TRUE,
   ARRAY['data-owner'], ARRAY['catalog:read','catalog:write']),

  ('a0000000-0000-0000-0000-000000000009',
   '00000000-0000-0000-0000-000000000001',
   'finance.owner@datacatalog.local', 'Sofia', 'Reyes', TRUE,
   ARRAY['data-owner'], ARRAY['catalog:read','catalog:write'])

ON CONFLICT (email) DO NOTHING;
