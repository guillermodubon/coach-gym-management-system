CREATE TABLE gym.memberships (
    id UUID PRIMARY KEY,
    membership_number BIGINT GENERATED ALWAYS AS IDENTITY,
    membership_code VARCHAR(32) GENERATED ALWAYS AS (
        'MEM-' || lpad(membership_number::TEXT, 6, '0')
    ) STORED,
    client_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cancelled_at TIMESTAMPTZ,
    cancelled_by_user_id UUID,
    cancellation_reason TEXT,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_memberships_membership_number UNIQUE (membership_number),
    CONSTRAINT uq_memberships_membership_code UNIQUE (membership_code),
    CONSTRAINT uq_memberships_id_client_id UNIQUE (id, client_id),
    CONSTRAINT ck_memberships_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_memberships_cancellation_state CHECK (
        (status <> 'CANCELLED'
            AND cancelled_at IS NULL
            AND cancelled_by_user_id IS NULL
            AND cancellation_reason IS NULL)
        OR
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancelled_by_user_id IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND btrim(cancellation_reason) <> '')
    ),
    CONSTRAINT ck_memberships_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_memberships_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_cancelled_by_user
        FOREIGN KEY (cancelled_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_memberships_one_current_per_client
    ON gym.memberships (client_id)
    WHERE status IN ('ACTIVE', 'FROZEN');

CREATE INDEX idx_memberships_client_status ON gym.memberships (client_id, status);

CREATE TABLE gym.membership_periods (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL,
    period_number SMALLINT NOT NULL,
    period_source VARCHAR(20) NOT NULL,
    membership_plan_id UUID NOT NULL,
    plan_code_snapshot VARCHAR(32) NOT NULL,
    plan_name_snapshot VARCHAR(160) NOT NULL,
    duration_value_snapshot SMALLINT NOT NULL,
    duration_unit_snapshot VARCHAR(10) NOT NULL,
    list_price NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    promotion_id UUID,
    promotion_code_snapshot VARCHAR(32),
    promotion_name_snapshot VARCHAR(160),
    promotion_type_snapshot VARCHAR(20),
    promotion_value_snapshot NUMERIC(12, 2),
    promotion_currency_snapshot CHAR(3),
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    final_price NUMERIC(12, 2) NOT NULL,
    starts_on DATE NOT NULL,
    base_ends_on DATE NOT NULL,
    effective_ends_on DATE NOT NULL,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_membership_periods_membership_period_number
        UNIQUE (membership_id, period_number),
    CONSTRAINT uq_membership_periods_id_membership_id UNIQUE (id, membership_id),
    CONSTRAINT ck_membership_periods_period_number_positive CHECK (period_number > 0),
    CONSTRAINT ck_membership_periods_period_source
        CHECK (period_source IN ('INITIAL', 'RENEWAL')),
    CONSTRAINT ck_membership_periods_plan_code_snapshot_not_blank
        CHECK (btrim(plan_code_snapshot) <> ''),
    CONSTRAINT ck_membership_periods_plan_name_snapshot_not_blank
        CHECK (btrim(plan_name_snapshot) <> ''),
    CONSTRAINT ck_membership_periods_duration_value_positive
        CHECK (duration_value_snapshot > 0),
    CONSTRAINT ck_membership_periods_duration_unit
        CHECK (duration_unit_snapshot IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    CONSTRAINT ck_membership_periods_list_price_non_negative CHECK (list_price >= 0),
    CONSTRAINT ck_membership_periods_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_membership_periods_discount_range
        CHECK (discount_amount >= 0 AND discount_amount <= list_price),
    CONSTRAINT ck_membership_periods_final_price_calculation
        CHECK (final_price = list_price - discount_amount),
    CONSTRAINT ck_membership_periods_final_price_non_negative CHECK (final_price >= 0),
    CONSTRAINT ck_membership_periods_date_range CHECK (
        base_ends_on > starts_on
        AND effective_ends_on >= base_ends_on
    ),
    CONSTRAINT ck_membership_periods_promotion_snapshot CHECK (
        (promotion_id IS NULL
            AND promotion_code_snapshot IS NULL
            AND promotion_name_snapshot IS NULL
            AND promotion_type_snapshot IS NULL
            AND promotion_value_snapshot IS NULL
            AND promotion_currency_snapshot IS NULL
            AND discount_amount = 0)
        OR
        (promotion_id IS NOT NULL
            AND promotion_code_snapshot IS NOT NULL
            AND btrim(promotion_code_snapshot) <> ''
            AND promotion_name_snapshot IS NOT NULL
            AND btrim(promotion_name_snapshot) <> ''
            AND promotion_type_snapshot IS NOT NULL
            AND promotion_value_snapshot IS NOT NULL
            AND promotion_value_snapshot > 0
            AND discount_amount > 0
            AND (
                (promotion_type_snapshot = 'PERCENTAGE'
                    AND promotion_value_snapshot <= 100
                    AND promotion_currency_snapshot IS NULL)
                OR
                (promotion_type_snapshot = 'FIXED_AMOUNT'
                    AND promotion_currency_snapshot IS NOT NULL
                    AND promotion_currency_snapshot ~ '^[A-Z]{3}$')
            ))
    ),
    CONSTRAINT ck_membership_periods_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_membership_periods_membership
        FOREIGN KEY (membership_id)
        REFERENCES gym.memberships (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_periods_membership_plan
        FOREIGN KEY (membership_plan_id)
        REFERENCES gym.membership_plans (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_periods_promotion
        FOREIGN KEY (promotion_id)
        REFERENCES gym.promotions (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_periods_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_periods_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_membership_periods_membership_starts_on
    ON gym.membership_periods (membership_id, starts_on DESC);

CREATE INDEX idx_membership_periods_effective_ends_on
    ON gym.membership_periods (effective_ends_on);

CREATE TABLE gym.membership_freezes (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL,
    membership_period_id UUID NOT NULL,
    starts_on DATE NOT NULL,
    planned_ends_on DATE NOT NULL,
    reactivated_on DATE,
    reason TEXT NOT NULL,
    created_by_user_id UUID NOT NULL,
    reactivated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_membership_freezes_planned_date_range CHECK (planned_ends_on > starts_on),
    CONSTRAINT ck_membership_freezes_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_membership_freezes_reactivation_state CHECK (
        (reactivated_on IS NULL AND reactivated_by_user_id IS NULL)
        OR
        (reactivated_on IS NOT NULL
            AND reactivated_by_user_id IS NOT NULL
            AND reactivated_on >= starts_on)
    ),
    CONSTRAINT ck_membership_freezes_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_membership_freezes_membership
        FOREIGN KEY (membership_id)
        REFERENCES gym.memberships (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_freezes_membership_period
        FOREIGN KEY (membership_period_id, membership_id)
        REFERENCES gym.membership_periods (id, membership_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_freezes_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_freezes_reactivated_by_user
        FOREIGN KEY (reactivated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_membership_freezes_one_open_per_membership
    ON gym.membership_freezes (membership_id)
    WHERE reactivated_on IS NULL;

CREATE INDEX idx_membership_freezes_membership_period_id
    ON gym.membership_freezes (membership_period_id);

CREATE TABLE gym.membership_status_history (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL,
    membership_period_id UUID,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_user_id UUID,
    CONSTRAINT ck_membership_status_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('ACTIVE', 'FROZEN', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_membership_status_history_new_status
        CHECK (new_status IN ('ACTIVE', 'FROZEN', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_membership_status_history_state_change
        CHECK (previous_status IS NULL OR previous_status <> new_status),
    CONSTRAINT fk_membership_status_history_membership
        FOREIGN KEY (membership_id)
        REFERENCES gym.memberships (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_status_history_membership_period
        FOREIGN KEY (membership_period_id, membership_id)
        REFERENCES gym.membership_periods (id, membership_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_membership_status_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_membership_status_history_membership_occurred_at
    ON gym.membership_status_history (membership_id, occurred_at DESC);

CREATE TRIGGER trg_memberships_set_updated_at
BEFORE UPDATE ON gym.memberships
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_membership_periods_set_updated_at
BEFORE UPDATE ON gym.membership_periods
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_membership_freezes_set_updated_at
BEFORE UPDATE ON gym.membership_freezes
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
