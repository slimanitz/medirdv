# ADR 0004 — Blocking retries for saga consumers, non-blocking retries for notifications

- Status: accepted
- Date: 2026-10-08

## Context

Consumers fail sometimes (database down, bug, unreadable message). Spring Kafka offers two
strategies: **blocking retries** (`DefaultErrorHandler`: the record is retried in place with a
backoff, the partition waits) and **non-blocking retries** (`@RetryableTopic`: the record is moved
to retry topics and the partition continues). Without any handler a poison record is retried 10
times without delay and then skipped silently.

## Decision

- **Saga consumers** (scheduling on `patient.events`, patient-service on `appointment.events`):
  `DefaultErrorHandler` with `ExponentialBackOffWithMaxRetries` (4 retries from 500 ms, ×2) and a
  `DeadLetterPublishingRecoverer` to `<topic>.dlt` (`patient.events.dlt`, `appointment.events.dlt`).
  Unreadable messages (`EventSerializationException`) go to the DLT immediately. Ordering per
  appointment matters here (validation before deactivation, confirmation before cancellation).
- **notification-service**: `@RetryableTopic` (3 attempts, 1 s ×2) with the DLT `notification.dlt`.
  Notifications are independent messages; a failing one must not delay the others.

## Consequences

- + The saga keeps per-aggregate ordering; a broken record ends in a DLT instead of being lost.
- + Notifications keep flowing when one of them fails.
- − **Notification ordering is not guaranteed**: if the confirmation notice of an appointment fails
  once, a later cancellation notice of the same appointment can be created before it. Each message
  is self-contained (date, outcome) and shown sorted by creation time, so this is accepted.
- − Blocking retries delay the partition for up to ~8 s per failing record.
- − DLTs need monitoring and a replay procedure (roadmap: operator replay endpoint).
