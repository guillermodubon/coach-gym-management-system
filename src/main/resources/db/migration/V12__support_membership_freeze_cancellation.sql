ALTER TABLE gym.membership_freezes
    ADD COLUMN cancelled_on DATE,
    ADD COLUMN cancelled_by_user_id UUID;

ALTER TABLE gym.membership_freezes
    ADD CONSTRAINT fk_membership_freezes_cancelled_by_user
        FOREIGN KEY (cancelled_by_user_id)
            REFERENCES gym.users (id)
            ON DELETE RESTRICT;

ALTER TABLE gym.membership_freezes
    ADD CONSTRAINT ck_membership_freezes_cancellation_state
        CHECK (
            (
                cancelled_on IS NULL
                    AND cancelled_by_user_id IS NULL
                )
                OR
            (
                cancelled_on IS NOT NULL
                    AND cancelled_by_user_id IS NOT NULL
                )
            );

ALTER TABLE gym.membership_freezes
    ADD CONSTRAINT ck_membership_freezes_single_closure
        CHECK (
            NOT (
                reactivated_on IS NOT NULL
                    AND cancelled_on IS NOT NULL
                )
            );

ALTER TABLE gym.membership_freezes
    ADD CONSTRAINT ck_membership_freezes_cancelled_on
        CHECK (
            cancelled_on IS NULL
                OR cancelled_on >= starts_on
            );

DROP INDEX IF EXISTS
    gym.uq_membership_freezes_one_open_per_membership;

CREATE UNIQUE INDEX
    uq_membership_freezes_one_open_per_membership
    ON gym.membership_freezes (membership_id)
    WHERE reactivated_on IS NULL
        AND cancelled_on IS NULL;

CREATE INDEX
    idx_membership_freezes_cancelled_by_user_id
    ON gym.membership_freezes (cancelled_by_user_id)
    WHERE cancelled_by_user_id IS NOT NULL;