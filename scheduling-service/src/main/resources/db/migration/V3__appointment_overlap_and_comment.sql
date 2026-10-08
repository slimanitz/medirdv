-- Free-text comment of a cancellation: stays in this database, never published in events.
ALTER TABLE appointments ADD COLUMN status_comment varchar(500);

-- No two active appointments of a practitioner may overlap, even with different slot grids
-- (the unique index only covers identical start times).
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointments ADD CONSTRAINT ex_appointments_no_overlap
    EXCLUDE USING gist (practitioner_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (status IN ('PENDING_VALIDATION', 'CONFIRMED'));
