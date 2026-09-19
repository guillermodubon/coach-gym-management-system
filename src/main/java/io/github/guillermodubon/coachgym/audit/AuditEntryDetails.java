package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.UUID;

/** Safe detail representation with a separately controlled metadata view. */
public record AuditEntryDetails(
        UUID id,
        String actionCode,
        String resourceType,
        UUID resourceId,
        String resourceCodeSnapshot,
        UUID actorUserId,
        String actorIdentifierSnapshot,
        String summary,
        Instant occurredAt,
        UUID correlationId,
        AuditMetadataProjection metadata) {

    public AuditEntryDetails {
        AuditEntrySummary summaryProjection = new AuditEntrySummary(
                id,
                actionCode,
                resourceType,
                resourceId,
                resourceCodeSnapshot,
                actorUserId,
                actorIdentifierSnapshot,
                summary,
                occurredAt,
                correlationId);
        id = summaryProjection.id();
        actionCode = summaryProjection.actionCode();
        resourceType = summaryProjection.resourceType();
        resourceId = summaryProjection.resourceId();
        resourceCodeSnapshot = summaryProjection.resourceCodeSnapshot();
        actorUserId = summaryProjection.actorUserId();
        actorIdentifierSnapshot = summaryProjection.actorIdentifierSnapshot();
        summary = summaryProjection.summary();
        occurredAt = summaryProjection.occurredAt();
        correlationId = summaryProjection.correlationId();
        metadata = metadata == null ? AuditMetadataProjection.empty() : metadata;
    }

    public AuditEntryDetails(
            AuditEntrySummary summary,
            AuditMetadataProjection metadata) {
        this(
                summary.id(),
                summary.actionCode(),
                summary.resourceType(),
                summary.resourceId(),
                summary.resourceCodeSnapshot(),
                summary.actorUserId(),
                summary.actorIdentifierSnapshot(),
                summary.summary(),
                summary.occurredAt(),
                summary.correlationId(),
                metadata);
    }

    public boolean metadataRedacted() {
        return metadata.metadataRedacted();
    }
}
