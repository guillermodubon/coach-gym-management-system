-- Preserve the resolved operational codes as immutable snapshots.
--
-- These columns allow access-record queries to return the client and
-- membership codes that were evaluated at check-in time without coupling
-- the access persistence module to client or membership JPA entities.
--
-- Both columns remain nullable because unresolved identifiers and
-- clients without a current membership are valid denied attempts.

ALTER TABLE gym.access_records
    ADD COLUMN client_code_snapshot VARCHAR(32);

ALTER TABLE gym.access_records
    ADD COLUMN membership_code_snapshot VARCHAR(32);

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_client_code_snapshot_not_blank
        CHECK (
            client_code_snapshot IS NULL
                OR btrim(client_code_snapshot) <> ''
            );

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_membership_code_snapshot_not_blank
        CHECK (
            membership_code_snapshot IS NULL
                OR btrim(membership_code_snapshot) <> ''
            );

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_client_snapshot_consistency
        CHECK (
            (client_id IS NULL AND client_code_snapshot IS NULL)
                OR
            (client_id IS NOT NULL AND client_code_snapshot IS NOT NULL)
            );

ALTER TABLE gym.access_records
    ADD CONSTRAINT ck_access_records_membership_snapshot_consistency
        CHECK (
            (
                membership_id IS NULL
                    AND membership_period_id IS NULL
                    AND membership_code_snapshot IS NULL
                )
                OR
            (
                membership_id IS NOT NULL
                    AND membership_period_id IS NOT NULL
                    AND membership_code_snapshot IS NOT NULL
                    AND client_id IS NOT NULL
                )
            );