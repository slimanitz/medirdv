CREATE TABLE patients (
    id         uuid PRIMARY KEY,
    first_name varchar(80)  NOT NULL,
    last_name  varchar(80)  NOT NULL,
    email      varchar(160) NOT NULL,
    phone      varchar(20),
    birth_date date         NOT NULL,
    status     varchar(16)  NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_patients_email UNIQUE (email)
);
CREATE INDEX ix_patients_last_name ON patients (last_name, first_name);

-- Transactional outbox (published by OutboxPublisher).
CREATE TABLE outbox (
    id             uuid PRIMARY KEY,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id   varchar(64) NOT NULL,
    type           varchar(64) NOT NULL,
    payload        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT clock_timestamp(),
    sent_at        timestamptz
);
CREATE INDEX ix_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;

-- Idempotent consumers.
CREATE TABLE processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
