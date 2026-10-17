CREATE INDEX idx_audit_entries_branch_occurred_at_id
    ON gym.audit_entries ((metadata ->> 'branchId'), occurred_at DESC, id DESC)
    WHERE metadata ->> 'branchId' IS NOT NULL;

COMMENT ON INDEX gym.idx_audit_entries_branch_occurred_at_id IS
    'Supports branch-scoped audit list and export filtering with deterministic time ordering.';
