#!/bin/sh
set -e

# Replace ${KEYCLOAK_URL} in the runtime config with the actual env var.
# Defaults to localhost if not set.
export KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"

envsubst '${KEYCLOAK_URL}' \
  < /usr/share/nginx/html/config.js \
  > /usr/share/nginx/html/config.js.tmp \
  && mv /usr/share/nginx/html/config.js.tmp /usr/share/nginx/html/config.js

exec nginx -g 'daemon off;'
