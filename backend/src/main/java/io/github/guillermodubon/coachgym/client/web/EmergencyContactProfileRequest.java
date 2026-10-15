package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.application.UpdateEmergencyContactCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record EmergencyContactProfileRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 100) String relationship,
        @NotBlank @Size(max = 32) String phone) {

    UpdateEmergencyContactCommand toCommand() {
        return new UpdateEmergencyContactCommand(fullName, relationship, phone);
    }
}
