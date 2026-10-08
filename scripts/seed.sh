#!/usr/bin/env bash
# Creates a few extra fictitious patients through the gateway, as the admin user.
# Base demo data (alice, Dr Martin, ...) is already loaded by Flyway (db/demo migrations).
set -euo pipefail
cd "$(dirname "$0")"
API="${API_URL:-http://localhost:8200/api}"
TOKEN=$(./token.sh admin)

create() {
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/patients" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$1")
  case "$code" in
    201) echo "created: $2" ;;
    409) echo "exists:  $2" ;;
    *) echo "error $code for $2" >&2; return 1 ;;
  esac
}

create '{"firstName":"Camille","lastName":"Rousseau","email":"camille.rousseau@example.fr","phone":"06 45 67 89 10","birthDate":"1994-09-03"}' "Camille Rousseau"
create '{"firstName":"Hugo","lastName":"Lambert","email":"hugo.lambert@example.fr","birthDate":"2015-02-14"}' "Hugo Lambert"
create '{"firstName":"Nadia","lastName":"Benkirane","email":"nadia.benkirane@example.fr","phone":"07 98 12 34 56","birthDate":"1972-12-01"}' "Nadia Benkirane"
