#!/bin/sh
# Writes the runtime configuration of the Angular app from environment variables.
# Served as /config.json (see nginx.conf). /tmp is writable even with a read-only root filesystem.
set -eu
json_escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
cat > /tmp/medirdv-config.json <<JSON
{
  "keycloakUrl": "$(json_escape "${KEYCLOAK_URL:-http://localhost:8280}")",
  "keycloakRealm": "$(json_escape "${KEYCLOAK_REALM:-medirdv}")",
  "keycloakClientId": "$(json_escape "${KEYCLOAK_CLIENT_ID:-medirdv-web}")",
  "apiUrl": "$(json_escape "${API_URL:-/api}")"
}
JSON
echo "medirdv: runtime config written (keycloakUrl=${KEYCLOAK_URL:-http://localhost:8280}, apiUrl=${API_URL:-/api})"
