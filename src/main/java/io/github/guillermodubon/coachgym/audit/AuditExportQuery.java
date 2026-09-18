package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.UUID;

/** Immutable, technology-neutral filters for a bounded audit export. */
public record AuditExportQuery(
        UUID actorUserId,
        String actorIdentifier,
        String actionCode,
        String resourceType,
        UUID resourceId,
        String resourceCode,
        UUID correlationId,
        Instant occurredFrom,
        Instant occurredUntil,
        AuditSortField sortField,
        AuditSortDirection direction) {

    public AuditExportQuery {
        if (occurredFrom == null || occurredUntil == null) {
            throw AuditExportValidationException.rangeRequired();
        }
        if (occurredFrom.isAfter(occurredUntil)) {
            throw new AuditExportValidationException(
                    "occurredFrom must not be after occurredUntil.");
        }
        AuditSearchQuery normalized = new AuditSearchQuery(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                correlationId,
                occurredFrom,
                occurredUntil,
                AuditQueryPolicy.DEFAULT_PAGE,
                1,
                sortField,
                direction);
        actorIdentifier = normalized.actorIdentifier();
        actionCode = normalized.actionCode();
        resourceType = normalized.resourceType();
        resourceCode = normalized.resourceCode();
        sortField = normalized.sortField();
        direction = normalized.direction();
    }

    /** Applies the configured export span policy to this already-normalized query. */
    public void validate(AuditExportPolicy policy) {
        if (policy == null) {
            throw new AuditExportValidationException("Audit export policy is required.");
        }
        policy.validateRange(occurredFrom, occurredUntil);
    }

    /** Parses HTTP-facing values through the existing audit filter allowlists. */
    public static AuditExportQuery from(
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            UUID resourceId,
            String resourceCode,
            UUID correlationId,
            Instant occurredFrom,
            Instant occurredUntil,
            String sort,
            String direction) {
        AuditSearchQuery normalized = AuditSearchQuery.from(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                correlationId,
                occurredFrom,
                occurredUntil,
                AuditQueryPolicy.DEFAULT_PAGE,
                1,
                sort,
                direction);
        return new AuditExportQuery(
                normalized.actorUserId(),
                normalized.actorIdentifier(),
                normalized.actionCode(),
                normalized.resourceType(),
                normalized.resourceId(),
                normalized.resourceCode(),
                normalized.correlationId(),
                normalized.occurredFrom(),
                normalized.occurredUntil(),
                normalized.sortField(),
                normalized.direction());
    }

    public AuditSortField sort() {
        return sortField;
    }

    public AuditSortDirection sortDirection() {
        return direction;
    }
}
