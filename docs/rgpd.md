# Personal and health data (RGPD)

MediRDV handles data concerning health (art. 9 RGPD): knowing that a person sees a dermatologist
at a given time is health data. The design minimises where that data travels.

## Where data lives

| Data | Where | Notes |
|---|---|---|
| Identity and contact (name, e-mail, phone, birth date) | patient-service database only | Never copied to other services or to Kafka |
| Visit reason typed by the patient | scheduling-service database only | Not in events, not in notifications |
| Cancellation comment typed by staff or patient (≤ 500 chars) | scheduling-service database only | Events carry a coded `reasonCode` instead |
| Appointment (patient id, practitioner id, time slot, status) | scheduling-service | Pseudonymous: linked to a person only through patient-service |
| Notification body (date, status) | notification-service | No name or address; a real sender would resolve the address from patient-service at send time |
| Account (username, e-mail, roles, `patient_id` attribute) | Keycloak | |

## Rules enforced in code

- **Events carry identifiers, instants and codes only** (`events` module).
  `EventCodecTest.payloadsNeverContainPersonalData` fails the build if a payload field is named
  like a name, e-mail, phone, birth date or comment, and `payloadsHaveNoFreeTextFields` fails if a
  payload has any field that is not a UUID, an instant or an enum: free text, which may contain
  health information, cannot be published. Cancellation and rejection reasons are enums.
- **Pseudonymised logs**: `Patient#toString` masks names (initial), e-mail (`a***@example.fr`) and
  phone (last two digits). Services log identifiers, never bodies of personal data.
- **Access control in every service**, not only at the gateway, failing closed: a patient can only
  read their own record, appointments and notifications (`patient_id` claim); a practitioner only
  the appointments and notifications of their own practice (`practitioner_id` claim).
- **Demo data** (fictitious) is only loaded with the `demo` Spring profile, never by the Helm chart.
- **Deactivation** (right to object / account closure): `POST /patients/{id}/deactivate` stops new
  bookings (saga rejects them) and cancels future appointments through `PatientDeactivated`.

## Not covered by this demo (would be required in production)

- Hosting on an HDS-certified provider (Hébergeur de Données de Santé) and encryption at rest.
- Right to erasure: hard deletion / anonymisation job across the three databases and Kafka
  retention aligned with it (events are pseudonymous, retention can stay short: 7 days).
- Audit trail of accesses to patient records, data processing register, DPIA.
- TLS between all components, secrets in a vault, Keycloak MFA for staff accounts.
