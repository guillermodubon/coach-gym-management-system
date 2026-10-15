package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.application.EmergencyContactCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 100) String relationship,
        @NotBlank @Size(max = 32) String phone) {

    EmergencyContactCommand toCommand() {
        return new EmergencyContactCommand(fullName, relationship, phone);
    }
}
