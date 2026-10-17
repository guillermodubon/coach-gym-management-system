package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.guillermodubon.coachgym.user.application.ChangeStaffPasswordCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Password-change request whose values are never echoed or logged. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        name = "ChangeStaffPasswordRequest",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ChangeStaffPasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword,
        @NotBlank String newPasswordConfirmation) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported password request field: " + name);
    }

    ChangeStaffPasswordCommand toCommand() {
        return new ChangeStaffPasswordCommand(
                currentPassword, newPassword, newPasswordConfirmation);
    }

    @Override
    public String toString() {
        return "ChangeStaffPasswordRequest[currentPasswordPresent="
                + (currentPassword != null)
                + ", newPasswordPresent=" + (newPassword != null)
                + ", confirmationPresent=" + (newPasswordConfirmation != null)
                + "]";
    }
}
