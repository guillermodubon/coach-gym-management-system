-- Staff profile photo metadata only. Image bytes remain in private object storage.
-- The photo version mirrors the owning user's profile version so profile and
-- photo mutations share one optimistic-concurrency sequence.

CREATE TABLE gym.staff_profile_photos (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id UUID NOT NULL,
    updated_by_user_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_staff_profile_photos_user UNIQUE (user_id),
    CONSTRAINT uq_staff_profile_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_staff_profile_photos_user
        FOREIGN KEY (user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_profile_photos_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_staff_profile_photos_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_staff_profile_photos_storage_key_not_blank
        CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_staff_profile_photos_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_staff_profile_photos_size
        CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_staff_profile_photos_checksum_sha256
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_staff_profile_photos_version_non_negative
        CHECK (version >= 0)
);

CREATE INDEX idx_staff_profile_photos_updated_at
    ON gym.staff_profile_photos (updated_at DESC, id ASC);

CREATE TRIGGER trg_staff_profile_photos_set_updated_at
BEFORE UPDATE ON gym.staff_profile_photos
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
