package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.UUID;

/** Safe, metadata-free representation used by audit list responses. */
public record AuditEntrySummary(
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

    public AuditEntrySummary {
        if (id == null || resourceId == null || occurredAt == null) {
            throw new AuditQueryValidationException(
                    "Audit summary id, resource id, and occurred-at are required.");
        }
        actionCode = requiredAction(actionCode);
        resourceType = requiredResource(resourceType);
        resourceCodeSnapshot = normalizeText(resourceCodeSnapshot);
        actorIdentifierSnapshot = normalizeActor(actorIdentifierSnapshot);
        summary = normalizeText(summary);
    }

    private static String requiredAction(String value) {
        String normalized = normalizeCode(value);
        if (!AuditQueryPolicy.isAllowedActionCode(normalized)) {
            throw new AuditQueryValidationException(
                    "Audit summary action code is not allowlisted.");
        }
        return normalized;
    }

    private static String requiredResource(String value) {
        String normalized = normalizeCode(value);
        if (!AuditQueryPolicy.isAllowedResourceType(normalized)) {
            throw new AuditQueryValidationException(
                    "Audit summary resource type is not allowlisted.");
        }
        return normalized;
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeActor(String value) {
        String normalized = normalizeText(value);
        return normalized == null
                ? null
                : normalized.toLowerCase(java.util.Locale.ROOT);
    }
}
