package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Compact projection used by the searchable client catalog. */
public record ClientSummary(
        UUID id,
        String clientCode,
        String firstName,
        String lastName,
        String phone,
        String email,
        ClientStatus status,
        String membershipStatus,
        LocalDate membershipExpiresOn,
        boolean photoAvailable,
        Instant updatedAt,
        long version,
        UUID homeBranchId) {

    public ClientSummary(
            UUID id,
            String clientCode,
            String firstName,
            String lastName,
            String phone,
            String email,
            ClientStatus status,
            String membershipStatus,
            LocalDate membershipExpiresOn,
            boolean photoAvailable,
            Instant updatedAt,
            long version) {
        this(id, clientCode, firstName, lastName, phone, email, status,
                membershipStatus, membershipExpiresOn, photoAvailable,
                updatedAt, version, null);
    }

    public ClientSummary {
        require(id, "Client id");
        clientCode = requiredText(clientCode, "Client code");
        firstName = requiredText(firstName, "Client first name");
        lastName = requiredText(lastName, "Client last name");
        phone = requiredText(phone, "Client phone");
        email = optionalText(email);
        require(status, "Client status");
        membershipStatus = optionalText(membershipStatus);
        require(updatedAt, "Client update timestamp");
        if (version < 0) {
            throw new IllegalArgumentException(
                    "Client version must not be negative.");
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static String requiredText(String value, String label) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
