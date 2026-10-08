CREATE TABLE notifications (
    id             uuid PRIMARY KEY,
    event_id       uuid          NOT NULL,
    patient_id     uuid          NOT NULL,
    appointment_id uuid          NOT NULL,
    type           varchar(32)   NOT NULL,
    channel        varchar(16)   NOT NULL,
    status         varchar(16)   NOT NULL,
    body           varchar(1000) NOT NULL,
    created_at     timestamptz   NOT NULL,
    CONSTRAINT uq_notifications_event UNIQUE (event_id)
);
CREATE INDEX ix_notifications_patient ON notifications (patient_id, created_at DESC);

-- Idempotent consumers.
CREATE TABLE processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
