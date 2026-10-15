-- Client lifecycle history and photo metadata.
-- This migration preserves existing clients and stores no image binary data.

CREATE TABLE gym.client_status_history (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    changed_by_user_id UUID NOT NULL,
    CONSTRAINT fk_client_status_history_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_client_status_history_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_client_status_history_previous_status
        CHECK (previous_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_client_status_history_new_status
        CHECK (new_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_client_status_history_transition
        CHECK (previous_status <> new_status),
    CONSTRAINT ck_client_status_history_reason_not_blank
        CHECK (btrim(reason) <> '')
);

CREATE INDEX idx_client_status_history_client_occurred_at
    ON gym.client_status_history (client_id, occurred_at DESC, id ASC);

CREATE TABLE gym.client_photos (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_client_photos_client UNIQUE (client_id),
    CONSTRAINT uq_client_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_client_photos_client
        FOREIGN KEY (client_id)
        REFERENCES gym.clients (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_client_photos_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_client_photos_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_client_photos_storage_key_not_blank
        CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_client_photos_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_client_photos_size
        CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_client_photos_checksum_sha256
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_client_photos_version_non_negative
        CHECK (version >= 0)
);

CREATE INDEX idx_client_photos_updated_at
    ON gym.client_photos (updated_at DESC, id ASC);

CREATE TRIGGER trg_client_photos_set_updated_at
BEFORE UPDATE ON gym.client_photos
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE OR REPLACE FUNCTION gym.reject_client_status_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION 'client status history is append-only'
        USING ERRCODE = '55000';
END;
$function$;

CREATE TRIGGER trg_client_status_history_reject_update
BEFORE UPDATE ON gym.client_status_history
FOR EACH ROW
EXECUTE FUNCTION gym.reject_client_status_history_mutation();

CREATE TRIGGER trg_client_status_history_reject_delete
BEFORE DELETE ON gym.client_status_history
FOR EACH ROW
EXECUTE FUNCTION gym.reject_client_status_history_mutation();
