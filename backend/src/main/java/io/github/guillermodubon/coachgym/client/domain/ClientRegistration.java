package io.github.guillermodubon.coachgym.client.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

public record ClientRegistration(
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        EmergencyContactRegistration emergencyContact) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public static ClientRegistration create(
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            EmergencyContactRegistration emergencyContact,
            LocalDate today) {
        if (dateOfBirth != null && dateOfBirth.isAfter(today)) {
            throw new ClientValidationException("Date of birth cannot be in the future.");
        }
        return new ClientRegistration(
                normalizeRequired(firstName, "First name"),
                normalizeRequired(lastName, "Last name"),
                normalizeEmail(email),
                normalizeRequired(phone, "Phone"),
                dateOfBirth,
                emergencyContact);
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ClientValidationException(field + " must not be blank.");
        }
        return value.trim();
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ClientValidationException("Email must be valid.");
        }
        return normalized;
    }
}
