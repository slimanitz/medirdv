-- Demo data, loaded only with the Spring profile "demo" (see application-demo.yml).
-- Repeatable migration: idempotent inserts.
-- Dr Martin's id is also set as the "practitioner_id" attribute of the Keycloak user dr.martin.
INSERT INTO practitioners (id, full_name, specialty) VALUES
    ('00000000-0000-4000-b000-000000000001', 'Dr Claire Martin', 'Médecine générale'),
    ('00000000-0000-4000-b000-000000000002', 'Dr Karim Benali', 'Dermatologie'),
    ('00000000-0000-4000-b000-000000000003', 'Dr Sophie Lefèvre', 'Pédiatrie')
ON CONFLICT (id) DO NOTHING;

-- Dr Martin: Monday to Friday 09:00-12:00 and 14:00-18:00, Saturday morning, 30 min slots.
INSERT INTO availabilities (id, practitioner_id, weekday, start_time, end_time, slot_minutes)
SELECT gen_random_uuid(), '00000000-0000-4000-b000-000000000001', d, '09:00', '12:00', 30
FROM generate_series(1, 6) AS d
WHERE NOT EXISTS (SELECT 1 FROM availabilities WHERE practitioner_id = '00000000-0000-4000-b000-000000000001');
INSERT INTO availabilities (id, practitioner_id, weekday, start_time, end_time, slot_minutes)
SELECT gen_random_uuid(), '00000000-0000-4000-b000-000000000001', d, '14:00', '18:00', 30
FROM generate_series(1, 5) AS d
WHERE (SELECT count(*) FROM availabilities WHERE practitioner_id = '00000000-0000-4000-b000-000000000001') = 6;

-- Dr Benali: Tuesday and Thursday 10:00-16:00, 20 min slots.
INSERT INTO availabilities (id, practitioner_id, weekday, start_time, end_time, slot_minutes)
SELECT gen_random_uuid(), '00000000-0000-4000-b000-000000000002', d, '10:00', '16:00', 20
FROM unnest(ARRAY[2, 4]) AS d
WHERE NOT EXISTS (SELECT 1 FROM availabilities WHERE practitioner_id = '00000000-0000-4000-b000-000000000002');

-- Dr Lefèvre: Monday, Wednesday, Friday 08:30-12:30, 15 min slots.
INSERT INTO availabilities (id, practitioner_id, weekday, start_time, end_time, slot_minutes)
SELECT gen_random_uuid(), '00000000-0000-4000-b000-000000000003', d, '08:30', '12:30', 15
FROM unnest(ARRAY[1, 3, 5]) AS d
WHERE NOT EXISTS (SELECT 1 FROM availabilities WHERE practitioner_id = '00000000-0000-4000-b000-000000000003');
