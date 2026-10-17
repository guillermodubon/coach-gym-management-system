-- Persist permanent client access credentials and their immutable lifecycle history.
-- The credential token itself is never stored; only a deterministic fingerprint
-- suitable for future protected lookup is retained.

CREATE TABLE gym.access_credentials (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    credential_code VARCHAR(64) NOT NULL,
    token_fingerprint CHAR(64) NOT NULL,
    token_scheme_version VARCHAR(32) NOT NULL,
    payload_version VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMPTZ NOT NULL,
    issued_by_user_id UUID NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_user_id UUID,
    revocation_reason VARCHAR(2000),
    replaced_by_credential_id UUID,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    renderer_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_access_credentials_code UNIQUE (credential_code),
    CONSTRAINT uq_access_credentials_token_fingerprint UNIQUE (token_fingerprint),
    CONSTRAINT ck_access_credentials_code_not_blank
        CHECK (btrim(credential_code) <> ''),
    CONSTRAINT ck_access_credentials_token_fingerprint
        CHECK (token_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_access_credentials_token_scheme_version
        CHECK (token_scheme_version ~ '^[a-z0-9-]{1,32}$'),
    CONSTRAINT ck_access_credentials_payload_version
        CHECK (payload_version ~ '^v[0-9]+$'),
    CONSTRAINT ck_access_credentials_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_access_credentials_lifecycle_metadata
        CHECK (
            (status = 'ACTIVE'
                AND revoked_at IS NULL
                AND revoked_by_user_id IS NULL
                AND revocation_reason IS NULL
                AND replaced_by_credential_id IS NULL)
            OR
            (status = 'REVOKED'
                AND revoked_at IS NOT NULL
                AND revoked_by_user_id IS NOT NULL
                AND revocation_reason IS NOT NULL
                AND char_length(btrim(revocation_reason)) >= 3
                AND revoked_at >= issued_at)
        ),
    CONSTRAINT ck_access_credentials_replacement_not_self
        CHECK (replaced_by_credential_id IS NULL OR replaced_by_credential_id <> id),
    CONSTRAINT ck_access_credentials_storage_key_not_blank
        CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_access_credentials_content_type
        CHECK (content_type = 'image/png'),
    CONSTRAINT ck_access_credentials_size_range
        CHECK (size_bytes > 0 AND size_bytes <= 1048576),
    CONSTRAINT ck_access_credentials_checksum_sha256
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_access_credentials_renderer_version
        CHECK (renderer_version IS NULL OR btrim(renderer_version) <> ''),
    CONSTRAINT ck_access_credentials_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_access_credentials_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_credentials_issued_by_user
        FOREIGN KEY (issued_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_credentials_revoked_by_user
        FOREIGN KEY (revoked_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_credentials_replaced_by
        FOREIGN KEY (replaced_by_credential_id)
        REFERENCES gym.access_credentials (id)
        ON DELETE RESTRICT
);

-- The approved public status model has only ACTIVE and REVOKED. A replacement
-- therefore revokes the previous credential and records the new credential in
-- replaced_by_credential_id instead of introducing a third terminal status.

-- This partial unique index is both the active-client lookup path and the
-- database-enforced one-active-credential invariant. Replacement operations
-- revoke the old row before inserting the new active row in one transaction;
-- the replacement link is then attached to the already-final old row.
CREATE UNIQUE INDEX uq_access_credentials_active_client
    ON gym.access_credentials (client_id)
    WHERE status = 'ACTIVE';

CREATE TRIGGER trg_access_credentials_set_updated_at
BEFORE UPDATE ON gym.access_credentials
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TABLE gym.access_credential_history (
    id UUID PRIMARY KEY,
    credential_id UUID NOT NULL,
    client_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(2000),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_user_id UUID NOT NULL,
    replacement_credential_id UUID,
    CONSTRAINT ck_access_credential_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_access_credential_history_new_status
        CHECK (new_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_access_credential_history_transition
        CHECK (
            (previous_status IS NULL
                AND new_status = 'ACTIVE'
                AND reason IS NULL
                AND replacement_credential_id IS NULL)
            OR
            (previous_status = 'ACTIVE'
                AND new_status = 'REVOKED'
                AND reason IS NOT NULL
                AND char_length(btrim(reason)) >= 3)
        ),
    CONSTRAINT ck_access_credential_history_replacement_not_self
        CHECK (replacement_credential_id IS NULL OR replacement_credential_id <> credential_id),
    CONSTRAINT fk_access_credential_history_credential
        FOREIGN KEY (credential_id)
        REFERENCES gym.access_credentials (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_credential_history_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_credential_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_credential_history_replacement
        FOREIGN KEY (replacement_credential_id)
        REFERENCES gym.access_credentials (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_access_credential_history_credential_occurred_at
    ON gym.access_credential_history (credential_id, occurred_at DESC, id ASC);

CREATE OR REPLACE FUNCTION gym.validate_access_credential()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    replacement_client_id UUID;
    replacement_status VARCHAR(20);
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'new access credentials must start active'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.client_id IS DISTINCT FROM NEW.client_id
        OR OLD.credential_code IS DISTINCT FROM NEW.credential_code
        OR OLD.token_fingerprint IS DISTINCT FROM NEW.token_fingerprint
        OR OLD.token_scheme_version IS DISTINCT FROM NEW.token_scheme_version
        OR OLD.payload_version IS DISTINCT FROM NEW.payload_version
        OR OLD.issued_at IS DISTINCT FROM NEW.issued_at
        OR OLD.issued_by_user_id IS DISTINCT FROM NEW.issued_by_user_id
        OR OLD.storage_key IS DISTINCT FROM NEW.storage_key
        OR OLD.content_type IS DISTINCT FROM NEW.content_type
        OR OLD.size_bytes IS DISTINCT FROM NEW.size_bytes
        OR OLD.checksum_sha256 IS DISTINCT FROM NEW.checksum_sha256
        OR OLD.renderer_version IS DISTINCT FROM NEW.renderer_version
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'access credential identity and artifact metadata are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'REVOKED' THEN
        IF NEW.status <> 'REVOKED'
            OR OLD.replaced_by_credential_id IS NOT NULL
            OR NEW.replaced_by_credential_id IS NULL
            OR OLD.revoked_at IS DISTINCT FROM NEW.revoked_at
            OR OLD.revoked_by_user_id IS DISTINCT FROM NEW.revoked_by_user_id
            OR OLD.revocation_reason IS DISTINCT FROM NEW.revocation_reason THEN
            RAISE EXCEPTION 'final access credentials are immutable'
                USING ERRCODE = '55000';
        END IF;

        SELECT c.client_id, c.status
        INTO replacement_client_id, replacement_status
        FROM gym.access_credentials AS c
        WHERE c.id = NEW.replaced_by_credential_id
        FOR KEY SHARE;

        IF NOT FOUND
            OR replacement_client_id IS DISTINCT FROM NEW.client_id
            OR replacement_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'replacement credential must be an active credential of the same client'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.status <> 'ACTIVE' OR NEW.status <> 'REVOKED' THEN
        RAISE EXCEPTION 'access credential lifecycle transition is invalid'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.replaced_by_credential_id IS NOT NULL THEN
        SELECT c.client_id, c.status
        INTO replacement_client_id, replacement_status
        FROM gym.access_credentials AS c
        WHERE c.id = NEW.replaced_by_credential_id
        FOR KEY SHARE;

        IF NOT FOUND
            OR replacement_client_id IS DISTINCT FROM NEW.client_id
            OR replacement_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'replacement credential must be an active credential of the same client'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_access_credentials_validate
BEFORE INSERT OR UPDATE ON gym.access_credentials
FOR EACH ROW
EXECUTE FUNCTION gym.validate_access_credential();

CREATE OR REPLACE FUNCTION gym.reject_access_credential_delete()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'access credentials cannot be deleted'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_access_credentials_reject_delete
BEFORE DELETE ON gym.access_credentials
FOR EACH ROW
EXECUTE FUNCTION gym.reject_access_credential_delete();

CREATE OR REPLACE FUNCTION gym.validate_access_credential_history()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    credential_client_id UUID;
    credential_status VARCHAR(20);
    credential_issued_at TIMESTAMPTZ;
    credential_revoked_at TIMESTAMPTZ;
    credential_replacement_id UUID;
    last_status VARCHAR(20);
    replacement_client_id UUID;
    replacement_status VARCHAR(20);
BEGIN
    SELECT c.client_id,
           c.status,
           c.issued_at,
           c.revoked_at,
           c.replaced_by_credential_id
    INTO credential_client_id,
         credential_status,
         credential_issued_at,
         credential_revoked_at,
         credential_replacement_id
    FROM gym.access_credentials AS c
    WHERE c.id = NEW.credential_id
    FOR KEY SHARE;

    IF NOT FOUND
        OR credential_client_id IS DISTINCT FROM NEW.client_id
        OR NEW.new_status <> credential_status
        OR NEW.occurred_at < credential_issued_at THEN
        RAISE EXCEPTION 'access credential history does not match its credential'
            USING ERRCODE = '23514';
    END IF;

    SELECT h.new_status
    INTO last_status
    FROM gym.access_credential_history AS h
    WHERE h.credential_id = NEW.credential_id
    ORDER BY h.occurred_at DESC, h.id DESC
    LIMIT 1;

    IF last_status IS NULL THEN
        IF NEW.previous_status IS NOT NULL OR NEW.new_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'access credential history must begin with active issuance'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.previous_status IS DISTINCT FROM last_status THEN
        RAISE EXCEPTION 'access credential history transition does not match prior status'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.new_status = 'REVOKED'
        AND NEW.replacement_credential_id IS DISTINCT FROM credential_replacement_id THEN
        RAISE EXCEPTION 'access credential history replacement does not match the credential'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.new_status = 'REVOKED'
        AND credential_revoked_at IS NOT NULL
        AND NEW.occurred_at < credential_revoked_at THEN
        RAISE EXCEPTION 'access credential history cannot precede revocation'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.replacement_credential_id IS NOT NULL THEN
        SELECT c.client_id, c.status
        INTO replacement_client_id, replacement_status
        FROM gym.access_credentials AS c
        WHERE c.id = NEW.replacement_credential_id
        FOR KEY SHARE;

        IF NOT FOUND
            OR replacement_client_id IS DISTINCT FROM NEW.client_id
            OR replacement_status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'history replacement must be active and belong to the same client'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_access_credential_history_validate
BEFORE INSERT ON gym.access_credential_history
FOR EACH ROW
EXECUTE FUNCTION gym.validate_access_credential_history();

CREATE OR REPLACE FUNCTION gym.reject_access_credential_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'access credential history is append-only'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_access_credential_history_append_only
BEFORE UPDATE OR DELETE ON gym.access_credential_history
FOR EACH ROW
EXECUTE FUNCTION gym.reject_access_credential_history_mutation();
