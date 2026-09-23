-- Attribute payment operations and their durable descendants to the branch
-- where the operation was registered. Existing rows are assigned through the
-- authoritative client/membership/payment relationships and then protected by
-- restrictive foreign keys and immutable branch snapshots.

ALTER TABLE gym.payments
    ADD COLUMN registered_at_branch_id UUID;

ALTER TABLE gym.payment_attempts
    ADD COLUMN initiated_at_branch_id UUID;

ALTER TABLE gym.payment_receipts
    ADD COLUMN branch_id UUID;

ALTER TABLE gym.email_deliveries
    ADD COLUMN branch_id UUID;

-- The receipt backfill below changes only its newly added ownership snapshot.
-- V22 makes every receipt UPDATE immutable; temporarily remove that trigger
-- inside this transactional migration and restore it before commit. The
-- delivery lifecycle and updated-at triggers are likewise suspended only
-- while their new branch snapshot is backfilled, preserving prior state.
DROP TRIGGER trg_payment_receipts_immutable ON gym.payment_receipts;
DROP TRIGGER trg_email_deliveries_validate_mutation ON gym.email_deliveries;
DROP TRIGGER trg_email_deliveries_set_updated_at ON gym.email_deliveries;

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
            'exactly one active canonical initial branch is required for payment ownership backfill';
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

    UPDATE gym.payments AS payment
    SET registered_at_branch_id = COALESCE(
        (SELECT period.registered_at_branch_id
         FROM gym.membership_periods AS period
         WHERE period.id = payment.membership_period_id),
        (SELECT membership.registered_at_branch_id
         FROM gym.memberships AS membership
         WHERE membership.id = payment.membership_id),
        (SELECT client.home_branch_id
         FROM gym.clients AS client
         WHERE client.id = payment.client_id),
        initial_branch_id)
    WHERE payment.registered_at_branch_id IS NULL;

    UPDATE gym.payment_attempts AS attempt
    SET initiated_at_branch_id = COALESCE(
        (SELECT period.registered_at_branch_id
         FROM gym.membership_periods AS period
         WHERE period.id = attempt.membership_period_id),
        (SELECT membership.registered_at_branch_id
         FROM gym.memberships AS membership
         WHERE membership.id = attempt.membership_id),
        (SELECT client.home_branch_id
         FROM gym.clients AS client
         WHERE client.id = attempt.client_id),
        initial_branch_id)
    WHERE attempt.initiated_at_branch_id IS NULL;

    UPDATE gym.payment_receipts AS receipt
    SET branch_id = payment.registered_at_branch_id
    FROM gym.payments AS payment
    WHERE receipt.payment_id = payment.id
      AND receipt.branch_id IS NULL;

    UPDATE gym.email_deliveries AS delivery
    SET branch_id = CASE delivery.delivery_type
        WHEN 'PAYMENT_RECEIPT' THEN (
            SELECT payment.registered_at_branch_id
            FROM gym.payments AS payment
            JOIN gym.payment_receipts AS receipt
                ON receipt.payment_id = payment.id
            WHERE receipt.id = delivery.source_resource_id)
        WHEN 'ACCESS_CREDENTIAL' THEN (
            SELECT client.home_branch_id
            FROM gym.access_credentials AS credential
            JOIN gym.clients AS client ON client.id = credential.client_id
            WHERE credential.id = delivery.source_resource_id)
        END
    WHERE delivery.branch_id IS NULL;

    IF EXISTS (SELECT 1 FROM gym.payments WHERE registered_at_branch_id IS NULL)
        OR EXISTS (SELECT 1 FROM gym.payment_attempts WHERE initiated_at_branch_id IS NULL)
        OR EXISTS (SELECT 1 FROM gym.payment_receipts WHERE branch_id IS NULL)
        OR EXISTS (SELECT 1 FROM gym.email_deliveries WHERE branch_id IS NULL) THEN
        RAISE EXCEPTION
            'payment and delivery ownership backfill left records without a branch';
    END IF;
