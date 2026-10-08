#!/usr/bin/env bash
# End-to-end check of the booking saga against a running stack (docker compose up -d --build):
#   1. alice books the next free slot of Dr Martin through the gateway
#   2. the appointment goes PENDING_VALIDATION -> CONFIRMED (patient-service validated alice)
#   3. a confirmation notification is created
#   4. dr.martin cancels it, alice receives a cancellation notification
set -euo pipefail

cd "$(dirname "$0")"
API="${API_URL:-http://localhost:8200/api}"
DR_MARTIN="00000000-0000-4000-b000-000000000001"

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

ALICE=$(./token.sh alice)
DOCTOR=$(./token.sh dr.martin)

FROM=$(date +%F)
TO=$(date -d "+14 days" +%F)
SLOT=$(curl -fsS -H "Authorization: Bearer $ALICE" "$API/practitioners/$DR_MARTIN/slots?from=$FROM&to=$TO" \
  | json 'd[0]["start"]')
echo "1. Booking slot $SLOT with Dr Martin as alice"

BOOKING=$(curl -fsS -X POST -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' \
  -d "{\"practitionerId\":\"$DR_MARTIN\",\"start\":\"$SLOT\",\"reason\":\"Consultation de contrôle\"}" \
  "$API/appointments")
ID=$(json 'd["id"]' <<< "$BOOKING")
echo "   -> appointment $ID status $(json 'd["status"]' <<< "$BOOKING")"

STATUS=""
for _ in $(seq 1 30); do
  STATUS=$(curl -fsS -H "Authorization: Bearer $ALICE" "$API/appointments/$ID" | json 'd["status"]')
  [ "$STATUS" != "PENDING_VALIDATION" ] && break
  sleep 1
done
echo "2. Saga finished with status $STATUS"
[ "$STATUS" = "CONFIRMED" ] || { echo "Expected CONFIRMED" >&2; exit 1; }

notification_for() {
  for _ in $(seq 1 30); do
    BODY=$(curl -fsS -H "Authorization: Bearer $ALICE" "$API/notifications" \
      | json "next((n['body'] for n in d if n['appointmentId']=='$ID' and n['type']=='$1'), '')")
    [ -n "$BODY" ] && { echo "$BODY"; return 0; }
    sleep 1
  done
  return 1
}
echo "3. Notification: $(notification_for APPOINTMENT_CONFIRMED)"

CANCELLED=$(curl -fsS -X DELETE -H "Authorization: Bearer $DOCTOR" "$API/appointments/$ID?reasonCode=PRACTITIONER_UNAVAILABLE&comment=Formation%20continue")
echo "4. Cancelled by dr.martin -> status $(json 'd["status"]' <<< "$CANCELLED")"
echo "   Notification: $(notification_for APPOINTMENT_CANCELLED)"
echo "OK"
