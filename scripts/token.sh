#!/usr/bin/env bash
# Prints an access token for a demo user: password grant on the dev-only client "medirdv-cli"
# (the SPA client "medirdv-web" only allows authorization code + PKCE).
#   TOKEN=$(scripts/token.sh alice)          # alice / dr.martin / admin (password = username)
set -euo pipefail

USERNAME="${1:-alice}"
PASSWORD="${2:-$USERNAME}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8280}"

response=$(curl -fsS -X POST "$KEYCLOAK_URL/realms/medirdv/protocol/openid-connect/token" \
  -d grant_type=password -d client_id="${KEYCLOAK_CLIENT_ID:-medirdv-cli}" \
  --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD")

if command -v jq > /dev/null; then
  jq -r .access_token <<< "$response"
else
  python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])' <<< "$response"
fi
