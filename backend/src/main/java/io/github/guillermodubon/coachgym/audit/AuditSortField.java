package io.github.guillermodubon.coachgym.audit;

/**
 * Allowlisted fields that may order an audit query.
 *
 * <p>Only the persisted event time is currently approved. Implementations
 * must use the audit identifier as a deterministic tie-breaker after this
 * field; callers cannot provide a SQL column name.</p>
 */
public enum AuditSortField {
    OCCURRED_AT
}
