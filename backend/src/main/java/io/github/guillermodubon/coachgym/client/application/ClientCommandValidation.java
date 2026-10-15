package io.github.guillermodubon.coachgym.client.application;

import java.util.regex.Pattern;

final class ClientCommandValidation {

    private static final Pattern EMAIL = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private ClientCommandValidation() {
    }

    static String required(String value, String label, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new ClientValidationException(label + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new ClientValidationException(
                    label + " must not exceed " + maximumLength + " characters.");
        }
        return normalized;
    }

    static String optional(String value, String label, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maximumLength) {
            throw new ClientValidationException(
                    label + " must not exceed " + maximumLength + " characters.");
        }
        return normalized;
    }

    static String optionalEmail(String value) {
        String email = optional(value, "Client email", 254);
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new ClientValidationException("Client email format is invalid.");
        }
        return email == null ? null : email.toLowerCase(java.util.Locale.ROOT);
    }

    static long version(long value) {
        if (value < 0) {
            throw new ClientValidationException("Client version must not be negative.");
        }
        return value;
    }
}
