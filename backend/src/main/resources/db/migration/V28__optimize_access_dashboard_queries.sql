-- The access dashboard filters by occurred_at and aggregates decision.
-- The existing decision-leading index cannot efficiently serve date-only
-- dashboard windows, so keep this index minimal and purpose-specific.
CREATE INDEX idx_access_records_occurred_at_decision
    ON gym.access_records (occurred_at DESC, decision);
