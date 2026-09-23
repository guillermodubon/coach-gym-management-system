-- Branch ownership for physical operations and resource-related notifications.
-- Existing rows are preserved and assigned to the canonical initial branch.

ALTER TABLE gym.equipment
    ADD COLUMN IF NOT EXISTS branch_id UUID;

ALTER TABLE gym.incidents
    ADD COLUMN IF NOT EXISTS branch_id UUID;

ALTER TABLE gym.maintenances
    ADD COLUMN IF NOT EXISTS branch_id UUID;

ALTER TABLE gym.notifications
    ADD COLUMN IF NOT EXISTS branch_id UUID;

-- Keep legacy SQL fixtures and administrative imports safe while the
-- application supplies the branch explicitly from the authenticated context.
ALTER TABLE gym.equipment
    ALTER COLUMN branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002'::UUID;
ALTER TABLE gym.incidents
    ALTER COLUMN branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002'::UUID;
ALTER TABLE gym.maintenances
    ALTER COLUMN branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002'::UUID;

-- Branch backfills must not alter the historical updated_at values.
DROP TRIGGER trg_equipment_set_updated_at ON gym.equipment;
DROP TRIGGER trg_incidents_set_updated_at ON gym.incidents;
DROP TRIGGER trg_maintenances_set_updated_at ON gym.maintenances;
DROP TRIGGER trg_notifications_set_updated_at ON gym.notifications;

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
            'exactly one active canonical initial branch is required for operations ownership backfill';
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

    UPDATE gym.equipment
    SET branch_id = initial_branch_id
    WHERE branch_id IS NULL;

    UPDATE gym.incidents
    SET branch_id = initial_branch_id
    WHERE branch_id IS NULL;

    UPDATE gym.maintenances
    SET branch_id = initial_branch_id
    WHERE branch_id IS NULL;

    UPDATE gym.notifications
    SET branch_id = CASE
        WHEN resource_type IN ('EQUIPMENT', 'INCIDENT', 'MAINTENANCE')
            THEN initial_branch_id
        ELSE NULL
    END
    WHERE branch_id IS NULL;
END
$$;

CREATE TRIGGER trg_equipment_set_updated_at
BEFORE UPDATE ON gym.equipment
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
CREATE TRIGGER trg_incidents_set_updated_at
BEFORE UPDATE ON gym.incidents
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
CREATE TRIGGER trg_maintenances_set_updated_at
BEFORE UPDATE ON gym.maintenances
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
CREATE TRIGGER trg_notifications_set_updated_at
BEFORE UPDATE ON gym.notifications
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM gym.equipment WHERE branch_id IS NULL)
       OR EXISTS (SELECT 1 FROM gym.incidents WHERE branch_id IS NULL)
       OR EXISTS (SELECT 1 FROM gym.maintenances WHERE branch_id IS NULL) THEN
        RAISE EXCEPTION 'V36 branch backfill left null operational ownership';
    END IF;
END
$$;

ALTER TABLE gym.equipment
    ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE gym.incidents
    ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE gym.maintenances
    ALTER COLUMN branch_id SET NOT NULL;

CREATE OR REPLACE FUNCTION gym.reject_equipment_branch_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $$
BEGIN
    IF NEW.branch_id IS DISTINCT FROM OLD.branch_id THEN
        RAISE EXCEPTION 'equipment branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_equipment_reject_branch_mutation
BEFORE UPDATE ON gym.equipment
FOR EACH ROW
EXECUTE FUNCTION gym.reject_equipment_branch_mutation();

CREATE OR REPLACE FUNCTION gym.reject_incident_branch_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $$
BEGIN
    IF NEW.branch_id IS DISTINCT FROM OLD.branch_id THEN
        RAISE EXCEPTION 'incident branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_incidents_reject_branch_mutation
BEFORE UPDATE ON gym.incidents
FOR EACH ROW
EXECUTE FUNCTION gym.reject_incident_branch_mutation();

CREATE OR REPLACE FUNCTION gym.reject_maintenance_branch_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $$
BEGIN
    IF NEW.branch_id IS DISTINCT FROM OLD.branch_id THEN
        RAISE EXCEPTION 'maintenance branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_maintenances_reject_branch_mutation
BEFORE UPDATE ON gym.maintenances
FOR EACH ROW
EXECUTE FUNCTION gym.reject_maintenance_branch_mutation();

CREATE OR REPLACE FUNCTION gym.reject_notification_branch_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $$
BEGIN
    IF NEW.branch_id IS DISTINCT FROM OLD.branch_id THEN
        RAISE EXCEPTION 'notification branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_reject_branch_mutation
