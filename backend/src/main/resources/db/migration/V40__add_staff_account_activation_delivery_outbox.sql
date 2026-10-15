-- Persist only the retry state for post-commit account-activation notices.
-- Recipient, message body, password, and invitation token are resolved or composed transiently.

CREATE TABLE gym.staff_account_activation_deliveries (
    id UUID PRIMARY KEY,
    invitation_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_failure_code VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_staff_account_activation_delivery_invitation
        UNIQUE (invitation_id),
    CONSTRAINT ck_staff_account_activation_delivery_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'AMBIGUOUS', 'EXHAUSTED')),
    CONSTRAINT ck_staff_account_activation_delivery_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_staff_account_activation_delivery_failure_code
        CHECK (last_failure_code IS NULL
            OR last_failure_code IN ('DELIVERY_FAILED', 'OUTCOME_UNKNOWN')),
    CONSTRAINT ck_staff_account_activation_delivery_lifecycle
        CHECK (
            (status = 'PENDING' AND attempt_count = 0
                AND last_attempt_at IS NULL AND lease_expires_at IS NULL
                AND next_attempt_at IS NULL AND sent_at IS NULL
                AND last_failure_code IS NULL)
            OR (status = 'SENDING' AND attempt_count BETWEEN 1 AND 5
                AND last_attempt_at IS NOT NULL
                AND lease_expires_at > last_attempt_at
                AND next_attempt_at IS NULL AND sent_at IS NULL
                AND last_failure_code IS NULL)
            OR (status = 'SENT' AND attempt_count BETWEEN 1 AND 5
                AND last_attempt_at IS NOT NULL AND lease_expires_at IS NULL
                AND next_attempt_at IS NULL AND sent_at >= last_attempt_at
                AND last_failure_code IS NULL)
            OR (status = 'FAILED' AND attempt_count BETWEEN 1 AND 4
                AND last_attempt_at IS NOT NULL AND lease_expires_at IS NULL
                AND next_attempt_at > last_attempt_at AND sent_at IS NULL
                AND last_failure_code = 'DELIVERY_FAILED')
            OR (status = 'AMBIGUOUS' AND attempt_count BETWEEN 1 AND 5
                AND last_attempt_at IS NOT NULL AND lease_expires_at IS NULL
                AND next_attempt_at IS NULL AND sent_at IS NULL
                AND last_failure_code = 'OUTCOME_UNKNOWN')
            OR (status = 'EXHAUSTED' AND attempt_count = 5
                AND last_attempt_at IS NOT NULL AND lease_expires_at IS NULL
                AND next_attempt_at IS NULL AND sent_at IS NULL
                AND last_failure_code = 'DELIVERY_FAILED')
        ),
    CONSTRAINT ck_staff_account_activation_delivery_version
        CHECK (version >= 0),
    CONSTRAINT fk_staff_account_activation_delivery_invitation
        FOREIGN KEY (invitation_id)
        REFERENCES gym.staff_invitations (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_staff_account_activation_delivery_retry
    ON gym.staff_account_activation_deliveries (status, next_attempt_at, created_at, id);

CREATE OR REPLACE FUNCTION gym.validate_staff_account_activation_delivery_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'staff account activation delivery history is retained'
            USING ERRCODE = '55000';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'PENDING' OR NEW.attempt_count <> 0
           OR NEW.last_attempt_at IS NOT NULL OR NEW.lease_expires_at IS NOT NULL
           OR NEW.next_attempt_at IS NOT NULL OR NEW.sent_at IS NOT NULL
           OR NEW.last_failure_code IS NOT NULL
           OR NOT EXISTS (
                SELECT 1 FROM gym.staff_invitations AS invitation
                 WHERE invitation.id = NEW.invitation_id
                   AND invitation.status = 'ACCEPTED'
                   AND invitation.accepted_user_id IS NOT NULL
           ) THEN
            RAISE EXCEPTION 'activation delivery requires a completed invitation acceptance'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.invitation_id IS DISTINCT FROM OLD.invitation_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'staff account activation delivery identity is immutable'
            USING ERRCODE = '40001';
    END IF;

    IF OLD.status = 'PENDING' THEN
        IF NEW.status <> 'SENDING' OR NEW.attempt_count <> 1
           OR NEW.last_attempt_at IS NULL OR NEW.lease_expires_at IS NULL THEN
            RAISE EXCEPTION 'pending activation delivery can only be claimed'
                USING ERRCODE = '55000';
        END IF;
    ELSIF OLD.status = 'FAILED' THEN
        IF NEW.status <> 'SENDING'
           OR NEW.attempt_count <> OLD.attempt_count + 1
           OR OLD.next_attempt_at > NEW.last_attempt_at
           OR NEW.lease_expires_at IS NULL THEN
            RAISE EXCEPTION 'failed activation delivery retry is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSIF OLD.status = 'SENDING' THEN
        IF NEW.attempt_count <> OLD.attempt_count
           OR NEW.last_attempt_at IS DISTINCT FROM OLD.last_attempt_at
           OR NOT (
                NEW.status IN ('SENT', 'FAILED', 'AMBIGUOUS', 'EXHAUSTED')
                AND NEW.lease_expires_at IS NULL
           ) THEN
            RAISE EXCEPTION 'activation delivery outcome is invalid'
                USING ERRCODE = '55000';
        END IF;
    ELSE
        RAISE EXCEPTION 'final activation delivery states are immutable except retry'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_staff_account_activation_delivery_validate_mutation
BEFORE INSERT OR UPDATE OR DELETE ON gym.staff_account_activation_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.validate_staff_account_activation_delivery_mutation();

CREATE TRIGGER trg_staff_account_activation_delivery_set_updated_at
BEFORE UPDATE ON gym.staff_account_activation_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

COMMENT ON TABLE gym.staff_account_activation_deliveries IS
    'Durable retry state for post-commit account-activation notices; contains no recipient or message content.';
