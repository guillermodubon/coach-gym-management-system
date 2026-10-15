CREATE TABLE gym.membership_plans (
    id UUID PRIMARY KEY,
    plan_number BIGINT GENERATED ALWAYS AS IDENTITY,
    plan_code VARCHAR(32) GENERATED ALWAYS AS (
        'PLAN-' || lpad(plan_number::TEXT, 6, '0')
    ) STORED,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    duration_value SMALLINT NOT NULL,
    duration_unit VARCHAR(10) NOT NULL,
    list_price NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_membership_plans_plan_number UNIQUE (plan_number),
    CONSTRAINT uq_membership_plans_plan_code UNIQUE (plan_code),
    CONSTRAINT ck_membership_plans_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_membership_plans_duration_value_positive CHECK (duration_value > 0),
    CONSTRAINT ck_membership_plans_duration_unit
        CHECK (duration_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    CONSTRAINT ck_membership_plans_list_price_non_negative CHECK (list_price >= 0),
    CONSTRAINT ck_membership_plans_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_membership_plans_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_membership_plans_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_membership_plans_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_membership_plans_active ON gym.membership_plans (name) WHERE is_active;

CREATE TABLE gym.promotions (
    id UUID PRIMARY KEY,
    promotion_number BIGINT GENERATED ALWAYS AS IDENTITY,
    promotion_code VARCHAR(32) GENERATED ALWAYS AS (
        'PROMO-' || lpad(promotion_number::TEXT, 6, '0')
    ) STORED,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(12, 2) NOT NULL,
    currency CHAR(3),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_promotions_promotion_number UNIQUE (promotion_number),
    CONSTRAINT uq_promotions_promotion_code UNIQUE (promotion_code),
    CONSTRAINT ck_promotions_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_promotions_valid_date_range CHECK (valid_from <= valid_until),
    CONSTRAINT ck_promotions_discount_definition CHECK (
        (discount_type = 'PERCENTAGE'
            AND discount_value > 0
            AND discount_value <= 100
            AND currency IS NULL)
        OR
        (discount_type = 'FIXED_AMOUNT'
            AND discount_value > 0
            AND currency IS NOT NULL
            AND currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_promotions_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_promotions_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_promotions_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_promotions_active_window
    ON gym.promotions (valid_from, valid_until)
    WHERE is_active;

CREATE TABLE gym.promotion_plan_eligibility (
    promotion_id UUID NOT NULL,
    membership_plan_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (promotion_id, membership_plan_id),
    CONSTRAINT fk_promotion_plan_eligibility_promotion
        FOREIGN KEY (promotion_id)
        REFERENCES gym.promotions (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_promotion_plan_eligibility_membership_plan
        FOREIGN KEY (membership_plan_id)
        REFERENCES gym.membership_plans (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_promotion_plan_eligibility_membership_plan_id
    ON gym.promotion_plan_eligibility (membership_plan_id);

CREATE TRIGGER trg_membership_plans_set_updated_at
BEFORE UPDATE ON gym.membership_plans
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_promotions_set_updated_at
BEFORE UPDATE ON gym.promotions
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
