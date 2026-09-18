package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Privacy-safe event emitted after a bounded CSV export has been streamed.
 *
 * <p>The event carries filter names, never filter values or exported rows. It
 * is handled by the audit module itself in a new transaction, so recording
 * this event cannot publish another export event.</p>
 */
public record AuditExportCompleted(
        UUID exportId,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredFrom,
        Instant occurredUntil,
        String filterSummary,
        AuditSortField sortField,
        AuditSortDirection sortDirection,
        long rowCount,
        int maximumRows,
        String format,
        Instant occurredAt) {

    public static final String ACTION_CODE = "AUDIT_ENTRIES_EXPORTED";
    public static final String RESOURCE_TYPE = "AUDIT_EXPORT";
    public static final String FORMAT_CSV = "CSV";

    public AuditExportCompleted {
        Objects.requireNonNull(exportId, "Export id is required.");
        Objects.requireNonNull(actorUserId, "Export actor user id is required.");
        if (actorIdentifier == null || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException("Export actor identifier is required.");
        }
        actorIdentifier = actorIdentifier.strip();
        if (actorIdentifier.length() > 100) {
            throw new IllegalArgumentException("Export actor identifier is too long.");
        }
        Objects.requireNonNull(occurredFrom, "Export range start is required.");
        Objects.requireNonNull(occurredUntil, "Export range end is required.");
        if (occurredFrom.isAfter(occurredUntil)) {
            throw new IllegalArgumentException("Export range is invalid.");
        }
        if (filterSummary == null || filterSummary.length() > 256
                || !filterSummary.matches("[A-Za-z0-9_,]*")) {
            throw new IllegalArgumentException("Export filter summary is invalid.");
        }
        Objects.requireNonNull(sortField, "Export sort field is required.");
        Objects.requireNonNull(sortDirection, "Export sort direction is required.");
        if (rowCount < 0 || maximumRows < 1 || rowCount > maximumRows) {
            throw new IllegalArgumentException("Export row count is invalid.");
        }
        if (!FORMAT_CSV.equals(format)) {
            throw new IllegalArgumentException("Only CSV export is supported.");
        }
        Objects.requireNonNull(occurredAt, "Export occurrence time is required.");
    }
}
