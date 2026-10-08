# ADR 0002 — Transactional outbox with a polling publisher over Debezium

- Status: accepted (amended after review: ordering with replicas, poison messages)
- Date: 2026-10-08

## Context

A service must change its database and publish an event atomically. Publishing to Kafka inside
the database transaction (dual write) loses or duplicates events when one of the two fails.
The transactional outbox pattern solves it: the event is written to an `outbox` table in the same
transaction, and something relays the table to Kafka. The relay can be a CDC connector
(Debezium on the PostgreSQL WAL, via Kafka Connect) or an in-process polling publisher.

## Decision

Use an **in-process polling publisher** (`platform` module, reused by scheduling and patient services):

- table `outbox(id, aggregate_type, aggregate_id, type, payload jsonb, created_at, sent_at,
  status, attempts, last_error)`; `status` is `PENDING`, `SENT` or `FAILED`;
- `OutboxPublisher` runs every 500 ms in one transaction:
  1. `pg_try_advisory_xact_lock(<constant>)`: **only one replica publishes at a time**;
  2. selects up to 50 `PENDING` rows `ORDER BY created_at FOR UPDATE SKIP LOCKED`;
  3. sends them in order with `KafkaTemplate` (`acks=all`, idempotent producer), key = aggregate id;
  4. marks the sent rows `SENT`;
- consumers are idempotent through a `processed_events(event_id)` table;
- gauges: `medirdv_outbox_backlog` (pending) and `medirdv_outbox_failed` (parked).

### Ordering with several replicas

`SKIP LOCKED` alone is not enough: with two replicas, replica A may lock rows 1-50 and replica B
rows 51-100 and B may publish first, so two events of the same appointment could be published out
of order. The transaction-scoped advisory lock serialises publication across replicas (the others
skip the tick), so the per-aggregate order of `created_at` is preserved and the services can still
scale out under the HPA for HTTP traffic. `SKIP LOCKED` stays as a safety net. The cost is that
publication throughput is that of a single replica (≈ hundreds of events per second, far above
the needs of a booking product).

### Failures and poison messages

The batch stops at the first message that cannot be published: later messages may belong to the
same aggregate and must not overtake it. Each failure increments `attempts` and stores `last_error`.

- Transient Kafka errors (`RetriableException`: broker down, timeouts) are retried forever;
  messages are never parked because Kafka is unavailable.
- Permanent errors (unreadable envelope, unknown aggregate type, record too large…) are retried
  `medirdv.outbox.max-attempts` times (default 5) and the message is then **parked** as `FAILED`,
  which unblocks the queue. `medirdv_outbox_failed > 0` must alert an operator, who fixes and
  re-queues the row (`UPDATE outbox SET status = 'PENDING', attempts = 0 WHERE id = ...`).

## Consequences

- + No extra infrastructure (Kafka Connect cluster, replication slots, connector configuration).
- + Ordered per aggregate even with several replicas; poison messages cannot block the queue.
- + Easy to test: unit tests with mocks (`OutboxPublisherTest`), integration tests with Testcontainers.
- − At-least-once delivery: a crash between `send` and commit re-sends the batch, hence
  idempotent consumers.
- − Parking a message breaks ordering for that aggregate (later events go out first): acceptable
  only because parking requires a permanent, operator-visible error.
- − Polling adds up to ~500 ms latency and some database load; sent rows must be purged
  (roadmap: scheduled cleanup of rows sent more than 7 days ago).
- − At higher volume, Debezium (log-based CDC) becomes the better option; the table layout is
  compatible with Debezium's outbox event router, so the migration path is open.
