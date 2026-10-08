-- Practitioner of the appointment, used to scope what a practitioner may list.
ALTER TABLE notifications ADD COLUMN practitioner_id uuid;
CREATE INDEX ix_notifications_practitioner ON notifications (practitioner_id, created_at DESC);
