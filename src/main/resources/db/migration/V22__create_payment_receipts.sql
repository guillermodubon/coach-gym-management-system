-- Persist immutable receipt metadata and the authoritative payment snapshot.
-- Receipt generation is limited to confirmed PAID payments; later payment
-- corrections do not rewrite an existing receipt.

CREATE TABLE gym.payment_receipts (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(32) NOT NULL,
    payment_id UUID NOT NULL,
    payment_code_snapshot VARCHAR(32) NOT NULL,
    payment_status_snapshot VARCHAR(20) NOT NULL,
    client_code_snapshot VARCHAR(32) NOT NULL,
    client_display_name_snapshot VARCHAR(200) NOT NULL,
    membership_code_snapshot VARCHAR(32) NOT NULL,
    plan_name_snapshot VARCHAR(160) NOT NULL,
    promotion_name_snapshot VARCHAR(160),
    membership_period_number SMALLINT NOT NULL,
    period_starts_on DATE NOT NULL,
    period_ends_on DATE NOT NULL,
    list_price NUMERIC(12, 2) NOT NULL,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by_user_id UUID NOT NULL,
    generated_by_display_name VARCHAR(200),
    test_mode BOOLEAN NOT NULL DEFAULT FALSE,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    renderer_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_receipts_receipt_number UNIQUE (receipt_number),
    CONSTRAINT uq_payment_receipts_payment_id UNIQUE (payment_id),
    CONSTRAINT uq_payment_receipts_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_payment_receipts_receipt_number_not_blank
        CHECK (btrim(receipt_number) <> ''),
    CONSTRAINT ck_payment_receipts_payment_code_snapshot_not_blank
        CHECK (btrim(payment_code_snapshot) <> ''),
    CONSTRAINT ck_payment_receipts_payment_status_snapshot
        CHECK (payment_status_snapshot = 'PAID'),
    CONSTRAINT ck_payment_receipts_client_code_snapshot_not_blank
        CHECK (btrim(client_code_snapshot) <> ''),
    CONSTRAINT ck_payment_receipts_client_display_name_snapshot_not_blank
        CHECK (btrim(client_display_name_snapshot) <> ''),
    CONSTRAINT ck_payment_receipts_membership_code_snapshot_not_blank
        CHECK (btrim(membership_code_snapshot) <> ''),
    CONSTRAINT ck_payment_receipts_plan_name_snapshot_not_blank
        CHECK (btrim(plan_name_snapshot) <> ''),
    CONSTRAINT ck_payment_receipts_promotion_name_snapshot
        CHECK (promotion_name_snapshot IS NULL
            OR btrim(promotion_name_snapshot) <> ''),
    CONSTRAINT ck_payment_receipts_period_number_positive
        CHECK (membership_period_number > 0),
    CONSTRAINT ck_payment_receipts_period_date_range
        CHECK (period_ends_on >= period_starts_on),
    CONSTRAINT ck_payment_receipts_list_price_non_negative
        CHECK (list_price >= 0),
    CONSTRAINT ck_payment_receipts_discount_range
        CHECK (discount_amount >= 0 AND discount_amount <= list_price),
    CONSTRAINT ck_payment_receipts_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_payment_receipts_amount_calculation
        CHECK (amount = list_price - discount_amount),
    CONSTRAINT ck_payment_receipts_currency_format
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_receipts_payment_method
        CHECK (payment_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT ck_payment_receipts_generation_after_payment
        CHECK (generated_at >= paid_at),
    CONSTRAINT ck_payment_receipts_generated_by_display_name
        CHECK (generated_by_display_name IS NULL
            OR btrim(generated_by_display_name) <> ''),
    CONSTRAINT ck_payment_receipts_storage_key_not_blank
        CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_payment_receipts_content_type
        CHECK (content_type = 'application/pdf'),
    CONSTRAINT ck_payment_receipts_size_range
        CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_payment_receipts_checksum_sha256
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_receipts_renderer_version
        CHECK (renderer_version IS NULL OR btrim(renderer_version) <> ''),
    CONSTRAINT ck_payment_receipts_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT fk_payment_receipts_payment
        FOREIGN KEY (payment_id)
        REFERENCES gym.payments (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payment_receipts_generated_by_user
        FOREIGN KEY (generated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE OR REPLACE FUNCTION gym.validate_payment_receipt()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
DECLARE
    payment_code_value VARCHAR(32);
    payment_status_value VARCHAR(20);
    payment_amount NUMERIC(12, 2);
    payment_currency CHAR(3);
    payment_method_value VARCHAR(20);
    payment_paid_at TIMESTAMPTZ;
BEGIN
    SELECT p.payment_code,
           p.status,
           p.amount,
           p.currency,
           p.payment_method,
           p.paid_at
    INTO payment_code_value,
         payment_status_value,
         payment_amount,
         payment_currency,
         payment_method_value,
         payment_paid_at
    FROM gym.payments AS p
    WHERE p.id = NEW.payment_id
    FOR KEY SHARE;

    IF NOT FOUND OR payment_status_value <> 'PAID' THEN
        RAISE EXCEPTION 'payment receipt requires a paid payment'
            USING ERRCODE = '23514';
    END IF;

    IF payment_code_value <> NEW.payment_code_snapshot
        OR payment_amount <> NEW.amount
        OR payment_currency <> NEW.currency
        OR payment_method_value <> NEW.payment_method
        OR payment_paid_at <> NEW.paid_at THEN
        RAISE EXCEPTION 'payment receipt snapshot does not match its payment'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_payment_receipts_validate
BEFORE INSERT ON gym.payment_receipts
FOR EACH ROW
EXECUTE FUNCTION gym.validate_payment_receipt();

CREATE OR REPLACE FUNCTION gym.reject_payment_receipt_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, gym
AS $function$
BEGIN
    RAISE EXCEPTION 'payment receipts are immutable'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_payment_receipts_immutable
BEFORE UPDATE OR DELETE ON gym.payment_receipts
FOR EACH ROW
EXECUTE FUNCTION gym.reject_payment_receipt_mutation();
