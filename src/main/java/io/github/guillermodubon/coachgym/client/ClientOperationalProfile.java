package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Consolidated current-state projection used by reception and administration. */
public record ClientOperationalProfile(
        UUID id,
        String clientCode,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        ClientStatus status,
        ClientEmergencyContactDetails emergencyContact,
        ClientMembershipSummary currentMembership,
        ClientPaymentSummary currentPeriodPayment,
        ClientAccessSummary lastAccess,
        ClientPhotoDetails photo,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public ClientOperationalProfile {
        if (id == null) {
            throw new IllegalArgumentException("Client id is required.");
        }
        clientCode = required(clientCode, "Client code");
        firstName = required(firstName, "Client first name");
        lastName = required(lastName, "Client last name");
        phone = required(phone, "Client phone");
        email = optional(email);
        if (status == null) {
            throw new IllegalArgumentException("Client status is required.");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Client timestamps are required.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Client update timestamp must not precede creation.");
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "Client version must not be negative.");
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean hasCurrentMembership() {
        return currentMembership != null;
    }

    public boolean hasPhoto() {
        return photo != null;
    }

    private static String required(String value, String label) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
