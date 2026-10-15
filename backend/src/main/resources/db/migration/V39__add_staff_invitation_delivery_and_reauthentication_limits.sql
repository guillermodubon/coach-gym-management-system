-- Durable abuse controls for staff invitation delivery and administrator
-- reauthentication. No message body, recipient copy, password, or token is stored.

CREATE INDEX idx_staff_invitations_inviter_created_at
    ON gym.staff_invitations (invited_by_user_id, created_at DESC, id);

CREATE TABLE gym.staff_invitation_delivery_attempts (
    id UUID PRIMARY KEY,
    invitation_id UUID NOT NULL,
    invitation_version BIGINT NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_staff_invitation_delivery_attempt_version
        UNIQUE (invitation_id, invitation_version),
    CONSTRAINT ck_staff_invitation_delivery_attempt_version
        CHECK (invitation_version >= 0),
    CONSTRAINT ck_staff_invitation_delivery_attempt_outcome
        CHECK (outcome IN ('RESERVED', 'SENT', 'FAILED', 'AMBIGUOUS')),
    CONSTRAINT ck_staff_invitation_delivery_attempt_completion
        CHECK (
            (outcome = 'RESERVED' AND completed_at IS NULL)
            OR (outcome <> 'RESERVED' AND completed_at IS NOT NULL
                AND completed_at >= attempted_at)
        ),
    CONSTRAINT fk_staff_invitation_delivery_attempt_invitation
        FOREIGN KEY (invitation_id)
        REFERENCES gym.staff_invitations (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_staff_invitation_delivery_attempts_attempted_at
    ON gym.staff_invitation_delivery_attempts (attempted_at, invitation_id);

CREATE OR REPLACE FUNCTION gym.protect_staff_invitation_delivery_attempt()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.attempted_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours' THEN
            RAISE EXCEPTION 'recent staff invitation delivery limits are protected'
                USING ERRCODE = '55000';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.outcome <> 'RESERVED'
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.invitation_id IS DISTINCT FROM OLD.invitation_id
       OR NEW.invitation_version IS DISTINCT FROM OLD.invitation_version
       OR NEW.attempted_at IS DISTINCT FROM OLD.attempted_at
       OR NEW.outcome NOT IN ('SENT', 'FAILED', 'AMBIGUOUS')
       OR NEW.completed_at IS NULL
       OR NEW.completed_at < OLD.attempted_at THEN
        RAISE EXCEPTION 'staff invitation delivery outcome is immutable after completion'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_invitation_delivery_attempt_protect
BEFORE UPDATE OR DELETE ON gym.staff_invitation_delivery_attempts
FOR EACH ROW
EXECUTE FUNCTION gym.protect_staff_invitation_delivery_attempt();

CREATE TABLE gym.staff_admin_reauthentication_failures (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_staff_admin_reauthentication_failures_user
        FOREIGN KEY (user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_staff_admin_reauthentication_failures_user_attempted
    ON gym.staff_admin_reauthentication_failures (user_id, attempted_at DESC, id);

COMMENT ON TABLE gym.staff_invitation_delivery_attempts IS
    'Minimal rolling-window reservation and outcome facts; never stores email content or token material.';
COMMENT ON TABLE gym.staff_admin_reauthentication_failures IS
    'Short-lived failed current-password checks for the organization-admin invitation throttle.';
