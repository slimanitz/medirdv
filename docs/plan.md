# MediRDV — Medical appointment booking in event-driven microservices

Repo: `medirdv`. Stack: Java 21, Spring Boot 3.5.x, Spring Cloud Gateway, Spring Kafka,
Apache Kafka 3.9 (KRaft), PostgreSQL 16 (one DB per service), Keycloak 26, Angular 20,
Micrometer Tracing + OpenTelemetry → Tempo, Prometheus, Grafana, Helm, GitHub Actions.

Pitch: a Doctolib-style booking system split into services communicating through Kafka, with
a transactional outbox, a choreography saga for bookings, and full tracing. Healthcare data
awareness: events never carry personal data, only identifiers; `docs/rgpd.md` explains it.

## Reserved ports
| Component | Port |
|---|---|
| gateway | 8200 |
| patient-service | 8201 |
| scheduling-service | 8202 |
| notification-service | 8203 |
| web (nginx) / Angular dev | 8204 / 4202 |
| Keycloak | 8280 |
| Kafka (host listener) | 9292 |
| Postgres patient/scheduling/notification | 5434 / 5435 / 5436 |
| Grafana / Prometheus / Tempo | 3200 / 9290 / 3201 |

## Layout (Maven multi-module, root pom with dependencyManagement + Spring Cloud BOM)
```
pom.xml
events/                 shared event records (Java records, JSON via Jackson) + topic names
gateway/                Spring Cloud Gateway, OAuth2 resource server, routes, CORS
patient-service/        patients CRUD, consumes AppointmentRequested, publishes PatientValidated|PatientRejected
scheduling-service/     practitioners, slots, appointments, outbox, saga state
notification-service/   consumes events, builds notifications, retry + dead letter topic
web/                    Angular 20
deploy/helm/medirdv/    umbrella chart with one template set per service + values
deploy/observability/   prometheus.yml, tempo.yaml, grafana provisioning + dashboard json
docker-compose.yml      core stack; `--profile observability` adds prom/tempo/grafana
docs/
```

## Events (topics: `patient.events`, `appointment.events`, `notification.dlt`)
Envelope: `{eventId uuid, type, occurredAt, traceparent?, payload}` as JSON. Types:
`AppointmentRequested{appointmentId, patientId, practitionerId, slotStart, slotEnd}`,
`PatientValidated{appointmentId, patientId}`, `PatientRejected{appointmentId, patientId, reason}`,
`AppointmentConfirmed`, `AppointmentCancelled{appointmentId, patientId, cancelledBy, reason}`,
`AppointmentRescheduled`, `PatientDeactivated{patientId}`. Key = aggregate id for ordering.
No names, emails or phone numbers in events (RGPD).

## Booking saga (choreography)
1. POST /appointments → scheduling creates appointment `PENDING_VALIDATION`, reserves slot,
   writes `AppointmentRequested` to the outbox in the same transaction.
2. patient-service consumes it: patient exists & ACTIVE → `PatientValidated`, else
   `PatientRejected`.
3. scheduling consumes: Validated → `CONFIRMED` + `AppointmentConfirmed`; Rejected → releases
   slot, `REJECTED` (compensation). Timeout: a scheduled job rejects PENDING older than 2 min.
4. notification-service consumes Confirmed/Cancelled/Rejected and creates a notification
   (channel EMAIL, status SENT, body rendered from a template; sending is a logging stub behind
   a `NotificationSender` interface).
Cancellation: DELETE /appointments/{id} → `CANCELLED` + `AppointmentCancelled` → notification.
`PatientDeactivated` → scheduling cancels all future appointments of that patient.

## Outbox (scheduling-service, also patient-service for its two events)
Table `outbox(id, aggregate_type, aggregate_id, type, payload jsonb, created_at, sent_at null)`.
`OutboxPublisher` `@Scheduled(fixedDelay=500)` reads unsent in batches of 50, publishes with
`KafkaTemplate` (acks=all, idempotence on), marks sent. Consumers are idempotent via a
`processed_events(event_id)` table. Document in ADR.

