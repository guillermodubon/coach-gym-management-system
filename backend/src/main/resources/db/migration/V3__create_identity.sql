CREATE TABLE gym.roles (
    id UUID PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_roles_role_code UNIQUE (role_code),
    CONSTRAINT ck_roles_role_code_not_blank CHECK (btrim(role_code) <> ''),
    CONSTRAINT ck_roles_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_roles_version_non_negative CHECK (version >= 0)
);

CREATE TABLE gym.users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_users_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT ck_users_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT ck_users_password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_users_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT ck_users_last_name_not_blank CHECK (btrim(last_name) <> ''),
    CONSTRAINT ck_users_version_non_negative CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_users_username_ci ON gym.users (lower(username));
CREATE UNIQUE INDEX uq_users_email_ci ON gym.users (lower(email));

CREATE TABLE gym.user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by_user_id UUID,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES gym.users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES gym.roles (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_granted_by_user
        FOREIGN KEY (granted_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_user_roles_role_id ON gym.user_roles (role_id);

CREATE TRIGGER trg_roles_set_updated_at
BEFORE UPDATE ON gym.roles
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON gym.users
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
