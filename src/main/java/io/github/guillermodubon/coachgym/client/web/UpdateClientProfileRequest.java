package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.application.UpdateClientCommand;
import io.github.guillermodubon.coachgym.client.application.UpdateEmergencyContactCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record UpdateClientProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 32) String phone,
        @PastOrPresent LocalDate dateOfBirth,
        @Valid EmergencyContactProfileRequest emergencyContact,
        @NotNull @PositiveOrZero
        @Schema(description = "Current client version used for optimistic locking.")
        Long version) {

    UpdateClientCommand toCommand() {
        UpdateEmergencyContactCommand contact = emergencyContact == null
                ? null
                : emergencyContact.toCommand();
        return new UpdateClientCommand(
                firstName,
                lastName,
                email,
                phone,
                dateOfBirth,
                contact,
                version);
    }
}
