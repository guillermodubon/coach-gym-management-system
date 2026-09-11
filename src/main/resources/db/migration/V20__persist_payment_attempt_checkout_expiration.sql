ALTER TABLE gym.payment_attempts
    ADD COLUMN checkout_expires_at TIMESTAMPTZ;

ALTER TABLE gym.payment_attempts
    ADD CONSTRAINT ck_payment_attempts_checkout_expiration
    CHECK (checkout_expires_at IS NULL OR checkout_expires_at > created_at);

CREATE OR REPLACE FUNCTION gym.validate_payment_attempt_checkout_expiration()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.checkout_expires_at IS NOT NULL
        AND NEW.status NOT IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED') THEN
        RAISE EXCEPTION 'payment attempt checkout expiration requires a provider interaction'
            USING ERRCODE = '23514';
    END IF;
    IF OLD.checkout_expires_at IS NOT NULL
        AND OLD.checkout_expires_at IS DISTINCT FROM NEW.checkout_expires_at THEN
        RAISE EXCEPTION 'payment attempt checkout expiration is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_attempts_checkout_expiration_validate
    BEFORE UPDATE ON gym.payment_attempts
    FOR EACH ROW EXECUTE FUNCTION gym.validate_payment_attempt_checkout_expiration();