END
$$;

CREATE TRIGGER trg_payment_receipts_immutable
BEFORE UPDATE OR DELETE ON gym.payment_receipts
FOR EACH ROW
EXECUTE FUNCTION gym.reject_payment_receipt_mutation();
CREATE TRIGGER trg_email_deliveries_set_updated_at
BEFORE UPDATE ON gym.email_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
CREATE TRIGGER trg_email_deliveries_validate_mutation
BEFORE UPDATE OR DELETE ON gym.email_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.validate_email_delivery_mutation();

ALTER TABLE gym.payments
    ALTER COLUMN registered_at_branch_id SET NOT NULL,
    ALTER COLUMN registered_at_branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_payments_registered_at_branch
        FOREIGN KEY (registered_at_branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

ALTER TABLE gym.payment_attempts
    ALTER COLUMN initiated_at_branch_id SET NOT NULL,
    ALTER COLUMN initiated_at_branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_payment_attempts_initiated_at_branch
        FOREIGN KEY (initiated_at_branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

ALTER TABLE gym.payment_receipts
    ALTER COLUMN branch_id SET NOT NULL,
    ALTER COLUMN branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_payment_receipts_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

ALTER TABLE gym.email_deliveries
    ALTER COLUMN branch_id SET NOT NULL,
    ALTER COLUMN branch_id SET DEFAULT '7b0bf7d5-5184-43d2-8f9a-200000000002',
    ADD CONSTRAINT fk_email_deliveries_branch
        FOREIGN KEY (branch_id)
        REFERENCES gym.gym_branches (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_payments_branch_status_paid_at
    ON gym.payments (registered_at_branch_id, status, paid_at DESC, id ASC);

CREATE INDEX idx_payment_attempts_branch_status_created_at
    ON gym.payment_attempts (initiated_at_branch_id, status, created_at DESC, id ASC);

CREATE INDEX idx_payment_receipts_branch_payment
    ON gym.payment_receipts (branch_id, payment_id);

CREATE INDEX idx_email_deliveries_branch_status_updated_at
    ON gym.email_deliveries (branch_id, status, updated_at ASC, id ASC);

CREATE OR REPLACE FUNCTION gym.reject_payment_branch_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NEW.registered_at_branch_id IS DISTINCT FROM OLD.registered_at_branch_id THEN
        RAISE EXCEPTION 'payment branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_payments_reject_branch_mutation
BEFORE UPDATE ON gym.payments
FOR EACH ROW
EXECUTE FUNCTION gym.reject_payment_branch_mutation();

CREATE OR REPLACE FUNCTION gym.reject_payment_attempt_branch_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NEW.initiated_at_branch_id IS DISTINCT FROM OLD.initiated_at_branch_id THEN
        RAISE EXCEPTION 'payment attempt branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_payment_attempts_reject_branch_mutation
BEFORE UPDATE ON gym.payment_attempts
FOR EACH ROW
EXECUTE FUNCTION gym.reject_payment_attempt_branch_mutation();

CREATE OR REPLACE FUNCTION gym.reject_email_delivery_branch_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    IF NEW.branch_id IS DISTINCT FROM OLD.branch_id THEN
        RAISE EXCEPTION 'email delivery branch ownership is immutable'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_email_deliveries_reject_branch_mutation
BEFORE UPDATE ON gym.email_deliveries
FOR EACH ROW
EXECUTE FUNCTION gym.reject_email_delivery_branch_mutation();

COMMENT ON COLUMN gym.payments.registered_at_branch_id IS
    'Immutable branch where the payment registration occurred.';
COMMENT ON COLUMN gym.payment_attempts.initiated_at_branch_id IS
    'Immutable branch where the provider attempt was initiated.';
COMMENT ON COLUMN gym.payment_receipts.branch_id IS
    'Immutable branch snapshot inherited from the source payment.';
COMMENT ON COLUMN gym.email_deliveries.branch_id IS
    'Immutable branch snapshot inherited from the delivery source.';
