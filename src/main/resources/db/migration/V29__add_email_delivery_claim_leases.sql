-- Coordinate transactional-email attempts across backend instances without
-- relying on process-local locks. A lease is short-lived and contains no
-- message content or provider secret.

CREATE TABLE gym.email_delivery_claims (
    delivery_id UUID PRIMARY KEY,
    claim_token UUID NOT NULL,
    expected_version BIGINT NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_email_delivery_claims_version_non_negative
        CHECK (expected_version >= 0),
    CONSTRAINT ck_email_delivery_claims_expiry
        CHECK (expires_at > claimed_at),
    CONSTRAINT fk_email_delivery_claims_delivery
        FOREIGN KEY (delivery_id)
        REFERENCES gym.email_deliveries (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_email_delivery_claims_expiry
    ON gym.email_delivery_claims (expires_at ASC, delivery_id ASC);
