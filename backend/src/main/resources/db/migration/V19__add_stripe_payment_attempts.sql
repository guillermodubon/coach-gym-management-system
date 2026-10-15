CREATE TABLE gym.payment_attempts (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    membership_period_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expected_amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    checkout_reference VARCHAR(255),
    provider_payment_reference VARCHAR(255),
    failure_code VARCHAR(50),
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    confirmed_payment_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_attempts_provider CHECK (provider IN ('STRIPE')),
    CONSTRAINT ck_payment_attempts_status CHECK (
        status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_payment_attempts_expected_amount_positive CHECK (expected_amount > 0),
    CONSTRAINT ck_payment_attempts_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_attempts_failure_code CHECK (
        failure_code IS NULL OR failure_code IN (
            'PROVIDER_DECLINED', 'PROVIDER_UNAVAILABLE', 'PROVIDER_DATA_MISMATCH',
            'PROVIDER_CANCELLED', 'PROVIDER_EXPIRED')),
    CONSTRAINT ck_payment_attempts_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_payment_attempts_references_not_blank CHECK (
        (checkout_reference IS NULL OR btrim(checkout_reference) <> '')
        AND (provider_payment_reference IS NULL OR btrim(provider_payment_reference) <> '')),
    CONSTRAINT ck_payment_attempts_outcome CHECK (
        (status IN ('CREATED', 'PROCESSING')
            AND failure_code IS NULL
            AND completed_at IS NULL
            AND confirmed_payment_id IS NULL)
        OR (status = 'SUCCEEDED'
            AND failure_code IS NULL
            AND completed_at IS NOT NULL
            AND checkout_reference IS NOT NULL
            AND provider_payment_reference IS NOT NULL
            AND confirmed_payment_id IS NOT NULL)
        OR (status IN ('FAILED', 'CANCELLED', 'EXPIRED')
            AND failure_code IS NOT NULL
            AND completed_at IS NOT NULL
            AND confirmed_payment_id IS NULL)),
    CONSTRAINT fk_payment_attempts_client FOREIGN KEY (client_id)
        REFERENCES gym.clients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_attempts_membership_client FOREIGN KEY (membership_id, client_id)
        REFERENCES gym.memberships (id, client_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_attempts_membership_period FOREIGN KEY (membership_period_id, membership_id)
        REFERENCES gym.membership_periods (id, membership_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_attempts_created_by_user FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_attempts_confirmed_payment FOREIGN KEY (confirmed_payment_id)
        REFERENCES gym.payments (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_payment_attempts_provider_checkout_reference
    ON gym.payment_attempts (provider, checkout_reference)
    WHERE checkout_reference IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_attempts_provider_payment_reference
    ON gym.payment_attempts (provider, provider_payment_reference)
    WHERE provider_payment_reference IS NOT NULL;
CREATE INDEX idx_payment_attempts_creator_status_created_at
    ON gym.payment_attempts (created_by_user_id, status, created_at DESC);
CREATE INDEX idx_payment_attempts_membership_period_id
    ON gym.payment_attempts (membership_period_id);

CREATE TABLE gym.payment_attempt_status_history (
    id UUID PRIMARY KEY,
    payment_attempt_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(50),
    change_source VARCHAR(20) NOT NULL,
    changed_by_user_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_attempt_history_previous_status CHECK (
        previous_status IS NULL OR previous_status IN (
            'CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_payment_attempt_history_new_status CHECK (
        new_status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_payment_attempt_history_failure_code CHECK (
        failure_code IS NULL OR failure_code IN (
            'PROVIDER_DECLINED', 'PROVIDER_UNAVAILABLE', 'PROVIDER_DATA_MISMATCH',
            'PROVIDER_CANCELLED', 'PROVIDER_EXPIRED')),
    CONSTRAINT ck_payment_attempt_history_source CHECK (
        change_source IN ('STAFF', 'PROVIDER', 'SYSTEM')),
    CONSTRAINT ck_payment_attempt_history_actor CHECK (
        (change_source = 'STAFF' AND changed_by_user_id IS NOT NULL)
        OR (change_source IN ('PROVIDER', 'SYSTEM') AND changed_by_user_id IS NULL)),
    CONSTRAINT ck_payment_attempt_history_outcome CHECK (
        (new_status IN ('CREATED', 'PROCESSING', 'SUCCEEDED') AND failure_code IS NULL)
        OR (new_status IN ('FAILED', 'CANCELLED', 'EXPIRED') AND failure_code IS NOT NULL)),
    CONSTRAINT fk_payment_attempt_history_attempt FOREIGN KEY (payment_attempt_id)
        REFERENCES gym.payment_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_attempt_history_user FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_payment_attempt_history_attempt_occurred_at
    ON gym.payment_attempt_status_history (payment_attempt_id, occurred_at DESC);

CREATE TABLE gym.processed_payment_provider_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_event_reference VARCHAR(255) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    payment_attempt_id UUID,
    processing_result VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_processed_payment_provider_events_provider_reference
        UNIQUE (provider, provider_event_reference),
    CONSTRAINT ck_processed_payment_provider_events_provider CHECK (provider IN ('STRIPE')),
    CONSTRAINT ck_processed_payment_provider_events_reference_not_blank
        CHECK (btrim(provider_event_reference) <> ''),
    CONSTRAINT ck_processed_payment_provider_events_type CHECK (
        event_type IN ('CHECKOUT_COMPLETED', 'PAYMENT_FAILED', 'CHECKOUT_CANCELLED', 'CHECKOUT_EXPIRED')),
    CONSTRAINT ck_processed_payment_provider_events_result CHECK (
        processing_result IN ('PENDING', 'PROCESSED', 'REJECTED')),
    CONSTRAINT ck_processed_payment_provider_events_completion CHECK (
        (processing_result = 'PENDING' AND processed_at IS NULL)
        OR (processing_result IN ('PROCESSED', 'REJECTED') AND processed_at IS NOT NULL)),
    CONSTRAINT fk_processed_payment_provider_events_attempt FOREIGN KEY (payment_attempt_id)
        REFERENCES gym.payment_attempts (id) ON DELETE RESTRICT
);

CREATE OR REPLACE FUNCTION gym.validate_payment_attempt()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    payment_client_id UUID;
    payment_membership_id UUID;
    payment_period_id UUID;
    payment_amount NUMERIC(12, 2);
    payment_currency CHAR(3);
    payment_method VARCHAR(20);
    payment_status VARCHAR(20);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.client_id <> NEW.client_id
            OR OLD.membership_id <> NEW.membership_id
            OR OLD.membership_period_id <> NEW.membership_period_id
            OR OLD.provider <> NEW.provider
            OR OLD.expected_amount <> NEW.expected_amount
            OR OLD.currency <> NEW.currency
            OR OLD.created_by_user_id <> NEW.created_by_user_id
            OR OLD.created_at <> NEW.created_at THEN
            RAISE EXCEPTION 'payment attempt financial lineage is immutable'
                USING ERRCODE = '55000';
        END IF;
        IF OLD.checkout_reference IS NOT NULL
            AND OLD.checkout_reference IS DISTINCT FROM NEW.checkout_reference THEN
            RAISE EXCEPTION 'payment attempt checkout reference is immutable'
                USING ERRCODE = '55000';
        END IF;
        IF OLD.provider_payment_reference IS NOT NULL
            AND OLD.provider_payment_reference IS DISTINCT FROM NEW.provider_payment_reference THEN
            RAISE EXCEPTION 'payment attempt provider payment reference is immutable'
                USING ERRCODE = '55000';
        END IF;
        IF OLD.confirmed_payment_id IS NOT NULL
            AND OLD.confirmed_payment_id IS DISTINCT FROM NEW.confirmed_payment_id THEN
            RAISE EXCEPTION 'payment attempt confirmed payment is immutable'
                USING ERRCODE = '55000';
        END IF;
        IF OLD.status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED') THEN
            RAISE EXCEPTION 'terminal payment attempts are immutable'
                USING ERRCODE = '55000';
        END IF;
        IF NOT ((OLD.status = 'CREATED' AND NEW.status IN ('PROCESSING', 'FAILED', 'CANCELLED'))
            OR (OLD.status = 'PROCESSING' AND NEW.status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'))
            OR OLD.status = NEW.status) THEN
            RAISE EXCEPTION 'invalid payment attempt state transition'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.status = 'SUCCEEDED' THEN
        SELECT client_id, membership_id, membership_period_id, amount, currency,
               payment_method, status
        INTO payment_client_id, payment_membership_id, payment_period_id, payment_amount,
             payment_currency, payment_method, payment_status
        FROM gym.payments
        WHERE id = NEW.confirmed_payment_id
        FOR KEY SHARE;

        IF NOT FOUND
            OR payment_client_id <> NEW.client_id
            OR payment_membership_id <> NEW.membership_id
            OR payment_period_id <> NEW.membership_period_id
            OR payment_amount <> NEW.expected_amount
            OR payment_currency <> NEW.currency
            OR payment_method <> 'CARD'
            OR payment_status <> 'PAID' THEN
            RAISE EXCEPTION 'succeeded payment attempt must link to matching paid card payment'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_attempts_validate
    BEFORE INSERT OR UPDATE ON gym.payment_attempts
    FOR EACH ROW EXECUTE FUNCTION gym.validate_payment_attempt();

CREATE OR REPLACE FUNCTION gym.validate_payment_attempt_status_history()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_status VARCHAR(20);
    last_status VARCHAR(20);
BEGIN
    SELECT status INTO current_status
    FROM gym.payment_attempts
    WHERE id = NEW.payment_attempt_id
    FOR KEY SHARE;

    IF NOT FOUND OR NEW.new_status <> current_status THEN
        RAISE EXCEPTION 'payment attempt history must match current attempt status'
            USING ERRCODE = '23514';
    END IF;

    SELECT new_status INTO last_status
    FROM gym.payment_attempt_status_history
    WHERE payment_attempt_id = NEW.payment_attempt_id
    ORDER BY occurred_at DESC, id DESC
    LIMIT 1;

    IF last_status IS NULL THEN
        IF NEW.previous_status IS NOT NULL OR NEW.new_status <> 'CREATED' THEN
            RAISE EXCEPTION 'payment attempt history must begin with created status'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.previous_status IS DISTINCT FROM last_status THEN
        RAISE EXCEPTION 'payment attempt history transition does not match prior status'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_attempt_history_validate
    BEFORE INSERT ON gym.payment_attempt_status_history
    FOR EACH ROW EXECUTE FUNCTION gym.validate_payment_attempt_status_history();

CREATE OR REPLACE FUNCTION gym.reject_payment_attempt_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payment attempt status history is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_payment_attempt_history_append_only
    BEFORE UPDATE OR DELETE ON gym.payment_attempt_status_history
    FOR EACH ROW EXECUTE FUNCTION gym.reject_payment_attempt_history_mutation();

CREATE OR REPLACE FUNCTION gym.validate_processed_payment_provider_event()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'processed provider events are immutable'
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.provider <> NEW.provider
            OR OLD.provider_event_reference <> NEW.provider_event_reference
            OR OLD.event_type <> NEW.event_type
            OR OLD.received_at <> NEW.received_at
            OR OLD.processing_result <> 'PENDING' THEN
            RAISE EXCEPTION 'processed provider event identity is immutable'
                USING ERRCODE = '55000';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_processed_payment_provider_events_validate
    BEFORE UPDATE OR DELETE ON gym.processed_payment_provider_events
    FOR EACH ROW EXECUTE FUNCTION gym.validate_processed_payment_provider_event();
