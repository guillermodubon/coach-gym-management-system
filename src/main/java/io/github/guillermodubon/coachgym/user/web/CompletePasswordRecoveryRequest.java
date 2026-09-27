package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.user.CompletePasswordRecoveryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record CompletePasswordRecoveryRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(min = 12, max = 256) String newPassword,
        @NotBlank @Size(min = 12, max = 256) String passwordConfirmation) {

    CompletePasswordRecoveryCommand toCommand() {
        return new CompletePasswordRecoveryCommand(token, newPassword, passwordConfirmation);
    }

    @Override
    public String toString() {
        return "CompletePasswordRecoveryRequest[tokenPresent=" + (token != null)
                + ", newPasswordPresent=" + (newPassword != null)
                + ", passwordConfirmationPresent=" + (passwordConfirmation != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported password recovery completion field: " + name);
    }
}
