-- Persist private staff invitations and password-recovery tokens without
-- storing either raw one-time token. No user row is created before acceptance.

ALTER TABLE gym.users
    DROP CONSTRAINT ck_users_status,
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DEACTIVATED'));

CREATE OR REPLACE FUNCTION gym.validate_staff_user_status_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF OLD.status = 'DEACTIVATED' AND NEW.status <> 'DEACTIVATED' THEN
        RAISE EXCEPTION 'deactivated staff accounts are terminal'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_users_validate_staff_status_transition
BEFORE UPDATE OF status ON gym.users
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_user_status_transition();

CREATE TABLE gym.staff_invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    proposed_role VARCHAR(20) NOT NULL,
    proposed_scope VARCHAR(20) NOT NULL,
    token_fingerprint VARCHAR(64) NOT NULL,
    token_scheme VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_user_id UUID,
    expired_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    invited_by_user_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    snapshot_created_xact_id XID8 NOT NULL DEFAULT pg_current_xact_id(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_staff_invitations_email_normalized
        CHECK (
            email_normalized = lower(btrim(email_normalized))
            AND email_normalized ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
        ),
    CONSTRAINT ck_staff_invitations_role_scope
        CHECK (
            (proposed_role = 'ADMIN' AND proposed_scope IN ('ORGANIZATION', 'BRANCH'))
            OR (proposed_role = 'RECEPTIONIST' AND proposed_scope = 'BRANCH')
        ),
    CONSTRAINT ck_staff_invitations_token_fingerprint
        CHECK (token_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_staff_invitations_token_scheme
        CHECK (token_scheme = 'staff-invitation-sha256-v1'),
    CONSTRAINT ck_staff_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_staff_invitations_expiration
        CHECK (
            last_sent_at >= created_at
            AND expires_at > last_sent_at
            AND expires_at <= last_sent_at + CASE proposed_role
                WHEN 'ADMIN' THEN INTERVAL '24 hours'
                ELSE INTERVAL '48 hours'
            END
        ),
    CONSTRAINT ck_staff_invitations_final_state_metadata
        CHECK (
            (status = 'PENDING'
                AND accepted_at IS NULL AND accepted_user_id IS NULL
                AND expired_at IS NULL AND revoked_at IS NULL)
            OR (status = 'ACCEPTED'
                AND accepted_at IS NOT NULL AND accepted_user_id IS NOT NULL
                AND accepted_at >= last_sent_at
                AND expired_at IS NULL AND revoked_at IS NULL)
            OR (status = 'EXPIRED'
                AND accepted_at IS NULL AND accepted_user_id IS NULL
                AND expired_at >= expires_at
                AND expired_at IS NOT NULL AND revoked_at IS NULL)
            OR (status = 'REVOKED'
                AND accepted_at IS NULL AND accepted_user_id IS NULL
                AND expired_at IS NULL AND revoked_at >= last_sent_at
                AND revoked_at IS NOT NULL)
        ),
    CONSTRAINT ck_staff_invitations_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_staff_invitations_organization
        FOREIGN KEY (organization_id)
        REFERENCES gym.organizations (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_invitations_invited_by_user
        FOREIGN KEY (invited_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_invitations_accepted_user
        FOREIGN KEY (accepted_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_staff_invitations_accepted_user
        UNIQUE (accepted_user_id)
);

CREATE UNIQUE INDEX uq_staff_invitations_token_fingerprint
    ON gym.staff_invitations (token_fingerprint);

CREATE UNIQUE INDEX uq_staff_invitations_pending_email
    ON gym.staff_invitations (email_normalized)
    WHERE status = 'PENDING';

CREATE INDEX idx_staff_invitations_organization_created_at
    ON gym.staff_invitations (organization_id, created_at DESC, id ASC);

CREATE INDEX idx_staff_invitations_pending_expiration
    ON gym.staff_invitations (expires_at ASC, id ASC)
    WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION gym.validate_staff_invitation_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM gym.organizations AS organization
         WHERE organization.id = NEW.organization_id
           AND organization.is_canonical
           AND organization.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'staff invitation requires the active canonical organization'
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM gym.users AS staff_user
         WHERE lower(btrim(staff_user.email)) = NEW.email_normalized
    ) THEN
        RAISE EXCEPTION 'an existing staff account cannot be invited again'
            USING ERRCODE = '23505';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM gym.users AS inviter
          JOIN gym.user_roles AS user_role
            ON user_role.user_id = inviter.id
          JOIN gym.roles AS role
            ON role.id = user_role.role_id
          JOIN gym.staff_scopes AS staff_scope
            ON staff_scope.user_id = inviter.id
         WHERE inviter.id = NEW.invited_by_user_id
           AND inviter.status = 'ACTIVE'
           AND role.role_code = 'ADMIN'
           AND role.is_active
           AND staff_scope.scope_type = 'ORGANIZATION'
    ) THEN
        RAISE EXCEPTION 'staff invitations require an active organization administrator'
            USING ERRCODE = '42501';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_invitations_validate_insert
BEFORE INSERT ON gym.staff_invitations
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_invitation_insert();

CREATE TABLE gym.staff_invitation_branches (
    invitation_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_staff_invitation_branches
        PRIMARY KEY (invitation_id, branch_id),
    CONSTRAINT fk_staff_invitation_branches_invitation
        FOREIGN KEY (invitation_id)
        REFERENCES gym.staff_invitations (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_invitation_branches_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_staff_invitation_branches_branch_invitation
    ON gym.staff_invitation_branches (branch_id, invitation_id);

CREATE OR REPLACE FUNCTION gym.validate_staff_invitation_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'staff invitations are lifecycle-managed'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status <> 'PENDING' THEN
        RAISE EXCEPTION 'final staff invitations are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.organization_id IS DISTINCT FROM OLD.organization_id
       OR NEW.email_normalized IS DISTINCT FROM OLD.email_normalized
       OR NEW.proposed_role IS DISTINCT FROM OLD.proposed_role
       OR NEW.proposed_scope IS DISTINCT FROM OLD.proposed_scope
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.invited_by_user_id IS DISTINCT FROM OLD.invited_by_user_id
       OR NEW.snapshot_created_xact_id IS DISTINCT FROM OLD.snapshot_created_xact_id THEN
        RAISE EXCEPTION 'staff invitation identity and proposal are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'staff invitation version must advance by one'
            USING ERRCODE = '40001';
    END IF;

    IF NEW.status = 'PENDING' THEN
        IF NEW.token_fingerprint IS NOT DISTINCT FROM OLD.token_fingerprint
           OR NEW.token_scheme IS DISTINCT FROM OLD.token_scheme
           OR NEW.last_sent_at <= OLD.last_sent_at
           OR NEW.accepted_at IS NOT NULL
           OR NEW.accepted_user_id IS NOT NULL
           OR NEW.expired_at IS NOT NULL
           OR NEW.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'pending invitation updates must rotate its token'
                USING ERRCODE = '55000';
        END IF;
    ELSIF NEW.status = 'ACCEPTED' THEN
        IF NEW.token_fingerprint IS DISTINCT FROM OLD.token_fingerprint
           OR NEW.accepted_at IS NULL
           OR NEW.accepted_at >= OLD.expires_at
           OR NEW.expired_at IS NOT NULL
           OR NEW.revoked_at IS NOT NULL
           OR NOT EXISTS (
                SELECT 1
                  FROM gym.users AS accepted_user
                 WHERE accepted_user.id = NEW.accepted_user_id
                   AND accepted_user.status = 'ACTIVE'
                   AND lower(btrim(accepted_user.email)) = OLD.email_normalized
           ) THEN
            RAISE EXCEPTION 'staff invitation acceptance is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSIF NEW.status = 'EXPIRED' THEN
        IF NEW.token_fingerprint IS DISTINCT FROM OLD.token_fingerprint
           OR NEW.expired_at IS NULL
           OR NEW.expired_at < OLD.expires_at
           OR NEW.accepted_at IS NOT NULL
           OR NEW.accepted_user_id IS NOT NULL
           OR NEW.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'staff invitation expiration is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSIF NEW.status = 'REVOKED' THEN
        IF NEW.token_fingerprint IS DISTINCT FROM OLD.token_fingerprint
           OR NEW.revoked_at IS NULL
           OR NEW.accepted_at IS NOT NULL
           OR NEW.accepted_user_id IS NOT NULL
           OR NEW.expired_at IS NOT NULL THEN
            RAISE EXCEPTION 'staff invitation revocation is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSE
        RAISE EXCEPTION 'staff invitation transition is invalid'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_invitations_validate_mutation
BEFORE UPDATE OR DELETE ON gym.staff_invitations
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_invitation_mutation();

CREATE TRIGGER trg_staff_invitations_set_updated_at
BEFORE UPDATE ON gym.staff_invitations
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE OR REPLACE FUNCTION gym.validate_staff_invitation_branch_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    invitation_organization_id UUID;
    invitation_status VARCHAR(20);
    invitation_snapshot_xact_id XID8;
    branch_organization_id UUID;
    branch_status VARCHAR(20);
    organization_is_canonical BOOLEAN;
    organization_status VARCHAR(20);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'staff invitation branch sets are immutable'
            USING ERRCODE = '55000';
    END IF;

    SELECT invitation.organization_id,
           invitation.status,
           invitation.snapshot_created_xact_id
      INTO invitation_organization_id,
           invitation_status,
           invitation_snapshot_xact_id
      FROM gym.staff_invitations AS invitation
     WHERE invitation.id = NEW.invitation_id
     FOR KEY SHARE;

    IF NOT FOUND
       OR invitation_status <> 'PENDING'
       OR invitation_snapshot_xact_id <> pg_current_xact_id() THEN
        RAISE EXCEPTION 'invitation branches must be captured with a pending invitation'
            USING ERRCODE = '23514';
    END IF;

    SELECT branch.organization_id,
           branch.status,
           organization.is_canonical,
           organization.status
      INTO branch_organization_id,
           branch_status,
           organization_is_canonical,
           organization_status
      FROM gym.gym_branches AS branch
      JOIN gym.organizations AS organization
        ON organization.id = branch.organization_id
     WHERE branch.id = NEW.branch_id;

    IF NOT FOUND
       OR branch_organization_id <> invitation_organization_id
       OR branch_status <> 'ACTIVE'
       OR organization_is_canonical IS DISTINCT FROM TRUE
       OR organization_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'invitation branch must be active in its organization'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_invitation_branches_validate_mutation
BEFORE INSERT OR UPDATE OR DELETE ON gym.staff_invitation_branches
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_invitation_branch_mutation();

CREATE OR REPLACE FUNCTION gym.validate_staff_invitation_branch_cardinality()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    checked_invitation_id UUID;
    proposal_scope VARCHAR(20);
    branch_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'staff_invitations' THEN
        checked_invitation_id := NEW.id;
    ELSE
        checked_invitation_id := NEW.invitation_id;
    END IF;

    SELECT invitation.proposed_scope
      INTO proposal_scope
      FROM gym.staff_invitations AS invitation
     WHERE invitation.id = checked_invitation_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT count(*)
      INTO branch_count
      FROM gym.staff_invitation_branches AS invitation_branch
     WHERE invitation_branch.invitation_id = checked_invitation_id;

    IF (proposal_scope = 'BRANCH' AND branch_count < 1)
       OR (proposal_scope = 'ORGANIZATION' AND branch_count <> 0) THEN
        RAISE EXCEPTION 'staff invitation branch set does not match its scope'
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$function$;

CREATE CONSTRAINT TRIGGER trg_staff_invitations_branch_cardinality
AFTER INSERT OR UPDATE ON gym.staff_invitations
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_invitation_branch_cardinality();

CREATE CONSTRAINT TRIGGER trg_staff_invitation_branches_cardinality
AFTER INSERT ON gym.staff_invitation_branches
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_invitation_branch_cardinality();

CREATE TABLE gym.staff_password_recovery_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_fingerprint VARCHAR(64) NOT NULL,
    token_scheme VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    failed_attempt_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_staff_password_recovery_token_fingerprint
        CHECK (token_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_staff_password_recovery_token_scheme
        CHECK (token_scheme = 'staff-password-recovery-sha256-v1'),
    CONSTRAINT ck_staff_password_recovery_status
        CHECK (status IN ('PENDING', 'USED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_staff_password_recovery_expiration
        CHECK (expires_at > requested_at
            AND expires_at <= requested_at + INTERVAL '30 minutes'),
    CONSTRAINT ck_staff_password_recovery_final_state_metadata
        CHECK (
            (status = 'PENDING'
                AND used_at IS NULL AND expired_at IS NULL AND revoked_at IS NULL)
            OR (status = 'USED'
                AND used_at IS NOT NULL
                AND used_at >= requested_at AND used_at < expires_at
                AND expired_at IS NULL AND revoked_at IS NULL)
            OR (status = 'EXPIRED'
                AND used_at IS NULL AND expired_at IS NOT NULL
                AND expired_at >= expires_at AND revoked_at IS NULL)
            OR (status = 'REVOKED'
                AND used_at IS NULL AND expired_at IS NULL AND revoked_at IS NOT NULL
                AND revoked_at >= requested_at)
        ),
    CONSTRAINT ck_staff_password_recovery_failed_attempts
        CHECK (failed_attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_staff_password_recovery_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_staff_password_recovery_user
        FOREIGN KEY (user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_staff_password_recovery_token_fingerprint
    ON gym.staff_password_recovery_tokens (token_fingerprint);

CREATE UNIQUE INDEX uq_staff_password_recovery_pending_user
    ON gym.staff_password_recovery_tokens (user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_staff_password_recovery_pending_expiration
    ON gym.staff_password_recovery_tokens (expires_at ASC, id ASC)
    WHERE status = 'PENDING';

CREATE OR REPLACE FUNCTION gym.validate_staff_password_recovery_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM gym.users AS staff_user
         WHERE staff_user.id = NEW.user_id
           AND staff_user.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'password recovery requires an active staff account'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_password_recovery_validate_insert
BEFORE INSERT ON gym.staff_password_recovery_tokens
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_password_recovery_insert();

CREATE OR REPLACE FUNCTION gym.validate_staff_password_recovery_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'password recovery history is lifecycle-managed'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status <> 'PENDING' THEN
        RAISE EXCEPTION 'final password recovery records are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.token_fingerprint IS DISTINCT FROM OLD.token_fingerprint
       OR NEW.token_scheme IS DISTINCT FROM OLD.token_scheme
       OR NEW.requested_at IS DISTINCT FROM OLD.requested_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
        RAISE EXCEPTION 'password recovery token identity is immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'password recovery version must advance by one'
            USING ERRCODE = '40001';
    END IF;

    IF NEW.failed_attempt_count = OLD.failed_attempt_count + 1 THEN
        IF NEW.failed_attempt_count > 5
           OR NEW.used_at IS NOT NULL
           OR NEW.expired_at IS NOT NULL THEN
            RAISE EXCEPTION 'password recovery failed-attempt update is invalid'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.failed_attempt_count = 5 THEN
            IF NEW.status <> 'REVOKED' OR NEW.revoked_at IS NULL THEN
                RAISE EXCEPTION 'password recovery token must be revoked at its attempt limit'
                    USING ERRCODE = '55000';
            END IF;
        ELSIF NEW.status <> 'PENDING' OR NEW.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'password recovery token state is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSIF NEW.failed_attempt_count <> OLD.failed_attempt_count THEN
        RAISE EXCEPTION 'password recovery attempt count must advance one at a time'
            USING ERRCODE = '55000';
    ELSIF NEW.status = 'USED' THEN
        IF NEW.used_at IS NULL OR NEW.used_at >= OLD.expires_at
           OR NEW.expired_at IS NOT NULL OR NEW.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'password recovery use is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSIF NEW.status = 'EXPIRED' THEN
        IF NEW.expired_at IS NULL OR NEW.expired_at < OLD.expires_at
           OR NEW.used_at IS NOT NULL OR NEW.revoked_at IS NOT NULL THEN
            RAISE EXCEPTION 'password recovery expiration is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSIF NEW.status = 'REVOKED' THEN
        IF NEW.revoked_at IS NULL OR NEW.used_at IS NOT NULL OR NEW.expired_at IS NOT NULL THEN
            RAISE EXCEPTION 'password recovery revocation is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSE
        RAISE EXCEPTION 'password recovery transition is invalid'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_password_recovery_validate_mutation
BEFORE UPDATE OR DELETE ON gym.staff_password_recovery_tokens
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_password_recovery_mutation();

CREATE TRIGGER trg_staff_password_recovery_set_updated_at
BEFORE UPDATE ON gym.staff_password_recovery_tokens
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

COMMENT ON TABLE gym.staff_invitations IS
    'Private one-time staff invitations; only token fingerprints are persisted.';
COMMENT ON TABLE gym.staff_invitation_branches IS
    'Immutable branch proposal captured in the same transaction as its invitation.';
COMMENT ON TABLE gym.staff_password_recovery_tokens IS
    'One-time password recovery; raw tokens are never stored.';
