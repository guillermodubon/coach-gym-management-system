package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Immutable, technology-neutral filters for a bounded audit export. */
public record AuditExportQuery(
        UUID actorUserId,
        String actorIdentifier,
        String actionCode,
        String resourceType,
        String result,
        UUID resourceId,
        String resourceCode,
        UUID correlationId,
        Set<UUID> branchIds,
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
                result,
                resourceId,
                resourceCode,
                correlationId,
                branchIds,
                occurredFrom,
                occurredUntil,
                AuditQueryPolicy.DEFAULT_PAGE,
                1,
                sortField,
                direction);
        actorIdentifier = normalized.actorIdentifier();
        actionCode = normalized.actionCode();
        resourceType = normalized.resourceType();
        result = normalized.result();
        branchIds = normalized.branchIds();
        resourceCode = normalized.resourceCode();
        sortField = normalized.sortField();
        direction = normalized.direction();
    }

    /** Compatibility constructor for callers that do not request result or branch filters. */
    public AuditExportQuery(
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
        this(actorUserId, actorIdentifier, actionCode, resourceType, null,
                resourceId, resourceCode, correlationId, Set.of(), occurredFrom,
                occurredUntil, sortField, direction);
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
        return from(actorUserId, actorIdentifier, actionCode, resourceType,
                null, resourceId, resourceCode, correlationId, Set.of(),
                occurredFrom, occurredUntil, sort, direction);
    }

    /** Parses HTTP-facing values, including the approved result and branch filters. */
    public static AuditExportQuery from(
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            String result,
            UUID resourceId,
            String resourceCode,
            UUID correlationId,
            Set<UUID> branchIds,
            Instant occurredFrom,
            Instant occurredUntil,
            String sort,
            String direction) {
        AuditSearchQuery normalized = AuditSearchQuery.from(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                result,
                resourceId,
                resourceCode,
                correlationId,
                branchIds == null ? Set.of() : branchIds,
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
                normalized.result(),
                normalized.resourceId(),
                normalized.resourceCode(),
                normalized.correlationId(),
                normalized.branchIds(),
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
