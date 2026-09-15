package io.github.guillermodubon.coachgym.client.application;

import java.time.LocalDate;

/** Mutable client profile data accepted by the application layer. */
public record UpdateClientCommand(
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        UpdateEmergencyContactCommand emergencyContact,
        long expectedVersion) {

    public UpdateClientCommand {
        firstName = ClientCommandValidation.required(
                firstName, "Client first name", 100);
        lastName = ClientCommandValidation.required(
                lastName, "Client last name", 100);
        email = ClientCommandValidation.optionalEmail(email);
        phone = ClientCommandValidation.required(
                phone, "Client phone", 32);
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new ClientValidationException(
                    "Client date of birth must not be in the future.");
        }
        ClientCommandValidation.version(expectedVersion);
    }
}
