INSERT INTO gym.roles (id, role_code, display_name, description)
VALUES
    ('7b0bf7d5-5184-43d2-8f9a-100000000001', 'ADMIN', 'Administrator',
        'Full administrative access to Coach Gym.'),
    ('7b0bf7d5-5184-43d2-8f9a-100000000002', 'RECEPTIONIST', 'Receptionist',
        'Client, membership, payment, and access operations.'),
    ('7b0bf7d5-5184-43d2-8f9a-100000000003', 'MAINTENANCE', 'Maintenance',
        'Equipment, incident, and maintenance operations.')
ON CONFLICT (role_code) DO NOTHING;
