-- Demo data, loaded only with the Spring profile "demo". Fictitious people. Idempotent (repeatable migration).
-- Alice's id is the "patient_id" attribute of the Keycloak user alice.
INSERT INTO patients (id, first_name, last_name, email, phone, birth_date, status, created_at, updated_at) VALUES
    ('00000000-0000-4000-a000-000000000001', 'Alice', 'Durand', 'alice.durand@example.fr', '06 12 34 56 78',
     '1990-04-12', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-a000-000000000002', 'Thomas', 'Petit', 'thomas.petit@example.fr', '06 98 76 54 32',
     '1985-11-02', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-a000-000000000003', 'Inès', 'Haddad', 'ines.haddad@example.fr', NULL,
     '2001-07-23', 'ACTIVE', now(), now()),
    ('00000000-0000-4000-a000-000000000004', 'Jean', 'Moreau', 'jean.moreau@example.fr', '07 11 22 33 44',
     '1958-02-17', 'INACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;
