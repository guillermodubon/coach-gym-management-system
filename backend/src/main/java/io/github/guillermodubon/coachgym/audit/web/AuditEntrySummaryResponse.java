package io.github.guillermodubon.coachgym.audit.web;

import io.github.guillermodubon.coachgym.audit.AuditEntrySummary;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** HTTP-safe summary representation for an audit entry. */
record AuditEntrySummaryResponse(
        UUID id,
        String actionCode,
        String resourceType,
        UUID resourceId,
        String resourceCodeSnapshot,
        UUID actorUserId,
        String actorIdentifierSnapshot,
        String summary,
        Instant occurredAt,
        UUID correlationId) {

    static AuditEntrySummaryResponse from(AuditEntrySummary source) {
        Objects.requireNonNull(source, "Audit summary must be provided.");
        return new AuditEntrySummaryResponse(
                source.id(),
                source.actionCode(),
                source.resourceType(),
                source.resourceId(),
                source.resourceCodeSnapshot(),
                source.actorUserId(),
                source.actorIdentifierSnapshot(),
                source.summary(),
                source.occurredAt(),
                source.correlationId());
    }
}
