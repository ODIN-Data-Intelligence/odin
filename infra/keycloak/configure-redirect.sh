#!/bin/sh
# Registers APP_URL/* in the Keycloak catalog-frontend client's allowed redirect URIs.
# Safe to run on every startup — skips if the URI is already present.
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8180}"
APP_URL="${APP_URL:-http://localhost:3000}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="datacatalog"
CLIENT_ID="catalog-frontend"

echo "keycloak-config: waiting for Keycloak at ${KEYCLOAK_URL}..."
until wget -qO- "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
  sleep 3
done
echo "keycloak-config: Keycloak is ready."

python3 - <<PYEOF
import json, sys, urllib.request, urllib.parse

base      = "${KEYCLOAK_URL}"
admin     = "${ADMIN_USER}"
password  = "${ADMIN_PASS}"
realm     = "${REALM}"
client_id = "${CLIENT_ID}"
app_url   = "${APP_URL}"
new_uri   = app_url.rstrip('/') + '/*'

def form_post(url, data):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body,
          headers={'Content-Type': 'application/x-www-form-urlencoded'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def api(method, url, token, data=None):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method,
          headers={'Authorization': 'Bearer ' + token,
                   'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read()) if r.length else {}
    except urllib.error.HTTPError as e:
        print('HTTP', e.code, e.read().decode()); sys.exit(1)

# 1. Admin token
token = form_post(f'{base}/realms/master/protocol/openid-connect/token', {
    'client_id': 'admin-cli', 'username': admin,
    'password': password, 'grant_type': 'password',
})['access_token']

# 2. Find catalog-frontend UUID
clients = api('GET', f'{base}/admin/realms/{realm}/clients?clientId={client_id}', token)
if not clients:
    print(f'Client {client_id} not found — skipping.'); sys.exit(0)
uuid = clients[0]['id']

# 3. Read current config
client = api('GET', f'{base}/admin/realms/{realm}/clients/{uuid}', token)
current = client.get('redirectUris', [])

if new_uri in current:
    print(f'keycloak-config: {new_uri} already in redirectUris — nothing to do.')
else:
    # 4. Patch redirectUris and PUT back
    client['redirectUris'] = current + [new_uri]
    api('PUT', f'{base}/admin/realms/{realm}/clients/{uuid}', token, data=client)
    print(f'keycloak-config: added redirect URI -> {new_uri}')

# 5. Grant realm-management roles to identity-service service account
MGMT_ROLES = ['view-users', 'manage-users', 'query-users', 'query-roles', 'view-realm']
svc_username = 'service-account-identity-service'

# Find the service account user
svc_users = api('GET', f'{base}/admin/realms/{realm}/users?username={svc_username}&exact=true', token)
if not svc_users:
    print(f'keycloak-config: service account user {svc_username!r} not found — skipping role grant.')
else:
    svc_uid = svc_users[0]['id']
    # Find realm-management client UUID
    rm_clients = api('GET', f'{base}/admin/realms/{realm}/clients?clientId=realm-management', token)
    if rm_clients:
        rm_uuid = rm_clients[0]['id']
        # Get available client roles
        available = api('GET', f'{base}/admin/realms/{realm}/clients/{rm_uuid}/roles', token)
        to_add = [r for r in available if r['name'] in MGMT_ROLES]
        # Get already-assigned client roles
        existing = api('GET',
            f'{base}/admin/realms/{realm}/users/{svc_uid}/role-mappings/clients/{rm_uuid}', token)
        existing_names = {r['name'] for r in existing}
        new_roles = [r for r in to_add if r['name'] not in existing_names]
        if new_roles:
            api('POST',
                f'{base}/admin/realms/{realm}/users/{svc_uid}/role-mappings/clients/{rm_uuid}',
                token, data=new_roles)
            print(f'keycloak-config: granted realm-management roles to {svc_username}: '
                  f'{[r["name"] for r in new_roles]}')
        else:
            print(f'keycloak-config: {svc_username} already has required realm-management roles.')
PYEOF
