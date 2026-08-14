package io.github.guillermodubon.coachgym.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientDetails(
        UUID id,
        String clientCode,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        ClientStatus status,
        Instant createdAt,
        Instant updatedAt,
        EmergencyContactDetails emergencyContact) {
}