## Services detail
- patient-service: `Patient(id, firstName, lastName, email, phone, birthDate, status ACTIVE|INACTIVE)`;
  REST /patients CRUD + `POST /patients/{id}/deactivate`. Flyway. Pseudonymised logs (mask
  email/phone in a Logback pattern or in the toString).
- scheduling-service: `Practitioner(id, fullName, specialty)`, `Availability(id, practitionerId,
  weekday, startTime, endTime, slotMinutes)`, `Appointment(id, patientId, practitionerId, start,
  end, status, reason, version)` with unique constraint (practitionerId, start) for double booking
  protection; `GET /practitioners/{id}/slots?from=&to=` computes free slots from availability
  minus appointments. Optimistic locking.
- notification-service: `Notification(id, eventId, patientId, appointmentId, type, status, body,
  createdAt)`; `@RetryableTopic` (3 attempts, backoff) with DLT `notification.dlt`;
  GET /notifications?patientId=.
- gateway: routes `/api/patients/**`→patient, `/api/practitioners/**`,`/api/appointments/**`
  →scheduling, `/api/notifications/**`→notification; validates Keycloak JWT; relays the token
  downstream; services also validate the JWT (resource server). Roles `practitioner`, `patient`,
  `admin`. Keycloak realm `medirdv` with users `dr.martin/dr.martin` (practitioner),
  `alice/alice` (patient, patientId linked by a `patient_id` user attribute mapped into the token),
  `admin/admin`. Same issuer pitfall as other projects: external issuer, internal JWK URI.
- All services: Actuator (health, prometheus), Micrometer Tracing (`micrometer-tracing-bridge-otel`
  + `opentelemetry-exporter-otlp`), `management.tracing.sampling.probability=1.0`, Kafka headers
  propagate trace context (Spring Kafka observation enabled). Structured JSON logs with traceId.

## Frontend (Angular 20, keycloak-angular)
Pages: `/agenda` (practitioner: week grid of appointments, cancel), `/patients` (admin/
practitioner: list + create + deactivate), `/book` (patient: pick practitioner, see free slots,
book, see status update to CONFIRMED by polling every 2s), `/notifications` (patient). French
copy, clean CSS. 3+ unit tests, `ng build` green.

## Observability profile
Grafana provisioned datasources (Prometheus, Tempo) + one dashboard JSON: request rate per
service, p95 latency, Kafka consumer lag (from Micrometer kafka metrics), outbox backlog gauge
(custom `Gauge` in scheduling), traces search. README explains how to find a booking trace
across 3 services in Tempo.

## Helm (deploy/helm/medirdv)
Umbrella chart: Deployment+Service+ConfigMap+HPA per service, Ingress for gateway and web,
values with image tags, Kafka/Postgres/Keycloak expected as external (document Bitnami charts
to use). `helm lint` and `helm template` must pass (install helm binary in ~/.local/bin with the
official get-helm-3 script if missing).

## CI (.github/workflows/ci.yml)
Job matrix per service: `mvn -B -pl <svc> -am verify` with Testcontainers (Postgres +
`org.testcontainers:kafka` using `apache/kafka:3.9.0` KRaft). Web job: test + build. On push to
main: build images with docker/build-push-action to GHCR (tag sha + latest). Helm lint job.

## Tests
- Unit: slot computation, saga state transitions, outbox publisher, event (de)serialisation.
- Integration per service with Testcontainers; one end-to-end saga test in scheduling-service
  that stubs patient-service by producing `PatientValidated` on the topic.

## Milestones
1. Root pom, events module, scheduling-service with REST + outbox + Testcontainers tests.
2. patient-service + notification-service + saga end-to-end in compose (Kafka up, booking
   confirmed, notification created).
