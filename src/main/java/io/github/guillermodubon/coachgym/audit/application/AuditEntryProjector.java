package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntrySummary;
import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import io.github.guillermodubon.coachgym.audit.AuditMetadataSanitizer;

/** Maps neutral audit rows to safe summary and detail projections. */
public final class AuditEntryProjector {

    private final AuditMetadataSanitizer metadataSanitizer;

    public AuditEntryProjector() {
        this(new AuditMetadataSanitizer());
    }

    public AuditEntryProjector(AuditMetadataSanitizer metadataSanitizer) {
        if (metadataSanitizer == null) {
            throw new IllegalArgumentException(
                    "Audit metadata sanitizer must be provided.");
        }
        this.metadataSanitizer = metadataSanitizer;
    }

    /** Projects only non-metadata columns for list responses. */
    public AuditEntrySummary toSummary(AuditEntryRow row) {
        if (row == null) {
            throw new IllegalArgumentException("Audit row must be provided.");
        }
        return new AuditEntrySummary(
                row.id(),
                row.actionCode(),
                row.resourceType(),
                row.resourceId(),
                row.resourceCodeSnapshot(),
                row.actorUserId(),
                row.actorIdentifierSnapshot(),
                row.summary(),
                row.occurredAt(),
                row.correlationId());
    }

    /** Projects a detail response after fail-closed metadata sanitization. */
    public AuditEntryDetails toDetails(AuditEntryRow row) {
        AuditEntrySummary summary = toSummary(row);
        AuditMetadataProjection metadata = metadataSanitizer.sanitize(
                row.actionCode(), row.metadata());
        return new AuditEntryDetails(summary, metadata);
    }
}
