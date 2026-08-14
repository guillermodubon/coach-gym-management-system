package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientResponse(
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
        EmergencyContactResponse emergencyContact) {

    static ClientResponse from(ClientDetails client) {
        EmergencyContactResponse emergencyContact = client.emergencyContact() == null
                ? null
                : EmergencyContactResponse.from(client.emergencyContact());
        return new ClientResponse(
                client.id(),
                client.clientCode(),
                client.firstName(),
                client.lastName(),
                client.email(),
                client.phone(),
                client.dateOfBirth(),
                client.status(),
                client.createdAt(),
                client.updatedAt(),
                emergencyContact);
    }
}