BEFORE UPDATE ON gym.notifications
FOR EACH ROW
EXECUTE FUNCTION gym.reject_notification_branch_mutation();

ALTER TABLE gym.equipment
    ADD CONSTRAINT fk_equipment_branch
        FOREIGN KEY (branch_id) REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;
ALTER TABLE gym.incidents
    ADD CONSTRAINT fk_incidents_branch
        FOREIGN KEY (branch_id) REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;
ALTER TABLE gym.maintenances
    ADD CONSTRAINT fk_maintenances_branch
        FOREIGN KEY (branch_id) REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;
ALTER TABLE gym.notifications
    ADD CONSTRAINT fk_notifications_branch
        FOREIGN KEY (branch_id) REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_equipment_branch_status_created_at
    ON gym.equipment (branch_id, status, created_at DESC, id ASC);
CREATE INDEX idx_incidents_branch_status_reported_at
    ON gym.incidents (branch_id, status, reported_at DESC, id ASC);
CREATE INDEX idx_maintenances_branch_status_scheduled_on
    ON gym.maintenances (branch_id, status, scheduled_on, id ASC);
CREATE INDEX idx_notifications_recipient_branch_created_at
    ON gym.notifications (recipient_user_id, branch_id, created_at DESC, id ASC);

CREATE OR REPLACE FUNCTION gym.validate_incident_branch_consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE equipment_branch UUID;
BEGIN
    SELECT branch_id INTO equipment_branch
    FROM gym.equipment
    WHERE id = NEW.equipment_id;
    IF equipment_branch IS NULL OR equipment_branch <> NEW.branch_id THEN
        RAISE EXCEPTION 'Incident branch must match equipment branch'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_incidents_validate_branch_consistency
BEFORE INSERT OR UPDATE OF equipment_id, branch_id ON gym.incidents
FOR EACH ROW
EXECUTE FUNCTION gym.validate_incident_branch_consistency();

CREATE OR REPLACE FUNCTION gym.validate_maintenance_branch_consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE equipment_branch UUID;
DECLARE incident_branch UUID;
BEGIN
    SELECT branch_id INTO equipment_branch
    FROM gym.equipment
    WHERE id = NEW.equipment_id;
    IF equipment_branch IS NULL OR equipment_branch <> NEW.branch_id THEN
        RAISE EXCEPTION 'Maintenance branch must match equipment branch'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.incident_id IS NOT NULL THEN
        SELECT branch_id INTO incident_branch
        FROM gym.incidents
        WHERE id = NEW.incident_id;
        IF incident_branch IS NULL OR incident_branch <> NEW.branch_id THEN
            RAISE EXCEPTION 'Maintenance branch must match incident branch'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_maintenances_validate_branch_consistency
BEFORE INSERT OR UPDATE OF equipment_id, incident_id, branch_id ON gym.maintenances
FOR EACH ROW
EXECUTE FUNCTION gym.validate_maintenance_branch_consistency();

CREATE OR REPLACE FUNCTION gym.validate_notification_branch_consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE source_branch UUID;
BEGIN
    IF NEW.resource_type = 'EQUIPMENT' THEN
        SELECT branch_id INTO source_branch FROM gym.equipment WHERE id = NEW.resource_id;
    ELSIF NEW.resource_type = 'INCIDENT' THEN
        SELECT branch_id INTO source_branch FROM gym.incidents WHERE id = NEW.resource_id;
    ELSIF NEW.resource_type = 'MAINTENANCE' THEN
        SELECT branch_id INTO source_branch FROM gym.maintenances WHERE id = NEW.resource_id;
    ELSE
        RETURN NEW;
    END IF;
    IF source_branch IS NULL THEN
        RAISE EXCEPTION 'Resource notification branch must match its source branch'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.branch_id IS NULL THEN
        NEW.branch_id := source_branch;
    ELSIF source_branch <> NEW.branch_id THEN
        RAISE EXCEPTION 'Resource notification branch must match its source branch'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_validate_branch_consistency
BEFORE INSERT OR UPDATE OF resource_type, resource_id, branch_id ON gym.notifications
FOR EACH ROW
EXECUTE FUNCTION gym.validate_notification_branch_consistency();

COMMENT ON COLUMN gym.equipment.branch_id IS
    'Immutable physical branch ownership; assigned by authorized server context.';
COMMENT ON COLUMN gym.incidents.branch_id IS
    'Immutable branch snapshot matching the related equipment.';
COMMENT ON COLUMN gym.maintenances.branch_id IS
    'Immutable branch snapshot matching equipment and optional incident.';
COMMENT ON COLUMN gym.notifications.branch_id IS
    'Nullable for organization-global notifications; required and source-matching for operational resources.';
