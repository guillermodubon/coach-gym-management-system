package io.github.guillermodubon.coachgym.configuration;

import java.util.UUID;

/** Server-resolved identity snapshot used by policy administration. */
public record AccessPaymentPolicyActor(UUID userId, String identifier) {

    public AccessPaymentPolicyActor {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Access payment policy actor is required.");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Access payment policy actor identifier is required.");
        }
        identifier = identifier.strip();
    }
}
