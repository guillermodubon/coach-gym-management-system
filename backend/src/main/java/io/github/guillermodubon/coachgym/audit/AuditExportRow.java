package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.UUID;

/** Safe, already-projected row supplied to a future export encoder. */
public record AuditExportRow(
        UUID entryId,
        Instant occurredAt,
        UUID actorUserId,
        String actorIdentifier,
        String actionCode,
        String resourceType,
        UUID resourceId,
        String resourceCode,
        String summary,
        UUID correlationId,
        UUID branchId,
        AuditMetadataProjection metadata) {

    public AuditExportRow {
        AuditEntryDetails details = new AuditEntryDetails(
                entryId,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                actorUserId,
                actorIdentifier,
                summary,
                occurredAt,
                correlationId,
                metadata);
        entryId = details.id();
        occurredAt = details.occurredAt();
        actorUserId = details.actorUserId();
        actorIdentifier = details.actorIdentifierSnapshot();
        actionCode = details.actionCode();
        resourceType = details.resourceType();
        resourceId = details.resourceId();
        resourceCode = details.resourceCodeSnapshot();
        summary = details.summary();
        correlationId = details.correlationId();
        metadata = details.metadata();
    }

    /** Compatibility constructor for rows without an explicitly projected branch identifier. */
    public AuditExportRow(
            UUID entryId,
            Instant occurredAt,
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            UUID resourceId,
            String resourceCode,
            String summary,
            UUID correlationId,
            AuditMetadataProjection metadata) {
        this(entryId, occurredAt, actorUserId, actorIdentifier, actionCode,
                resourceType, resourceId, resourceCode, summary, correlationId,
                null, metadata);
    }

    public AuditExportRow(AuditEntryDetails details) {
        this(
                details.id(),
                details.occurredAt(),
                details.actorUserId(),
                details.actorIdentifierSnapshot(),
                details.actionCode(),
                details.resourceType(),
                details.resourceId(),
                details.resourceCodeSnapshot(),
                details.summary(),
                details.correlationId(),
                null,
                details.metadata());
    }

    public AuditExportRow(AuditEntryDetails details, UUID branchId) {
        this(
                details.id(),
                details.occurredAt(),
                details.actorUserId(),
                details.actorIdentifierSnapshot(),
                details.actionCode(),
                details.resourceType(),
                details.resourceId(),
                details.resourceCodeSnapshot(),
                details.summary(),
                details.correlationId(),
                branchId,
                details.metadata());
    }
}
