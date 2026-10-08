# Roadmap

## Done
- Event-driven booking saga (choreography) across scheduling, patient and notification services
- Transactional outbox (polling publisher, `SKIP LOCKED`), idempotent consumers, backlog gauge
- Saga compensation (patient rejected) and timeout job, cancellation, rescheduling API, patient deactivation cascade
- Free-slot computation from weekly availabilities (Europe/Paris, DST-safe), double-booking protection
- Retry topics + `notification.dlt` dead letter topic in notification-service
- Keycloak (OIDC + PKCE), gateway + per-service JWT validation, role-based access
- Angular 20 app: booking with live status, agenda, patients, notifications
- Tracing across services through Kafka (traceparent in the outbox), Prometheus metrics, Grafana dashboard
- Docker Compose one-command run, Helm chart, GitHub Actions CI (Testcontainers, web, Helm, GHCR images)
- Hardening: coded cancellation reasons, poison-message parking in the outbox, consumer DLTs,
  overlap exclusion constraint, management ports, runtime web config, non-root read-only pods

## Next
- Reschedule from the UI (API `PATCH /appointments/{id}` already exists)
- Outbox and `processed_events` purge job; DLT and parked-outbox replay endpoints for operators
- Practitioner self-service for availabilities and absences
- Real e-mail sender (Brevo / SES) resolving the address from patient-service at send time

## Later
- SMS reminders 24 h before the appointment (scheduled job + new event)
- Debezium CDC instead of polling when volume grows (ADR 0002)
- Avro/Protobuf + Schema Registry if teams multiply (ADR 0003)
- HDS hosting, audit trail, right-to-erasure workflow (see `rgpd.md`)
- Contract tests between services, load tests (Gatling) on the booking path
