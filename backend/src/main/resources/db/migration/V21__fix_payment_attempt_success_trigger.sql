-- Qualify payment columns used by the payment-attempt success trigger.
-- The original V19 function used PL/pgSQL variable names that collide with
-- column names on PostgreSQL and fail only when a real success is confirmed.
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
    payment_method_value VARCHAR(20);
    payment_status_value VARCHAR(20);
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
        SELECT p.client_id, p.membership_id, p.membership_period_id, p.amount, p.currency,
               p.payment_method, p.status
        INTO payment_client_id, payment_membership_id, payment_period_id, payment_amount,
             payment_currency, payment_method_value, payment_status_value
        FROM gym.payments AS p
        WHERE p.id = NEW.confirmed_payment_id
        FOR KEY SHARE;

        IF NOT FOUND
            OR payment_client_id <> NEW.client_id
            OR payment_membership_id <> NEW.membership_id
            OR payment_period_id <> NEW.membership_period_id
            OR payment_amount <> NEW.expected_amount
            OR payment_currency <> NEW.currency
            OR payment_method_value <> 'CARD'
            OR payment_status_value <> 'PAID' THEN
            RAISE EXCEPTION 'succeeded payment attempt must link to matching paid card payment'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
