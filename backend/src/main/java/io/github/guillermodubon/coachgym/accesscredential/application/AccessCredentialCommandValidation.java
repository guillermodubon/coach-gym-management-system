package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Shared normalization and validation for access-credential commands. */
final class AccessCredentialCommandValidation {

    static final int MIN_REASON_LENGTH = 3;
    static final int MAX_REASON_LENGTH = 2000;

    private AccessCredentialCommandValidation() {
    }

    static UUID identifier(UUID value, String field) {
        if (value == null) {
            throw new AccessCredentialValidationException(field + " is required.");
        }
        return value;
    }

    static String reason(String value) {
        if (value == null || value.isBlank()) {
            throw new AccessCredentialValidationException(
                    "Access credential reason is required.");
        }
        String normalized = value.strip();
        if (normalized.length() < MIN_REASON_LENGTH) {
            throw new AccessCredentialValidationException(
                    "Access credential reason must contain at least "
                            + MIN_REASON_LENGTH + " characters.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new AccessCredentialValidationException(
                    "Access credential reason must not exceed "
                            + MAX_REASON_LENGTH + " characters.");
        }
        return normalized;
    }

    static long expectedVersion(long value) {
        if (value < 0) {
            throw new AccessCredentialValidationException(
                    "Access credential version must not be negative.");
        }
        return value;
    }
}
