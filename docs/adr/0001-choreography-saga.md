# ADR 0001 — Choreography saga over orchestration for bookings

- Status: accepted
- Date: 2026-10-08

## Context

Booking an appointment spans two services that own their data: scheduling-service (slot and
appointment) and patient-service (patient record and status). A distributed transaction (2PC) is
not an option with Kafka and PostgreSQL. We need a saga: a sequence of local transactions with
compensation. Two styles exist: an orchestrator that sends commands and tracks state, or
choreography where each service reacts to events.

## Decision

Use a **choreography** saga:

1. scheduling creates the appointment `PENDING_VALIDATION` (slot reserved) and emits `AppointmentRequested`;
2. patient-service answers `PatientValidated` or `PatientRejected`;
3. scheduling confirms (`AppointmentConfirmed`) or compensates (`REJECTED`, slot released, `AppointmentRejected`);
4. notification-service reacts to the outcome.

The saga state lives in the appointment aggregate itself (status + timestamps). A scheduled job
in scheduling acts as the saga timeout (2 minutes).

## Consequences

- + Only two participants and three steps: no extra orchestrator service to deploy and operate.
- + Services stay decoupled: patient-service does not know about appointments beyond the id.
- + The state machine is a plain domain object, easy to unit-test (`AppointmentTest`).
- − The flow is spread across services: it is documented in `docs/architecture.md` and
  observable end to end thanks to trace propagation through the outbox.
- − If the saga grows (payment, teleconsultation room...), an orchestrator (e.g. a
  scheduling-side process manager or Temporal) should be reconsidered.
