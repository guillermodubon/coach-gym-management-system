-- Database-backed, bounded abuse windows shared across application instances.
-- Email and IP subjects are short-lived identifiers; one-time token material is
-- never stored here (only its high-entropy SHA-256 fingerprint is used).
CREATE TABLE gym.staff_identity_abuse_windows (
    bucket_type VARCHAR(40) NOT NULL,
    subject_key VARCHAR(254) NOT NULL,
    attempt_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_staff_identity_abuse_windows
        PRIMARY KEY (bucket_type, subject_key),
    CONSTRAINT ck_staff_identity_abuse_bucket
        CHECK (bucket_type IN (
            'RECOVERY_EMAIL_REQUEST',
            'RECOVERY_IP_REQUEST',
            'RECOVERY_GLOBAL_REQUEST',
            'INVITATION_TOKEN_FAILURE',
            'INVITATION_IP_FAILURE',
            'RECOVERY_TOKEN_FAILURE',
            'RECOVERY_IP_FAILURE'
        )),
    CONSTRAINT ck_staff_identity_abuse_subject
        CHECK (
            (bucket_type = 'RECOVERY_EMAIL_REQUEST'
                AND subject_key = lower(btrim(subject_key))
                AND subject_key ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$')
            OR (bucket_type = 'RECOVERY_GLOBAL_REQUEST' AND subject_key = 'GLOBAL')
            OR (bucket_type IN ('INVITATION_TOKEN_FAILURE', 'RECOVERY_TOKEN_FAILURE')
                AND subject_key ~ '^[0-9a-f]{64}$')
            OR (bucket_type IN (
                    'RECOVERY_IP_REQUEST', 'INVITATION_IP_FAILURE', 'RECOVERY_IP_FAILURE')
                AND subject_key !~ '[[:cntrl:]]')
        ),
    CONSTRAINT ck_staff_identity_abuse_attempt_count
        CHECK (attempt_count BETWEEN 1 AND 100001),
    CONSTRAINT ck_staff_identity_abuse_expiration
        CHECK (expires_at > window_started_at)
);

CREATE INDEX idx_staff_identity_abuse_expiration
    ON gym.staff_identity_abuse_windows (expires_at ASC);

COMMENT ON TABLE gym.staff_identity_abuse_windows IS
    'Short-lived database-backed identity abuse counters. Expired subjects are pruned by the limiter and scheduled cleanup.';

COMMENT ON COLUMN gym.staff_identity_abuse_windows.subject_key IS
    'Normalized email or observed IP retained only for its configured abuse window, fixed GLOBAL sentinel, or high-entropy token fingerprint; never raw token.';
