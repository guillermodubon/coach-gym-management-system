package io.github.guillermodubon.coachgym.access.application;

/**
 * Allowlisted sort fields for access-record queries.
 *
 * <p>Only {@code CHECKED_IN_AT} is offered. The schema has no
 * {@code created_at} column, so that sort field is deliberately absent.</p>
 */
public enum AccessSortField {

    /** Sort by the {@code occurred_at} timestamp (the check-in moment). */
    CHECKED_IN_AT
}