3. gateway + Keycloak + Angular app working through the gateway.
4. Observability profile, Helm chart, CI.
5. Docs per conventions (ADRs: "choreography saga over orchestration", "transactional outbox
   with polling publisher over Debezium", "JSON events over Avro/Schema Registry"), docs/rgpd.md.

## Acceptance
- `docker compose up -d --build` → login alice at http://localhost:8204, book a slot with
  Dr Martin, status becomes CONFIRMÉ within seconds, notification visible; dr.martin sees it in
  agenda and can cancel; alice gets a cancellation notification.
- `mvn -B verify` green at root; `helm lint deploy/helm/medirdv` green; web build green.

---

## Deviations (implementation notes)

1. **Kafka image `apache/kafka:3.9.1` instead of `3.9.0`** (compose, Testcontainers, CI). The 3.9.0
   image fails at startup in combined broker+controller mode ("advertised.listeners cannot use the
   nonroutable meta-address 0.0.0.0"); 3.9.1 is the patch release of the same line. The
   Testcontainers `KafkaContainer` also gets an explicit hostname (`withCreateContainerCmdModifier`).
2. **Extra Maven module `platform`** (outbox writer/publisher/backlog gauge, idempotent consumer
   store, Keycloak roles converter, resource-server security config) so that the outbox is not
   duplicated between scheduling and patient services. `events` stays a pure contract module.
3. **Extra event `AppointmentRejected`** `{appointmentId, patientId, practitionerId, slotStart, reason}`
   published by scheduling on compensation/timeout, consumed by notification-service (the plan
   lists "Rejected" among the notified outcomes). `AppointmentConfirmed/Cancelled/Rescheduled`
   also carry `practitionerId` and slot times (identifiers and dates only) so that notifications can
   be rendered without calling other services.
4. **Double-booking constraint is a partial unique index** on `(practitioner_id, start_at)` limited
   to `PENDING_VALIDATION`/`CONFIRMED`, so that rejected/cancelled appointments release the slot.
   `appointments` also has a `status_reason` column.
5. **Helm**: one chart (`deploy/helm/medirdv`) whose templates loop over a `services:` map
   (ConfigMap + Deployment + Service + HPA per service) plus web and Ingress, instead of one
   subchart per service. Same result, less duplication.
6. **Gateway**: token relay = the `Authorization` header is forwarded unchanged (the gateway is a
   resource server, not an OAuth2 client, so the `TokenRelay` filter does not apply). CORS is a
   `CorsWebFilter` (only needed for the Angular dev server on 4202); in compose the SPA calls the
   API same-origin through nginx.
7. **Tracing export is opt-in** (`TRACING_EXPORT_ENABLED=true`, set by `make up-obs`) so that the
   core stack does not log exporter errors when Tempo is not running. Trace ids are always in logs.
   Trace continuity across Kafka is achieved by storing the `traceparent` in the outbox envelope and
   setting it as a Kafka header at publication.
8. **Keycloak 26.4**; the Angular app reads its Keycloak / API URLs at runtime from `/config.json`
   (written by the web container from `KEYCLOAK_URL` / `API_URL`). Password grant only on a
   dev-only client `medirdv-cli`.
9. **Demo data** is loaded by repeatable Flyway migrations (`classpath:db/demo`) only with the
   Spring profile `demo` (set by compose, `demoData` in Helm). `make seed` adds a few patients.
10. Rescheduling is implemented in the API (`PATCH /appointments/{id}` → `AppointmentRescheduled`)
    but not exposed in the UI yet.
11. **Review hardening** (after the first delivery): `AppointmentCancelled` carries a `reasonCode`
    enum instead of free text (`reason` field of the plan), rejection reasons and `cancelledBy` are
    enums; outbox gets `status/attempts/last_error` and an advisory lock; saga consumers get
    blocking retries + `<topic>.dlt`; exclusion constraint against overlapping appointments;
    actuator on management ports 9200-9203; fail-closed, role-scoped list endpoints; hardened Helm
    security contexts (UID 10001, read-only root FS) and nginx-unprivileged web image on port 8080.
