CREATE INDEX idx_audit_entries_occurred_at_id
    ON gym.audit_entries (occurred_at DESC, id DESC);
