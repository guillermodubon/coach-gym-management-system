package io.github.guillermodubon.coachgym.audit.application;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Neutral row used between the future persistence adapter and audit
 * projections. It is not a JPA entity and must never be serialized directly.
 */
public record AuditEntryRow(
        UUID id,
        UUID actorUserId,
        String actorIdentifierSnapshot,
        String actionCode,
        String resourceType,
        UUID resourceId,
        String resourceCodeSnapshot,
        String summary,
        Map<String, Object> metadata,
        UUID correlationId,
        Instant occurredAt) {

    public AuditEntryRow {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
