CREATE TABLE gym.payments (
    id UUID PRIMARY KEY,
    payment_number BIGINT GENERATED ALWAYS AS IDENTITY,
    payment_code VARCHAR(32) GENERATED ALWAYS AS (
        'PAY-' || lpad(payment_number::TEXT, 6, '0')
    ) STORED,
    client_id UUID NOT NULL,
    membership_id UUID,
    membership_period_id UUID,
    amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PAID',
    external_reference VARCHAR(128),
    paid_at TIMESTAMPTZ NOT NULL,
    registered_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_payment_number UNIQUE (payment_number),
    CONSTRAINT uq_payments_payment_code UNIQUE (payment_code),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payments_method
        CHECK (payment_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PAID', 'VOIDED', 'REFUNDED')),
    CONSTRAINT ck_payments_membership_period_pair CHECK (
        (membership_id IS NULL AND membership_period_id IS NULL)
        OR
        (membership_id IS NOT NULL AND membership_period_id IS NOT NULL)
    ),
    CONSTRAINT ck_payments_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_payments_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payments_membership_period
        FOREIGN KEY (membership_period_id, membership_id)
        REFERENCES gym.membership_periods (id, membership_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payments_membership_client
        FOREIGN KEY (membership_id, client_id)
        REFERENCES gym.memberships (id, client_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payments_registered_by_user
        FOREIGN KEY (registered_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_payments_client_paid_at ON gym.payments (client_id, paid_at DESC);
CREATE INDEX idx_payments_membership_period_id
    ON gym.payments (membership_period_id)
    WHERE membership_period_id IS NOT NULL;
CREATE INDEX idx_payments_membership_id
    ON gym.payments (membership_id)
    WHERE membership_id IS NOT NULL;
CREATE INDEX idx_payments_status_paid_at ON gym.payments (status, paid_at DESC);

CREATE TABLE gym.payment_status_history (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_user_id UUID,
    CONSTRAINT ck_payment_status_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('PAID', 'VOIDED', 'REFUNDED')),
    CONSTRAINT ck_payment_status_history_new_status
        CHECK (new_status IN ('PAID', 'VOIDED', 'REFUNDED')),
    CONSTRAINT ck_payment_status_history_state_change
        CHECK (previous_status IS NULL OR previous_status <> new_status),
    CONSTRAINT fk_payment_status_history_payment
        FOREIGN KEY (payment_id)
        REFERENCES gym.payments (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payment_status_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_payment_status_history_payment_occurred_at
    ON gym.payment_status_history (payment_id, occurred_at DESC);

CREATE TABLE gym.payment_refunds (
    id UUID PRIMARY KEY,
    refund_number BIGINT GENERATED ALWAYS AS IDENTITY,
    refund_code VARCHAR(32) GENERATED ALWAYS AS (
        'REF-' || lpad(refund_number::TEXT, 6, '0')
    ) STORED,
    payment_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    refund_method VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    external_reference VARCHAR(128),
    refunded_at TIMESTAMPTZ NOT NULL,
    refunded_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_refunds_refund_number UNIQUE (refund_number),
    CONSTRAINT uq_payment_refunds_refund_code UNIQUE (refund_code),
    CONSTRAINT uq_payment_refunds_payment_id UNIQUE (payment_id),
    CONSTRAINT ck_payment_refunds_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payment_refunds_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_refunds_method
        CHECK (refund_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT ck_payment_refunds_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT fk_payment_refunds_payment
        FOREIGN KEY (payment_id)
        REFERENCES gym.payments (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_payment_refunds_refunded_by_user
        FOREIGN KEY (refunded_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_payment_refunds_refunded_at ON gym.payment_refunds (refunded_at DESC);

CREATE TABLE gym.access_records (
    id UUID PRIMARY KEY,
    entered_code VARCHAR(64) NOT NULL,
    client_id UUID,
    membership_id UUID,
    membership_period_id UUID,
    recorded_by_user_id UUID,
    decision VARCHAR(10) NOT NULL,
    denial_reason VARCHAR(40),
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_access_records_entered_code_not_blank CHECK (btrim(entered_code) <> ''),
    CONSTRAINT ck_access_records_decision CHECK (decision IN ('GRANTED', 'DENIED')),
    CONSTRAINT ck_access_records_denial_reason CHECK (
        denial_reason IS NULL OR denial_reason IN (
            'CLIENT_NOT_FOUND',
            'CLIENT_INACTIVE',
            'NO_MEMBERSHIP',
            'MEMBERSHIP_EXPIRED',
            'MEMBERSHIP_FROZEN',
            'MEMBERSHIP_CANCELLED',
            'NO_VALID_PERIOD'
        )
    ),
    CONSTRAINT ck_access_records_decision_reason CHECK (
        (decision = 'GRANTED' AND denial_reason IS NULL)
        OR
        (decision = 'DENIED' AND denial_reason IS NOT NULL)
    ),
    CONSTRAINT ck_access_records_membership_period_pair CHECK (
        (membership_id IS NULL AND membership_period_id IS NULL)
        OR
        (membership_id IS NOT NULL AND membership_period_id IS NOT NULL AND client_id IS NOT NULL)
    ),
    CONSTRAINT fk_access_records_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_records_membership
        FOREIGN KEY (membership_id)
        REFERENCES gym.memberships (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_records_membership_period
        FOREIGN KEY (membership_period_id, membership_id)
        REFERENCES gym.membership_periods (id, membership_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_records_membership_client
        FOREIGN KEY (membership_id, client_id)
        REFERENCES gym.memberships (id, client_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_records_recorded_by_user
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_access_records_client_occurred_at
    ON gym.access_records (client_id, occurred_at DESC)
    WHERE client_id IS NOT NULL;
CREATE INDEX idx_access_records_decision_occurred_at
    ON gym.access_records (decision, occurred_at DESC);
CREATE INDEX idx_access_records_membership_period_id
    ON gym.access_records (membership_period_id)
    WHERE membership_period_id IS NOT NULL;
