-- Backfill Keycloak UUIDs for seeded users.
-- IDs sourced from the datacatalog realm via kcadm.sh get users.
UPDATE catalog_users SET keycloak_user_id = 'e9fc7f30-484b-42e1-aae7-fb3121c6f027' WHERE email = 'admin@datacatalog.local'              AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = '08e03a71-f5ab-43c6-a5e8-3e2ab7cef292' WHERE email = 'governance@datacatalog.local'          AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = '568ecd68-2c76-4f72-86eb-a080a9ebc3eb' WHERE email = 'owner@datacatalog.local'               AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = 'a6da9d8c-0d2b-471d-88bb-70d107cfd4f7' WHERE email = 'steward@datacatalog.local'             AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = '43947296-166a-46c1-b4b9-2c147871110a' WHERE email = 'trading.owner@datacatalog.local'       AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = 'a7719d57-5079-44e3-9bcf-7526d7998a96' WHERE email = 'risk.owner@datacatalog.local'          AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = 'b89bf0f0-7232-4d5f-8e5a-3c537522ff4b' WHERE email = 'refdata.owner@datacatalog.local'       AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = '43ecc7a0-81b3-4109-a979-2f00be7f829c' WHERE email = 'compliance.owner@datacatalog.local'    AND keycloak_user_id IS NULL;
UPDATE catalog_users SET keycloak_user_id = 'b49d27c2-912e-48fa-9d7e-2c8ebf65a028' WHERE email = 'finance.owner@datacatalog.local'       AND keycloak_user_id IS NULL;
