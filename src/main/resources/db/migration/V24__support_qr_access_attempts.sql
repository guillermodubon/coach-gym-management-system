-- Persist the safe metadata needed by QR access attempts.
--
-- The scanned payload and the opaque token are deliberately not stored. The
-- access record retains only the source and, after successful resolution, the
-- credential UUID that was used for the decision.

ALTER TABLE gym.access_records
    ADD COLUMN identification_source VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE gym.access_records
    ADD COLUMN access_credential_id UUID;

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_identification_source
        CHECK (identification_source IN (
            'MEMBERSHIP_CODE',
            'CLIENT_CODE',
            'QR_CREDENTIAL',
            'UNKNOWN'
        ));

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_qr_credential_metadata
        CHECK (
            (
                identification_source = 'QR_CREDENTIAL'
                AND access_credential_id IS NOT NULL
                AND client_id IS NOT NULL
            )
            OR
            (
                identification_source <> 'QR_CREDENTIAL'
                AND access_credential_id IS NULL
            )
        );

ALTER TABLE gym.access_records
    ADD CONSTRAINT fk_access_records_access_credential
        FOREIGN KEY (access_credential_id)
        REFERENCES gym.access_credentials (id)
        ON DELETE RESTRICT;

-- Duplicate decisions are evaluated by the application in a later block. The
-- reason is made persistable now so the database can enforce the same outcome
-- when that workflow records a denied duplicate attempt.
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
            'DUPLICATE_CHECK_IN'
        ));

-- The duplicate-scan query filters by credential, result and a recent
-- occurred-at interval. The source predicate keeps this access path focused on
-- QR attempts without duplicating the existing client-code indexes.
CREATE INDEX idx_access_records_qr_credential_result_occurred_at
    ON gym.access_records (
        access_credential_id,
        decision,
        occurred_at DESC,
        id ASC
    )
    WHERE identification_source = 'QR_CREDENTIAL'
      AND access_credential_id IS NOT NULL;

-- A QR credential must belong to the client resolved for the same access
-- attempt. A composite FK would require a redundant uniqueness index on the
-- credential table, so this trigger performs the cross-column invariant while
-- retaining the restrictive credential FK above.
CREATE OR REPLACE FUNCTION gym.validate_access_record_qr_metadata()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    credential_client_id UUID;
BEGIN
    IF NEW.identification_source = 'QR_CREDENTIAL' THEN
        SELECT c.client_id
        INTO credential_client_id
        FROM gym.access_credentials AS c
        WHERE c.id = NEW.access_credential_id
        FOR KEY SHARE;

        IF NOT FOUND
            OR NEW.client_id IS NULL
            OR credential_client_id IS DISTINCT FROM NEW.client_id THEN
            RAISE EXCEPTION
                'QR access record credential does not belong to the resolved client'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_access_records_validate_qr_metadata
BEFORE INSERT ON gym.access_records
FOR EACH ROW
EXECUTE FUNCTION gym.validate_access_record_qr_metadata();

-- Access attempts are historical facts. The existing actor FK intentionally
-- uses ON DELETE SET NULL, so the append-only guard permits only that metadata
-- cleanup while rejecting all other updates and every direct delete.
CREATE OR REPLACE FUNCTION gym.reject_access_record_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'access records are append-only'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.entered_code IS DISTINCT FROM OLD.entered_code
        OR NEW.client_id IS DISTINCT FROM OLD.client_id
        OR NEW.client_code_snapshot IS DISTINCT FROM OLD.client_code_snapshot
        OR NEW.membership_id IS DISTINCT FROM OLD.membership_id
        OR NEW.membership_code_snapshot IS DISTINCT FROM OLD.membership_code_snapshot
        OR NEW.membership_period_id IS DISTINCT FROM OLD.membership_period_id
        OR NEW.decision IS DISTINCT FROM OLD.decision
        OR NEW.reason_code IS DISTINCT FROM OLD.reason_code
        OR NEW.details IS DISTINCT FROM OLD.details
        OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at
        OR NEW.identification_source IS DISTINCT FROM OLD.identification_source
        OR NEW.access_credential_id IS DISTINCT FROM OLD.access_credential_id
        OR (
            NEW.recorded_by_user_id IS DISTINCT FROM OLD.recorded_by_user_id
            AND NOT (
                OLD.recorded_by_user_id IS NOT NULL
                AND NEW.recorded_by_user_id IS NULL
            )
        ) THEN
        RAISE EXCEPTION 'access records are append-only'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_access_records_append_only
BEFORE UPDATE OR DELETE ON gym.access_records
FOR EACH ROW
EXECUTE FUNCTION gym.reject_access_record_mutation();
