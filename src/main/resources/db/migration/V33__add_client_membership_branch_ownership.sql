-- Establishes the immutable operational origin of clients, memberships, and
-- membership periods. Existing single-branch data is attributed to the
-- canonical initial branch before the columns become mandatory.

ALTER TABLE gym.clients
    ADD COLUMN home_branch_id UUID;

ALTER TABLE gym.memberships
    ADD COLUMN registered_at_branch_id UUID;

ALTER TABLE gym.membership_periods
    ADD COLUMN registered_at_branch_id UUID;

-- Preserve historical last-updated timestamps while adding ownership data.
DROP TRIGGER trg_clients_set_updated_at ON gym.clients;
DROP TRIGGER trg_memberships_set_updated_at ON gym.memberships;
DROP TRIGGER trg_membership_periods_set_updated_at ON gym.membership_periods;

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
            'exactly one active canonical initial branch is required for operational ownership backfill';
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

    UPDATE gym.clients
    SET home_branch_id = initial_branch_id
    WHERE home_branch_id IS NULL;

    UPDATE gym.memberships AS membership
    SET registered_at_branch_id = client.home_branch_id
    FROM gym.clients AS client
    WHERE membership.client_id = client.id
      AND membership.registered_at_branch_id IS NULL;

    UPDATE gym.membership_periods AS period
    SET registered_at_branch_id = membership.registered_at_branch_id
    FROM gym.memberships AS membership
    WHERE period.membership_id = membership.id
      AND period.registered_at_branch_id IS NULL;

    IF EXISTS (SELECT 1 FROM gym.clients WHERE home_branch_id IS NULL)
        OR EXISTS (
            SELECT 1
            FROM gym.memberships
            WHERE registered_at_branch_id IS NULL)
        OR EXISTS (
            SELECT 1
            FROM gym.membership_periods
            WHERE registered_at_branch_id IS NULL) THEN
        RAISE EXCEPTION
            'operational ownership backfill left records without a branch';
    END IF;
END
$$;

CREATE TRIGGER trg_clients_set_updated_at
BEFORE UPDATE ON gym.clients
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
CREATE TRIGGER trg_memberships_set_updated_at
BEFORE UPDATE ON gym.memberships
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
CREATE TRIGGER trg_membership_periods_set_updated_at
BEFORE UPDATE ON gym.membership_periods
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

ALTER TABLE gym.clients
    ALTER COLUMN home_branch_id SET NOT NULL,
    ALTER COLUMN home_branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_clients_home_branch
        FOREIGN KEY (home_branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

ALTER TABLE gym.memberships
    ALTER COLUMN registered_at_branch_id SET NOT NULL,
    ALTER COLUMN registered_at_branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_memberships_registered_at_branch
        FOREIGN KEY (registered_at_branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

ALTER TABLE gym.membership_periods
    ALTER COLUMN registered_at_branch_id SET NOT NULL,
    ALTER COLUMN registered_at_branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_membership_periods_registered_at_branch
        FOREIGN KEY (registered_at_branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_clients_home_branch_status
    ON gym.clients (home_branch_id, status, last_name, first_name, id);

CREATE INDEX idx_memberships_registered_at_branch_status
    ON gym.memberships (registered_at_branch_id, status, client_id, id);

CREATE INDEX idx_membership_periods_registered_at_branch_membership
    ON gym.membership_periods (
        registered_at_branch_id,
        membership_id,
        starts_on DESC,
        id);

COMMENT ON COLUMN gym.clients.home_branch_id IS
    'Immutable operational home branch; current cross-branch use policy is deferred.';

COMMENT ON COLUMN gym.memberships.registered_at_branch_id IS
    'Branch where the membership was registered; immutable origin snapshot.';

COMMENT ON COLUMN gym.membership_periods.registered_at_branch_id IS
    'Branch where the membership period was registered; immutable origin snapshot.';
