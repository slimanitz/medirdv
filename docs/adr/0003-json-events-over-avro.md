# ADR 0003 — JSON events over Avro and a Schema Registry

- Status: accepted
- Date: 2026-10-08

## Context

Events need a format and a way to evolve without breaking consumers. Common options: JSON with
shared classes, Avro or Protobuf with a Schema Registry enforcing compatibility rules.

## Decision

Use **JSON** events described by **Java records in a shared `events` module**:

- envelope `{eventId, type, occurredAt, traceparent?, payload}`;
- payload records only contain identifiers and time slots (RGPD, see `docs/rgpd.md`);
- consumers ignore unknown properties (`FAIL_ON_UNKNOWN_PROPERTIES=false`) and unknown types;
- evolution rule: only add optional fields; a breaking change means a new event type.

## Consequences

- + Human-readable messages (`kafka-console-consumer` is enough to debug), no registry to run.
- + One Maven module is the contract, checked at compile time by every service of the repo.
- + Unit tests guard the contract (round trip, no personal data field names).
- − No broker-side compatibility check: contract discipline relies on code review and tests.
- − Larger messages than Avro. Acceptable at this volume.
- − If teams or languages multiply, move to Avro/Protobuf + Schema Registry (Apicurio or
  Confluent) with BACKWARD compatibility.
