package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.application.EmergencyContactCommand;
import io.github.guillermodubon.coachgym.client.application.RegisterClientCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterClientRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 254) String email,
        @NotBlank @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        @Valid EmergencyContactRequest emergencyContact) {

    RegisterClientCommand toCommand() {
        EmergencyContactCommand emergencyContactCommand = emergencyContact == null
                ? null
                : emergencyContact.toCommand();
        return new RegisterClientCommand(
                firstName,
                lastName,
                email,
                phone,
                dateOfBirth,
                emergencyContactCommand);
    }
}
