-- Persist transactional email delivery intent and immutable send-attempt history.
-- Message bodies and attachment bytes remain outside PostgreSQL; only the
-- server-owned metadata required for idempotency, retries, and auditability is
-- retained here.

CREATE TABLE gym.email_deliveries (
    id UUID PRIMARY KEY,
    delivery_type VARCHAR(32) NOT NULL,
    source_resource_id UUID NOT NULL,
    client_id UUID NOT NULL,
    recipient_snapshot VARCHAR(254) NOT NULL,
    subject_snapshot VARCHAR(200) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    attachment_resource_type VARCHAR(32) NOT NULL,
    attachment_resource_id UUID NOT NULL,
    attachment_filename VARCHAR(200) NOT NULL,
    attachment_content_type VARCHAR(50) NOT NULL,
    attachment_size_bytes BIGINT NOT NULL,
    attachment_checksum_sha256 CHAR(64) NOT NULL,
    idempotency_key_digest CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_failure_code VARCHAR(64),
    last_failure_message VARCHAR(500),
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_user_id UUID NOT NULL,
    sent_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_email_deliveries_idempotency_key_digest
        UNIQUE (idempotency_key_digest),
    CONSTRAINT ck_email_deliveries_type
        CHECK (delivery_type IN ('PAYMENT_RECEIPT', 'ACCESS_CREDENTIAL')),
    CONSTRAINT ck_email_deliveries_source_not_blank
        CHECK (source_resource_id IS NOT NULL),
    CONSTRAINT ck_email_deliveries_recipient_snapshot
        CHECK (
            recipient_snapshot = lower(btrim(recipient_snapshot))
            AND recipient_snapshot ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
        ),
    CONSTRAINT ck_email_deliveries_subject_snapshot
        CHECK (
            btrim(subject_snapshot) <> ''
            AND position(chr(10) IN subject_snapshot) = 0
            AND position(chr(13) IN subject_snapshot) = 0
        ),
    CONSTRAINT ck_email_deliveries_template_version
        CHECK (template_version ~ '^[a-z0-9][a-z0-9._-]{0,31}$'),
    CONSTRAINT ck_email_deliveries_attachment_type
        CHECK (attachment_resource_type = delivery_type),
    CONSTRAINT ck_email_deliveries_attachment_filename
        CHECK (
            btrim(attachment_filename) <> ''
            AND position(chr(10) IN attachment_filename) = 0
            AND position(chr(13) IN attachment_filename) = 0
            AND position(chr(92) IN attachment_filename) = 0
            AND position('/' IN attachment_filename) = 0
            AND position('..' IN attachment_filename) = 0
        ),
    CONSTRAINT ck_email_deliveries_attachment_content_type
        CHECK (
            (delivery_type = 'PAYMENT_RECEIPT'
                AND attachment_content_type = 'application/pdf')
            OR
            (delivery_type = 'ACCESS_CREDENTIAL'
                AND attachment_content_type = 'image/png')
        ),
    CONSTRAINT ck_email_deliveries_attachment_size
        CHECK (attachment_size_bytes > 0 AND attachment_size_bytes <= 10485760),
    CONSTRAINT ck_email_deliveries_attachment_checksum
        CHECK (attachment_checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_email_deliveries_idempotency_digest
        CHECK (idempotency_key_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_email_deliveries_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_email_deliveries_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_email_deliveries_failure_code
        CHECK (
            last_failure_code IS NULL
            OR last_failure_code IN (
                'DELIVERY_DISABLED',
                'RECIPIENT_UNAVAILABLE',
                'SOURCE_NOT_FOUND',
                'ATTACHMENT_UNAVAILABLE',
                'ATTACHMENT_INTEGRITY_FAILED',
                'VALIDATION_FAILED',
                'DUPLICATE_DELIVERY',
                'DELIVERY_STATE_CONFLICT',
                'DELIVERY_VERSION_CONFLICT',
                'RETRY_LIMIT_REACHED',
                'TRANSPORT_TIMEOUT',
                'TRANSPORT_AUTHENTICATION_FAILED',
                'TRANSPORT_CONFIGURATION_FAILED',
                'TRANSPORT_REJECTED',
                'AMBIGUOUS_TRANSPORT_OUTCOME',
                'DATA_ACCESS_FAILED',
                'COMPOSITION_FAILED',
                'UNEXPECTED_FAILURE'
            )
        ),
    CONSTRAINT ck_email_deliveries_failure_message
        CHECK (
            last_failure_message IS NULL
            OR (
                btrim(last_failure_message) <> ''
                AND position(chr(10) IN last_failure_message) = 0
                AND position(chr(13) IN last_failure_message) = 0
            )
        ),
    CONSTRAINT ck_email_deliveries_lifecycle_metadata
        CHECK (
            (
                status = 'PENDING'
                AND attempt_count = 0
                AND last_failure_code IS NULL
                AND last_failure_message IS NULL
                AND sent_at IS NULL
                AND last_attempt_at IS NULL
            )
            OR
            (
                status = 'SENT'
                AND attempt_count > 0
                AND last_failure_code IS NULL
                AND last_failure_message IS NULL
                AND sent_at IS NOT NULL
                AND last_attempt_at IS NOT NULL
            )
            OR
            (
                status = 'FAILED'
                AND attempt_count > 0
                AND last_failure_code IS NOT NULL
                AND last_failure_message IS NOT NULL
                AND sent_at IS NULL
                AND last_attempt_at IS NOT NULL
            )
        ),
    CONSTRAINT ck_email_deliveries_requested_timestamps
        CHECK (
            created_at >= requested_at
            AND updated_at >= created_at
            AND (last_attempt_at IS NULL OR last_attempt_at >= requested_at)
            AND (sent_at IS NULL OR sent_at >= requested_at)
        ),
    CONSTRAINT ck_email_deliveries_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_email_deliveries_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_email_deliveries_requested_by_user
        FOREIGN KEY (requested_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_email_deliveries_source
    ON gym.email_deliveries (delivery_type, source_resource_id, created_at DESC, id ASC);

CREATE INDEX idx_email_deliveries_client
    ON gym.email_deliveries (client_id, created_at DESC, id ASC);

CREATE INDEX idx_email_deliveries_status
    ON gym.email_deliveries (status, updated_at ASC, id ASC);

CREATE INDEX idx_email_deliveries_requested_at
    ON gym.email_deliveries (requested_at DESC, id ASC);

CREATE TABLE gym.email_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    result VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    provider_message_id VARCHAR(200),
    attempted_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_email_delivery_attempts_delivery_number
        UNIQUE (delivery_id, attempt_number),
    CONSTRAINT ck_email_delivery_attempts_number_positive
        CHECK (attempt_number > 0),
    CONSTRAINT ck_email_delivery_attempts_result
        CHECK (result IN ('SENT', 'FAILED', 'AMBIGUOUS')),
    CONSTRAINT ck_email_delivery_attempts_timestamps
        CHECK (completed_at >= started_at),
    CONSTRAINT ck_email_delivery_attempts_failure_code
        CHECK (
            failure_code IS NULL
            OR failure_code IN (
                'DELIVERY_DISABLED',
                'RECIPIENT_UNAVAILABLE',
                'SOURCE_NOT_FOUND',
                'ATTACHMENT_UNAVAILABLE',
                'ATTACHMENT_INTEGRITY_FAILED',
                'VALIDATION_FAILED',
                'DUPLICATE_DELIVERY',
                'DELIVERY_STATE_CONFLICT',
                'DELIVERY_VERSION_CONFLICT',
                'RETRY_LIMIT_REACHED',
                'TRANSPORT_TIMEOUT',
                'TRANSPORT_AUTHENTICATION_FAILED',
                'TRANSPORT_CONFIGURATION_FAILED',
                'TRANSPORT_REJECTED',
                'AMBIGUOUS_TRANSPORT_OUTCOME',
                'DATA_ACCESS_FAILED',
                'COMPOSITION_FAILED',
                'UNEXPECTED_FAILURE'
            )
        ),
    CONSTRAINT ck_email_delivery_attempts_result_metadata
        CHECK (
            (
                result = 'SENT'
                AND failure_code IS NULL
                AND failure_message IS NULL
            )
            OR
            (
                result = 'FAILED'
                AND failure_code IS NOT NULL
                AND failure_message IS NOT NULL
                AND btrim(failure_message) <> ''
            )
            OR
            (
                result = 'AMBIGUOUS'
                AND failure_code = 'AMBIGUOUS_TRANSPORT_OUTCOME'
                AND failure_message IS NOT NULL
                AND btrim(failure_message) <> ''
            )
        ),
    CONSTRAINT ck_email_delivery_attempts_failure_message
        CHECK (
            failure_message IS NULL
            OR (
                position(chr(10) IN failure_message) = 0
                AND position(chr(13) IN failure_message) = 0
            )
        ),
    CONSTRAINT ck_email_delivery_attempts_provider_message_id
        CHECK (
            provider_message_id IS NULL
            OR (
                btrim(provider_message_id) <> ''
                AND position(chr(10) IN provider_message_id) = 0
                AND position(chr(13) IN provider_message_id) = 0
            )
        ),
    CONSTRAINT fk_email_delivery_attempts_delivery
        FOREIGN KEY (delivery_id)
        REFERENCES gym.email_deliveries (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_email_delivery_attempts_attempted_by_user
        FOREIGN KEY (attempted_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_email_delivery_attempts_delivery_started_at
    ON gym.email_delivery_attempts (delivery_id, started_at ASC, attempt_number ASC);

CREATE OR REPLACE FUNCTION gym.validate_email_delivery_attempt()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    current_status VARCHAR(20);
    current_attempt_count INTEGER;
    request_timestamp TIMESTAMPTZ;
BEGIN
    SELECT d.status, d.attempt_count, d.requested_at
    INTO current_status, current_attempt_count, request_timestamp
    FROM gym.email_deliveries AS d
    WHERE d.id = NEW.delivery_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'email delivery does not exist'
            USING ERRCODE = '23503';
    END IF;

    IF current_status = 'SENT' THEN
        RAISE EXCEPTION 'sent email deliveries cannot receive another attempt'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.attempt_number <> current_attempt_count + 1 THEN
        RAISE EXCEPTION 'email delivery attempt number is not sequential'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.started_at < request_timestamp THEN
        RAISE EXCEPTION 'email delivery attempt cannot precede its request'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_email_delivery_attempts_validate
BEFORE INSERT ON gym.email_delivery_attempts
FOR EACH ROW
EXECUTE FUNCTION gym.validate_email_delivery_attempt();

CREATE OR REPLACE FUNCTION gym.reject_email_delivery_attempt_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'email delivery attempts are append-only'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_email_delivery_attempts_append_only
BEFORE UPDATE OR DELETE ON gym.email_delivery_attempts
FOR EACH ROW
EXECUTE FUNCTION gym.reject_email_delivery_attempt_mutation();

CREATE OR REPLACE FUNCTION gym.validate_email_delivery_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    persisted_attempt_count INTEGER;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'email deliveries cannot be deleted'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.delivery_type IS DISTINCT FROM OLD.delivery_type
        OR NEW.source_resource_id IS DISTINCT FROM OLD.source_resource_id
        OR NEW.client_id IS DISTINCT FROM OLD.client_id
        OR NEW.recipient_snapshot IS DISTINCT FROM OLD.recipient_snapshot
        OR NEW.subject_snapshot IS DISTINCT FROM OLD.subject_snapshot
        OR NEW.template_version IS DISTINCT FROM OLD.template_version
        OR NEW.attachment_resource_type IS DISTINCT FROM OLD.attachment_resource_type
        OR NEW.attachment_resource_id IS DISTINCT FROM OLD.attachment_resource_id
        OR NEW.attachment_filename IS DISTINCT FROM OLD.attachment_filename
        OR NEW.attachment_content_type IS DISTINCT FROM OLD.attachment_content_type
        OR NEW.attachment_size_bytes IS DISTINCT FROM OLD.attachment_size_bytes
        OR NEW.attachment_checksum_sha256 IS DISTINCT FROM OLD.attachment_checksum_sha256
        OR NEW.idempotency_key_digest IS DISTINCT FROM OLD.idempotency_key_digest
        OR NEW.requested_at IS DISTINCT FROM OLD.requested_at
        OR NEW.requested_by_user_id IS DISTINCT FROM OLD.requested_by_user_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'email delivery identity and snapshots are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'email delivery version must increase by one'
            USING ERRCODE = '40001';
    END IF;

    IF OLD.status = 'SENT' THEN
        RAISE EXCEPTION 'sent email deliveries are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'PENDING' AND NEW.status NOT IN ('SENT', 'FAILED') THEN
        RAISE EXCEPTION 'pending email delivery transition is invalid'
            USING ERRCODE = '23514';
    END IF;

    IF OLD.status = 'FAILED' AND NEW.status NOT IN ('SENT', 'FAILED') THEN
        RAISE EXCEPTION 'failed email delivery transition is invalid'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.attempt_count <> OLD.attempt_count + 1 THEN
        RAISE EXCEPTION 'email delivery attempt count must increase by one'
            USING ERRCODE = '23514';
    END IF;

    SELECT count(*)
    INTO persisted_attempt_count
    FROM gym.email_delivery_attempts AS a
    WHERE a.delivery_id = NEW.id;

    IF persisted_attempt_count <> NEW.attempt_count THEN
        RAISE EXCEPTION 'email delivery attempt count does not match its history'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_email_deliveries_set_updated_at
BEFORE UPDATE ON gym.email_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_email_deliveries_validate_mutation
BEFORE UPDATE OR DELETE ON gym.email_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.validate_email_delivery_mutation();
