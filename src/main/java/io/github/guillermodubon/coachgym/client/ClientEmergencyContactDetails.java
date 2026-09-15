package io.github.guillermodubon.coachgym.client;

import java.util.UUID;

/** Emergency contact shown in the operational client profile. */
public record ClientEmergencyContactDetails(
        UUID id,
        String fullName,
        String relationship,
        String phone) {

    public ClientEmergencyContactDetails {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Emergency contact id is required.");
        }
        fullName = required(fullName, "Emergency contact name");
        relationship = required(relationship, "Emergency contact relationship");
        phone = required(phone, "Emergency contact phone");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }
}
