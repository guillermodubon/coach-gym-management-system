-- Establishes the single Coach Gym organization and the physical branch
-- catalog. Existing business tables remain intentionally unscoped; the later
-- operational-scoping branch will associate historical rows explicitly.

CREATE TABLE gym.organizations (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    brand_name VARCHAR(160) NOT NULL,
    support_email VARCHAR(254),
    support_phone VARCHAR(32),
    default_timezone VARCHAR(64) NOT NULL,
    default_currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_canonical BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_organizations_code UNIQUE (code),
    CONSTRAINT ck_organizations_code_format
        CHECK (code ~ '^[A-Z0-9]+([_-][A-Z0-9]+)*$'),
    CONSTRAINT ck_organizations_legal_name_not_blank
        CHECK (btrim(legal_name) <> ''),
    CONSTRAINT ck_organizations_brand_name_not_blank
        CHECK (btrim(brand_name) <> ''),
    CONSTRAINT ck_organizations_support_email
        CHECK (support_email IS NULL
            OR (btrim(support_email) <> ''
                AND support_email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$')),
    CONSTRAINT ck_organizations_support_phone
        CHECK (support_phone IS NULL OR btrim(support_phone) <> ''),
    CONSTRAINT ck_organizations_timezone_not_blank
        CHECK (btrim(default_timezone) <> ''),
    CONSTRAINT ck_organizations_currency_format
        CHECK (default_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_organizations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_organizations_version_non_negative
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_organizations_one_canonical
    ON gym.organizations (is_canonical)
    WHERE is_canonical;

CREATE TABLE gym.gym_branches (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(120),
    state_or_department VARCHAR(120),
    postal_code VARCHAR(32),
    country_code CHAR(2),
    phone VARCHAR(32),
    email VARCHAR(254),
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_initial_branch BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_gym_branches_organization_code
        UNIQUE (organization_id, code),
    CONSTRAINT ck_gym_branches_code_format
        CHECK (code ~ '^[A-Z0-9]+([_-][A-Z0-9]+)*$'),
    CONSTRAINT ck_gym_branches_name_not_blank
        CHECK (btrim(name) <> ''),
    CONSTRAINT ck_gym_branches_address_line1
        CHECK (address_line1 IS NULL OR btrim(address_line1) <> ''),
    CONSTRAINT ck_gym_branches_address_line2
        CHECK (address_line2 IS NULL OR btrim(address_line2) <> ''),
    CONSTRAINT ck_gym_branches_city
        CHECK (city IS NULL OR btrim(city) <> ''),
    CONSTRAINT ck_gym_branches_state_or_department
        CHECK (state_or_department IS NULL OR btrim(state_or_department) <> ''),
    CONSTRAINT ck_gym_branches_postal_code
        CHECK (postal_code IS NULL OR btrim(postal_code) <> ''),
    CONSTRAINT ck_gym_branches_country_code
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_gym_branches_phone
        CHECK (phone IS NULL OR btrim(phone) <> ''),
    CONSTRAINT ck_gym_branches_email
        CHECK (email IS NULL
            OR (btrim(email) <> ''
                AND email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$')),
    CONSTRAINT ck_gym_branches_timezone_not_blank
        CHECK (btrim(timezone) <> ''),
    CONSTRAINT ck_gym_branches_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_gym_branches_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_gym_branches_organization
        FOREIGN KEY (organization_id)
        REFERENCES gym.organizations (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_gym_branches_one_initial_per_organization
    ON gym.gym_branches (organization_id)
    WHERE is_initial_branch;

-- Supports the bounded organization/status/code/id ordering used by the
-- branch catalog query while retaining the unique organization/code key.
CREATE INDEX idx_gym_branches_organization_status_code
    ON gym.gym_branches (organization_id, status, code, id);

CREATE OR REPLACE FUNCTION gym.reject_organization_branch_delete()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'organization and gym branch records are lifecycle-managed'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_organizations_reject_delete
BEFORE DELETE ON gym.organizations
FOR EACH ROW
EXECUTE FUNCTION gym.reject_organization_branch_delete();

CREATE TRIGGER trg_gym_branches_reject_delete
BEFORE DELETE ON gym.gym_branches
FOR EACH ROW
EXECUTE FUNCTION gym.reject_organization_branch_delete();

CREATE TRIGGER trg_organizations_set_updated_at
BEFORE UPDATE ON gym.organizations
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_gym_branches_set_updated_at
BEFORE UPDATE ON gym.gym_branches
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

COMMENT ON TABLE gym.organizations IS
    'The canonical Coach Gym organization; this roadmap is not SaaS multi-tenant.';
COMMENT ON TABLE gym.gym_branches IS
    'Physical branches owned by the canonical Coach Gym organization.';
COMMENT ON COLUMN gym.gym_branches.is_initial_branch IS
    'Marks the deterministic branch representing the historical single-location context.';

INSERT INTO gym.organizations (
    id,
    code,
    legal_name,
    brand_name,
    default_timezone,
    default_currency,
    status,
    is_canonical,
    version
)
VALUES (
    '7b0bf7d5-5184-43d2-8f9a-200000000001',
    'COACH_GYM',
    'Coach Gym',
    'Coach Gym',
    'America/El_Salvador',
    'USD',
    'ACTIVE',
    TRUE,
    0
);

INSERT INTO gym.gym_branches (
    id,
    organization_id,
    code,
    name,
    country_code,
    timezone,
    status,
    is_initial_branch,
    version
)
SELECT
    '7b0bf7d5-5184-43d2-8f9a-200000000002',
    id,
    'PRINCIPAL',
    'Coach Gym Principal',
    'SV',
    default_timezone,
    'ACTIVE',
    TRUE,
    0
FROM gym.organizations
WHERE code = 'COACH_GYM';
