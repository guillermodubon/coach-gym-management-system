-- Keep authorization freshness independent from the JPA row version, which
-- also advances for profile edits and successful-login timestamps.
ALTER TABLE gym.users
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_users_security_version_non_negative
        CHECK (security_version >= 0);

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

    IF OLD.status = 'ACTIVE'
       AND NEW.status NOT IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED') THEN
        RAISE EXCEPTION 'active staff accounts may only be suspended or deactivated'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'SUSPENDED'
       AND NEW.status NOT IN ('SUSPENDED', 'ACTIVE', 'DEACTIVATED') THEN
        RAISE EXCEPTION 'suspended staff accounts may only be reactivated or deactivated'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'INACTIVE' AND NEW.status <> 'INACTIVE' THEN
        RAISE EXCEPTION 'legacy inactive staff accounts require explicit recovery'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.security_version < OLD.security_version
       OR NEW.security_version > OLD.security_version + 1 THEN
        RAISE EXCEPTION 'staff security version must stay fixed or advance once'
            USING ERRCODE = '40001';
    END IF;

    IF (NEW.status IS DISTINCT FROM OLD.status
        OR NEW.password_hash IS DISTINCT FROM OLD.password_hash
        OR NEW.password_change_required IS DISTINCT FROM OLD.password_change_required)
       AND NEW.security_version <> OLD.security_version + 1 THEN
        RAISE EXCEPTION 'security-sensitive staff changes must advance the security version'
            USING ERRCODE = '40001';
    END IF;

    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_users_validate_staff_status_transition ON gym.users;

CREATE TRIGGER trg_users_validate_staff_status_transition
BEFORE UPDATE ON gym.users
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_user_status_transition();
