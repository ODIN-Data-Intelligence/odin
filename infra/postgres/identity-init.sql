-- Create a separate database for Keycloak on the same postgres instance
SELECT 'CREATE DATABASE keycloak OWNER identity'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
