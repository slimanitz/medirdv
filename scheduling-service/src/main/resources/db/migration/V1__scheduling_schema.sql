CREATE TABLE practitioners (
    id        uuid PRIMARY KEY,
    full_name varchar(120) NOT NULL,
    specialty varchar(80)  NOT NULL
);

CREATE TABLE availabilities (
    id              uuid PRIMARY KEY,
    practitioner_id uuid    NOT NULL REFERENCES practitioners (id),
    weekday         integer NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_time      time    NOT NULL,
    end_time        time    NOT NULL,
    slot_minutes    integer NOT NULL CHECK (slot_minutes > 0),
    CHECK (end_time > start_time)
);
CREATE INDEX ix_availabilities_practitioner ON availabilities (practitioner_id);

CREATE TABLE appointments (
    id              uuid PRIMARY KEY,
    patient_id      uuid         NOT NULL,
    practitioner_id uuid         NOT NULL REFERENCES practitioners (id),
    start_at        timestamptz  NOT NULL,
    end_at          timestamptz  NOT NULL,
    status          varchar(32)  NOT NULL,
    reason          varchar(255),
    status_reason   varchar(255),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CHECK (end_at > start_at)
);
-- Double booking protection: one active appointment per practitioner and start time.
-- Partial so that rejected / cancelled appointments release the slot.
CREATE UNIQUE INDEX uq_appointments_practitioner_start_active
    ON appointments (practitioner_id, start_at)
    WHERE status IN ('PENDING_VALIDATION', 'CONFIRMED');
CREATE INDEX ix_appointments_patient ON appointments (patient_id);
CREATE INDEX ix_appointments_practitioner_start ON appointments (practitioner_id, start_at);
CREATE INDEX ix_appointments_pending ON appointments (created_at) WHERE status = 'PENDING_VALIDATION';

-- Transactional outbox (published by OutboxPublisher).
CREATE TABLE outbox (
    id             uuid PRIMARY KEY,
    aggregate_type varchar(64)  NOT NULL,
    aggregate_id   varchar(64)  NOT NULL,
    type           varchar(64)  NOT NULL,
    payload        jsonb        NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT clock_timestamp(),
    sent_at        timestamptz
);
CREATE INDEX ix_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;

-- Idempotent consumers.
CREATE TABLE processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
