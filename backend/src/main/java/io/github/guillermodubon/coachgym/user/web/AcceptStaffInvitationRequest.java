package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.user.AcceptStaffInvitationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Acceptance allowlist: token, credentials and profile names only. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record AcceptStaffInvitationRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(min = 12, max = 256) String password,
        @NotBlank @Size(min = 12, max = 256) String passwordConfirmation,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) {

    AcceptStaffInvitationCommand toCommand() {
        return new AcceptStaffInvitationCommand(
                token, password, passwordConfirmation, firstName, lastName);
    }

    @Override
    public String toString() {
        return "AcceptStaffInvitationRequest[tokenPresent=" + (token != null)
                + ", passwordPresent=" + (password != null)
                + ", passwordConfirmationPresent=" + (passwordConfirmation != null)
                + ", firstNamePresent=" + (firstName != null)
                + ", lastNamePresent=" + (lastName != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported invitation acceptance field: " + name);
    }
}
