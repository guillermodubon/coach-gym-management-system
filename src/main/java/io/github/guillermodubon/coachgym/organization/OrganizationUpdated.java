package io.github.guillermodubon.coachgym.organization;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Privacy-safe event emitted after a meaningful organization update. */
public record OrganizationUpdated(
        UUID organizationId,
        String organizationCode,
        Set<String> changedFields,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public OrganizationUpdated {
        if (organizationId == null || actorUserId == null || occurredAt == null) {
            throw new OrganizationValidationException(
                    "Organization event identifiers and timestamp are required.");
        }
        organizationCode = required(organizationCode, "Organization event code is required.");
        actorIdentifier = required(actorIdentifier, "Organization event actor is required.");
        if (changedFields == null || changedFields.isEmpty()) {
            throw new OrganizationValidationException(
                    "Organization event changed fields are required.");
        }
        changedFields = Set.copyOf(changedFields);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OrganizationValidationException(message);
        }
        return value.strip();
    }
}
