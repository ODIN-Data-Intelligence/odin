// Runtime configuration injected by the nginx entrypoint.
// ${KEYCLOAK_URL} is replaced by envsubst at container startup.
window.__ODIN_CONFIG__ = {
  keycloakUrl: "${KEYCLOAK_URL}"
};
