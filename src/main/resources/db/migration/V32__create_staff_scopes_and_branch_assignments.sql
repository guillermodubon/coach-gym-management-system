-- Establishes one authoritative scope per supported staff account and an
-- append-only lifecycle for branch assignments. Existing business resources
-- remain intentionally unscoped until the later operational blocks.

CREATE TABLE gym.staff_scopes (
    user_id UUID PRIMARY KEY,
    scope_type VARCHAR(20) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_scopes_scope_type
        CHECK (scope_type IN ('ORGANIZATION', 'BRANCH')),
    CONSTRAINT ck_staff_scopes_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_staff_scopes_user
        FOREIGN KEY (user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_scopes_granted_by_user
        FOREIGN KEY (granted_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE TABLE gym.staff_branch_assignments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_user_id UUID,
    ended_at TIMESTAMPTZ,
    ended_by_user_id UUID,
    end_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_branch_assignments_status
        CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_staff_branch_assignments_end_metadata
        CHECK (
            (status = 'ACTIVE'
                AND ended_at IS NULL
                AND ended_by_user_id IS NULL
                AND end_reason IS NULL)
            OR
            (status = 'ENDED'
                AND ended_at IS NOT NULL
                AND ended_by_user_id IS NOT NULL
                AND end_reason IS NOT NULL
                AND btrim(end_reason) <> '')
        ),
    CONSTRAINT ck_staff_branch_assignments_end_order
        CHECK (ended_at IS NULL OR ended_at >= assigned_at),
    CONSTRAINT ck_staff_branch_assignments_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_staff_branch_assignments_user
        FOREIGN KEY (user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_branch_assignments_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_branch_assignments_assigned_by_user
        FOREIGN KEY (assigned_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_branch_assignments_ended_by_user
        FOREIGN KEY (ended_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_staff_branch_assignments_active_user_branch
    ON gym.staff_branch_assignments (user_id, branch_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_staff_branch_assignments_branch_active
    ON gym.staff_branch_assignments (branch_id, user_id, id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_staff_branch_assignments_user_history
    ON gym.staff_branch_assignments (user_id, assigned_at DESC, id DESC);

CREATE OR REPLACE FUNCTION gym.reject_staff_scope_delete()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'staff scope records are lifecycle-managed'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_staff_scopes_reject_delete
BEFORE DELETE ON gym.staff_scopes
FOR EACH ROW
EXECUTE FUNCTION gym.reject_staff_scope_delete();

CREATE OR REPLACE FUNCTION gym.validate_staff_scope_update()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        RAISE EXCEPTION 'staff scope identity is immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'staff scope version must advance by one'
            USING ERRCODE = '40001';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_scopes_validate_update
BEFORE UPDATE ON gym.staff_scopes
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_scope_update();

CREATE OR REPLACE FUNCTION gym.validate_staff_scope_role_pair()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    role_count INTEGER;
    has_admin BOOLEAN;
    has_receptionist BOOLEAN;
BEGIN
    SELECT COUNT(*),
           BOOL_OR(role.role_code = 'ADMIN'),
           BOOL_OR(role.role_code = 'RECEPTIONIST')
    INTO role_count, has_admin, has_receptionist
    FROM gym.user_roles AS user_role
    JOIN gym.roles AS role ON role.id = user_role.role_id
    WHERE user_role.user_id = NEW.user_id;

    IF role_count <> 1
        OR (NEW.scope_type = 'ORGANIZATION' AND COALESCE(has_admin, FALSE) = FALSE)
        OR (NEW.scope_type = 'BRANCH'
            AND COALESCE(has_admin, FALSE) = FALSE
            AND COALESCE(has_receptionist, FALSE) = FALSE) THEN
        RAISE EXCEPTION 'staff scope is incompatible with the user role'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.scope_type = 'ORGANIZATION' AND COALESCE(has_receptionist, FALSE) THEN
        RAISE EXCEPTION 'RECEPTIONIST staff cannot have organization scope'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_scopes_validate_role
BEFORE INSERT OR UPDATE ON gym.staff_scopes
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_scope_role_pair();

CREATE OR REPLACE FUNCTION gym.validate_user_role_scope_pair()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    affected_user_id UUID;
    existing_scope VARCHAR(20);
    role_count INTEGER;
    has_admin BOOLEAN;
    has_receptionist BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        affected_user_id := OLD.user_id;
    ELSE
        affected_user_id := NEW.user_id;
    END IF;

    SELECT scope_type
    INTO existing_scope
    FROM gym.staff_scopes
    WHERE user_id = affected_user_id;

    IF existing_scope IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(*),
           BOOL_OR(role.role_code = 'ADMIN'),
           BOOL_OR(role.role_code = 'RECEPTIONIST')
    INTO role_count, has_admin, has_receptionist
    FROM gym.user_roles AS user_role
    JOIN gym.roles AS role ON role.id = user_role.role_id
    WHERE user_role.user_id = affected_user_id;

    IF role_count <> 1
        OR (existing_scope = 'ORGANIZATION' AND COALESCE(has_admin, FALSE) = FALSE)
        OR (existing_scope = 'BRANCH'
            AND COALESCE(has_admin, FALSE) = FALSE
            AND COALESCE(has_receptionist, FALSE) = FALSE) THEN
        RAISE EXCEPTION 'staff role change would invalidate the current staff scope'
            USING ERRCODE = '23514';
    END IF;

    IF existing_scope = 'ORGANIZATION' AND COALESCE(has_receptionist, FALSE) THEN
        RAISE EXCEPTION 'RECEPTIONIST staff cannot have organization scope'
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$function$;

CREATE TRIGGER trg_user_roles_validate_staff_scope
AFTER INSERT OR UPDATE OR DELETE ON gym.user_roles
FOR EACH ROW
EXECUTE FUNCTION gym.validate_user_role_scope_pair();

CREATE OR REPLACE FUNCTION gym.protect_staff_branch_assignment_history()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'staff branch assignments are append-only and cannot be deleted'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status <> 'ACTIVE' OR NEW.status <> 'ENDED' THEN
        RAISE EXCEPTION 'an assignment may transition only from ACTIVE to ENDED'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.user_id IS DISTINCT FROM OLD.user_id
        OR NEW.branch_id IS DISTINCT FROM OLD.branch_id
        OR NEW.assigned_at IS DISTINCT FROM OLD.assigned_at
        OR NEW.assigned_by_user_id IS DISTINCT FROM OLD.assigned_by_user_id THEN
        RAISE EXCEPTION 'staff branch assignment identity and assignment metadata are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'staff branch assignment version must advance by one'
            USING ERRCODE = '40001';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_branch_assignments_protect_history
BEFORE UPDATE OR DELETE ON gym.staff_branch_assignments
FOR EACH ROW
EXECUTE FUNCTION gym.protect_staff_branch_assignment_history();

DO
$$
DECLARE
    initial_branch_id UUID;
    initial_branch_count INTEGER;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM gym.user_roles AS user_role
        JOIN gym.roles AS role ON role.id = user_role.role_id
        WHERE role.role_code NOT IN ('ADMIN', 'RECEPTIONIST')
    ) THEN
        RAISE EXCEPTION
            'unsupported staff role assignment prevents deterministic scope migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM gym.users AS user_account
        LEFT JOIN gym.user_roles AS user_role
            ON user_role.user_id = user_account.id
        LEFT JOIN gym.roles AS role
            ON role.id = user_role.role_id
        GROUP BY user_account.id
        HAVING COUNT(role.role_code) <> 1
    ) THEN
        RAISE EXCEPTION
            'each existing staff user must have exactly one supported role before scope migration';
    END IF;

    SELECT COUNT(*)
    INTO initial_branch_count
    FROM gym.organizations AS organization
    JOIN gym.gym_branches AS branch
        ON branch.organization_id = organization.id
    WHERE organization.is_canonical
      AND organization.status = 'ACTIVE'
      AND branch.is_initial_branch
      AND branch.status = 'ACTIVE';

    IF initial_branch_count <> 1 THEN
        RAISE EXCEPTION
            'exactly one active canonical initial branch is required for staff scope migration';
    END IF;

    SELECT branch.id
    INTO initial_branch_id
    FROM gym.organizations AS organization
    JOIN gym.gym_branches AS branch
        ON branch.organization_id = organization.id
    WHERE organization.is_canonical
      AND organization.status = 'ACTIVE'
      AND branch.is_initial_branch
      AND branch.status = 'ACTIVE';

    INSERT INTO gym.staff_scopes (
        user_id,
        scope_type,
        granted_at,
        granted_by_user_id,
        version
    )
    SELECT
        user_account.id,
        CASE role.role_code
            WHEN 'ADMIN' THEN 'ORGANIZATION'
            WHEN 'RECEPTIONIST' THEN 'BRANCH'
        END,
        CURRENT_TIMESTAMP,
        NULL,
        0
    FROM gym.users AS user_account
    JOIN gym.user_roles AS user_role
        ON user_role.user_id = user_account.id
    JOIN gym.roles AS role
        ON role.id = user_role.role_id;

    INSERT INTO gym.staff_branch_assignments (
        id,
        user_id,
        branch_id,
        status,
        assigned_at,
        assigned_by_user_id,
        version
    )
    SELECT
        md5(user_account.id::text || ':initial-branch')::uuid,
        user_account.id,
        initial_branch_id,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        NULL,
        0
    FROM gym.users AS user_account
    JOIN gym.user_roles AS user_role
        ON user_role.user_id = user_account.id
    JOIN gym.roles AS role
        ON role.id = user_role.role_id
    WHERE role.role_code = 'RECEPTIONIST';
END
$$;

COMMENT ON TABLE gym.staff_scopes IS
    'One current organizational scope per supported staff account.';
COMMENT ON TABLE gym.staff_branch_assignments IS
    'Append-only staff branch assignment lifecycle; ACTIVE rows may transition once to ENDED.';
