-- Establishes branch coverage for plans and immutable coverage snapshots for
-- purchased periods. Existing plan coverage is derived from active canonical
-- branch origins already recorded on membership periods; each historical
-- period retains only its own authoritative registration branch.

DO
$$
DECLARE
    active_canonical_organization_count INTEGER;
    active_initial_branch_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO active_canonical_organization_count
    FROM gym.organizations
    WHERE is_canonical
      AND status = 'ACTIVE';

    IF active_canonical_organization_count <> 1 THEN
        RAISE EXCEPTION
            'exactly one active canonical organization is required for branch coverage backfill';
    END IF;

    SELECT COUNT(*)
    INTO active_initial_branch_count
    FROM gym.organizations AS organization
    JOIN gym.gym_branches AS branch
        ON branch.organization_id = organization.id
    WHERE organization.is_canonical
      AND organization.status = 'ACTIVE'
      AND branch.is_initial_branch
      AND branch.status = 'ACTIVE';

    IF active_initial_branch_count <> 1 THEN
        RAISE EXCEPTION
            'exactly one active canonical initial branch is required for branch coverage backfill';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM gym.membership_periods AS period
        WHERE NOT EXISTS (
            SELECT 1
            FROM gym.gym_branches AS branch
            JOIN gym.organizations AS organization
                ON organization.id = branch.organization_id
            WHERE branch.id = period.registered_at_branch_id
              AND organization.is_canonical
              AND organization.status = 'ACTIVE'
        )
    ) THEN
        RAISE EXCEPTION
            'membership period branch ownership is missing or outside the active canonical organization';
    END IF;
END
$$;

ALTER TABLE gym.membership_plans
    ADD COLUMN branch_coverage_scope VARCHAR(24);

