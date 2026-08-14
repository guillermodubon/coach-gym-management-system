package io.github.guillermodubon.coachgym.client.domain;

public record EmergencyContactRegistration(
        String fullName,
        String relationship,
        String phone) {

    public EmergencyContactRegistration {
        fullName = normalizeRequired(fullName, "Emergency contact name");
        relationship = normalizeRequired(relationship, "Emergency contact relationship");
        phone = normalizeRequired(phone, "Emergency contact phone");
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ClientValidationException(field + " must not be blank.");
        }
        return value.trim();
    }
}
