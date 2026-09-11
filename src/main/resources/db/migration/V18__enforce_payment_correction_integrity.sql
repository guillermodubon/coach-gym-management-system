-- Strengthen payment correction history and full-refund integrity.
-- V17 is reserved/used by the client profile feature. Flyway supports this
-- migration even when a local checkout temporarily has a version gap.

ALTER TABLE gym.payment_status_history
    DROP CONSTRAINT ck_payment_status_history_state_change;

ALTER TABLE gym.payment_status_history
    ADD CONSTRAINT ck_payment_status_history_allowed_transition
        CHECK (
            (previous_status IS NULL AND new_status = 'PAID')
            OR
            (previous_status = 'PAID'
                AND new_status IN ('VOIDED', 'REFUNDED'))
        );

ALTER TABLE gym.payment_status_history
    ADD CONSTRAINT ck_payment_status_history_reason_required
        CHECK (reason IS NOT NULL AND btrim(reason) <> '');

-- Existing registration code always records an actor. Abort migration instead
-- of silently inventing one if legacy data violates that invariant.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM gym.payment_status_history
        WHERE changed_by_user_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'payment_status_history contains rows without changed_by_user_id';
    END IF;
END;
$$;

ALTER TABLE gym.payment_status_history
    ALTER COLUMN changed_by_user_id SET NOT NULL;

ALTER TABLE gym.payment_status_history
    DROP CONSTRAINT fk_payment_status_history_changed_by_user;

ALTER TABLE gym.payment_status_history
    ADD CONSTRAINT fk_payment_status_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION gym.reject_payment_status_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payment status history is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_payment_status_history_append_only
    BEFORE UPDATE OR DELETE
    ON gym.payment_status_history
    FOR EACH ROW
    EXECUTE FUNCTION gym.reject_payment_status_history_mutation();

CREATE OR REPLACE FUNCTION gym.validate_payment_full_refund()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    original_amount NUMERIC(12, 2);
    original_currency CHAR(3);
    original_method VARCHAR(20);
    original_status VARCHAR(20);
BEGIN
    SELECT amount, currency, payment_method, status
    INTO original_amount, original_currency, original_method, original_status
    FROM gym.payments
    WHERE id = NEW.payment_id
    FOR KEY SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'refund references a missing payment'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.amount <> original_amount THEN
        RAISE EXCEPTION 'refund amount must equal original payment amount'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.currency <> original_currency THEN
        RAISE EXCEPTION 'refund currency must equal original payment currency'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.refund_method <> original_method THEN
        RAISE EXCEPTION 'refund method must equal original payment method'
            USING ERRCODE = '23514';
    END IF;

    IF original_status NOT IN ('PAID', 'REFUNDED') THEN
        RAISE EXCEPTION 'only a paid payment can receive a refund'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_refunds_validate_full_refund
    BEFORE INSERT OR UPDATE
    ON gym.payment_refunds
    FOR EACH ROW
    EXECUTE FUNCTION gym.validate_payment_full_refund();

CREATE OR REPLACE FUNCTION gym.reject_payment_refund_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payment refunds are immutable'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_payment_refunds_immutable
    BEFORE UPDATE OR DELETE
    ON gym.payment_refunds
    FOR EACH ROW
    EXECUTE FUNCTION gym.reject_payment_refund_mutation();
