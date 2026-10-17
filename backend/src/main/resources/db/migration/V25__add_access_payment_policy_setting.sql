-- Persist the global access-payment requirement without changing existing
-- settings or access history. FALSE preserves behavior for existing gyms.

ALTER TABLE gym.gym_settings
    ADD COLUMN require_confirmed_payment_for_access BOOLEAN NOT NULL DEFAULT FALSE;

-- The access module already exposes this safe denial reason. Allow it to be
-- persisted when the later access integration block records the decision.
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
            'ACCESS_CREDENTIAL_INVALID',
            'DUPLICATE_CHECK_IN',
            'PAYMENT_REQUIRED'
        ));
