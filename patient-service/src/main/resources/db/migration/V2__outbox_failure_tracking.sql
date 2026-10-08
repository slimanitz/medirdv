-- Outbox failure tracking: poison messages are parked (status FAILED) instead of blocking the queue.
ALTER TABLE outbox ADD COLUMN status varchar(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox ADD COLUMN attempts integer NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN last_error varchar(1000);
UPDATE outbox SET status = 'SENT' WHERE sent_at IS NOT NULL;
ALTER TABLE outbox ADD CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'));
DROP INDEX ix_outbox_unsent;
CREATE INDEX ix_outbox_pending ON outbox (created_at) WHERE status = 'PENDING';
