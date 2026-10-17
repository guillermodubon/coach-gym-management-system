package io.github.guillermodubon.coachgym.audit;

import java.util.Objects;
import java.util.UUID;

/** Minimal staff identity used when an export operation is audited. */
public record AuditExportActor(UUID userId, String identifier) {

    public AuditExportActor {
        Objects.requireNonNull(userId, "Export actor user id is required.");
        if (identifier == null || identifier.isBlank()) {
            throw new AuditExportValidationException(
                    "Export actor identifier is required.");
        }
        identifier = identifier.strip();
        if (identifier.length() > 100) {
            throw new AuditExportValidationException(
                    "Export actor identifier is too long.");
        }
    }
}
