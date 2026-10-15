CREATE TABLE gym.audit_entries (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    actor_identifier_snapshot VARCHAR(100),
    action_code VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID NOT NULL,
    resource_code_snapshot VARCHAR(64),
    summary TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    correlation_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_entries_action_code_not_blank CHECK (btrim(action_code) <> ''),
    CONSTRAINT ck_audit_entries_resource_type_not_blank CHECK (btrim(resource_type) <> ''),
    CONSTRAINT ck_audit_entries_actor_snapshot CHECK (
        actor_user_id IS NULL OR actor_identifier_snapshot IS NOT NULL
    ),
    CONSTRAINT fk_audit_entries_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_audit_entries_resource_occurred_at
    ON gym.audit_entries (resource_type, resource_id, occurred_at DESC);
CREATE INDEX idx_audit_entries_actor_occurred_at
    ON gym.audit_entries (actor_user_id, occurred_at DESC)
    WHERE actor_user_id IS NOT NULL;
CREATE INDEX idx_audit_entries_correlation_id
    ON gym.audit_entries (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE gym.notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    severity VARCHAR(10) NOT NULL DEFAULT 'INFO',
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_notifications_type CHECK (notification_type IN (
        'MEMBERSHIP_EXPIRING',
        'PAYMENT_VOIDED',
        'PAYMENT_REFUNDED',
        'INCIDENT_ASSIGNED',
        'MAINTENANCE_ASSIGNED',
        'SYSTEM'
    )),
    CONSTRAINT ck_notifications_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_notifications_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_notifications_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_notifications_resource_pair CHECK (
        (resource_type IS NULL AND resource_id IS NULL)
        OR
        (resource_type IS NOT NULL AND btrim(resource_type) <> '' AND resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_notifications_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_notifications_recipient_user
        FOREIGN KEY (recipient_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_notifications_unread_by_recipient
    ON gym.notifications (recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;
CREATE INDEX idx_notifications_recipient_created_at
    ON gym.notifications (recipient_user_id, created_at DESC);

CREATE TRIGGER trg_notifications_set_updated_at
BEFORE UPDATE ON gym.notifications
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
