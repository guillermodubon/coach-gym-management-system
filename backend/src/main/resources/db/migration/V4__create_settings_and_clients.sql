CREATE TABLE gym.gym_settings (
    id SMALLINT PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    legal_name VARCHAR(255),
    email VARCHAR(254),
    phone VARCHAR(32),
    address TEXT,
    time_zone VARCHAR(64) NOT NULL,
    default_currency CHAR(3) NOT NULL,
    membership_expiration_warning_days SMALLINT NOT NULL DEFAULT 7,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_gym_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_gym_settings_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_gym_settings_time_zone_not_blank CHECK (btrim(time_zone) <> ''),
    CONSTRAINT ck_gym_settings_currency_format CHECK (default_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_gym_settings_warning_days_range
        CHECK (membership_expiration_warning_days BETWEEN 0 AND 365),
    CONSTRAINT ck_gym_settings_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_gym_settings_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_gym_settings_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

INSERT INTO gym.gym_settings (
    id,
    display_name,
    time_zone,
    default_currency,
    membership_expiration_warning_days
)
VALUES (1, 'Coach Gym', 'America/El_Salvador', 'USD', 7);

CREATE TABLE gym.clients (
    id UUID PRIMARY KEY,
    client_number BIGINT GENERATED ALWAYS AS IDENTITY,
    client_code VARCHAR(32) GENERATED ALWAYS AS (
        'CLI-' || lpad(client_number::TEXT, 6, '0')
    ) STORED,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(32) NOT NULL,
    date_of_birth DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deactivated_at TIMESTAMPTZ,
    deactivated_by_user_id UUID,
    deactivation_reason TEXT,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_clients_client_number UNIQUE (client_number),
    CONSTRAINT uq_clients_client_code UNIQUE (client_code),
    CONSTRAINT ck_clients_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT ck_clients_last_name_not_blank CHECK (btrim(last_name) <> ''),
    CONSTRAINT ck_clients_phone_not_blank CHECK (btrim(phone) <> ''),
    CONSTRAINT ck_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_clients_deactivation_state CHECK (
        (status = 'ACTIVE' AND deactivated_at IS NULL AND deactivated_by_user_id IS NULL
            AND deactivation_reason IS NULL)
        OR
        (status = 'INACTIVE' AND deactivated_at IS NOT NULL)
    ),
    CONSTRAINT ck_clients_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_clients_deactivated_by_user
        FOREIGN KEY (deactivated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_clients_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_clients_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_clients_name ON gym.clients (last_name, first_name);
CREATE INDEX idx_clients_phone ON gym.clients (phone);
CREATE INDEX idx_clients_email_ci ON gym.clients (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_clients_status ON gym.clients (status) WHERE status = 'ACTIVE';

CREATE TABLE gym.emergency_contacts (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    relationship VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_emergency_contacts_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_emergency_contacts_relationship_not_blank CHECK (btrim(relationship) <> ''),
    CONSTRAINT ck_emergency_contacts_phone_not_blank CHECK (btrim(phone) <> ''),
    CONSTRAINT ck_emergency_contacts_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_emergency_contacts_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_emergency_contacts_one_primary_per_client
    ON gym.emergency_contacts (client_id)
    WHERE is_primary;

CREATE INDEX idx_emergency_contacts_client_id ON gym.emergency_contacts (client_id);

CREATE TRIGGER trg_gym_settings_set_updated_at
BEFORE UPDATE ON gym.gym_settings
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_clients_set_updated_at
BEFORE UPDATE ON gym.clients
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_emergency_contacts_set_updated_at
BEFORE UPDATE ON gym.emergency_contacts
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
