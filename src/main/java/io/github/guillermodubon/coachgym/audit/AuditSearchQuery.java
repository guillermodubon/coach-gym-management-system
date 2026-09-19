package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Immutable, validated filters for the administrative audit catalogue.
 *
 * <p>All string filters are exact matches. Codes are normalized to upper
 * case, actor identifiers are normalized to lower case, and blank optional
 * values become {@code null}. Both timestamp bounds are inclusive. A bounded
 * pair may span at most {@link AuditQueryPolicy#MAX_DATE_RANGE}; a single
 * bound is allowed for the established operational-query convention.</p>
 *
 * <p>The current {@code audit_entries} schema has no result column. Outcomes
 * are represented by the allowlisted action code and safe metadata, so this
 * contract deliberately has no result or arbitrary metadata-key filter.</p>
 */
public record AuditSearchQuery(
        UUID actorUserId,
        String actorIdentifier,
        String actionCode,
        String resourceType,
        UUID resourceId,
        String resourceCode,
        UUID correlationId,
        Instant occurredFrom,
        Instant occurredUntil,
        int page,
        int size,
        AuditSortField sortField,
        AuditSortDirection direction) {

    public AuditSearchQuery {
        actorIdentifier = normalizeActor(actorIdentifier);
        actionCode = normalizeAndValidateAction(actionCode);
        resourceType = normalizeAndValidateResource(resourceType);
        resourceCode = normalizeOptional(resourceCode);

        if (resourceCode != null && resourceCode.length() > 64) {
            throw new AuditQueryValidationException(
                    "Resource code filter must not exceed 64 characters.");
        }
        if (resourceCode != null
                && (resourceCode.startsWith("%") || resourceCode.startsWith("_"))) {
            throw new AuditQueryValidationException(
                    "Resource code filter must be an exact value.");
        }
        if (actorIdentifier != null && actorIdentifier.length() > 100) {
            throw new AuditQueryValidationException(
                    "Actor identifier filter must not exceed 100 characters.");
        }
        if (page < 0) {
            throw new AuditQueryValidationException(
                    "Audit query page must not be negative.");
        }
        if (size < 1 || size > AuditQueryPolicy.MAX_SIZE) {
            throw new AuditQueryValidationException(
                    "Audit query size must be between 1 and 100.");
        }
        if (occurredFrom != null && occurredUntil != null) {
            if (occurredFrom.isAfter(occurredUntil)) {
                throw new AuditQueryValidationException(
                        "occurredFrom must not be after occurredUntil.");
            }
            if (AuditQueryPolicy.MAX_DATE_RANGE.compareTo(
                    java.time.Duration.between(occurredFrom, occurredUntil)) < 0) {
                throw new AuditQueryValidationException(
                        "Audit query date range must not exceed 366 days.");
            }
        }
        sortField = sortField == null
                ? AuditQueryPolicy.DEFAULT_SORT
                : sortField;
        direction = direction == null
                ? AuditQueryPolicy.DEFAULT_DIRECTION
                : direction;
    }

    /** Returns the deterministic default query. */
    public static AuditSearchQuery defaults() {
        return new AuditSearchQuery(
                null, null, null, null, null, null, null, null, null,
                AuditQueryPolicy.DEFAULT_PAGE,
                AuditQueryPolicy.DEFAULT_SIZE,
                AuditQueryPolicy.DEFAULT_SORT,
                AuditQueryPolicy.DEFAULT_DIRECTION);
    }

    /**
     * Parses HTTP-facing filter values without allowing a client-provided
     * column, SQL fragment, wildcard, or unrecognized code.
     */
    public static AuditSearchQuery from(
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            UUID resourceId,
            String resourceCode,
            UUID correlationId,
            Instant occurredFrom,
            Instant occurredUntil,
            int page,
            int size,
            String sort,
            String direction) {
        return new AuditSearchQuery(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                correlationId,
                occurredFrom,
                occurredUntil,
                page,
                size,
                parseSort(sort),
                parseDirection(direction));
    }

    /**
     * Compatibility overload for callers that still send a result filter.
     * The schema has no result column, therefore every non-blank result value
     * is rejected instead of being silently ignored.
     */
    public static AuditSearchQuery from(
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            String result,
            UUID resourceId,
            String resourceCode,
            UUID correlationId,
            Instant occurredFrom,
            Instant occurredUntil,
            int page,
            int size,
            String sort,
            String direction) {
        if (normalizeOptional(result) != null) {
            throw new AuditQueryValidationException(
                    "Result filtering is not supported by the audit schema.");
        }
        return from(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                correlationId,
                occurredFrom,
                occurredUntil,
                page,
                size,
                sort,
                direction);
    }

    /** Alias matching other module query contracts. */
    public AuditSortField sort() {
        return sortField;
    }

    /** Alias matching other module query contracts. */
    public AuditSortDirection sortDirection() {
        return direction;
    }

    private static String normalizeAndValidateAction(String value) {
        String normalized = normalizeCode(value);
        if (normalized != null && !AuditQueryPolicy.isAllowedActionCode(normalized)) {
            throw new AuditQueryValidationException(
                    "Unsupported audit action-code filter.");
        }
        return normalized;
    }

    private static String normalizeAndValidateResource(String value) {
        String normalized = normalizeCode(value);
        if (normalized != null && !AuditQueryPolicy.isAllowedResourceType(normalized)) {
            throw new AuditQueryValidationException(
                    "Unsupported audit resource-type filter.");
        }
        return normalized;
    }

    private static String normalizeCode(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeActor(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null
                ? null
                : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static AuditSortField parseSort(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return AuditQueryPolicy.DEFAULT_SORT;
        }
        try {
            return AuditSortField.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AuditQueryValidationException(
                    "Unsupported audit sort field.");
        }
    }

    private static AuditSortDirection parseDirection(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return AuditQueryPolicy.DEFAULT_DIRECTION;
        }
        try {
            return AuditSortDirection.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AuditQueryValidationException(
                    "Unsupported audit sort direction.");
        }
    }
}