CREATE TABLE gym.membership_plan_branches (
    membership_plan_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_membership_plan_branches
        PRIMARY KEY (membership_plan_id, branch_id),
    CONSTRAINT fk_membership_plan_branches_plan
        FOREIGN KEY (membership_plan_id)
        REFERENCES gym.membership_plans (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_plan_branches_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_membership_plan_branches_branch_plan
    ON gym.membership_plan_branches (branch_id, membership_plan_id);

CREATE TABLE gym.membership_period_coverage_snapshots (
    membership_period_id UUID PRIMARY KEY,
    coverage_scope_snapshot VARCHAR(24) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_plan_version BIGINT NOT NULL,
    snapshot_created_xact_id xid8 NOT NULL DEFAULT pg_current_xact_id(),
    CONSTRAINT ck_membership_period_coverage_snapshots_scope
        CHECK (coverage_scope_snapshot IN (
            'SINGLE_BRANCH', 'SELECTED_BRANCHES', 'ALL_BRANCHES')),
    CONSTRAINT ck_membership_period_coverage_snapshots_source_version
        CHECK (source_plan_version >= 0),
    CONSTRAINT fk_membership_period_coverage_snapshots_period
        FOREIGN KEY (membership_period_id)
        REFERENCES gym.membership_periods (id)
        ON DELETE RESTRICT
);

CREATE TABLE gym.membership_period_branch_coverage (
    membership_period_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    CONSTRAINT pk_membership_period_branch_coverage
        PRIMARY KEY (membership_period_id, branch_id),
    CONSTRAINT fk_membership_period_branch_coverage_snapshot
        FOREIGN KEY (membership_period_id)
        REFERENCES gym.membership_period_coverage_snapshots (membership_period_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_period_branch_coverage_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT
);

CREATE TABLE gym.branch_access_policy_overrides (
    branch_id UUID PRIMARY KEY,
    policy_mode VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_branch_access_policy_overrides_mode
        CHECK (policy_mode IN ('INHERIT', 'REQUIRED', 'NOT_REQUIRED')),
    CONSTRAINT ck_branch_access_policy_overrides_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_branch_access_policy_overrides_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_branch_access_policy_overrides_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

-- Only the canonical organization's active branches can become current plan
-- coverage. Historical period snapshots may retain an inactive branch.
CREATE OR REPLACE FUNCTION gym.validate_canonical_coverage_branch()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    branch_is_allowed BOOLEAN;
BEGIN
    SELECT organization.is_canonical
           AND organization.status = 'ACTIVE'
           AND (TG_ARGV[0] <> 'ACTIVE' OR branch.status = 'ACTIVE')
    INTO branch_is_allowed
    FROM gym.gym_branches AS branch
    JOIN gym.organizations AS organization
        ON organization.id = branch.organization_id
    WHERE branch.id = NEW.branch_id;

    IF branch_is_allowed IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'branch coverage must reference a canonical organization branch'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_membership_plan_branches_validate_branch
BEFORE INSERT OR UPDATE OF branch_id ON gym.membership_plan_branches
FOR EACH ROW
EXECUTE FUNCTION gym.validate_canonical_coverage_branch('ACTIVE');

CREATE TRIGGER trg_membership_period_branch_coverage_validate_branch
BEFORE INSERT OR UPDATE OF branch_id ON gym.membership_period_branch_coverage
FOR EACH ROW
EXECUTE FUNCTION gym.validate_canonical_coverage_branch('ANY');

CREATE TRIGGER trg_branch_access_policy_overrides_validate_branch
BEFORE INSERT OR UPDATE OF branch_id ON gym.branch_access_policy_overrides
FOR EACH ROW
EXECUTE FUNCTION gym.validate_canonical_coverage_branch('ANY');

-- A snapshot's branch set is written with its header in one transaction. The
-- transaction identifier prevents a later INSERT from expanding an already
-- committed entitlement, while deferred validation allows the complete set to
-- be inserted atomically by the later membership application block.
CREATE OR REPLACE FUNCTION gym.validate_membership_period_branch_coverage_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    snapshot_xact_id xid8;
BEGIN
    SELECT snapshot.snapshot_created_xact_id
    INTO snapshot_xact_id
    FROM gym.membership_period_coverage_snapshots AS snapshot
    WHERE snapshot.membership_period_id = NEW.membership_period_id
    FOR KEY SHARE;

    IF NOT FOUND OR snapshot_xact_id IS DISTINCT FROM pg_current_xact_id() THEN
        RAISE EXCEPTION 'membership-period branch coverage is immutable after capture'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_membership_period_branch_coverage_insert_during_capture
BEFORE INSERT ON gym.membership_period_branch_coverage
FOR EACH ROW
EXECUTE FUNCTION gym.validate_membership_period_branch_coverage_insert();

CREATE OR REPLACE FUNCTION gym.validate_membership_period_coverage_cardinality()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    snapshot_scope VARCHAR(24);
    covered_branch_count BIGINT;
BEGIN
    SELECT snapshot.coverage_scope_snapshot
    INTO snapshot_scope
    FROM gym.membership_period_coverage_snapshots AS snapshot
    WHERE snapshot.membership_period_id = NEW.membership_period_id;

    SELECT COUNT(*)
    INTO covered_branch_count
    FROM gym.membership_period_branch_coverage AS coverage
    WHERE coverage.membership_period_id = NEW.membership_period_id;

    IF (snapshot_scope = 'SINGLE_BRANCH' AND covered_branch_count <> 1)
       OR (snapshot_scope = 'SELECTED_BRANCHES' AND covered_branch_count < 2)
       OR (snapshot_scope = 'ALL_BRANCHES' AND covered_branch_count < 1) THEN
        RAISE EXCEPTION 'membership-period branch coverage has invalid scope cardinality'
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$function$;

CREATE CONSTRAINT TRIGGER trg_membership_period_coverage_cardinality
AFTER INSERT ON gym.membership_period_coverage_snapshots
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION gym.validate_membership_period_coverage_cardinality();

CREATE OR REPLACE FUNCTION gym.reject_membership_period_coverage_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'membership-period branch coverage snapshots are immutable'
        USING ERRCODE = '55000';
    RETURN NULL;
END;
$function$;

CREATE TRIGGER trg_membership_period_coverage_snapshots_append_only
BEFORE UPDATE OR DELETE ON gym.membership_period_coverage_snapshots
FOR EACH ROW
EXECUTE FUNCTION gym.reject_membership_period_coverage_mutation();

CREATE TRIGGER trg_membership_period_branch_coverage_append_only
BEFORE UPDATE OR DELETE ON gym.membership_period_branch_coverage
FOR EACH ROW
EXECUTE FUNCTION gym.reject_membership_period_coverage_mutation();

-- The backfill only considers branch origins that remain active for new plan
-- sales. Period snapshots below retain their own branch even if that branch is
-- now inactive. Plans with no active historical use map to the initial branch.
INSERT INTO gym.membership_plan_branches (membership_plan_id, branch_id)
WITH canonical_organization AS (
    SELECT id
    FROM gym.organizations
    WHERE is_canonical
      AND status = 'ACTIVE'
),
initial_branch AS (
    SELECT branch.id
    FROM gym.gym_branches AS branch
    JOIN canonical_organization AS organization
        ON organization.id = branch.organization_id
    WHERE branch.is_initial_branch
      AND branch.status = 'ACTIVE'
),
active_period_branches AS (
    SELECT DISTINCT period.membership_plan_id, period.registered_at_branch_id AS branch_id
    FROM gym.membership_periods AS period
    JOIN gym.gym_branches AS branch
        ON branch.id = period.registered_at_branch_id
    JOIN canonical_organization AS organization
        ON organization.id = branch.organization_id
    WHERE branch.status = 'ACTIVE'
),
plan_branch_assignments AS (
    SELECT membership_plan_id, branch_id
    FROM active_period_branches
    UNION
    SELECT plan.id AS membership_plan_id, initial_branch.id AS branch_id
    FROM gym.membership_plans AS plan
    CROSS JOIN initial_branch
    WHERE NOT EXISTS (
        SELECT 1
        FROM active_period_branches AS used_branch
        WHERE used_branch.membership_plan_id = plan.id
    )
)
SELECT membership_plan_id, branch_id
FROM plan_branch_assignments;

-- Coverage metadata must not make an existing plan look newly updated or
-- increment its optimistic-lock version.
DROP TRIGGER trg_membership_plans_set_updated_at ON gym.membership_plans;

UPDATE gym.membership_plans AS plan
SET branch_coverage_scope = CASE
    WHEN branch_count.covered_branch_count = 1 THEN 'SINGLE_BRANCH'
    ELSE 'SELECTED_BRANCHES'
END
FROM (
    SELECT membership_plan_id, COUNT(*) AS covered_branch_count
    FROM gym.membership_plan_branches
    GROUP BY membership_plan_id
) AS branch_count
WHERE plan.id = branch_count.membership_plan_id;

CREATE TRIGGER trg_membership_plans_set_updated_at
BEFORE UPDATE ON gym.membership_plans
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

ALTER TABLE gym.membership_plans
    ALTER COLUMN branch_coverage_scope SET DEFAULT 'SINGLE_BRANCH',
    ALTER COLUMN branch_coverage_scope SET NOT NULL,
    ADD CONSTRAINT ck_membership_plans_branch_coverage_scope
        CHECK (branch_coverage_scope IN (
            'SINGLE_BRANCH', 'SELECTED_BRANCHES', 'ALL_BRANCHES'));

-- Legacy periods have no trustworthy record of their original coverage rule.
-- Snapshot only the persisted registration branch to preserve origin without
-- broadening historical entitlements. source_plan_version identifies the
-- definition version present when this migration captures that baseline.
INSERT INTO gym.membership_period_coverage_snapshots (
    membership_period_id,
    coverage_scope_snapshot,
    captured_at,
    source_plan_version
)
SELECT period.id,
       'SINGLE_BRANCH',
       CURRENT_TIMESTAMP,
       plan.version
FROM gym.membership_periods AS period
JOIN gym.membership_plans AS plan
    ON plan.id = period.membership_plan_id;

INSERT INTO gym.membership_period_branch_coverage (
    membership_period_id,
    branch_id
)
SELECT period.id,
       period.registered_at_branch_id
FROM gym.membership_periods AS period;

-- An absent row for a newly opened branch is also interpreted as INHERIT by
-- the policy adapter. Existing branches receive an explicit version-zero
-- baseline so a later clear/inherit operation remains versioned.
INSERT INTO gym.branch_access_policy_overrides (branch_id, policy_mode)
SELECT branch.id, 'INHERIT'
FROM gym.gym_branches AS branch
JOIN gym.organizations AS organization
    ON organization.id = branch.organization_id
WHERE organization.is_canonical
  AND organization.status = 'ACTIVE';

CREATE TRIGGER trg_branch_access_policy_overrides_set_updated_at
BEFORE UPDATE ON gym.branch_access_policy_overrides
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

-- Permit the denial contract introduced in Block 1 only after its persistence
-- is supported by the database. Preserve every reason already allowed by V25.
ALTER TABLE gym.access_records
    DROP CONSTRAINT ck_access_records_reason_code;

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_reason_code
        CHECK (reason_code IN (
            'ACCESS_ALLOWED',
            'IDENTIFIER_NOT_FOUND',
            'CLIENT_INACTIVE',
            'MEMBERSHIP_NOT_FOUND',
            'MEMBERSHIP_NOT_STARTED',
            'MEMBERSHIP_PERIOD_EXPIRED',
            'MEMBERSHIP_FROZEN',
            'MEMBERSHIP_EXPIRED',
            'MEMBERSHIP_CANCELLED',
            'ACCESS_CREDENTIAL_INVALID',
            'DUPLICATE_CHECK_IN',
            'PAYMENT_REQUIRED',
            'MEMBERSHIP_NOT_VALID_AT_BRANCH'
        ));

COMMENT ON COLUMN gym.membership_plans.branch_coverage_scope IS
    'Current branch coverage definition; cardinality is validated by the plan application service.';
COMMENT ON TABLE gym.membership_plan_branches IS
    'Explicit active canonical branch associations for SINGLE_BRANCH and SELECTED_BRANCHES plans.';
COMMENT ON TABLE gym.membership_period_coverage_snapshots IS
    'Immutable per-period coverage scope and source-version metadata captured at sale, renewal, or migration.';
COMMENT ON COLUMN gym.membership_period_coverage_snapshots.snapshot_created_xact_id IS
    'Database transaction identifier used to prevent post-capture additions to the immutable branch set.';
COMMENT ON TABLE gym.membership_period_branch_coverage IS
    'Immutable branch entitlement rows for a membership-period coverage snapshot.';
COMMENT ON TABLE gym.branch_access_policy_overrides IS
    'Optional versioned branch override for the organization confirmed-payment access default.';
