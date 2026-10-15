-- V14: Align gym.access_records with the approved Access Check-In domain vocabulary.
--
-- Background
-- ----------
-- V7 created access_records with two issues that block implementation:
--
-- 1. 'decision' stores 'GRANTED'/'DENIED'.
--    The approved domain uses AccessResult.ALLOWED / DENIED.
--    Keeping a silent GRANTED→ALLOWED mapping in the entity would create
--    invisible translation bugs and inconsistent audit queries.
--    This migration renames the allowed vocabulary to 'ALLOWED'/'DENIED'.
--
-- 2. 'denial_reason' stores 7 obsolete codes that differ from the 9 approved
--    AccessReasonCode values and only covers denial results.
--    Filtering or auditing by reason code is impossible without the new column.
--    This migration replaces the column with 'reason_code VARCHAR(40)' that
--    holds the approved codes for both ALLOWED and DENIED results symmetrically.
--
-- 3. Two indexes required by the approved query filters are missing.
--
-- Existing rows
-- -------------
-- The table is append-only and no production rows exist yet (the feature has
-- not been implemented). The UPDATE below is a forward-compatibility safeguard
-- in case any test or manual rows were inserted with the old vocabulary.
--
-- Migration rules
-- ---------------
-- - V7 is never modified.
-- - This migration is idempotent with respect to clean rebuilds via Flyway.
-- - No other tables are affected.

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 1: Migrate any existing 'GRANTED' rows to 'ALLOWED'.
-- ──────────────────────────────────────────────────────────────────────────────
UPDATE gym.access_records
SET decision = 'ALLOWED'
WHERE decision = 'GRANTED';

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 2: Replace the 'decision' check constraint.
--         Old: CHECK (decision IN ('GRANTED', 'DENIED'))
--         New: CHECK (decision IN ('ALLOWED', 'DENIED'))
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE gym.access_records
    DROP CONSTRAINT ck_access_records_decision;

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_decision
        CHECK (decision IN ('ALLOWED', 'DENIED'));

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 3: Drop the obsolete decision/denial_reason consistency constraint.
--         It referenced 'GRANTED' and only applied to denial results.
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE gym.access_records
    DROP CONSTRAINT ck_access_records_decision_reason;

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 4: Drop the obsolete denial_reason check constraint and column.
--         The 7 old codes are superseded by the 9 approved AccessReasonCode values
--         stored in the new reason_code column (Step 5).
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE gym.access_records
    DROP CONSTRAINT ck_access_records_denial_reason;

ALTER TABLE gym.access_records
    DROP COLUMN denial_reason;

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 5: Add 'reason_code' column holding the approved AccessReasonCode values.
--         Covers both ALLOWED and DENIED results in one column.
--         The 'details' TEXT column is retained for the human-readable reason string.
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE gym.access_records
    ADD COLUMN reason_code VARCHAR(40);

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
            'MEMBERSHIP_CANCELLED'
        ));

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 6: Add new symmetrical decision/reason_code consistency constraint.
--         ALLOWED rows must carry ACCESS_ALLOWED.
--         DENIED rows must carry any other approved reason code.
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_decision_reason_code
        CHECK (
            (decision = 'ALLOWED' AND reason_code = 'ACCESS_ALLOWED')
            OR
            (decision = 'DENIED'
                AND reason_code IS NOT NULL
                AND reason_code <> 'ACCESS_ALLOWED')
        );

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 7: Add missing index for the approved membershipId query filter.
--         V7 has an index on membership_period_id but not on membership_id alone.
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_access_records_membership_id
    ON gym.access_records (membership_id)
    WHERE membership_id IS NOT NULL;

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 8: Add missing index for the approved processedByUserId query filter.
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_access_records_recorded_by_user_id
    ON gym.access_records (recorded_by_user_id)
    WHERE recorded_by_user_id IS NOT NULL;
