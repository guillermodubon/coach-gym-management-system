CREATE UNIQUE INDEX uq_clients_email_ci
    ON gym.clients (lower(email))
    WHERE email IS NOT NULL;
