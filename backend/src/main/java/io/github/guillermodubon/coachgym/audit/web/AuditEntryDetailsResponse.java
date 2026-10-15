package io.github.guillermodubon.coachgym.audit.web;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** HTTP-safe detail representation with a sanitized metadata map only. */
record AuditEntryDetailsResponse(
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
        Map<String, Object> metadata,
        boolean metadataRedacted) {

    AuditEntryDetailsResponse {
        AuditMetadataProjection projection =
                new AuditMetadataProjection(metadata, metadataRedacted);
        metadata = projection.values();
        metadataRedacted = projection.metadataRedacted();
    }

    static AuditEntryDetailsResponse from(AuditEntryDetails source) {
        if (source == null) {
            throw new IllegalArgumentException("Audit details must be provided.");
        }
        return new AuditEntryDetailsResponse(
                source.id(),
                source.actionCode(),
                source.resourceType(),
                source.resourceId(),
                source.resourceCodeSnapshot(),
                source.actorUserId(),
                source.actorIdentifierSnapshot(),
                source.summary(),
                source.occurredAt(),
                source.correlationId(),
                source.metadata().values(),
                source.metadata().metadataRedacted());
    }
}
