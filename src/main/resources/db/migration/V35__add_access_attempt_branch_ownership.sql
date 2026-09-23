-- Records the physical branch where each access attempt was processed.
-- Historical attempts have no reliable physical-location snapshot, so they
-- are deliberately attributed to the canonical initial branch.

ALTER TABLE gym.access_records
    ADD COLUMN branch_id UUID;

-- Access attempts are append-only after creation. The branch snapshot is the
-- sole backfill mutation; restore the append-only trigger before commit.
DROP TRIGGER trg_access_records_append_only ON gym.access_records;

DO
$$
DECLARE
    initial_branch_id UUID;
    initial_branch_count INTEGER;
BEGIN
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
            'exactly one active canonical initial branch is required for access backfill';
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

    UPDATE gym.access_records
    SET branch_id = initial_branch_id
    WHERE branch_id IS NULL;

    IF EXISTS (SELECT 1 FROM gym.access_records WHERE branch_id IS NULL) THEN
        RAISE EXCEPTION
            'access-attempt ownership backfill left records without a branch';
    END IF;
END
$$;

CREATE TRIGGER trg_access_records_append_only
BEFORE UPDATE OR DELETE ON gym.access_records
FOR EACH ROW
EXECUTE FUNCTION gym.reject_access_record_mutation();

ALTER TABLE gym.access_records
    ALTER COLUMN branch_id SET NOT NULL,
    ALTER COLUMN branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_access_records_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_access_records_branch_source_result_occurred_at
    ON gym.access_records (
        branch_id,
        access_credential_id,
        identification_source,
        decision,
        occurred_at DESC,
        id ASC);

CREATE OR REPLACE FUNCTION gym.reject_access_record_branch_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NEW.branch_id IS DISTINCT FROM OLD.branch_id THEN
        RAISE EXCEPTION 'access attempt branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_access_records_reject_branch_mutation
BEFORE UPDATE ON gym.access_records
FOR EACH ROW
EXECUTE FUNCTION gym.reject_access_record_branch_mutation();

COMMENT ON COLUMN gym.access_records.branch_id IS
    'Immutable physical branch where the access attempt was processed.';
